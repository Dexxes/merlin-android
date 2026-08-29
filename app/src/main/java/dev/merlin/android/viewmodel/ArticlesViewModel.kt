package dev.merlin.android.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.ImageLoader
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.merlin.android.data.ArticleCacheService
import dev.merlin.android.data.HapticUtil
import dev.merlin.android.data.ImageCacheService
import dev.merlin.android.data.OfflineMutationQueue
import dev.merlin.android.data.PreferencesStore
import dev.merlin.android.models.Article
import dev.merlin.android.models.ArticleFilter
import dev.merlin.android.models.Tag
import dev.merlin.android.network.ArticleCounts
import dev.merlin.android.network.CategoryCounts
import dev.merlin.android.network.CreateArticleRequest
import dev.merlin.android.network.CreateTagRequest
import dev.merlin.android.network.MerlinApi
import java.io.IOException
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Äquivalent zu `UndoableAction` (Swift): merkt sich die letzte rückgängig machbare Mutation. */
data class UndoableAction(val kind: Kind, val article: Article) {
    enum class Kind { TOGGLE_FAVORITE, TOGGLE_ARCHIVE }

    val description: String
        get() = when (kind) {
            Kind.TOGGLE_FAVORITE -> if (article.isFavorite) "Favorit entfernen rückgängig" else "Zu Favoriten hinzufügen rückgängig"
            Kind.TOGGLE_ARCHIVE -> if (article.isArchived) "Aus Archiv entfernen rückgängig" else "Archivieren rückgängig"
        }

    /** Text für den Snackbar, der direkt NACH der Mutation erscheint (Android hat keine Shake-Geste für [undo]). */
    val promptDescription: String
        get() = when (kind) {
            Kind.TOGGLE_FAVORITE -> if (article.isFavorite) "Favorit entfernt" else "Zu Favoriten hinzugefügt"
            Kind.TOGGLE_ARCHIVE -> if (article.isArchived) "Aus Archiv entfernt" else "Archiviert"
        }
}

/**
 * Äquivalent zu `ArticlesViewModel.swift`: zentraler State-Holder für die
 * Artikelliste. Optimistische Updates + Offline-Queue-Fallback folgen exakt
 * dem iOS-Muster (siehe [toggleFavorite]/[toggleArchive]/[delete]/[setTags]):
 * Netzwerkfehler (kein Server erreichbar) behalten den optimistischen Stand
 * und werden in [OfflineMutationQueue] nachgetragen; ein echter Serverfehler
 * (HTTP-Fehlercode) macht die Änderung rückgängig.
 */
