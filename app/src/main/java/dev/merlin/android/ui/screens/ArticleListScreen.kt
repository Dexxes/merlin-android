package dev.merlin.android.ui.screens

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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.merlin.android.models.ArticleFilter
import dev.merlin.android.viewmodel.ArticlesViewModel

/**
 * Äquivalent zur Artikel-Liste aus dem iOS-Original (`ArticleListView`/
 * `HomeView`-Bereich). Nutzt `PullToRefreshBox` (Äquivalent zu SwiftUIs
 * `.refreshable { await viewModel.load() }`) statt eines dauerhaften
 * TopAppBar-Refresh-Buttons – iOS hat ebenfalls keinen solchen Button,
 * nur die Geste plus einen "Refresh"-Button im Empty-State.
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
    val selectedFilter by viewModel.selectedFilter.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isOffline by viewModel.isOffline.collectAsState()
    val error by viewModel.error.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val lastUndoableAction by viewModel.lastUndoableAction.collectAsState()
    val undoToast by viewModel.undoToast.collectAsState()
    val excludedTagIds by viewModel.excludedTagIds.collectAsState()
    val allTags by viewModel.allTags.collectAsState()
    val isCardView by viewModel.isCardView.collectAsState()
    val accentColorHex by viewModel.accentColorHex.collectAsState()
    val selectedTagId by viewModel.selectedTagId.collectAsState()
    val showArchivedInTagView by viewModel.showArchivedInTagView.collectAsState()
    val selectedTagName = selectedTagId?.let { id -> allTags.firstOrNull { it.id == id }?.name }

    var searchVisible by remember { mutableStateOf(false) }
    var tagsArticleId by remember { mutableStateOf<Int?>(null) }
    var showAddSheet by remember { mutableStateOf(false) }
    var showTagFilterSheet by remember { mutableStateOf(false) }
    // Hamburger-Menü: bewusst getrennt vom Augen-Icon-Flyout (TagFilterSheet) – enthält
    // List/Card-Umschalter sowie Erinnerungen und Einstellungen (vormals eigene
    // Toolbar-Icons), auf Wunsch explizit NICHT wie auf iOS im Augen-Icon-Flyout
    // platziert (siehe ListFlyoutModifier.swift dort als Vergleich).
    var showOverflowMenu by remember { mutableStateOf(false) }

    // Äquivalent zu iOS' `@State private var activeSwipeId: Int?` in ArticleListView.swift –
    // wird per Referenz an jede ArticleCard durchgereicht, damit immer nur eine Swipe-Reihe
    // offen ist (siehe SwipeActionsRow.kt).
    val activeSwipeKey = remember { mutableStateOf<Any?>(null) }

    val snackbarHostState = remember { SnackbarHostState() }

    // Snackbar mit "Rückgängig"-Action, sobald eine Mutation passiert ist.
    LaunchedEffect(lastUndoableAction) {
        val action = lastUndoableAction ?: return@LaunchedEffect
        val result = snackbarHostState.showSnackbar(
            message = action.promptDescription,
            actionLabel = "Rückgängig",
            withDismissAction = true,
        )
        if (result == SnackbarResult.ActionPerformed) viewModel.undo()
    }

    // Zweiter Toast (Bestätigung NACH dem Rückgängig-Machen).
    LaunchedEffect(undoToast) {
        val message = undoToast ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
    }

    // Äquivalent zu iOS' `.onChange(of: scenePhase)` (ArticleListView.swift): beim
    // Zurückkehren aus dem Hintergrund erneut laden. Ohne das blieb `isOffline` auf
    // Android bis zum manuellen Pull-to-Refresh/Filterwechsel hängen – z.B. wenn der
    // erste Ladeversuch während aktiviertem Flugmodus fehlschlug und der Nutzer danach
    // (bei weiterhin aktivem Flugmodus) nur das WLAN einschaltete: die App merkte das
    // nie automatisch, weil nirgends auf Connectivity-Änderungen oder Resume gelauscht
    // wurde. `isFirstResume` überspringt den synchron beim Registrieren ausgelösten
    // ON_RESUME (Activity ist beim ersten Compose-Durchlauf bereits resumed), damit
    // hier nicht parallel zum `init`-Load in ArticlesViewModel ein zweiter Fetch läuft.
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

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    if (selectedTagName != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Filled.Tag,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Text(selectedTagName, modifier = Modifier.padding(start = 6.dp))
                        }
                    } else {
                        Text("Merlin")
                    }
                },
                navigationIcon = {
                    // Äquivalent zum iOS-`topBarLeading`-Toolbar-Button (Augen-Icon + Badge) in
                    // ArticleListView. In der Einzel-Tag-Ansicht übernimmt derselbe Button die
                    // Archiv-Sichtbarkeit für diesen Tag statt des Tag-Filter-Sheets (das dort
                    // keinen Sinn ergibt, da bereits auf einen Tag gefiltert ist).
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
                                contentDescription = if (showArchivedInTagView) "Archiv ausblenden" else "Archiv einblenden",
                            )
                        } else {
                            BadgedBox(
                                badge = {
                                    if (excludedTagIds.isNotEmpty()) {
                                        Badge { Text("${excludedTagIds.size}") }
                                    }
                                },
                            ) {
                                Icon(Icons.Filled.VisibilityOff, contentDescription = "Tag-Filter")
                            }
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { showAddSheet = true }) {
                        Icon(Icons.Filled.Add, contentDescription = "Artikel hinzufügen")
                    }
                    IconButton(onClick = { searchVisible = !searchVisible }) {
                        Icon(Icons.Filled.Search, contentDescription = "Suchen")
                    }
                    Box {
                        IconButton(onClick = { showOverflowMenu = true }) {
                            Icon(Icons.Filled.Menu, contentDescription = "Menü")
                        }
                        DropdownMenu(expanded = showOverflowMenu, onDismissRequest = { showOverflowMenu = false }) {
                            DropdownMenuItem(
                                text = { Text(if (isCardView) "Listenansicht" else "Kartenansicht") },
                                leadingIcon = {
                                    Icon(
                                        imageVector = if (isCardView) Icons.AutoMirrored.Filled.ViewList else Icons.Filled.GridView,
                                        contentDescription = null,
                                    )
                                },
                                onClick = {
                                    showOverflowMenu = false
                                    viewModel.setIsCardView(!isCardView)
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Erinnerungen") },
                                leadingIcon = {
                                    Icon(Icons.Filled.Notifications, contentDescription = null)
                                },
                                onClick = {
                                    showOverflowMenu = false
                                    onRemindersClick()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Einstellungen") },
                                leadingIcon = {
                                    Icon(Icons.Filled.Settings, contentDescription = null)
                                },
                                onClick = {
                                    showOverflowMenu = false
                                    onSettingsClick()
                                },
                            )
                        }
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

            if (searchVisible) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = viewModel::setSearchQuery,
                    placeholder = { Text("Artikel durchsuchen…") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ArticleFilter.entries.forEach { filter ->
                    FilterChip(
                        // Kein Filter-Chip aktiv, solange eine Einzel-Tag-Ansicht läuft
                        // (Äquivalent zu iOS' `selectedFilter == filter && selectedTagId == nil`
                        // in `ListFlyoutModifier.swift`).
                        selected = filter == selectedFilter && selectedTagId == null,
                        onClick = { viewModel.selectFilter(filter) },
                        label = { Text(filter.label) },
                    )
                }
            }

            if (isOffline) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.CloudOff, contentDescription = null, modifier = Modifier.size(16.dp))
                    Text(
                        text = "Offline – zeige zwischengespeicherte Artikel",
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

            // Äquivalent zu `.refreshable { await viewModel.load() }` in ArticleListView.swift.
            // iOS hat dafür keinen dauerhaften Toolbar-Button, nur die Geste plus einen
            // "Refresh"-Button im Empty-State (siehe unten) – daher der entfernte IconButton oben.
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
                        articles.isEmpty() -> {
                            Column(
                                modifier = Modifier.align(Alignment.Center),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Text(
                                    text = "Keine Artikel",
                                    style = MaterialTheme.typography.bodyLarge,
                                )
                                OutlinedButton(
                                    onClick = { viewModel.load() },
                                    modifier = Modifier.padding(top = 12.dp),
                                ) {
                                    Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Text("Aktualisieren", modifier = Modifier.padding(start = 6.dp))
                                }
                            }
                        }
                        else -> {
                            LazyColumn(
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxSize(),
                            ) {
                                items(articles, key = { it.id }) { article ->
                                    // Äquivalent zu iOS' `if isCardView { articleGrid } else { articleList }`
                                    // (ArticleListView.swift) – hier pro Zeile statt pro ganzer Liste entschieden,
                                    // da beide Varianten dieselbe LazyColumn/PullToRefreshBox-Hülle teilen.
                                    if (isCardView) {
                                        ArticleGridCard(
                                            article = article,
                                            imageLoader = viewModel.imageLoader,
                                            onClick = { onArticleClick(article.id) },
                                            onToggleFavorite = { viewModel.toggleFavorite(article) },
                                            onToggleArchive = { viewModel.toggleArchive(article) },
                                            onDelete = { viewModel.delete(article) },
                                            onEditTags = { tagsArticleId = article.id },
                                            activeSwipeKey = activeSwipeKey,
                                            showFavoriteAction = selectedFilter != ArticleFilter.FAVORITES,
                                            showArchiveAction = selectedFilter != ArticleFilter.ARCHIVE,
                                            accentColorHex = accentColorHex,
                                        )
                                    } else {
                                        ArticleCard(
                                            article = article,
                                            imageLoader = viewModel.imageLoader,
                                            onClick = { onArticleClick(article.id) },
                                            onToggleFavorite = { viewModel.toggleFavorite(article) },
                                            onToggleArchive = { viewModel.toggleArchive(article) },
                                            onDelete = { viewModel.delete(article) },
                                            onEditTags = { tagsArticleId = article.id },
                                            activeSwipeKey = activeSwipeKey,
                                            // Äquivalent zu ArticleListView.swift: Aktion ausblenden, wenn
                                            // der entsprechende Filter selbst schon aktiv ist.
                                            showFavoriteAction = selectedFilter != ArticleFilter.FAVORITES,
                                            showArchiveAction = selectedFilter != ArticleFilter.ARCHIVE,
                                            accentColorHex = accentColorHex,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
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
                onSave = { selectedIds, pendingNames -> viewModel.saveTags(article, selectedIds, pendingNames) },
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
            allTags = allTags,
            excludedTagIds = excludedTagIds,
            selectedTagId = selectedTagId,
            onToggle = { tagId -> viewModel.toggleTagExclusion(tagId) },
            onClearAll = { viewModel.clearExcludedTags() },
            onSelectTag = { tagId ->
                viewModel.selectTag(tagId)
                showTagFilterSheet = false
            },
            onClearTagFilter = { viewModel.selectTag(null) },
            onDismiss = { showTagFilterSheet = false },
        )
    }
}
