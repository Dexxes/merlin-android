package dev.merlin.android.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.ImageLoader
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.merlin.android.data.ArticleCacheService
import dev.merlin.android.data.HighlightCacheService
import dev.merlin.android.data.OfflineHighlightQueue
import dev.merlin.android.data.OfflineMutationQueue
import dev.merlin.android.data.PreferencesStore
import dev.merlin.android.data.ReminderService
import dev.merlin.android.data.ReportService
import dev.merlin.android.di.ApplicationScope
import dev.merlin.android.models.Article
import dev.merlin.android.models.ArticleFilter
import dev.merlin.android.models.Highlight
import dev.merlin.android.models.HighlightCreate
import dev.merlin.android.models.ProgressEdge
import dev.merlin.android.models.ReaderFont
import dev.merlin.android.models.ReaderTheme
import dev.merlin.android.models.Reminder
import dev.merlin.android.models.Tag
import dev.merlin.android.network.ArticleShareResponse
import dev.merlin.android.network.CreateShareRequest
import dev.merlin.android.network.CreateTagRequest
import dev.merlin.android.network.MerlinApi
import dev.merlin.android.network.UpdateProgressRequest
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Äquivalent zum Lese-relevanten Teil von `ArticleReaderView.swift` +
 * `ArticleReaderViewModel`-artigem State, den das iOS-Original direkt in der
 * View hält. Bewusst eine eigene ViewModel-Instanz statt Wiederverwendung von
 * [ArticlesViewModel]: der Reader braucht einen unabhängigen Lebenszyklus
 * (nav-graph-/Activity-scoped wäre unnötig eng an die Liste gekoppelt) und
 * eigene Appearance-/Highlight-/Scroll-State, die die Liste nicht kennt.
 * Listen-Mutationen (Favorit/Archiv/Löschen) werden hier dünn dupliziert
 * statt über [ArticlesViewModel] geteilt, da beide ViewModels sonst denselben
 * Artikel parallel optimistisch verändern könnten.
 */
