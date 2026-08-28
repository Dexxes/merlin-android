package dev.merlin.android.models

/** Äquivalent zu `ProgressEdge` aus `PreferencesStore.swift`. */
enum class ProgressEdge {
    LEFT,
    RIGHT,
    TOP,
    BOTTOM,
    OFF,
    ;

    val serverValue: String get() = name.lowercase()

    companion object {
        fun fromServerValue(value: String): ProgressEdge =
            entries.firstOrNull { it.serverValue == value } ?: LEFT
    }
}
