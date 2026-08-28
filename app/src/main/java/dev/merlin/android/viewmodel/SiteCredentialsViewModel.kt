package dev.merlin.android.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.merlin.android.models.SiteCredentialErrorResponse
import dev.merlin.android.models.SiteCredentialInfo
import dev.merlin.android.models.SiteCredentialUpdateRequest
import dev.merlin.android.network.MerlinApi
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import retrofit2.HttpException

/**
 * Äquivalent zu `SiteCredentialsViewModel.swift`: verwaltet Paywall-Site-Zugangsdaten
 * (getrennt von Nextcloud/Server-Login, siehe `SettingsViewModel`/`CredentialsStore`) über
 * `GET`/`PUT`/`DELETE /api/user/site-credentials(/{domain})` (`MerlinApi.kt`).
 *
 * `save()`/`delete()` folgen demselben `try { api… } catch (e: HttpException) { … }`-Muster
 * wie `ShareViewModel.saveArticle`, werten bei `save()` aber zusätzlich den vom Server
 * gelieferten JSON-Fehler-Body (`{ message, reason? }`, 400/401) aus – die einzige Stelle
 * im Client, die `retrofit2.HttpException.response()?.errorBody()` parst, da es bislang
 * keinen anderen Endpunkt mit typisiertem Fehler-Body gibt.
 */
@HiltViewModel
class SiteCredentialsViewModel @Inject constructor(
    private val api: MerlinApi,
    private val json: Json,
) : ViewModel() {

    private val _credentials = MutableStateFlow<List<SiteCredentialInfo>>(emptyList())
    val credentials: StateFlow<List<SiteCredentialInfo>> = _credentials.asStateFlow()

    private val _availableDomains = MutableStateFlow<List<String>>(emptyList())
    val availableDomains: StateFlow<List<String>> = _availableDomains.asStateFlow()

    /** `availableDomains` abzüglich bereits verbundener Domains – die "Hinzufügen"-Sektion in [dev.merlin.android.ui.screens.SiteCredentialsScreen]. */
    val connectableDomains: StateFlow<List<String>> = combine(_availableDomains, _credentials) { available, connected ->
        val connectedDomains = connected.map { it.domain }.toSet()
        available.filterNot { it in connectedDomains }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    init {
        load()
    }

    fun load() {
        _isLoading.value = true
        viewModelScope.launch {
            try {
                val response = api.getSiteCredentials()
                _credentials.value = response.credentials
                _availableDomains.value = response.availableDomains
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "Zugangsdaten konnten nicht geladen werden."
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun clearError() {
        _errorMessage.value = null
    }

    /**
     * Speichert Username/Passwort für `domain` (legt neu an oder ersetzt bestehende
     * Zugangsdaten). Liefert `true` bei Erfolg (UI zeigt dann "Erfolgreich gespeichert.")
     * und lädt die Liste neu; bei Fehler wird [errorMessage] aus dem Server-`message`-Feld
     * gesetzt und `false` zurückgegeben.
     */
    suspend fun save(domain: String, username: String, password: String): Boolean {
        _errorMessage.value = null
        return try {
            api.updateSiteCredential(domain, SiteCredentialUpdateRequest(username = username, password = password))
            load()
            true
        } catch (e: HttpException) {
            _errorMessage.value = parseErrorMessage(e) ?: "Zugangsdaten konnten nicht gespeichert werden."
            false
        } catch (e: Exception) {
            _errorMessage.value = e.message ?: "Zugangsdaten konnten nicht gespeichert werden."
            false
        }
    }

    /**
     * Entfernt die Zugangsdaten für `domain` und aktualisiert die Liste optimistisch.
     * `deleteSiteCredential` liefert `Response<Unit>` statt `SuccessResponse` (siehe `MerlinApi.kt`)
     * – anders als bei den übrigen, per Exception fehlschlagenden Calls muss der Erfolg hier daher
     * explizit über `isSuccessful` geprüft werden, statt sich auf eine geworfene `HttpException` zu verlassen.
     */
    fun delete(domain: String) {
        viewModelScope.launch {
            try {
                val response = api.deleteSiteCredential(domain)
                if (response.isSuccessful) {
                    _credentials.value = _credentials.value.filterNot { it.domain == domain }
                } else {
                    _errorMessage.value = "Zugangsdaten konnten nicht entfernt werden (HTTP ${response.code()})."
                }
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "Zugangsdaten konnten nicht entfernt werden."
            }
        }
    }

    /** Parst `{ "message": String, "reason": String? }` aus dem 400/401-Fehler-Body (siehe `SiteCredentialErrorResponse`). */
    private fun parseErrorMessage(e: HttpException): String? {
        val body = e.response()?.errorBody()?.string() ?: return null
        return runCatching { json.decodeFromString(SiteCredentialErrorResponse.serializer(), body) }
            .getOrNull()?.message
    }
}
