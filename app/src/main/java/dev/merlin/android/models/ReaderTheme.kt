package dev.merlin.android.models

/** Äquivalent zu `ReaderTheme` aus `PreferencesStore.swift`. */
enum class ReaderTheme {
    AUTO,
    LIGHT,
    DARK,
    SEPIA,
    ;

    val serverValue: String get() = name.lowercase()

    companion object {
        fun fromServerValue(value: String): ReaderTheme =
            entries.firstOrNull { it.serverValue == value } ?: AUTO
    }
}
