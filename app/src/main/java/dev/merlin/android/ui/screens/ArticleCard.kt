package dev.merlin.android.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.ImageLoader
import dev.merlin.android.models.Article
import dev.merlin.android.models.Tag
import dev.merlin.android.ui.components.SwipeAction
import dev.merlin.android.ui.components.SwipeActionsRow

private val FavoriteYellow = Color(0xFFFFC107)
private val LockOrange = Color(0xFFFF9500)
private const val ARCHIVED_ALPHA = 0.6f

/** Lesefortschritt nur zwischen 1 % und 99 % anzeigen, wie in ArticleRowView.swift/ArticleCardView.swift. */
private fun Float.isVisibleProgress() = this > 0.01f && this < 0.99f

private fun parseAccent(hex: String): Color =
    runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrDefault(Color(0xFFFF3B30))

/**
 * Äquivalent zu `ArticleRowView.swift` (iOS-Listenansicht): schlichte Zeile ohne Karte,
 * Vorschaubild rechts, Lesefortschritt als Ring mit Prozentangabe, Schloss bei Login/Paywall
 * und eine „Archiviert“-Markierung in gemischten Listen. Aktionen gibt es wie auf iOS nur über
 * Wischen (Teilen links, Archiv/Favorit/Löschen rechts) und langes Drücken (Kontextmenü) –
 * kein sichtbarer Stern- oder ⋮-Button.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ArticleRow(
    article: Article,
    imageLoader: ImageLoader,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    onToggleArchive: () -> Unit,
    onDelete: () -> Unit,
    onEditTags: () -> Unit,
    activeSwipeKey: MutableState<Any?>,
    modifier: Modifier = Modifier,
    /** Lesefortschritt 0–1 (lokal/Server per Last-Write-Wins aufgelöst, siehe ArticleListScreen). */
    progress: Float = 0f,
    /** Äquivalent zu `showFavoriteAction` (Swift) – ausgeblendet in der Favoriten-Ansicht. */
    showFavoriteAction: Boolean = true,
    /** Äquivalent zu `showArchiveAction` (Swift) – ausgeblendet in der Archiv-Ansicht. */
    showArchiveAction: Boolean = true,
    /** Archivierte Artikel markieren und abblenden; die Archiv-Ansicht schaltet es ab. */
    showArchivedState: Boolean = true,
    /** Hintergrundfarbe hinter dem Logo-Platzhalter in [ArticleThumbnail] und Farbe des Fortschrittsrings. */
    accentColorHex: String = "#FF3B30",
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val isMarkedArchived = showArchivedState && article.isArchived
    val accent = remember(accentColorHex) { parseAccent(accentColorHex) }

    SwipeActionsRow(
        swipeKey = article.id,
        activeSwipeKey = activeSwipeKey,
        leadingAction = buildShareAction(article, context),
        trailingActions = buildTrailingActions(article, showFavoriteAction, showArchiveAction, onToggleArchive, onToggleFavorite, onDelete),
        modifier = modifier.fillMaxWidth(),
    ) {
        Box {
            Row(
                verticalAlignment = Alignment.Top,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .combinedClickable(onClick = onClick, onLongClick = { menuExpanded = true })
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    TitleLine(
                        article = article,
                        maxLines = 2,
                        modifier = Modifier.alpha(if (isMarkedArchived) ARCHIVED_ALPHA else 1f),
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        MetaText(article.displaySiteName, modifier = Modifier.weight(1f, fill = false))
                        if (article.readingTime > 0) {
                            MetaText(" · ${article.readingTime} min")
                        }
                        if (progress.isVisibleProgress()) {
                            Spacer(Modifier.width(6.dp))
                            CircularProgressIndicator(
                                progress = { progress },
                                color = accent,
                                trackColor = accent.copy(alpha = 0.2f),
                                strokeWidth = 1.5.dp,
                                modifier = Modifier.size(10.dp),
                            )
                            Spacer(Modifier.width(3.dp))
                            MetaText("${(progress * 100).toInt()}%", small = true)
                        }
                        if (article.isFavorite) {
                            Spacer(Modifier.width(6.dp))
                            Icon(Icons.Filled.Star, contentDescription = "Favorit", tint = FavoriteYellow, modifier = Modifier.size(12.dp))
                        }
                        if (isMarkedArchived) {
                            Spacer(Modifier.width(6.dp))
                            Icon(
                                Icons.Filled.Inventory2,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(11.dp),
                            )
                            Spacer(Modifier.width(2.dp))
                            MetaText("Archiviert", small = true)
                        }
                    }
                    if (article.tags.isNotEmpty()) {
                        Spacer(Modifier.height(4.dp))
                        TagLine(article.tags)
                    }
                }

                Spacer(Modifier.width(12.dp))

                ArticleThumbnail(
                    imageUrl = article.imageUrl,
                    imageLoader = imageLoader,
                    contentDescription = article.displayTitle,
                    modifier = Modifier
                        .size(width = 72.dp, height = 54.dp)
                        .alpha(if (isMarkedArchived) ARCHIVED_ALPHA else 1f),
                    accentColorHex = accentColorHex,
                )
            }

            ArticleContextMenu(
                expanded = menuExpanded,
                onDismiss = { menuExpanded = false },
                article = article,
                showFavoriteAction = showFavoriteAction,
                showArchiveAction = showArchiveAction,
                onToggleFavorite = onToggleFavorite,
                onToggleArchive = onToggleArchive,
                onEditTags = onEditTags,
                onDelete = onDelete,
            )
        }
    }
}

