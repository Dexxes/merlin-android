package dev.merlin.android.models

import kotlinx.serialization.Serializable

/**
 * Äquivalent zu `SiteCredential.swift`: Login-Zugangsdaten pro Paywall-Domain
 * (z.B. Tagesspiegel Plus), die der Server nutzt, um bei der Volltext-Extraktion
 * hinter einer Paywall einzuloggen. Enthält bewusst **kein** Passwort-Feld – der
 * Server liefert es nie zurück (write-only, siehe `PUT /user/site-credentials/{domain}`
 * in `MerlinApi.kt`).
 */
@Serializable
data class SiteCredentialInfo(
    val domain: String,
    val status: String,
    val lastLoginAt: String? = null,
) {
    /** Äquivalent zu `SiteCredential.Status` (Swift-Enum) als Convenience-Property statt eigenem Enum-Typ (Server kann jederzeit neue Werte liefern). */
    enum class Status { OK, INVALID_CREDENTIALS, LOGIN_FLOW_BROKEN, PENDING }

    val statusEnum: Status
        get() = when (status) {
            "ok" -> Status.OK
            "invalid_credentials" -> Status.INVALID_CREDENTIALS
            "login_flow_broken" -> Status.LOGIN_FLOW_BROKEN
            else -> Status.PENDING
        }
}

/** Antwort von `GET /user/site-credentials`. */
@Serializable
data class SiteCredentialsResponse(
    val credentials: List<SiteCredentialInfo> = emptyList(),
    val availableDomains: List<String> = emptyList(),
)

/** Body für `PUT /user/site-credentials/{domain}`. */
@Serializable
data class SiteCredentialUpdateRequest(
    val username: String,
    val password: String,
)

/**
 * Fehler-Body von `PUT /user/site-credentials/{domain}` bei 400/401 – siehe
 * `SiteCredentialsViewModel.save()`, das `reason` derzeit nicht separat auswertet
 * (nur `message` wird angezeigt, analog zum iOS-Original).
 */
@Serializable
data class SiteCredentialErrorResponse(
    val message: String,
    val reason: String? = null,
)
