package dev.merlin.android.viewmodel

import dagger.hilt.android.lifecycle.HiltViewModel
import dev.merlin.android.network.CreateArticleRequest
import dev.merlin.android.network.CreateTagRequest
import dev.merlin.android.network.CredentialsStore
import dev.merlin.android.network.MerlinApi
import dev.merlin.android.models.Tag
import java.util.regex.Pattern
import javax.inject.Inject
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import retrofit2.HttpException

/**
 * Äquivalent zu `ShareViewController.swift` (MerlinShare-Extension). Anders als
 * iOS – wo die Extension in einem eigenen, eingeschränkten Prozess läuft und
 * daher Zugangsdaten über eine Keychain-Access-Group + eigenen `URLSession`-Client
 * lesen/aufrufen muss – ist `ShareActivity` eine normale Activity im selben
 * App-Prozess: [CredentialsStore] und [MerlinApi] (Hilt-Singletons) werden
 * direkt injiziert, kein separater Netzwerk-Stack nötig.
 *
 * **Architekturentscheidung (abweichend von iOS):** für den "Settings"-Modus
 * (noch nicht konfiguriert) nutzt iOS drei manuelle Textfelder (URL/Username/
 * App-Passwort), weil eine Extension keinen vollwertigen Custom-Tabs-Login
 * starten kann. Android-Activities können das problemlos – `ShareActivity`
 * zeigt in diesem Fall daher direkt den bestehenden `OnboardingScreen`
 * (Login Flow v2 über den System-Browser) statt einer Passwort-Eingabe, was
 * dem Rest der App entspricht und sicherer ist (Merlin sieht das
 * Nextcloud-Passwort nie).
 */
@HiltViewModel
class ShareViewModel @Inject constructor(
    private val api: MerlinApi,
    private val credentialsStore: CredentialsStore,
) : ViewModel() {

    enum class Mode { ONBOARDING, EXTRACTING, STAGING, SAVING, SUCCESS, ERROR, RATE_LIMITED }

    private val _mode = MutableStateFlow(if (credentialsStore.isConfigured) Mode.EXTRACTING else Mode.ONBOARDING)
    val mode: StateFlow<Mode> = _mode.asStateFlow()

    private val _pendingUrl = MutableStateFlow("")
    val pendingUrl: StateFlow<String> = _pendingUrl.asStateFlow()

    private val _availableTags = MutableStateFlow<List<Tag>>(emptyList())
    val availableTags: StateFlow<List<Tag>> = _availableTags.asStateFlow()

    private val _selectedTagIds = MutableStateFlow<Set<Int>>(emptySet())
    val selectedTagIds: StateFlow<Set<Int>> = _selectedTagIds.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    /** Von der UI beobachtet: wird `true`, sobald der Vorgang abgeschlossen ist und die Activity sich schließen soll. */
    private val _finished = MutableStateFlow(false)
    val finished: StateFlow<Boolean> = _finished.asStateFlow()

    /** Vom `OnboardingScreen` aufgerufen, sobald Login Flow v2 erfolgreich war. */
    fun onLoginSuccess() {
        _mode.value = Mode.EXTRACTING
    }

    /** Liest den geteilten Text/URL aus dem `ACTION_SEND`-Intent und sucht die erste http(s)-URL darin. */
    fun handleSharedText(rawText: String?) {
        if (_mode.value != Mode.EXTRACTING) return
        val url = rawText?.let { extractFirstUrl(it) }
        if (url == null) {
            showError("Keine URL im geteilten Inhalt gefunden.")
            return
        }
        _pendingUrl.value = url
        _mode.value = Mode.STAGING
        loadTags()
    }

    private fun loadTags() {
        viewModelScope.launch {
            _availableTags.value = runCatching { api.listTags() }.getOrDefault(emptyList())
        }
    }

    fun toggleTag(tagId: Int) {
        _selectedTagIds.value = _selectedTagIds.value.let { current ->
            if (current.contains(tagId)) current - tagId else current + tagId
        }
    }

    /**
     * Löst kommagetrennte, neu eingetippte Tag-Namen zu IDs auf (legt fehlende
     * Tags per POST an) und speichert den Artikel mit der Vereinigung aus
     * Chip-Auswahl und neuen Tags. Äquivalent zu `confirmSave()`/`resolveTagIds()`.
     */
    fun confirmSave(newTagNamesRaw: String) {
        val url = _pendingUrl.value
        if (url.isEmpty()) {
            finish()
            return
        }
        val newNames = newTagNamesRaw
            .split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        _mode.value = Mode.SAVING
        viewModelScope.launch {
            val resolvedIds = resolveTagIds(newNames)
            val allIds = (_selectedTagIds.value + resolvedIds).toList()
            saveArticle(url, allIds)
        }
    }

    private suspend fun resolveTagIds(names: List<String>): List<Int> {
        if (names.isEmpty()) return emptyList()
        val cached = _availableTags.value.ifEmpty {
            runCatching { api.listTags() }.getOrDefault(emptyList()).also { _availableTags.value = it }
        }
        val ids = mutableListOf<Int>()
        for (name in names) {
            val match = cached.firstOrNull { it.name.equals(name, ignoreCase = true) }
            if (match != null) {
                ids.add(match.id)
            } else {
                val created = runCatching { api.createTag(CreateTagRequest(name = name)) }.getOrNull()
                created?.let { ids.add(it.id) }
            }
        }
        return ids
    }

    private suspend fun saveArticle(url: String, tagIds: List<Int>) {
        try {
            api.createArticle(CreateArticleRequest(url = url, tagIds = tagIds))
            _mode.value = Mode.SUCCESS
            finish()
        } catch (e: HttpException) {
            when (e.code()) {
                401 -> showError("Anmeldung fehlgeschlagen.\nÜberprüfe deine Zugangsdaten in der Merlin-App.")
                404 -> showError("Merlin-App auf diesem Server nicht gefunden.")
                429 -> showRateLimited("Der Server begrenzt aktuell Anfragen.\nBitte kurz warten und erneut versuchen.")
                else -> showError("Serverfehler (HTTP ${e.code()}).")
            }
        } catch (e: Exception) {
            showError("Netzwerkfehler: ${e.message ?: "unbekannt"}")
        }
    }

    private fun showError(message: String) {
        _statusMessage.value = message
        _mode.value = Mode.ERROR
    }

    private fun showRateLimited(message: String) {
        _statusMessage.value = message
        _mode.value = Mode.RATE_LIMITED
    }

    /** Nach Rate-Limit-Anzeige zurück zur Staging-Ansicht, ohne Tags neu zu laden. */
    fun backToStaging() {
        _mode.value = Mode.STAGING
    }

    fun finish() {
        _finished.value = true
    }

    companion object {
        /**
         * Findet die erste http(s)-URL in einem beliebigen Text – Äquivalent zu
         * `extractFirstURL` (Swift), das dort `NSDataDetector` nutzt. Android hat
         * keinen direkten Link-Detector ohne UI-Kontext, daher ein Pattern analog
         * zum iOS-Fallback-Regex.
         */
        private val URL_PATTERN: Pattern = Pattern.compile("https?://\\S+")

        fun extractFirstUrl(text: String): String? {
            val matcher = URL_PATTERN.matcher(text)
            return if (matcher.find()) matcher.group() else null
        }
    }
}