/**
 * Äquivalent zu `ArticleCardView.swift` (iOS-Kartenansicht): 16:9-Hero-Bild mit
 * Lesefortschrittsbalken und „Archiviert“-Plakette, darunter Titel, Meta und Tags. Wie auf
 * iOS ohne Wischgesten und ohne ⋮-Button – alle Aktionen über langes Drücken.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ArticleGridCard(
    article: Article,
    imageLoader: ImageLoader,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    onToggleArchive: () -> Unit,
    onDelete: () -> Unit,
    onEditTags: () -> Unit,
    modifier: Modifier = Modifier,
    progress: Float = 0f,
    showFavoriteAction: Boolean = true,
    showArchiveAction: Boolean = true,
    showArchivedState: Boolean = true,
    accentColorHex: String = "#FF3B30",
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val isMarkedArchived = showArchivedState && article.isArchived
    val accent = remember(accentColorHex) { parseAccent(accentColorHex) }

    Box(modifier = modifier.fillMaxWidth()) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .combinedClickable(onClick = onClick, onLongClick = { menuExpanded = true }),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f),
            ) {
                ArticleThumbnail(
                    imageUrl = article.imageUrl,
                    imageLoader = imageLoader,
                    contentDescription = article.displayTitle,
                    modifier = Modifier
                        .fillMaxSize()
                        .alpha(if (isMarkedArchived) ARCHIVED_ALPHA else 1f),
                    accentColorHex = accentColorHex,
                    cornerRadius = 0.dp,
                )
                if (isMarkedArchived) {
                    ArchivedBadge(modifier = Modifier.align(Alignment.TopStart).padding(6.dp))
                }
                if (progress.isVisibleProgress()) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth(progress)
                            .height(3.dp)
                            .background(accent),
                    )
                }
            }

            Column(modifier = Modifier.padding(10.dp)) {
                TitleLine(
                    article = article,
                    maxLines = 3,
                    modifier = Modifier.alpha(if (isMarkedArchived) ARCHIVED_ALPHA else 1f),
                )
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    MetaText(article.displaySiteName, small = true, modifier = Modifier.weight(1f, fill = false))
                    if (article.readingTime > 0) {
                        MetaText(" · ${article.readingTime} min", small = true)
                    }
                    if (article.isFavorite) {
                        Spacer(Modifier.width(4.dp))
                        Icon(Icons.Filled.Star, contentDescription = "Favorit", tint = FavoriteYellow, modifier = Modifier.size(12.dp))
                    }
                }
                if (article.tags.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    TagLine(article.tags)
                }
            }
        }

        ArticleContextMenu(
            expanded = menuExpanded,
            onDismiss = { menuExpanded = false },
            article = article,
            showFavoriteAction = showFavoriteAction,
            showArchiveAction = showArchiveAction,
            onToggleFavorite = onToggleFavorite,
            onToggleArchive = onToggleArchive,
            onEditTags = onEditTags,
            onDelete = onDelete,
        )
    }
}

/** Titel plus Status-Symbole (Verarbeitung, Login/Paywall, nicht unterstützte Seite) wie auf iOS. */
@Composable
private fun TitleLine(article: Article, maxLines: Int, modifier: Modifier = Modifier) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier) {
        Text(
            text = article.displayTitle,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        if (article.isProcessing) {
            Spacer(Modifier.width(6.dp))
            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
        }
        if (article.requiresLoginDomain != null || article.isPaywalled) {
            Spacer(Modifier.width(6.dp))
            Icon(Icons.Filled.Lock, contentDescription = "Paywall", tint = LockOrange, modifier = Modifier.size(12.dp))
        }
        if (article.unsupportedSiteDomain != null) {
            Spacer(Modifier.width(6.dp))
            Icon(Icons.Filled.Block, contentDescription = "Seite nicht unterstützt", tint = LockOrange, modifier = Modifier.size(12.dp))
        }
    }
}

@Composable
private fun MetaText(text: String, modifier: Modifier = Modifier, small: Boolean = false) {
    Text(
        text = text,
        style = if (small) MaterialTheme.typography.labelSmall else MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}

@Composable
private fun TagLine(tags: List<Tag>) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        items(tags) { tag -> TagChip(tag) }
    }
}

/** Kleine Plakette „Archiviert“ auf dem Kartenbild – dunkel und nicht abgeblendet, damit sie auf jedem Bild lesbar bleibt. */
@Composable
private fun ArchivedBadge(modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(Color.Black.copy(alpha = 0.65f))
            .padding(horizontal = 7.dp, vertical = 3.dp),
    ) {
        Icon(Icons.Filled.Inventory2, contentDescription = null, tint = Color.White, modifier = Modifier.size(11.dp))
        Spacer(Modifier.width(3.dp))
        Text("Archiviert", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, color = Color.White)
    }
}