@HiltViewModel
class ArticlesViewModel @Inject constructor(
    private val api: MerlinApi,
    private val articleCacheService: ArticleCacheService,
    private val imageCacheService: ImageCacheService,
    private val offlineMutationQueue: OfflineMutationQueue,
    private val preferencesStore: PreferencesStore,
    /** Geteilter Coil-Loader mit [dev.merlin.android.data.RefererInterceptor] (siehe `ArticleReaderViewModel`) – für die Artikelbilder in [dev.merlin.android.ui.screens.ArticleCard]. */
    val imageLoader: ImageLoader,
    /** Äquivalent zu `HapticFeedback` (iOS) – Vibration-Feedback bei Favorit/Archiv/Löschen, siehe Aufrufe unten. */
    private val hapticUtil: HapticUtil,
) : ViewModel() {

    // MARK: – State

    private val _articles = MutableStateFlow<List<Article>>(emptyList())
    val articles: StateFlow<List<Article>> = _articles.asStateFlow()

    private val _selectedFilter = MutableStateFlow(ArticleFilter.PAGES_UNREAD)
    val selectedFilter: StateFlow<ArticleFilter> = _selectedFilter.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isOffline = MutableStateFlow(false)
    val isOffline: StateFlow<Boolean> = _isOffline.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _counts = MutableStateFlow(
        ArticleCounts(
            pages = CategoryCounts(total = 0, unread = 0, favorites = 0, archived = 0),
            videos = CategoryCounts(total = 0, unread = 0, favorites = 0, archived = 0),
        ),
    )
    val counts: StateFlow<ArticleCounts> = _counts.asStateFlow()

    /** Tag-IDs, deren Artikel ausgeblendet werden; automatisch in [PreferencesStore] persistiert. */
    private val _excludedTagIds = MutableStateFlow<Set<Int>>(emptySet())
    val excludedTagIds: StateFlow<Set<Int>> = _excludedTagIds.asStateFlow()

    /**
     * Äquivalent zu `PreferencesStore.shared.accentProgressColorHex` (iOS), genutzt von
     * `NoImageView.swift` als Hintergrundfarbe hinter dem Logo-Platzhalter; hier identisch
     * für [dev.merlin.android.ui.screens.ArticleThumbnail] (kein eigener Reader-Kontext nötig,
     * siehe gleiches Muster in `ArticleReaderViewModel.accentColorHex`).
     */
    val accentColorHex: StateFlow<String> = preferencesStore.accentProgressColorHex
        .stateIn(viewModelScope, SharingStarted.Eagerly, "#FF3B30")

    private val _allTags = MutableStateFlow<List<Tag>>(emptyList())
    val allTags: StateFlow<List<Tag>> = _allTags.asStateFlow()

    private val _selectedTagId = MutableStateFlow<Int?>(null)
    val selectedTagId: StateFlow<Int?> = _selectedTagId.asStateFlow()

    /** Blendet archivierte Artikel innerhalb der Einzel-Tag-Ansicht ein/aus (Äquivalent zu
     * iOS' `showArchivedInTagView`, siehe `ArticlesViewModel.swift`). Startzustand bewusst
     * `false` – konsistent mit der normalen Liste, die archivierte Artikel ebenfalls nicht
     * standardmäßig mischt. Toggle über den Augen-Button in `ArticleListScreen.kt`. */
    private val _showArchivedInTagView = MutableStateFlow(false)
    val showArchivedInTagView: StateFlow<Boolean> = _showArchivedInTagView.asStateFlow()

    /**
     * Äquivalent zu iOS' `@AppStorage("merlinIsCardView")`: List- vs. Card-Ansicht, lokal in
     * [PreferencesStore]. Initialer Wert hier `true`, identisch zum DataStore-Default dort,
     * damit beim ersten Compose-Durchlauf (vor dem async Laden in `init`) nicht kurz die
     * Listenansicht aufblitzt.
     */
    private val _isCardView = MutableStateFlow(true)
    val isCardView: StateFlow<Boolean> = _isCardView.asStateFlow()

    /**
     * True, sobald der initiale Server-Settings-Fetch (siehe `init`) abgeschlossen ist –
     * egal ob erfolgreich oder fehlgeschlagen. Gated den ersten Render von [ArticleListScreen]
     * (siehe `MainActivity`), damit Theme/Akzentfarbe/Schrift erst angezeigt werden, nachdem
     * "Server gewinnt" angewendet wurde, statt kurz mit den lokalen DataStore-Defaults
     * aufzublitzen – Äquivalent zum Splash-Gate in iOS' `MerlinApp.swift`.
     */
    private val _settingsLoaded = MutableStateFlow(false)
    val settingsLoaded: StateFlow<Boolean> = _settingsLoaded.asStateFlow()

    val selectedTagName: String?
        get() = _selectedTagId.value?.let { id -> _allTags.value.firstOrNull { it.id == id }?.name }

    // MARK: – Undo stack

    private val _lastUndoableAction = MutableStateFlow<UndoableAction?>(null)
    /** Exponiert (anders als das iOS-Original, das nur `canUndo` braucht), damit die UI direkt
     * nach einer Mutation einen Snackbar mit "Rückgängig"-Action zeigen kann – Android hat
     * keine Shake-Geste, daher ersetzt der Snackbar-Button hier die `undo()`-Auslösung. */
    val lastUndoableAction: StateFlow<UndoableAction?> = _lastUndoableAction.asStateFlow()
    val canUndo: StateFlow<Boolean> = _lastUndoableAction
        .map { it != null }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private val _undoToast = MutableStateFlow<String?>(null)
    val undoToast: StateFlow<String?> = _undoToast.asStateFlow()

    /** Reversiert die letzte Mutation und lädt die Liste neu. */
    fun undo() {
        val action = _lastUndoableAction.value ?: return
        _lastUndoableAction.value = null // sofort konsumieren, damit Doppel-Klick keinen Effekt hat
        viewModelScope.launch {
            when (action.kind) {
                UndoableAction.Kind.TOGGLE_FAVORITE -> doToggleFavorite(action.article, recordUndo = false)
                UndoableAction.Kind.TOGGLE_ARCHIVE -> doToggleArchive(action.article, recordUndo = false)
            }
            load()
            _undoToast.value = action.description
            delay(2_500)
            _undoToast.value = null
        }
    }

    // MARK: – Computed

    val filteredArticles: StateFlow<List<Article>> = combine(_articles, _excludedTagIds, _searchQuery) { list, excluded, query ->
        var result = list
        if (excluded.isNotEmpty()) {
            result = result.filter { article -> article.tags.all { it.id !in excluded } }
        }
        if (query.isBlank()) return@combine result
        val q = query.lowercase()
        result.filter { article ->
            article.displayTitle.lowercase().contains(q) ||
                article.excerpt?.lowercase()?.contains(q) == true ||
                article.siteName?.lowercase()?.contains(q) == true
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun toggleTagExclusion(tagId: Int) {
        val current = _excludedTagIds.value
        _excludedTagIds.value = if (tagId in current) current - tagId else current + tagId
        viewModelScope.launch { preferencesStore.setExcludedTagIds(_excludedTagIds.value) }
    }

    /** Äquivalent zu iOS' `onClearAll: { viewModel.excludedTagIds.removeAll() }` in `TagFilterSheet`. */
    fun clearExcludedTags() {
        _excludedTagIds.value = emptySet()
        viewModelScope.launch { preferencesStore.setExcludedTagIds(emptySet()) }
    }

    /** Schaltet zwischen List- und Card-Ansicht um (Toggle sitzt im Hamburger-Menü, siehe `ArticleListScreen.kt`). */
    fun setIsCardView(value: Boolean) {
        _isCardView.value = value
        viewModelScope.launch { preferencesStore.setIsCardView(value) }
    }

    // MARK: – Init

    init {
        // Server-Settings einmal pro App-Start ziehen ("Server gewinnt"), analog zum
        // `.task`-Block in iOS' `MerlinApp.swift`. Vorher wurde auf Android nirgends
        // `PreferencesStore.loadFromServer()` aufgerufen – Settings (u.a. Akzentfarbe,
        // reportBackendUrl) liefen nur lokal→Server, nie Server→lokal.
        viewModelScope.launch {
            try {
                runCatching { api.getSettings() }.getOrNull()?.let { preferencesStore.loadFromServer(it) }
            } finally {
                // Im finally (nicht nur im Erfolgsfall) setzen, damit ein Netzwerkfehler die Liste
                // nicht für immer hinter dem Splash hält – analog dem `try?` in iOS' `.task`-Block,
                // der bei Fehlschlag einfach mit den lokalen Defaults weitermacht.
                _settingsLoaded.value = true
            }
        }
        viewModelScope.launch { _excludedTagIds.value = preferencesStore.excludedTagIds.first() }
        viewModelScope.launch { _selectedFilter.value = preferencesStore.defaultFilter.first() }
        viewModelScope.launch { _isCardView.value = preferencesStore.isCardView.first() }
        // Veraltete Cache-Einträge (archiviert > 24h) einmal pro App-Start entfernen.
        viewModelScope.launch { articleCacheService.evict() }
        // Bilder aller bereits gecachten Artikel nachträglich vorwärmen, damit
        // Offline-Lesen sofort funktioniert, auch für Artikel aus früheren Sessions.
        viewModelScope.launch {
            val cached = articleCacheService.loadAllCached()
            imageCacheService.prefetch(cached)
        }
        offlineMutationQueue.onDrained = { load() }
        loadTags()
        load()
    }

    // MARK: – Load

    fun load() {
        processingListenerJob?.cancel()
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val fetched = fetchForFilter(_selectedFilter.value)
                _counts.value = api.getArticleCounts()
                _articles.value = fetched
                _isOffline.value = false
                articleCacheService.upsert(fetched)
                prefetchImages(fetched)
                startProcessingListenerIfNeeded()
                offlineMutationQueue.drain()
            } catch (e: Exception) {
                val cached = articleCacheService.loadFiltered(
                    _selectedFilter.value, _selectedTagId.value, _showArchivedInTagView.value,
                )
                if (cached.isNotEmpty()) {
                    _articles.value = cached
                    _isOffline.value = true
                    // counts bewusst nicht überschreiben – letzten Online-Stand behalten.
                } else {
                    _isOffline.value = false
                    _error.value = e.message ?: "Unbekannter Fehler"
                }
            }
            _isLoading.value = false
        }
    }

    private suspend fun fetchForFilter(filter: ArticleFilter): List<Article> {
        val tagId = _selectedTagId.value
        if (tagId != null) {
            // In der Einzel-Tag-Ansicht blendet `showArchivedInTagView` archivierte
            // Artikel standardmäßig aus (sonst mischt der Server Archiv + Aktiv,
            // da `isArchived` beim Tag-Filter sonst gar nicht gesetzt wird).
            val archivedFilter = if (_showArchivedInTagView.value) null else 0
            return api.listArticles(tagId = tagId, isArchived = archivedFilter)
        }
        return when (filter) {
            ArticleFilter.PAGES_UNREAD -> api.listArticles(isArchived = 0, contentType = "page")
            // Bewusst OHNE isArchived-Filter: Favoriten unabhängig vom Archiv-
            // Status anzeigen, chronologisch nach Favorisierungszeitpunkt.
            ArticleFilter.PAGES_FAVORITES -> api.listArticles(isFavorite = 1, contentType = "page").sortedByDescending { it.favoritedAt ?: "" }
            ArticleFilter.PAGES_ARCHIVE -> api.listArticles(isArchived = 1, contentType = "page").sortedByDescending { it.archivedAt ?: "" }
            ArticleFilter.VIDEOS_UNREAD -> api.listArticles(isArchived = 0, contentType = "video")
            ArticleFilter.VIDEOS_FAVORITES -> api.listArticles(isFavorite = 1, contentType = "video").sortedByDescending { it.favoritedAt ?: "" }
            ArticleFilter.VIDEOS_ARCHIVE -> api.listArticles(isArchived = 1, contentType = "video").sortedByDescending { it.archivedAt ?: "" }
        }
    }

    fun selectFilter(filter: ArticleFilter) {
        // Verlässt eine evtl. aktive Einzel-Tag-Ansicht (Äquivalent zu iOS' Flyout-Filterzeilen,
        // die `viewModel.selectedTagId = nil` vor dem Filterwechsel setzen, siehe
        // `ListFlyoutModifier.swift`) – sonst würde `fetchForFilter` weiterhin den alten Tag
        // statt des neu gewählten Filters verwenden.
        _selectedTagId.value = null
        _showArchivedInTagView.value = false
        _selectedFilter.value = filter
        load()
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    // MARK: – Tags

    fun loadTags() {
        viewModelScope.launch {
            _allTags.value = runCatching { api.listTags() }.getOrDefault(emptyList())
        }
    }

    fun selectTag(tagId: Int?) {
        _selectedTagId.value = tagId
        _showArchivedInTagView.value = false // frischer Start pro Tag/Tag-Wechsel
        load()
    }

    /** Toggle für den Augen-Button in der Einzel-Tag-Ansicht (siehe [showArchivedInTagView]). */
    fun toggleShowArchivedInTagView() {
        _showArchivedInTagView.value = !_showArchivedInTagView.value
        load()
    }

    // MARK: – Add

    /**
     * Äquivalent zu `ArticlesViewModel.addArticle(url:tagIds:)` (Swift). [pendingTagNames]
     * entspricht den iOS-`pendingTags` aus `AddArticleSheet.swift`: Tag-Namen, die es serverseitig
     * noch nicht gibt und die hier zuerst angelegt werden (siehe [resolveTagIds], Äquivalent
     * `MerlinAPI.resolveTagIds(for:)`), bevor der Artikel mit der vollständigen Tag-ID-Liste erstellt wird.
     * Wirft die Exception weiter (statt sie zu schlucken) – der Aufrufer (`AddArticleSheet`) zeigt
     * sie als Fehlertext, analog dem `catch`-Block in `AddArticleSheet.save()`.
     */
    suspend fun addArticle(url: String, tagIds: Set<Int> = emptySet(), pendingTagNames: List<String> = emptyList()) {
        val resolvedIds = resolveTagIds(pendingTagNames)
        val article = api.createArticle(CreateArticleRequest(url = url, tagIds = (tagIds + resolvedIds).toList()))
        _articles.value = listOf(article) + _articles.value
        // Kategorie steht erst nach der (async) Extraktion fest - optimistisch als
        // Seite zählen, refreshCounts() korrigiert bei Bedarf auf den Server-Stand.
        _counts.value = _counts.value.copy(pages = _counts.value.pages.copy(total = _counts.value.pages.total + 1))
        articleCacheService.upsert(article)
        prefetchImages(listOf(article))
        startProcessingListenerIfNeeded()
        if (pendingTagNames.isNotEmpty()) loadTags() // neu angelegte Tags sofort in allTags verfügbar machen
    }

    /**
     * Äquivalent zu `MerlinAPI.resolveTagIds(for:)`: gleicht [names] zunächst gegen bereits
     * bekannte Tags ab (case-insensitiv) und legt nur wirklich neue Namen serverseitig an –
     * verhindert Duplikate, falls ein Name parallel schon als existierender Tag angelegt wurde
     * (z.B. von einem anderen Client), seit [allTags] zuletzt geladen wurde.
     */
    private suspend fun resolveTagIds(names: List<String>): List<Int> {
        if (names.isEmpty()) return emptyList()
        val knownTags = _allTags.value.ifEmpty { runCatching { api.listTags() }.getOrDefault(emptyList()) }
        return names.map { name ->
            knownTags.firstOrNull { it.name.equals(name, ignoreCase = true) }?.id
                ?: api.createTag(CreateTagRequest(name = name)).id
        }
    }

    /**
     * Öffentlicher Speicherpunkt für [EditTagsDialog]: löst [pendingTagNames] zu IDs auf
     * (legt neue Tags an) und ruft anschließend [setTags] mit der vollständigen Ziel-Menge.
     * Suspend (anders als [setTags]), damit der Aufrufer per `try`/`catch` einen Ladezustand
     * bzw. eine Fehlermeldung anzeigen kann (analog `AddArticleSheet.save()`).
     */
    suspend fun saveTags(article: Article, tagIds: Set<Int>, pendingTagNames: List<String> = emptyList()) {
        val resolvedIds = resolveTagIds(pendingTagNames)
        doSetTags(article, tagIds + resolvedIds)
        loadTags()
    }

    // MARK: – Processing-Listener

    private var processingListenerJob: Job? = null

    /**
     * Pollt alle 2s, bis kein Artikel mehr in Verarbeitung ist. SSE wäre die
     * naheliegende Alternative, hält die Verbindung serverseitig aber bis zu
     * 55s offen, was die Spinner-Entfernung auf langsamen Proxys spürbar
     * verzögert (1:1 übernommene Begründung aus dem iOS-Original).
     */
    private fun startProcessingListenerIfNeeded() {
        if (_articles.value.none { it.isProcessing }) return
        processingListenerJob?.cancel()
        processingListenerJob = viewModelScope.launch {
            while (_articles.value.any { it.isProcessing }) {
                delay(2_000)
                val processingIds = _articles.value.filter { it.isProcessing }.map { it.id }
                for (id in processingIds) {
                    val refreshed = runCatching { api.getArticle(id) }.getOrNull() ?: continue
                    applyUpdate(refreshed)
                    articleCacheService.upsert(refreshed)
                    imageCacheService.prefetch(listOf(refreshed))
                }
            }
            processingListenerJob = null
        }
    }

    private fun prefetchImages(articles: List<Article>) {
        viewModelScope.launch { imageCacheService.prefetch(articles) }
    }

    // MARK: – Mutations

    /** Öffentlich nicht-suspend (analog [OnboardingViewModel]), damit Compose-`onClick`-Handler direkt aufrufen können. */
    fun toggleFavorite(article: Article, recordUndo: Boolean = true) {
        viewModelScope.launch { doToggleFavorite(article, recordUndo) }
    }

    private suspend fun doToggleFavorite(article: Article, recordUndo: Boolean) {
        if (recordUndo) _lastUndoableAction.value = UndoableAction(UndoableAction.Kind.TOGGLE_FAVORITE, article)

        val willBeFavorite = !article.isFavorite
        val optimistic = article.copy(
            favoritedAt = if (willBeFavorite) java.time.Instant.now().toString() else null,
        )
        applyListMembership(optimistic)
        hapticUtil.lightTap() // Äquivalent zu HapticFeedback.lightTap() in ArticlesViewModel.swift (toggleFavorite)

        try {
            api.toggleFavorite(article.id)
            val updated = api.getArticle(article.id)
            applyListMembership(updated)
            articleCacheService.upsert(updated)
        } catch (e: Exception) {
            if (isNetworkError(e)) {
                articleCacheService.upsert(optimistic)
                offlineMutationQueue.enqueueToggleFavorite(article.id)
            } else {
                reinsertIfMissing(article)
                _error.value = e.message ?: "Unbekannter Fehler"
            }
        }
    }

    fun toggleArchive(article: Article, recordUndo: Boolean = true) {
        viewModelScope.launch { doToggleArchive(article, recordUndo) }
    }

    private suspend fun doToggleArchive(article: Article, recordUndo: Boolean) {
        if (recordUndo) _lastUndoableAction.value = UndoableAction(UndoableAction.Kind.TOGGLE_ARCHIVE, article)

        val willBeArchived = !article.isArchived
        val optimistic = article.copy(
            isArchived = willBeArchived,
            archivedAt = if (willBeArchived) Instant.now().toString() else null,
        )
        applyListMembership(optimistic)
        hapticUtil.mediumTap() // Äquivalent zu HapticFeedback.mediumTap() in ArticlesViewModel.swift (toggleArchive)

        try {
            api.toggleArchive(article.id)
            val updated = api.getArticle(article.id)
            applyListMembership(updated)
            articleCacheService.upsert(updated)
            if (updated.isArchived) imageCacheService.evict(updated.id)
            refreshCounts()
        } catch (e: Exception) {
            if (isNetworkError(e)) {
                articleCacheService.upsert(optimistic)
                offlineMutationQueue.enqueueToggleArchive(article.id)
            } else {
                reinsertIfMissing(article)
                _error.value = e.message ?: "Unbekannter Fehler"
            }
        }
    }

    fun delete(article: Article) {
        viewModelScope.launch {
            val removedIndex = _articles.value.indexOfFirst { it.id == article.id }
            if (removedIndex >= 0) _articles.value = _articles.value.toMutableList().also { it.removeAt(removedIndex) }
            hapticUtil.heavyTap() // Äquivalent zu HapticFeedback.heavyTap() in ArticlesViewModel.swift (delete)

            try {
                api.deleteArticle(article.id)
                articleCacheService.remove(article.id)
                imageCacheService.evict(article.id)
                refreshCounts()
            } catch (e: Exception) {
                if (isNetworkError(e)) {
                    articleCacheService.remove(article.id)
                    imageCacheService.evict(article.id)
                    offlineMutationQueue.enqueueDelete(article.id)
                } else {
                    if (removedIndex >= 0) {
                        _articles.value = _articles.value.toMutableList().also {
                            it.add(minOf(removedIndex, it.size), article)
                        }
                    }
                    _error.value = e.message ?: "Unbekannter Fehler"
                }
            }
        }
    }

    /** Diffed die aktuellen Tags des Artikels gegen [tagIds] und wendet Adds/Removes an. */
    fun setTags(article: Article, tagIds: Set<Int>) {
        viewModelScope.launch {
            doSetTags(article, tagIds)
            // Tag-Liste neu laden, damit neu angelegte Tags sofort verfügbar sind.
            loadTags()
        }
    }

    private suspend fun doSetTags(article: Article, tagIds: Set<Int>) {
        val desiredTagObjects = _allTags.value.filter { it.id in tagIds }
        val optimistic = article.copy(tags = desiredTagObjects)
        applyUpdate(optimistic)

        // Gegen server-frischen Stand diffen statt gegen den `article`-Snapshot,
        // den der Aufrufer beim Öffnen des Tag-Dialogs eingefroren hat. Dieser
        // Snapshot kann veraltet sein (anderes Gerät oder eine andere Mutation
        // hat die Tags zwischenzeitlich geändert); ein Diff dagegen würde ein
        // anderswo hinzugefügtes Tag fälschlich als "soll entfernt werden"
        // behandeln. Fällt offline auf den übergebenen Snapshot zurück.
        val baseline = runCatching { api.getArticle(article.id) }.getOrDefault(article)
        val currentIds = baseline.tags.map { it.id }.toSet()
        val toAdd = tagIds - currentIds
        val toRemove = currentIds - tagIds
        try {
            toAdd.forEach { api.addTagToArticle(article.id, it) }
            toRemove.forEach { api.removeTagFromArticle(article.id, it) }
            val refreshed = runCatching { api.getArticle(article.id) }.getOrNull()
            if (refreshed != null) {
                applyUpdate(refreshed)
                articleCacheService.upsert(refreshed)
            }
        } catch (e: Exception) {
            if (isNetworkError(e)) {
                articleCacheService.upsert(optimistic)
                // Das tatsächliche Delta einreihen, nicht das volle Ziel-Set
                // (siehe Doc-Kommentar an PendingMutationEntity) – beim Replay
                // dürfen nur die hier tatsächlich angefassten Tag-IDs berührt
                // werden, nie ein zwischenzeitlich von woanders hinzugefügtes Tag.
                if (toAdd.isNotEmpty() || toRemove.isNotEmpty()) {
                    offlineMutationQueue.enqueueSetTags(article.id, toAdd.toList(), toRemove.toList())
                }
            } else {
                applyUpdate(article) // rollback
                _error.value = e.message ?: "Unbekannter Fehler"
            }
        }
    }

    /**
     * Synchronisationspunkt für Mutationen, die außerhalb dieses ViewModels passiert sind –
     * aktuell nur [dev.merlin.android.viewmodel.ArticleReaderViewModel] im Reader-Drawer, der
     * bewusst eine eigene, unabhängige `_article`-Instanz hält (siehe Architektur-Hinweis dort)
     * statt direkt auf diese Liste zuzugreifen. Ohne diesen Aufruf bliebe z.B. ein im Reader
     * archivierter Artikel in "Ungelesen" sichtbar, bis die Liste das nächste Mal `load()`
     * (Pull-to-Refresh/Filterwechsel/Activity-Resume) ausführt. Koppelt bewusst nur auf
     * Compose-Callback-Ebene (siehe `MainActivity`s `reader/{articleId}`-Route), nicht über eine
     * direkte ViewModel-zu-ViewModel-Referenz. No-Op, falls [article] aktuell nicht in der Liste
     * geladen ist (z.B. Reader über Reminder-Deep-Link geöffnet, ohne dass die Liste lief).
     */
    fun applyExternalUpdate(article: Article) {
        if (_articles.value.none { it.id == article.id }) return
        applyListMembership(article)
    }

    /** Gegenstück zu [applyExternalUpdate] für eine externe Löschung (siehe dort). */
    fun removeExternally(articleId: Int) {
        _articles.value = _articles.value.filterNot { it.id == articleId }
    }

    // MARK: – Helfer

    /**
     * Wendet [article] an und gleicht seine Sichtbarkeit in der aktuellen
     * Liste ab: Zeilen, die der aktive Filter ausblenden würde, werden sofort
     * entfernt – spiegelt [ArticleCacheService]s Filter-Logik, damit
     * optimistische und server-bestätigte Updates sich identisch verhalten.
     */
    private fun applyListMembership(article: Article) {
        if (shouldHide(article, _selectedFilter.value)) {
            _articles.value = _articles.value.filterNot { it.id == article.id }
        } else {
            applyUpdate(article)
        }
    }

    private fun shouldHide(article: Article, filter: ArticleFilter): Boolean {
        val isVideo = article.category == "Video"
        return when (filter) {
            ArticleFilter.PAGES_UNREAD -> article.isArchived || isVideo
            ArticleFilter.PAGES_FAVORITES -> !article.isFavorite || isVideo
            ArticleFilter.PAGES_ARCHIVE -> !article.isArchived || isVideo
            ArticleFilter.VIDEOS_UNREAD -> article.isArchived || !isVideo
            ArticleFilter.VIDEOS_FAVORITES -> !article.isFavorite || !isVideo
            ArticleFilter.VIDEOS_ARCHIVE -> !article.isArchived || !isVideo
        }
    }

    /** Fügt [article] wieder ein, falls der aktive Filter es zeigen würde und es fehlt (Rollback nach echtem Fehler). */
    private fun reinsertIfMissing(article: Article) {
        if (shouldHide(article, _selectedFilter.value)) return
        val current = _articles.value
        if (current.any { it.id == article.id }) {
            applyUpdate(article)
            return
        }
        val idx = if (_selectedFilter.value == ArticleFilter.PAGES_ARCHIVE || _selectedFilter.value == ArticleFilter.VIDEOS_ARCHIVE) {
            current.indexOfFirst { (it.archivedAt ?: "") < (article.archivedAt ?: "") }
        } else {
            current.indexOfFirst { it.createdAt < article.createdAt }
        }.let { if (it == -1) current.size else it }
        _articles.value = current.toMutableList().also { it.add(idx, article) }
    }

    private fun applyUpdate(updated: Article) {
        val idx = _articles.value.indexOfFirst { it.id == updated.id }
        if (idx >= 0) _articles.value = _articles.value.toMutableList().also { it[idx] = updated }
    }

    /** Echte Verbindungsfehler (kein Host erreichbar/Timeout) – nicht zu verwechseln mit HTTP-Fehlercodes (`HttpException`), die echte Serverfehler sind. */
    private fun isNetworkError(e: Exception): Boolean = e is IOException

    private suspend fun refreshCounts() {
        runCatching { api.getArticleCounts() }.getOrNull()?.let { _counts.value = it }
    }
}
