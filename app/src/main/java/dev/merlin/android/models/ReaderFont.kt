package dev.merlin.android.models

/** Äquivalent zu `ReaderFont` aus `PreferencesStore.swift`. */
enum class ReaderFont {
    SYSTEM,
    SERIF,
    SANS_SERIF,
    MONO,
    ;

    /** CSS-`font-family`-Wert für den HTML-Reader-Template (Abschnitt 9). */
    val cssValue: String
        get() = when (this) {
            SYSTEM -> "Roboto, 'Helvetica Neue', Arial, sans-serif"
            SERIF -> "Georgia, 'Times New Roman', serif"
            SANS_SERIF -> "Roboto, 'Helvetica Neue', sans-serif"
            MONO -> "'Roboto Mono', Menlo, 'Courier New', monospace"
        }

    /** Wert, den der Server für diese Schriftart verwendet. */
    val serverValue: String
        get() = when (this) {
            SYSTEM -> "default"
            SERIF -> "serif"
            SANS_SERIF -> "sans-serif"
            MONO -> "monospace"
        }

    companion object {
        fun fromServerValue(value: String): ReaderFont = when (value) {
            "serif" -> SERIF
            "sans-serif" -> SANS_SERIF
            "monospace" -> MONO
            else -> SYSTEM
        }
    }
}
