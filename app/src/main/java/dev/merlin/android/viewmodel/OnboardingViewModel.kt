package dev.merlin.android.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.merlin.android.R
import dev.merlin.android.auth.LoginFlowService
import dev.merlin.android.auth.loginErrorMessage
import dev.merlin.android.network.CredentialsStore
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Verdrahtet [LoginFlowService] mit der Onboarding-UI – Äquivalent zur
 * `LoginFlowService.isLoading`/`loginURL`-Beobachtung in `OnboardingView`/
 * `SettingsView` auf iOS, dort direkt über `@Published` auf dem Service.
 * Hier als eigenes ViewModel, weil [LoginFlowService] selbst kein
 * Compose-/Lifecycle-bewusster State-Holder ist.
 */
@HiltViewModel
class OnboardingViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val loginFlowService: LoginFlowService,
    private val credentialsStore: CredentialsStore,
) : ViewModel() {

    /** Deckt sowohl den initialen `start()`-Request als auch die Poll-Phase ab –
     * `LoginFlowService.isLoading` allein wäre während des Pollings bereits wieder `false`. */
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    /** URL, die die UI per Custom Tabs öffnen soll, sobald sie sich ändert. */
    val loginUrl: StateFlow<String?> = loginFlowService.loginUrl

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _loginSuccess = MutableStateFlow(false)
    val loginSuccess: StateFlow<Boolean> = _loginSuccess.asStateFlow()

    /** Laufender Start+Polling-Vorgang, damit "Abbrechen" ihn wirklich beenden kann. */
    private var loginJob: Job? = null

    /** Vorbelegung für die Backend-Auswahl in der UI (zuletzt gewählte Art, Default NEXTCLOUD). */
    val initialBackendKind: CredentialsStore.BackendKind get() = credentialsStore.backendKind

    /**
     * Startet Login Flow v2: holt die Login-URL, pollt danach automatisch weiter.
     * `backendKind` wird VOR dem Start gesetzt, da LoginFlowService.start() ihn
     * liest, um zwischen Nextclouds `/index.php/login/v2` und merlin-servers
     * `/login/v2` zu unterscheiden (siehe dortiger Kommentar).
     */
    fun startLogin(serverUrl: String, backendKind: CredentialsStore.BackendKind) {
        credentialsStore.backendKind = backendKind
        _error.value = null
        _isLoading.value = true
        loginJob?.cancel()
        loginJob = viewModelScope.launch {
            try {
                loginFlowService.start(serverUrl)
                loginFlowService.pollForCredentials()
                _loginSuccess.value = true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _error.value = loginErrorMessage(context, e)
            } finally {
                // Ein abgelöster Vorgang darf den Ladezustand des neuen nicht zurücksetzen.
                if (loginJob == null || loginJob === coroutineContext[Job]) _isLoading.value = false
            }
        }
    }

    fun cancelLogin() {
        // Polling-Coroutine wirklich beenden (iOS: `pollTask?.cancel()`), sonst
        // pollt sie mit ihren lokalen Token-Kopien bis zum Timeout weiter.
        loginJob?.cancel()
        loginJob = null
        loginFlowService.cancel()
        _isLoading.value = false
    }

    fun isAlreadyConfigured(): Boolean = credentialsStore.isConfigured
}
