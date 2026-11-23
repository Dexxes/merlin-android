package dev.merlin.android.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.merlin.android.R
import dev.merlin.android.models.Article
import dev.merlin.android.models.ArticleFilter
import dev.merlin.android.models.Tag
import dev.merlin.android.ui.tour.OnboardingTourOverlay
import dev.merlin.android.ui.tour.TourAnchor
import dev.merlin.android.ui.tour.rememberTourAnchors
import dev.merlin.android.ui.tour.tourAnchor
import dev.merlin.android.viewmodel.ArticlesViewModel
import kotlinx.coroutines.launch

/**
 * Äquivalent zu `ArticleListView.swift`: Artikelliste mit Seitenmenü ([ListDrawerContent],
 * Gegenstück zu `ListFlyoutModifier.swift`), Suchfeld hinter dem Lupen-Icon neben dem Menü-Button, Pull-to-Refresh,
 * Nachladen beim Scrollen und filterabhängigem Leer-Zustand.
 *
 * Toolbar wie auf iOS: in der Mitte Logo und Name der Ansicht bzw. des Tags, rechts „+“ und
 * das Augen-Symbol (Tag-Filter bzw. Archiv-Sichtbarkeit in der Tag-Ansicht). Anders als auf
 * iOS öffnet das Seitenmenü über den Menü-Button links statt per Wischen vom Rand: der linke
 * Bildschirmrand gehört auf Android der System-Zurück-Geste.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArticleListScreen(
    onArticleClick: (Int) -> Unit,
    onRemindersClick: () -> Unit,
    onSettingsClick: () -> Unit,
    viewModel: ArticlesViewModel = hiltViewModel(),
) {
    val articles by viewModel.filteredArticles.collectAsState()
    val allLoaded by viewModel.articles.collectAsState()
    val selectedFilter by viewModel.selectedFilter.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isOffline by viewModel.isOffline.collectAsState()
    val error by viewModel.error.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val lastUndoableAction by viewModel.lastUndoableAction.collectAsState()
    val undoToast by viewModel.undoToast.collectAsState()
    val excludedTagIds by viewModel.excludedTagIds.collectAsState()
    val allTags by viewModel.allTags.collectAsState()
    val tagTree by viewModel.tagTree.collectAsState()
    val expandedTagIds by viewModel.expandedTagIds.collectAsState()
    val isCardView by viewModel.isCardView.collectAsState()
    val accentColorHex by viewModel.accentColorHex.collectAsState()
    val selectedTagId by viewModel.selectedTagId.collectAsState()
    val showArchivedInTagView by viewModel.showArchivedInTagView.collectAsState()
    val counts by viewModel.counts.collectAsState()
    val hasMoreArticles by viewModel.hasMoreArticles.collectAsState()
    val loadMoreFailed by viewModel.loadMoreFailed.collectAsState()
    val rawOffset by viewModel.rawOffset.collectAsState()
    val localProgress by viewModel.localProgress.collectAsState()
    val selectedTagName = selectedTagId?.let { id -> allTags.firstOrNull { it.id == id }?.name }

    var searchVisible by remember { mutableStateOf(false) }
    val searchFocusRequester = remember { FocusRequester() }
    var tagsArticleId by remember { mutableStateOf<Int?>(null) }
    var showAddSheet by remember { mutableStateOf(false) }
    var showTagFilterSheet by remember { mutableStateOf(false) }
    /** Tag, dessen „Verschieben nach…“-Sheet bzw. Lösch-Bestätigung offen ist (langes Drücken im Seitenmenü). */
    var tagToMove by remember { mutableStateOf<Tag?>(null) }
    var tagToDelete by remember { mutableStateOf<Tag?>(null) }
    var tagToRename by remember { mutableStateOf<Tag?>(null) }
    /** Datei-Eintrag, dessen Umbenennen-Dialog offen ist. */
    var renameArticleId by remember { mutableStateOf<Int?>(null) }
    val supportsNextcloudOnly = viewModel.supportsNextcloudOnlyFeatures

    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    // Echte Archiv-Ansicht (nicht die Tag-Ansicht): dort keine Archiv-Aktion und keine
    // Archiv-Markierung – wie `isArchiveView` in ArticleListView.swift.
    val isArchiveView = selectedTagId == null && selectedFilter.kind == ArticleFilter.Kind.ARCHIVE
    val showFavoriteAction = selectedFilter.kind != ArticleFilter.Kind.FAVORITES

    // Äquivalent zu iOS' `@State private var activeSwipeId: Int?` – nur eine Swipe-Reihe offen.
    val activeSwipeKey = remember { mutableStateOf<Any?>(null) }

    val snackbarHostState = remember { SnackbarHostState() }

    // Snackbar mit "Rückgängig"-Action, sobald eine Mutation passiert ist (Android hat keine
    // Shake-Geste, daher ersetzt der Snackbar-Button iOS' Shake-to-Undo).
    val context = LocalContext.current
    val undoLabel = stringResource(R.string.articleList_archiveToast_undoButton)
    LaunchedEffect(lastUndoableAction) {
        val action = lastUndoableAction ?: return@LaunchedEffect
        val result = snackbarHostState.showSnackbar(
            message = context.getString(action.promptDescription),
            actionLabel = undoLabel,
            withDismissAction = true,
        )
        if (result == SnackbarResult.ActionPerformed) viewModel.undo()
    }

    // Zweiter Toast (Bestätigung NACH dem Rückgängig-Machen).
    LaunchedEffect(undoToast) {
        val message = undoToast ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
    }

    // Äquivalent zu iOS' `.onChange(of: scenePhase)`: beim Zurückkehren aus dem Hintergrund
    // erneut laden. `isFirstResume` überspringt den synchron beim Registrieren ausgelösten
    // ON_RESUME, damit nicht parallel zum `init`-Load ein zweiter Fetch läuft.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        var isFirstResume = true
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                if (isFirstResume) {
                    isFirstResume = false
                } else {
                    viewModel.load()
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Beim Öffnen des Seitenmenüs bleibt der gewählte Tag sichtbar: seine Eltern-Tags aufklappen.
    LaunchedEffect(drawerState.targetValue) {
        if (drawerState.targetValue == DrawerValue.Open) viewModel.expandAncestorsOfSelectedTag()
    }

    fun closeDrawer(then: () -> Unit = {}) {
        scope.launch {
            drawerState.close()
            then()
        }
    }

    /** Lesefortschritt per Last-Write-Wins aus lokalem und Server-Wert (wie `resolvedInitialFraction`). */
    fun progressFor(article: Article): Float {
        val local = localProgress[article.id] ?: return article.scrollProgress
        return if (article.scrollUpdatedAt > local.second) article.scrollProgress else local.first
    }

    val tourDone by viewModel.tourDone.collectAsState()
    val readerHandsFreeButton by viewModel.readerHandsFreeButton.collectAsState()
    val tourAnchors = rememberTourAnchors()

    Box(Modifier.fillMaxSize()) {
    ModalNavigationDrawer(
        drawerState = drawerState,
        // Nur zum Schließen per Wischen; Öffnen über den Menü-Button (siehe KDoc).
        gesturesEnabled = drawerState.isOpen,
        drawerContent = {
            ListDrawerContent(
                // Bei jedem Öffnen auf die Gruppe des aktiven Filters zurückspringen.
                initialGroup = selectedFilter.group,
                resetKey = drawerState.targetValue == DrawerValue.Open,
                selectedFilter = selectedFilter,
                selectedTagId = selectedTagId,
                counts = counts,
                tagTree = tagTree,
                expandedTagIds = expandedTagIds,
                showFilesTab = supportsNextcloudOnly,
                canMoveTags = supportsNextcloudOnly,
                isCardView = isCardView,
                onSelectFilter = { filter -> closeDrawer { viewModel.selectFilter(filter) } },
                onSelectTag = { tagId -> closeDrawer { viewModel.selectTag(tagId) } },
                onSetTagExpanded = viewModel::setTagExpanded,
                onRenameTag = { tag -> tagToRename = tag },
                onMoveTag = { tag -> tagToMove = tag },
                onDeleteTag = { tag -> tagToDelete = tag },
                onToggleCardView = { closeDrawer { viewModel.setIsCardView(!isCardView) } },
                onRemindersClick = { closeDrawer(onRemindersClick) },
                onSettingsClick = { closeDrawer(onSettingsClick) },
                onAppTourClick = { closeDrawer { viewModel.setTourDone(false) } },
                modifier = Modifier.tourAnchor(tourAnchors, TourAnchor.LIST_DRAWER),
                viewToggleModifier = Modifier.tourAnchor(tourAnchors, TourAnchor.VIEW_TOGGLE),
            )
        },
    ) {
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Image(
                                bitmap = ImageBitmap.imageResource(R.drawable.merlin_logo),
                                contentDescription = null,
                                filterQuality = FilterQuality.None,
                                modifier = Modifier
                                    .size(26.dp)
                                    .clip(RoundedCornerShape(6.dp)),
                            )
                            if (selectedTagName != null) {
                                Icon(
                                    imageVector = Icons.Filled.Tag,
                                    contentDescription = null,
                                    modifier = Modifier.padding(start = 6.dp).size(18.dp),
                                )
                                // Einzeilig wie auf iOS: lange Tag-/Ansichtsnamen werden gekürzt statt umzubrechen.
                                Text(
                                    selectedTagName,
                                    // Wie der kompakte Inline-Titel auf iOS: kleiner als titleLarge, damit
                                    // auch „Nicht geöffnet“ zwischen Menü/Suche und Auge/Plus passt.
                                    style = MaterialTheme.typography.titleMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(start = 4.dp).weight(1f, fill = false),
                                )
                            } else {
                                Text(
                                    stringResource(selectedFilter.label),
                                    // Wie der kompakte Inline-Titel auf iOS: kleiner als titleLarge, damit
                                    // auch „Nicht geöffnet“ zwischen Menü/Suche und Auge/Plus passt.
                                    style = MaterialTheme.typography.titleMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(start = 6.dp).weight(1f, fill = false),
                                )
                            }
                        }
                    },
                    navigationIcon = {
                        Row {
                            IconButton(
                                onClick = { scope.launch { drawerState.open() } },
                                modifier = Modifier.tourAnchor(tourAnchors, TourAnchor.MENU_BUTTON),
                            ) {
                                Icon(Icons.Filled.Menu, contentDescription = stringResource(R.string.android_common_menu))
                            }
                            // Suchfeld ein-/ausblenden; beim Ausblenden wird die Suche geleert,
                            // damit kein unsichtbarer Filter auf der Liste liegen bleibt.
                            IconButton(
                                onClick = {
                                    if (searchVisible) viewModel.setSearchQuery("")
                                    searchVisible = !searchVisible
                                },
                            ) {
                                Icon(
                                    Icons.Filled.Search,
                                    contentDescription = stringResource(if (searchVisible) R.string.android_articleList_closeSearch else R.string.android_common_search),
                                    tint = if (searchVisible) MaterialTheme.colorScheme.primary else LocalContentColor.current,
                                )
                            }
                        }
                    },
                    actions = {
                        // Äquivalent zum iOS-Augen-Button (Badge = Zahl ausgeblendeter Tags). In der
                        // Einzel-Tag-Ansicht schaltet derselbe Button die Archiv-Sichtbarkeit für diesen Tag.
                        IconButton(
                            onClick = {
                                if (selectedTagId != null) {
                                    viewModel.toggleShowArchivedInTagView()
                                } else {
                                    showTagFilterSheet = true
                                }
                            },
                        ) {
                            if (selectedTagId != null) {
                                Icon(
                                    imageVector = if (showArchivedInTagView) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                                    contentDescription = stringResource(if (showArchivedInTagView) R.string.android_articleList_hideArchived else R.string.android_articleList_showArchived),
                                )
                            } else {
                                BadgedBox(
                                    badge = {
                                        if (excludedTagIds.isNotEmpty()) {
                                            Badge { Text("${excludedTagIds.size}") }
                                        }
                                    },
                                ) {
                                    Icon(Icons.Filled.VisibilityOff, contentDescription = stringResource(R.string.tagFilterSheet_title))
                                }
                            }
                        }
                        IconButton(
                            onClick = { showAddSheet = true },
                            modifier = Modifier.tourAnchor(tourAnchors, TourAnchor.PLUS_BUTTON),
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.addArticle_title))
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(),
                )
            },
            snackbarHost = {
                SnackbarHost(snackbarHostState) { data ->
                    Snackbar(snackbarData = data)
                }
            },
        ) { padding ->
            Column(modifier = Modifier.padding(padding).fillMaxSize()) {

                // Suchfeld über das Lupen-Icon neben dem Menü-Button ein- und ausblendbar.
                if (searchVisible) {
                    LaunchedEffect(Unit) { searchFocusRequester.requestFocus() }
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = viewModel::setSearchQuery,
                        placeholder = { Text(stringResource(R.string.articleList_searchPlaceholder)) },
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { viewModel.setSearchQuery("") }) {
                                    Icon(Icons.Filled.Clear, contentDescription = stringResource(R.string.android_articleList_clearSearch))
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                            .focusRequester(searchFocusRequester),
                    )
                }

                if (isOffline) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.CloudOff, contentDescription = null, modifier = Modifier.size(16.dp))
                        Text(
                            text = stringResource(R.string.android_articleList_offlineBanner),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(start = 6.dp),
                        )
                    }
                }

                if (error != null) {
                    Text(
                        text = error.orEmpty(),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }

                PullToRefreshBox(
                    isRefreshing = isLoading,
                    onRefresh = { viewModel.load() },
                    modifier = Modifier.fillMaxSize(),
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        when {
                            isLoading && articles.isEmpty() -> {
                                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                            }
                            articles.isEmpty() && !hasMoreArticles -> {
                                EmptyState(
                                    filter = selectedFilter,
                                    tagName = selectedTagName,
                                    onRefresh = { viewModel.load() },
                                    modifier = Modifier.align(Alignment.Center),
                                )
                            }
                            else -> {
                                LazyColumn(
                                    contentPadding = if (isCardView) PaddingValues(horizontal = 12.dp, vertical = 6.dp) else PaddingValues(0.dp),
                                    verticalArrangement = if (isCardView) Arrangement.spacedBy(12.dp) else Arrangement.Top,
                                    modifier = Modifier.fillMaxSize(),
                                ) {
                                    itemsIndexed(articles, key = { _, it -> it.id }) { index, article ->
                                        LaunchedEffect(article.id) { viewModel.loadMoreIfNeeded(article) }
                                        if (isCardView) {
                                            ArticleGridCard(
                                                article = article,
                                                imageLoader = viewModel.imageLoader,
                                                onClick = { onArticleClick(article.id) },
                                                onToggleFavorite = { viewModel.toggleFavorite(article) },
                                                onToggleArchive = { viewModel.toggleArchive(article) },
                                                onDelete = { viewModel.delete(article) },
                                                onEditTags = { tagsArticleId = article.id },
                                                onRename = { renameArticleId = article.id },
                                                progress = progressFor(article),
                                                showFavoriteAction = showFavoriteAction,
                                                showArchiveAction = !isArchiveView,
                                                showArchivedState = !isArchiveView,
                                                accentColorHex = accentColorHex,
                                            )
                                        } else {
                                            Column(
                                                // Erste Zeile für die Wisch-Schritte der App-Tour.
                                                modifier = if (index == 0) Modifier.tourAnchor(tourAnchors, TourAnchor.FIRST_ARTICLE) else Modifier,
                                            ) {
                                                ArticleRow(
                                                    article = article,
                                                    imageLoader = viewModel.imageLoader,
                                                    onClick = { onArticleClick(article.id) },
                                                    onToggleFavorite = { viewModel.toggleFavorite(article) },
                                                    onToggleArchive = { viewModel.toggleArchive(article) },
                                                    onDelete = { viewModel.delete(article) },
                                                    onEditTags = { tagsArticleId = article.id },
                                                    onRename = { renameArticleId = article.id },
                                                    activeSwipeKey = activeSwipeKey,
                                                    progress = progressFor(article),
                                                    showFavoriteAction = showFavoriteAction,
                                                    showArchiveAction = !isArchiveView,
                                                    showArchivedState = !isArchiveView,
                                                    accentColorHex = accentColorHex,
                                                )
                                                HorizontalDivider(modifier = Modifier.padding(start = 16.dp))
                                            }
                                        }
                                    }
                                    // Letzte Zeile, solange der Server ältere Artikel hat: Spinner beim
                                    // Nachladen, nach einem Fehler ein Button (wie `loadMoreFooter`).
                                    if (loadMoreFailed) {
                                        item(key = "loadMoreRetry") {
                                            Box(Modifier.fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
                                                TextButton(onClick = { viewModel.retryLoadMore() }) { Text(stringResource(R.string.articleList_loadMore_retryButton)) }
                                            }
                                        }
                                    } else if (hasMoreArticles) {
                                        item(key = "loadMoreSpinner") {
                                            // Auch nachladen, wenn die Zeile nach einer Seite sichtbar bleibt
                                            // (z. B. weil Tag-Filter, Suche oder „Weiterlesen“ alle neuen Artikel ausblenden).
                                            LaunchedEffect(allLoaded.size, rawOffset) { viewModel.loadMore() }
                                            Box(Modifier.fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
                                                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // App-Tour beim ersten Start (und nach „App-Tour“ im Seitenmenü), wie in ArticleListView.swift.
    if (tourDone == false) {
        OnboardingTourOverlay(
            anchors = tourAnchors,
            isCardView = isCardView,
            accentColorHex = accentColorHex,
            showHandsFreeButton = readerHandsFreeButton,
            showComments = supportsNextcloudOnly,
            onListDrawerVisibleChange = { open ->
                scope.launch { if (open) drawerState.open() else drawerState.close() }
            },
            onAccentColorChange = viewModel::setAccentColorHex,
            onAccentColorCommit = viewModel::syncAccentColorToServer,
            onFinish = { viewModel.setTourDone(true) },
        )
    }
    }

    // Tag-Editor: Chip-Auswahl + Inline-Erstellung neuer Tags (Äquivalent zu iOS' ArticleTagSheet).
    tagsArticleId?.let { articleId ->
        val article = articles.firstOrNull { it.id == articleId }
        if (article != null) {
            EditTagsDialog(
                article = article,
                allTags = allTags,
                onDismiss = { tagsArticleId = null },
                onSave = { selectedIds, pendingNames, parentId -> viewModel.saveTags(article, selectedIds, pendingNames, parentId) },
                canNestTags = supportsNextcloudOnly,
            )
        } else {
            tagsArticleId = null
        }
    }

    if (showAddSheet) {
        AddArticleSheet(onDismiss = { showAddSheet = false }, viewModel = viewModel)
    }

    if (showTagFilterSheet) {
        TagFilterSheet(
            tagTree = tagTree,
            excludedTagIds = excludedTagIds,
            onToggle = { tagId -> viewModel.toggleTagExclusion(tagId) },
            onClearAll = { viewModel.clearExcludedTags() },
            onDismiss = { showTagFilterSheet = false },
        )
    }

    tagToMove?.let { tag ->
        MoveTagSheet(
            tag = tag,
            tree = tagTree,
            onMove = { parentId ->
                viewModel.moveTag(tag.id, parentId)
                if (parentId != null) viewModel.setTagExpanded(parentId, true)
            },
            onDismiss = { tagToMove = null },
        )
    }

    tagToRename?.let { tag ->
        RenameTagDialog(
            tag = tag,
            onRename = { name -> viewModel.renameTag(tag, name) },
            onDismiss = { tagToRename = null },
        )
    }

    tagToDelete?.let { tag ->
        AlertDialog(
            onDismissRequest = { tagToDelete = null },
            title = { Text(stringResource(R.string.navigationMenu_deleteTag_title)) },
            text = {
                Text(stringResource(R.string.navigationMenu_deleteTag_message, tag.name))
            },
            confirmButton = {
                TextButton(onClick = {
                    tagToDelete = null
                    viewModel.deleteTag(tag)
                }) { Text(stringResource(R.string.common_delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { tagToDelete = null }) { Text(stringResource(R.string.common_cancel)) }
            },
        )
    }

    renameArticleId?.let { articleId ->
        val article = allLoaded.firstOrNull { it.id == articleId }
        if (article != null) {
            RenameFileDialog(
                article = article,
                onRename = { name -> viewModel.renameFile(article, name) },
                onDismiss = { renameArticleId = null },
            )
        } else {
            renameArticleId = null
        }
    }
}

/** Filterabhängiger Leer-Zustand, wie `emptyView`/`emptyMessage` in ArticleListView.swift. */
@Composable
private fun EmptyState(
    filter: ArticleFilter,
    tagName: String?,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val message = when {
        tagName != null -> stringResource(R.string.articleList_emptyState_taggedMessage, tagName)
        else -> stringResource(
            when (filter) {
                ArticleFilter.PAGES_CONTINUE -> R.string.articleList_emptyState_continueReadingMessage
                ArticleFilter.PAGES_UNREAD -> R.string.articleList_emptyState_allMessage
                ArticleFilter.VIDEOS_CONTINUE -> R.string.articleList_emptyState_continueWatchingMessage
                ArticleFilter.VIDEOS_UNREAD -> R.string.articleList_emptyState_unseenMessage
                ArticleFilter.AUDIO_CONTINUE -> R.string.articleList_emptyState_continueListeningMessage
                ArticleFilter.AUDIO_UNREAD -> R.string.articleList_emptyState_unheardMessage
                ArticleFilter.FILES_UNREAD -> R.string.articleList_emptyState_filesMessage
                ArticleFilter.PAGES_FAVORITES, ArticleFilter.VIDEOS_FAVORITES, ArticleFilter.AUDIO_FAVORITES,
                ArticleFilter.FILES_FAVORITES -> R.string.articleList_emptyState_favoritesMessage
                ArticleFilter.PAGES_ARCHIVE, ArticleFilter.VIDEOS_ARCHIVE, ArticleFilter.AUDIO_ARCHIVE,
                ArticleFilter.FILES_ARCHIVE -> R.string.articleList_emptyState_archiveMessage
            },
        )
    }
    Column(
        modifier = modifier.padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            imageVector = filter.icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(48.dp),
        )
        Text(stringResource(R.string.articleList_emptyState_title), style = MaterialTheme.typography.titleMedium)
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        OutlinedButton(onClick = onRefresh, modifier = Modifier.padding(top = 4.dp)) {
            Text(stringResource(R.string.articleList_emptyState_refreshButton))
        }
    }
}