@HiltViewModel
class ArticleReaderViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val api: MerlinApi,
    private val articleCacheService: ArticleCacheService,
    private val highlightCacheService: HighlightCacheService,
    private val offlineMutationQueue: OfflineMutationQueue,
    private val offlineHighlightQueue: OfflineHighlightQueue,
    private val reminderService: ReminderService,
    private val reportService: ReportService,
    val preferencesStore: PreferencesStore,
    /** Geteilter Coil-Loader mit [dev.merlin.android.data.RefererInterceptor] – auch von [ImageLightboxScreen] genutzt, damit Lightbox-Bilder denselben Hotlink-Schutz/Disk-Cache wie der Rest der App nutzen. */
    val imageLoader: ImageLoader,
    /** App-überlebender Scope für das Speichern/Pushen der Scroll-Position – siehe [ApplicationScope]. */
    @ApplicationScope private val appScope: CoroutineScope,
) : ViewModel() {

    val articleId: Int = checkNotNull(savedStateHandle["articleId"]) { "articleId fehlt in SavedStateHandle" }

    // MARK: – Artikel

    private val _article = MutableStateFlow<Article?>(null)
    val article: StateFlow<Article?> = _article.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _deleted = MutableStateFlow(false)
    /** Wird true, sobald der Artikel gelöscht wurde – Reader-Screen sollte dann zurücknavigieren. */
    val deleted: StateFlow<Boolean> = _deleted.asStateFlow()

    // MARK: – Wiederherzustellende Leseposition (Fraktion 0..1)

    /**
     * `null` = noch nicht ermittelt (Artikel lädt). Sobald gesetzt, rendert der
     * Reader die WebView und stellt diese Fraktion wieder her. Bewusst erst nach
     * dem Artikel-Load gesetzt (in [reconcileInitialScroll]), da die
     * geräteübergreifende Auflösung den Server-Wert aus dem geladenen Artikel
     * braucht – das ersetzt die frühere, race-anfällige LaunchedEffect-Logik.
     */
    private val _initialScrollProgress = MutableStateFlow<Float?>(null)
    val initialScrollProgress: StateFlow<Float?> = _initialScrollProgress.asStateFlow()

    // MARK: – Tags (für EditTagsDialog im Drawer)

    private val _allTags = MutableStateFlow<List<Tag>>(emptyList())
    /** Wird erst bei Öffnen des Tag-Editors per [loadTags] befüllt – der Reader braucht die volle Tag-Liste sonst nicht. */
    val allTags: StateFlow<List<Tag>> = _allTags.asStateFlow()

    fun loadTags() {
        viewModelScope.launch {
            _allTags.value = runCatching { api.listTags() }.getOrDefault(emptyList())
        }
    }

    // MARK: – Highlights

    private val _highlights = MutableStateFlow<List<Highlight>>(emptyList())
    val highlights: StateFlow<List<Highlight>> = _highlights.asStateFlow()

    // MARK: – Reminder (Äquivalent zum `currentReminder`-Binding in ReminderSheet.swift)

    private val _currentReminder = MutableStateFlow<Reminder?>(null)
    val currentReminder: StateFlow<Reminder?> = _currentReminder.asStateFlow()

    private val _reminderError = MutableStateFlow<String?>(null)
    val reminderError: StateFlow<String?> = _reminderError.asStateFlow()

    // MARK: – Report (Äquivalent zu den `isSending`/`feedback`-Bindings in ReportArticleSheet.swift)

    private val _reportSending = MutableStateFlow(false)
    val reportSending: StateFlow<Boolean> = _reportSending.asStateFlow()

    private val _reportFeedback = MutableStateFlow<ReportFeedback?>(null)
    val reportFeedback: StateFlow<ReportFeedback?> = _reportFeedback.asStateFlow()

    // MARK: – Appearance (direkt aus PreferencesStore gespeist, reaktiv für HTML-Rebuild)

    val readerTheme: StateFlow<ReaderTheme> = preferencesStore.readerTheme
        .stateIn(viewModelScope, SharingStarted.Eagerly, ReaderTheme.AUTO)
    val readerFont: StateFlow<ReaderFont> = preferencesStore.readerFont
        .stateIn(viewModelScope, SharingStarted.Eagerly, ReaderFont.SYSTEM)
    val readerFontSize: StateFlow<Int> = preferencesStore.readerFontSize
        .stateIn(viewModelScope, SharingStarted.Eagerly, 17)
    val lineHeight: StateFlow<Double> = preferencesStore.lineHeight
        .stateIn(viewModelScope, SharingStarted.Eagerly, 1.6)
    val accentColorHex: StateFlow<String> = preferencesStore.accentProgressColorHex
        .stateIn(viewModelScope, SharingStarted.Eagerly, "#FF3B30")
    val progressEdge: StateFlow<ProgressEdge> = preferencesStore.progressEdge
        .stateIn(viewModelScope, SharingStarted.Eagerly, ProgressEdge.LEFT)

    /** Fasst alle Appearance-Werte zusammen – ein Wechsel hier löst im Reader-Screen den vollen HTML-Rebuild aus. */
    val appearance: StateFlow<Appearance> = combine(
        readerTheme, readerFont, readerFontSize, lineHeight, accentColorHex,
    ) { theme, font, fontSize, lineHeightValue, accent ->
        Appearance(theme, font, fontSize, lineHeightValue, accent)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, Appearance(ReaderTheme.AUTO, ReaderFont.SYSTEM, 17, 1.6, "#FF3B30"))

    data class Appearance(
        val theme: ReaderTheme,
        val font: ReaderFont,
        val fontSize: Int,
        val lineHeight: Double,
        val accentColorHex: String,
    )

    init {
        load()
    }

    // MARK: – Appearance: Server-Sync

    /**
     * Pusht Theme/Font/Größe/Zeilenhöhe/Akzentfarbe/Fortschrittsbalken zum Server – Äquivalent zu
     * iOS' `pushAppearanceToServer()` (ArticleReaderView.swift, aufgerufen in jedem `onChange`
     * der Appearance-Bindings). Ohne diesen Aufruf blieb die Auswahl rein lokal: das nächste
     * "Server gewinnt"-`loadFromServer()` (ArticlesViewModel.init/SettingsViewModel.init, jeweils
     * bei App-Start) überschrieb sie beim nächsten Start wieder mit dem alten Serverstand – sichtbar
     * z. B. als Reset von Dunkel zurück auf Hell nach Neustart der App.
     */
    suspend fun syncAppearanceToServer() {
        runCatching { api.updateSettings(preferencesStore.toServerSettings()) }
    }

    // MARK: – Load

    fun load() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val fetched = api.getArticle(articleId)
                _article.value = fetched
                articleCacheService.upsert(fetched)
                reconcileInitialScroll(fetched)
            } catch (e: Exception) {
                val cached = articleCacheService.loadFiltered(ArticleFilter.ALL, tagId = null)
                    .firstOrNull { it.id == articleId }
                    ?: articleCacheService.loadFiltered(ArticleFilter.ARCHIVE, tagId = null)
                        .firstOrNull { it.id == articleId }
                if (cached != null) {
                    _article.value = cached
                    reconcileInitialScroll(cached)
                } else {
                    _error.value = e.message ?: "Artikel konnte nicht geladen werden"
                    // Ohne Artikel keine sinnvolle Server-Position – von oben starten,
                    // damit der Reader nicht ewig im Lade-Branch (initialScrollProgress == null) hängt.
                    _initialScrollProgress.value = 0f
                }
            }
            _isLoading.value = false
            loadHighlights()
            loadReminder()
        }
    }

    // MARK: – Scroll-Position: geräteübergreifende Synchronisation
    //
    // Synchronisiert wird die Fraktion (0..1), nicht der Pixel-Offset (Pixel sind
    // gerätespezifisch). Konfliktauflösung ist Last-Write-Wins per Zeitstempel:
    // lokaler vs. Server-`scrollUpdatedAt`, der neuere gewinnt. So setzt sich die
    // zuletzt gespeicherte Position durch – egal von welchem Gerät.

    /**
     * Ermittelt die wiederherzustellende Fraktion aus lokalem Wert und
     * Server-Wert des geladenen Artikels und veröffentlicht sie über
     * [initialScrollProgress]. Respektiert die `resumeOnOpen`-Einstellung.
     */
    private suspend fun reconcileInitialScroll(loaded: Article) {
        if (!preferencesStore.resumeOnOpen.first()) {
            _initialScrollProgress.value = 0f
            return
        }
        val localTs = preferencesStore.savedScrollTimestamp(articleId)
        val localPct = preferencesStore.savedScrollProgress(articleId)
        // Server gewinnt nur, wenn sein Schreibvorgang strikt neuer ist als der lokale
        // (z.B. an einem anderen Gerät weitergelesen). Bei Gleichstand bleibt der lokale
        // Wert (identisch zum eigenen letzten Push).
        val target = if (loaded.scrollUpdatedAt > localTs) loaded.scrollProgress else localPct
        _initialScrollProgress.value = target.coerceIn(0f, 1f)
    }

    // MARK: – Reminder: Mutationen (Äquivalent zu den Aktionen in ReminderSheet.swift)

    private fun loadReminder() {
        viewModelScope.launch {
            _currentReminder.value = reminderService.reminder(articleId)
        }
    }

    /** Plant einen Reminder für [triggerAtMillis]; ersetzt einen ggf. bestehenden (siehe ReminderService.schedule). */
    fun scheduleReminder(triggerAtMillis: Long) {
        val current = _article.value ?: return
        viewModelScope.launch {
            _reminderError.value = null
            try {
                reminderService.schedule(current, triggerAtMillis)
                _currentReminder.value = reminderService.reminder(articleId)
            } catch (e: Exception) {
                _reminderError.value = e.message ?: "Erinnerung konnte nicht gesetzt werden"
            }
        }
    }

    /** Fire-and-forget wie im iOS-Original: UI wird sofort zurückgesetzt, Stornierung läuft im Hintergrund. */
    fun cancelReminder() {
        _currentReminder.value = null
        viewModelScope.launch { reminderService.cancel(articleId) }
    }

    fun clearReminderError() {
        _reminderError.value = null
    }

    // MARK: – Report: Aktion (Äquivalent zum `onSend`-Closure in ArticleReaderView.swift)

    /** Meldet den aktuellen Artikel mit optionalem [comment] ans merlin-reports-Backend. */
    fun sendReport(comment: String) {
        val current = _article.value ?: return
        viewModelScope.launch {
            _reportSending.value = true
            try {
                reportService.report(url = current.url, comment = comment.trim())
                _reportFeedback.value = ReportFeedback.Success
            } catch (e: Exception) {
                _reportFeedback.value = ReportFeedback.Failure(e.message ?: "Unbekannter Fehler")
            }
            _reportSending.value = false
        }
    }

    /** Äquivalent zu `onDismiss: { reportComment = "" }` im iOS-Original – setzt das Sheet für die nächste Öffnung zurück. */
    fun clearReportFeedback() {
        _reportFeedback.value = null
    }

    private fun loadHighlights() {
        viewModelScope.launch {
            try {
                val fetched = api.getHighlights(articleId)
                _highlights.value = fetched
                highlightCacheService.replaceAll(fetched, articleId)
            } catch (e: Exception) {
                _highlights.value = highlightCacheService.highlights(articleId)
            }
            offlineHighlightQueue.onDrained = { loadHighlights() }
            offlineHighlightQueue.drain()
        }
    }

    // MARK: – Highlights: Mutationen

    fun createHighlight(payload: HighlightCreate) {
        viewModelScope.launch {
            try {
                val real = api.createHighlight(articleId, payload)
                _highlights.value = _highlights.value + real
                highlightCacheService.upsert(real)
            } catch (e: Exception) {
                if (e is IOException) {
                    val optimistic = offlineHighlightQueue.enqueueCreate(articleId, payload)
                    _highlights.value = _highlights.value + optimistic
                    offlineHighlightQueue.scheduleDrain()
                } else {
                    _error.value = e.message ?: "Highlight konnte nicht gespeichert werden"
                }
            }
        }
    }

    fun deleteHighlight(highlight: Highlight) {
        viewModelScope.launch {
            _highlights.value = _highlights.value.filterNot { it.id == highlight.id }
            if (highlight.id < 0) {
                // Noch nicht serverseitig angelegt – Cancel-Logik in der Queue übernimmt das Entfernen.
                offlineHighlightQueue.enqueueDelete(highlight)
                return@launch
            }
            try {
                api.deleteHighlight(highlight.id)
                highlightCacheService.remove(highlight.id, articleId)
            } catch (e: Exception) {
                if (e is IOException) {
                    offlineHighlightQueue.enqueueDelete(highlight)
                    offlineHighlightQueue.scheduleDrain()
                } else {
                    // Echter Serverfehler: Highlight wieder anzeigen.
                    _highlights.value = _highlights.value + highlight
                    _error.value = e.message ?: "Highlight konnte nicht gelöscht werden"
                }
            }
        }
    }

    /**
     * Speichert die Leseposition beim Verlassen des Readers (einmalig, analog
     * iOS' `.onDisappear`): lokal in den DataStore UND als Push an den Server
     * für die geräteübergreifende Synchronisation.
     *
     * Läuft bewusst im [appScope], nicht im `viewModelScope`: das `onCleared()`
     * des ViewModels feuert beim Zurücknavigieren fast zeitgleich mit dem
     * auslösenden `onDispose` und würde einen `viewModelScope`-Job (DataStore-
     * Write + Netzwerk-Push) sonst abbrechen, bevor er fertig ist.
     *
     * Respektiert `save_progress`: ist das Setting aus, wird weder lokal
     * gespeichert noch synchronisiert.
     */
    fun saveScrollProgress(progress: Float) {
        appScope.launch {
            if (!preferencesStore.saveProgress.first()) return@launch
            val now = System.currentTimeMillis()
            val clamped = progress.coerceIn(0f, 1f)
            preferencesStore.saveScrollProgress(articleId, clamped, now)
            // Fire-and-forget: bei Offline/Server-Fehler bleibt der lokale Wert erhalten,
            // andere Geräte sehen ihn erst nach einem späteren erfolgreichen Push
            // (kein Offline-Retry in v1 – analog fehlendem SettingsSyncQueue).
            runCatching { api.updateProgress(articleId, UpdateProgressRequest(clamped, now)) }
        }
    }

    // MARK: – Artikel-Mutationen (dünne Duplikation aus ArticlesViewModel, siehe Klassen-Kommentar)

    fun toggleFavorite() {
        val current = _article.value ?: return
        viewModelScope.launch {
            val willBeFavorite = !current.isFavorite
            val optimistic = current.copy(
                favoritedAt = if (willBeFavorite) java.time.Instant.now().toString() else null,
            )
            _article.value = optimistic
            try {
                api.toggleFavorite(current.id)
                val updated = api.getArticle(current.id)
                _article.value = updated
                articleCacheService.upsert(updated)
            } catch (e: Exception) {
                if (e is IOException) {
                    articleCacheService.upsert(optimistic)
                    offlineMutationQueue.enqueueToggleFavorite(current.id)
                    offlineMutationQueue.scheduleDrain()
                } else {
                    _article.value = current
                    _error.value = e.message ?: "Unbekannter Fehler"
                }
            }
        }
    }

    fun toggleArchive() {
        val current = _article.value ?: return
        viewModelScope.launch {
            val willBeArchived = !current.isArchived
            val optimistic = current.copy(
                isArchived = willBeArchived,
                archivedAt = if (willBeArchived) java.time.Instant.now().toString() else null,
            )
            _article.value = optimistic
            try {
                api.toggleArchive(current.id)
                val updated = api.getArticle(current.id)
                _article.value = updated
                articleCacheService.upsert(updated)
            } catch (e: Exception) {
                if (e is IOException) {
                    articleCacheService.upsert(optimistic)
                    offlineMutationQueue.enqueueToggleArchive(current.id)
                    offlineMutationQueue.scheduleDrain()
                } else {
                    _article.value = current
                    _error.value = e.message ?: "Unbekannter Fehler"
                }
            }
        }
    }

    fun delete() {
        val current = _article.value ?: return
        viewModelScope.launch {
            try {
                api.deleteArticle(current.id)
                articleCacheService.remove(current.id)
                _deleted.value = true
            } catch (e: Exception) {
                if (e is IOException) {
                    articleCacheService.remove(current.id)
                    offlineMutationQueue.enqueueDelete(current.id)
                    offlineMutationQueue.scheduleDrain()
                    _deleted.value = true
                } else {
                    _error.value = e.message ?: "Unbekannter Fehler"
                }
            }
        }
    }

    fun setTags(tagIds: Set<Int>) {
        viewModelScope.launch { doSetTags(tagIds) }
    }

    private suspend fun doSetTags(tagIds: Set<Int>) {
        val current = _article.value ?: return
        // Gegen server-frischen Stand diffen statt gegen `current` (analog
        // ArticlesViewModel.doSetTags) – sonst würde ein zwischenzeitlich von
        // woanders hinzugefügtes Tag hier fälschlich als "soll entfernt
        // werden" erkannt.
        val baseline = runCatching { api.getArticle(current.id) }.getOrDefault(current)
        val currentIds = baseline.tags.map { it.id }.toSet()
        val toAdd = tagIds - currentIds
        val toRemove = currentIds - tagIds
        try {
            toAdd.forEach { api.addTagToArticle(current.id, it) }
            toRemove.forEach { api.removeTagFromArticle(current.id, it) }
            val updated = api.getArticle(current.id)
            _article.value = updated
            articleCacheService.upsert(updated)
        } catch (e: Exception) {
            if (e is IOException) {
                // Delta einreihen, nicht das volle Ziel-Set (siehe Doc-Kommentar
                // an PendingMutationEntity) – Replay darf nur die hier
                // tatsächlich angefassten Tag-IDs berühren.
                if (toAdd.isNotEmpty() || toRemove.isNotEmpty()) {
                    offlineMutationQueue.enqueueSetTags(current.id, toAdd.toList(), toRemove.toList())
                    offlineMutationQueue.scheduleDrain()
                }
            } else {
                _error.value = e.message ?: "Unbekannter Fehler"
            }
        }
    }

    /**
     * Öffentlicher Speicherpunkt für [EditTagsDialog] im Reader-Drawer – Äquivalent zu
     * [ArticlesViewModel.saveTags]: löst [pendingTagNames] zu IDs auf (legt neue Tags an,
     * dedupliziert gegen [allTags]) und ruft [setTags] mit der vollständigen Ziel-Menge.
     */
    suspend fun saveTags(tagIds: Set<Int>, pendingTagNames: List<String> = emptyList()) {
        val resolvedIds = resolveTagIds(pendingTagNames)
        doSetTags(tagIds + resolvedIds)
        loadTags()
    }

    /** Äquivalent zu `ArticlesViewModel.resolveTagIds` – gleiche Dedup-Logik gegen [allTags]. */
    private suspend fun resolveTagIds(names: List<String>): List<Int> {
        if (names.isEmpty()) return emptyList()
        val knownTags = _allTags.value.ifEmpty { runCatching { api.listTags() }.getOrDefault(emptyList()) }
        return names.map { name ->
            knownTags.firstOrNull { it.name.equals(name, ignoreCase = true) }?.id
                ?: api.createTag(CreateTagRequest(name = name)).id
        }
    }

    // MARK: – Public Share (Äquivalent zu ShareLinkSheet.swift/MerlinAPI+Share)
    //
    // Ein Artikel hat höchstens einen Share-Link – "Regenerieren" tauscht nur
    // den Token aus (siehe ShareController im Backend).

    private val _share = MutableStateFlow(ArticleShareResponse(enabled = false))
    val share: StateFlow<ArticleShareResponse> = _share.asStateFlow()

    private val _shareLoading = MutableStateFlow(true)
    val shareLoading: StateFlow<Boolean> = _shareLoading.asStateFlow()

    private val _shareBusy = MutableStateFlow(false)
    val shareBusy: StateFlow<Boolean> = _shareBusy.asStateFlow()

    private val _shareError = MutableStateFlow<String?>(null)
    val shareError: StateFlow<String?> = _shareError.asStateFlow()

    fun loadShare() {
        viewModelScope.launch {
            _shareLoading.value = true
            _shareError.value = null
            try {
                _share.value = api.getShare(articleId)
            } catch (e: Exception) {
                _shareError.value = e.message ?: "Share-Status konnte nicht geladen werden"
            }
            _shareLoading.value = false
        }
    }

    fun createShare(password: String?, expiresAt: String?) {
        viewModelScope.launch {
            _shareBusy.value = true
            _shareError.value = null
            try {
                _share.value = api.createShare(articleId, CreateShareRequest(password = password, expiresAt = expiresAt))
            } catch (e: Exception) {
                _shareError.value = e.message ?: "Link konnte nicht erstellt werden"
            }
            _shareBusy.value = false
        }
    }

    fun setSharePassword(password: String) {
        updateShare(mapOf("password" to password), "Passwort konnte nicht gespeichert werden")
    }

    fun removeSharePassword() {
        updateShare(mapOf("password" to null), "Passwortschutz konnte nicht entfernt werden")
    }

    fun setShareExpiry(expiresAt: String) {
        updateShare(mapOf("expiresAt" to expiresAt), "Ablaufdatum konnte nicht gespeichert werden")
    }

    fun removeShareExpiry() {
        updateShare(mapOf("expiresAt" to null), "Ablaufdatum konnte nicht entfernt werden")
    }

    private fun updateShare(body: Map<String, String?>, errorMessage: String) {
        viewModelScope.launch {
            _shareBusy.value = true
            _shareError.value = null
            try {
                _share.value = api.updateShare(articleId, body)
            } catch (e: Exception) {
                _shareError.value = e.message ?: errorMessage
            }
            _shareBusy.value = false
        }
    }

    fun regenerateShare() {
        viewModelScope.launch {
            _shareBusy.value = true
            _shareError.value = null
            try {
                _share.value = api.regenerateShare(articleId)
            } catch (e: Exception) {
                _shareError.value = e.message ?: "Link konnte nicht erneuert werden"
            }
            _shareBusy.value = false
        }
    }

    fun revokeShare() {
        viewModelScope.launch {
            _shareBusy.value = true
            _shareError.value = null
            try {
                api.deleteShare(articleId)
                _share.value = ArticleShareResponse(enabled = false)
            } catch (e: Exception) {
                _shareError.value = e.message ?: "Link konnte nicht widerrufen werden"
            }
            _shareBusy.value = false
        }
    }
}

/**
 * Äquivalent zu `enum ReportFeedback` in `ReportArticleSheet.swift` – verhindert
 * fragile String-Prefix-Checks zur Unterscheidung von Erfolg/Fehler.
 */
sealed class ReportFeedback {
    object Success : ReportFeedback()
    data class Failure(val message: String) : ReportFeedback()
}