/**
 * Kontextmenü beim langen Drücken, gleiche Einträge und Reihenfolge wie `.contextMenu` in
 * ArticleRowView.swift/ArticleCardView.swift: Favorit, Archiv – Tags – Link kopieren, Teilen –
 * Löschen.
 */
@Composable
private fun ArticleContextMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    article: Article,
    showFavoriteAction: Boolean,
    showArchiveAction: Boolean,
    onToggleFavorite: () -> Unit,
    onToggleArchive: () -> Unit,
    onEditTags: () -> Unit,
    onDelete: () -> Unit,
) {
    val context = LocalContext.current
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        if (article.isFavorite) {
            MenuItem("Aus Favoriten entfernen", Icons.Filled.StarBorder) { onDismiss(); onToggleFavorite() }
        } else if (showFavoriteAction) {
            MenuItem("Zu Favoriten hinzufügen", Icons.Filled.Star) { onDismiss(); onToggleFavorite() }
        }
        if (article.isArchived) {
            MenuItem("Als ungelesen markieren", Icons.Filled.Unarchive) { onDismiss(); onToggleArchive() }
        } else if (showArchiveAction) {
            MenuItem("Archivieren", Icons.Filled.Archive) { onDismiss(); onToggleArchive() }
        }
        HorizontalDivider()
        MenuItem("Tags bearbeiten", Icons.Filled.Tag) { onDismiss(); onEditTags() }
        HorizontalDivider()
        MenuItem("Link kopieren", Icons.Filled.Link) {
            onDismiss()
            val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            clipboard.setPrimaryClip(android.content.ClipData.newPlainText(article.displayTitle, article.url))
        }
        MenuItem("Teilen", Icons.Filled.Share) { onDismiss(); shareArticle(article, context) }
        HorizontalDivider()
        DropdownMenuItem(
            text = { Text("Löschen", color = MaterialTheme.colorScheme.error) },
            leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            onClick = { onDismiss(); onDelete() },
        )
    }
}

@Composable
private fun MenuItem(label: String, icon: ImageVector, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(label) },
        leadingIcon = { Icon(icon, contentDescription = null) },
        onClick = onClick,
    )
}

/**
 * Trailing-Swipe-Aktionen der Listenansicht, wie `.swipeActions(edge: .trailing)` in
 * ArticleRowView.swift: Archiv ganz außen (Mail-Muster), dann Favorit, Löschen am
 * content-nächsten. [SwipeActionsRow] legt das erste Element der Liste innen an.
 */
private fun buildTrailingActions(
    article: Article,
    showFavoriteAction: Boolean,
    showArchiveAction: Boolean,
    onToggleArchive: () -> Unit,
    onToggleFavorite: () -> Unit,
    onDelete: () -> Unit,
): List<SwipeAction> = buildList {
    add(SwipeAction(icon = Icons.Filled.Delete, label = "Löschen", color = Color(0xFFD32F2F), onClick = onDelete))
    if (article.isFavorite || showFavoriteAction) {
        add(
            SwipeAction(
                icon = if (article.isFavorite) Icons.Filled.StarBorder else Icons.Filled.Star,
                label = if (article.isFavorite) "Entfernen" else "Favorit",
                color = FavoriteYellow,
                onClick = onToggleFavorite,
            ),
        )
    }
    if (article.isArchived || showArchiveAction) {
        add(
            SwipeAction(
                icon = if (article.isArchived) Icons.Filled.Unarchive else Icons.Filled.Archive,
                label = if (article.isArchived) "Ungelesen" else "Archiv",
                color = Color(0xFFF57C00),
                onClick = onToggleArchive,
            ),
        )
    }
}

/** Leading-Swipe-Aktion „Teilen“ der Listenansicht. */
private fun buildShareAction(article: Article, context: android.content.Context): SwipeAction = SwipeAction(
    icon = Icons.Filled.Share,
    label = "Teilen",
    color = Color(0xFF1976D2),
    onClick = { shareArticle(article, context) },
)

/** System-Teilen-Dialog mit der Artikel-URL (Äquivalent zu `ArticleShareLink`). */
private fun shareArticle(article: Article, context: android.content.Context) {
    val sendIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(android.content.Intent.EXTRA_TEXT, article.url)
        putExtra(android.content.Intent.EXTRA_SUBJECT, article.displayTitle)
    }
    context.startActivity(android.content.Intent.createChooser(sendIntent, null))
}

@Composable
private fun TagChip(tag: Tag) {
    val chipColor = remember(tag.color) {
        tag.color?.let { hex ->
            runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrNull()
        } ?: Color.Gray
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(chipColor.copy(alpha = 0.2f))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(text = tag.name, style = MaterialTheme.typography.labelSmall, color = chipColor, maxLines = 1)
    }
}
