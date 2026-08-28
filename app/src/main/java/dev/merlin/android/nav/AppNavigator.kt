package dev.merlin.android.nav

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Äquivalent zu `AppNavigator` aus `MerlinApp.swift`: ein zentraler, von
 * außerhalb der Compose-Hierarchie beschreibbarer Navigationszustand. Wird
 * von [dev.merlin.android.MainActivity] gesetzt, wenn ein Reminder-Tap die
 * Activity öffnet, und perspektivisch vom künftigen `ArticleReaderScreen`
 * (todo.md Abschnitt 9) konsumiert, um direkt zum Artikel zu springen.
 */
@Singleton
class AppNavigator @Inject constructor() {
    private val _articleIdToOpen = MutableStateFlow<Int?>(null)
    val articleIdToOpen: StateFlow<Int?> = _articleIdToOpen

    fun open(articleId: Int) {
        _articleIdToOpen.value = articleId
    }

    /** Vom UI-Layer aufzurufen, nachdem die Navigation ausgeführt wurde. */
    fun consume() {
        _articleIdToOpen.value = null
    }
}
