package dev.merlin.android.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.merlin.android.auth.LoginFlowService
import dev.merlin.android.data.ArticleCacheService
import dev.merlin.android.data.HighlightCacheService
import dev.merlin.android.data.ImageCacheService
import dev.merlin.android.data.PreferencesStore
import dev.merlin.android.models.ArticleFilter
import dev.merlin.android.models.ProgressEdge
import dev.merlin.android.network.CredentialsStore
import dev.merlin.android.network.MerlinApi
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Äquivalent zu `SettingsView.swift`: Account (Login/Logout), Verbindungstest,
 * App-Präferenzen mit Server-Sync, Cache leeren, Entwicklermodus, Versionsanzeige
 * (Letztere direkt in `SettingsScreen.kt` über `BuildConfig`, kein State hier nötig).
 *
 * Die Login-Logik ist hier bewusst aus [OnboardingViewModel] dupliziert statt es zu
 * injizieren (ViewModels sollten keine anderen ViewModels referenzieren) – beide
 * kapseln denselben [LoginFlowService], genau wie iOS' `OnboardingView` und
 * `SettingsView` jeweils ein eigenes `LoginFlowService`-`@StateObject` halten.
 *
 * `preferencesStore` ist public, analog zum bestehenden `ArticleReaderViewModel.preferencesStore`-
 * Pattern (siehe `AppearanceSheet.kt`): die UI liest dessen reaktive `Flow`s direkt per
 * `collectAsState()`, Schreibzugriffe laufen aber über die Setter hier, damit nach jeder
 * sync-fähigen Änderung automatisch [syncPreferences] aufgerufen wird (Äquivalent zu
 * `SettingsView.syncPreferences()`).
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    val preferencesStore: PreferencesStore,
    private val credentialsStore: CredentialsStore,
    private val api: MerlinApi,
    private val loginFlowService: LoginFlowService,
    private val articleCacheService: ArticleCacheService,
    private val imageCacheService: ImageCacheService,
    private val highlightCacheService: HighlightCacheService,
) : ViewModel() {

    /** Äquivalent zu `SettingsView.TestResult` (Swift-Enum). */
    sealed class TestResult {
        data class Success(val message: String) : TestResult()
        data class Failure(val message: String) : TestResult()
    }

    private val _nextcloudUrl = MutableStateFlow(credentialsStore.nextcloudUrl)
    val nextcloudUrl: StateFlow<String> = _nextcloudUrl.asStateFlow()

    private val _backendKind = MutableStateFlow(credentialsStore.backendKind)
    val backendKind: StateFlow<CredentialsStore.BackendKind> = _backendKind.asStateFlow()

    private val _username = MutableStateFlow(credentialsStore.username)
    val username: StateFlow<String> = _username.asStateFlow()

    private val _isConfigured = MutableStateFlow(credentialsStore.isConfigured)
    val isConfigured: StateFlow<Boolean> = _isConfigured.asStateFlow()

    /** Deckt sowohl den `start()`-Request als auch die Poll-Phase ab (siehe `OnboardingViewModel`). */
    private val _isLoginLoading = MutableStateFlow(false)
    val isLoginLoading: StateFlow<Boolean> = _isLoginLoading.asStateFlow()

    /** URL, die die UI per Custom Tabs öffnen soll, sobald sie sich ändert (siehe `OnboardingScreen`). */
    val loginUrl: StateFlow<String?> = loginFlowService.loginUrl

    private val _loginError = MutableStateFlow<String?>(null)
    val loginError: StateFlow<String?> = _loginError.asStateFlow()

    private val _loginSuccess = MutableStateFlow(false)
    val loginSuccess: StateFlow<Boolean> = _loginSuccess.asStateFlow()

    private val _isTestingConnection = MutableStateFlow(false)
    val isTestingConnection: StateFlow<Boolean> = _isTestingConnection.asStateFlow()

    private val _testResult = MutableStateFlow<TestResult?>(null)
    val testResult: StateFlow<TestResult?> = _testResult.asStateFlow()

    private val _cacheCleared = MutableStateFlow(false)
    val cacheCleared: StateFlow<Boolean> = _cacheCleared.asStateFlow()

    /**
     * Äquivalent zu `SettingsView.swift`'s `.task`-Block (siehe dort Zeile ~308):
     * beim Öffnen der Settings werden die Server-Werte gezogen und per
     * `loadFromServer` lokal übernommen ("Server gewinnt"). Ohne diesen Aufruf war
     * `PreferencesStore.loadFromServer()` totes Pendant – Android hat nie etwas vom
     * Server gepullt, sondern bei jedem `syncPreferences()` nur lokale (Default-)
     * Werte hochgeschrieben. Das war die eigentliche Ursache dafür, dass eine am
     * Web-UI/iOS gesetzte Akzentfarbe auf Android nie ankam.
     */
    init {
        viewModelScope.launch {
            if (credentialsStore.isConfigured) {
                runCatching { api.getSettings() }.getOrNull()?.let { preferencesStore.loadFromServer(it) }
            }
        }
    }

    /** UI ruft dies bei jeder Auswahländerung, damit LoginFlowService.start() bereits den richtigen Wert liest. */
    fun setBackendKind(value: CredentialsStore.BackendKind) {
        credentialsStore.backendKind = value
        _backendKind.value = value
    }

    /** Startet Login Flow v2 (Äquivalent zu `SettingsView.startLoginFlow()`). */
    fun startLogin(serverUrl: String) {
        _loginError.value = null
        _loginSuccess.value = false
        _isLoginLoading.value = true
        viewModelScope.launch {
            try {
                loginFlowService.start(serverUrl)
                loginFlowService.pollForCredentials()
                refreshAccountInfo()
                _loginSuccess.value = true
            } catch (e: Exception) {
                _loginError.value = e.message ?: "Login fehlgeschlagen"
            } finally {
                _isLoginLoading.value = false
            }
        }
    }

    fun cancelLogin() {
        loginFlowService.cancel()
        _isLoginLoading.value = false
    }

    /**
     * Äquivalent zu `SettingsView.logout()`. Das iOS-Original löscht zusätzlich
     * `WKWebsiteDataStore`-Cookies, da dort der Login in einer eingebetteten `WKWebView`
     * läuft – auf Android läuft der Login per Custom Tabs im System-Browser
     * (`OnboardingScreen`/`SettingsScreen`), es gibt also keine App-eigene
     * Browser-Session, die bereinigt werden müsste.
     */
    fun logout() {
        credentialsStore.clearCredentials()
        refreshAccountInfo()
        _testResult.value = null
    }

    /** Äquivalent zu `SettingsView.testConnection()`. */
    fun testConnection() {
        _isTestingConnection.value = true
        _testResult.value = null
        viewModelScope.launch {
            _testResult.value = try {
                // Kein dediziertes `/test`-Endpoint im Backend (`MerlinAPI.testConnection()`
                // auf iOS ruft ebenfalls nur einen leichten, bereits authentifizierten
                // GET-Call auf) – `getSettings()` validiert hier Basic-Auth + Erreichbarkeit.
                api.getSettings()
                TestResult.Success("Verbindung erfolgreich!")
            } catch (e: Exception) {
                TestResult.Failure(e.message ?: "Verbindung fehlgeschlagen")
            }
            _isTestingConnection.value = false
        }
    }

    fun setDefaultFilter(value: ArticleFilter) = viewModelScope.launch {
        preferencesStore.setDefaultFilter(value)
        syncPreferences()
    }

    fun setProgressEdge(value: ProgressEdge) = viewModelScope.launch {
        preferencesStore.setProgressEdge(value)
        syncPreferences()
    }

    fun setSaveProgress(value: Boolean) = viewModelScope.launch {
        preferencesStore.setSaveProgress(value)
        syncPreferences()
    }

    fun setResumeOnOpen(value: Boolean) = viewModelScope.launch {
        preferencesStore.setResumeOnOpen(value)
        syncPreferences()
    }

    /** Kein Server-Feld in `Settings` (Swift-Pendant ist ebenfalls rein lokal) – kein [syncPreferences]-Aufruf nötig. */
    fun setPrefetchWifiOnly(value: Boolean) = viewModelScope.launch {
        preferencesStore.setPrefetchImagesOnWifiOnly(value)
    }

    /** Rein lokal (Speicherkapazität ist pro Gerät unterschiedlich) – kein [syncPreferences]-Aufruf nötig. */
    fun setCacheRetentionDays(value: Int) = viewModelScope.launch {
        preferencesStore.setCacheRetentionDays(value)
    }

    /** Äquivalent zu iOS' `@AppStorage("merlin_developer_mode")` – rein lokal, kein Server-Sync. */
    fun setDeveloperMode(value: Boolean) = viewModelScope.launch {
        preferencesStore.setDeveloperMode(value)
    }

    /**
     * Äquivalent zu `SettingsView.syncPreferences()`. iOS fängt `networkError` ab und
     * markiert `SettingsSyncQueue` für einen späteren Retry – dieses Offline-Queue-Äquivalent
     * existiert auf Android noch nicht (todo.md), daher wird ein Fehlschlag hier bewusst
     * verschluckt: der lokale Wert bleibt in jedem Fall gesetzt, nur der Server-Sync entfällt.
     */
    private suspend fun syncPreferences() {
        runCatching { api.updateSettings(preferencesStore.toServerSettings()) }
    }

    /**
     * Äquivalent zu `SettingsView.clearCache()`: löscht alle Caches, Lesepositionen und
     * Zugangsdaten – der Nutzer wird dadurch implizit ausgeloggt (`isConfigured` wird `false`).
     */
    fun clearCache() {
        viewModelScope.launch {
            preferencesStore.clearReadingPositions()
            articleCacheService.clear()
            imageCacheService.clear()
            highlightCacheService.clear()
            credentialsStore.clearCredentials()
            refreshAccountInfo()
            _cacheCleared.value = true
        }
    }

    private fun refreshAccountInfo() {
        _nextcloudUrl.value = credentialsStore.nextcloudUrl
        _username.value = credentialsStore.username
        _isConfigured.value = credentialsStore.isConfigured
    }
}
