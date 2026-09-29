package dev.merlin.android.models

import kotlinx.serialization.Serializable

/**
 * Daten der Support-Infobox (Abo-/Spendenlink der Quelle) aus `GET /articles/{id}` – Äquivalent zu
 * `SupportBox` in `Article.swift` (merlin-ios). Fehlt (null), wenn die Domain weder Abo- noch
 * Spenden-URL hinterlegt hat oder der Nutzer dort einen aktiven Abo-Login hat.
 */
@Serializable
data class SupportBox(
    val siteName: String,
    val subscribeUrl: String? = null,
    val donationsUrl: String? = null,
    /** Akzentfarbe des Nutzers (`#RRGGBB`), wie sie der Server aus den Einstellungen kennt. */
    val accentColor: String = "#FF3B30",
    /** Icon der konkreten Artikelseite (apple-touch-icon/`rel=icon`), vom Server aus dem Seiten-HTML gelesen. */
    val iconUrl: String? = null,
)
