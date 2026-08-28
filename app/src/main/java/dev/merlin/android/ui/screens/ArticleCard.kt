package dev.merlin.android.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.ImageLoader
import dev.merlin.android.models.Article
import dev.merlin.android.models.Tag
import dev.merlin.android.ui.components.SwipeAction
import dev.merlin.android.ui.components.SwipeActionsRow

/**
 * Äquivalent zu `ArticleRowView.swift`/`ArticleCardView.swift`: sichtbares Favoriten-Icon
 * plus Overflow-Menü (Archivieren/Tags/Löschen) als primäre Touch-Targets, ZUSÄTZLICH
 * eine [SwipeActionsRow] mit Leading-Teilen + Trailing-Löschen/Archiv/Favorit – exakt die
 * doppelte Interaktionsebene (Swipe UND Kontextmenü) wie im iOS-Original, das ebenfalls
 * `.swipeActions` und `.contextMenu` mit denselben Aktionen parallel anbietet.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun ArticleCard(
    article: Article,
    imageLoader: ImageLoader,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    onToggleArchive: () -> Unit,
    onDelete: () -> Unit,
    onEditTags: () -> Unit,
    activeSwipeKey: MutableState<Any?>,
    modifier: Modifier = Modifier,
    /** Äquivalent zu `showFavoriteAction` (Swift) – ausgeblendet, wenn der Favoriten-Filter selbst aktiv ist. */
    showFavoriteAction: Boolean = true,
    /** Äquivalent zu `showArchiveAction` (Swift) – ausgeblendet, wenn der Archiv-Filter selbst aktiv ist. */
    showArchiveAction: Boolean = true,
    /** Hintergrundfarbe hinter dem Logo-Platzhalter in [ArticleThumbnail], siehe `NoImageView.swift`. */
    accentColorHex: String = "#FF3B30",
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val context = LocalContext.current

    val trailingActions = buildTrailingActions(article, showFavoriteAction, showArchiveAction, onToggleArchive, onToggleFavorite, onDelete)
    val leadingAction = buildShareAction(article, context)

    SwipeActionsRow(
        swipeKey = article.id,
        activeSwipeKey = activeSwipeKey,
        leadingAction = leadingAction,
        trailingActions = trailingActions,
        modifier = modifier.fillMaxWidth(),
    ) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = { menuExpanded = true }),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = androidx.compose.ui.Alignment.Top,
        ) {
            ArticleThumbnail(
                imageUrl = article.imageUrl,
                imageLoader = imageLoader,
                contentDescription = article.displayTitle,
                modifier = Modifier.size(width = 72.dp, height = 54.dp),
                accentColorHex = accentColorHex,
            )

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Text(
                        text = article.displayTitle,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (article.isProcessing) {
                        Spacer(modifier = Modifier.width(6.dp))
                        CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                    }
                }

                Spacer(modifier = Modifier.padding(top = 2.dp))

                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Text(
                        text = article.displaySiteName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (article.readingTime > 0) {
                        Text(
                            text = " · ${article.readingTime} min",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (article.isFavorite) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Filled.Star,
                            contentDescription = "Favorit",
                            tint = androidx.compose.ui.graphics.Color(0xFFFFC107),
                            modifier = Modifier.size(12.dp),
                        )
                    }
                }

                if (article.tags.isNotEmpty()) {
                    Spacer(modifier = Modifier.padding(top = 4.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        items(article.tags) { tag -> TagChip(tag) }
                    }
                }
            }

            IconButton(onClick = onToggleFavorite) {
                Icon(
                    imageVector = if (article.isFavorite) Icons.Filled.Star else Icons.Outlined.Star,
                    contentDescription = if (article.isFavorite) "Favorit entfernen" else "Als Favorit markieren",
                    tint = if (article.isFavorite) androidx.compose.ui.graphics.Color(0xFFFFC107) else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "Weitere Aktionen")
                }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    DropdownMenuItem(
                        text = { Text(if (article.isArchived) "Aus Archiv entfernen" else "Archivieren") },
                        leadingIcon = { Icon(if (article.isArchived) Icons.Filled.Inventory2 else Icons.Filled.Archive, contentDescription = null) },
                        onClick = { menuExpanded = false; onToggleArchive() },
                    )
                    DropdownMenuItem(
                        text = { Text("Tags bearbeiten…") },
                        leadingIcon = { Icon(Icons.Filled.Tag, contentDescription = null) },
                        onClick = { menuExpanded = false; onEditTags() },
                    )
                    DropdownMenuItem(
                        text = { Text("Löschen") },
                        leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null) },
                        onClick = { menuExpanded = false; onDelete() },
                    )
                }
            }
        }
    }
    }
}

/**
 * Gemeinsame Trailing-Swipe-Aktionen für [ArticleCard] und [ArticleGridCard] – Reihenfolge wie
 * ArticleRowView.swift (Löschen zuerst deklariert = am content-nächsten): Löschen, dann
 * Archiv/Unarchiv, dann Favorit/Unfavorit zuletzt (= am Bildschirmrand).
 */
private fun buildTrailingActions(
    article: Article,
    showFavoriteAction: Boolean,
    showArchiveAction: Boolean,
    onToggleArchive: () -> Unit,
    onToggleFavorite: () -> Unit,
    onDelete: () -> Unit,
): List<SwipeAction> = buildList {
    add(
        SwipeAction(
            icon = Icons.Filled.Delete,
            label = "Löschen",
            color = Color(0xFFD32F2F),
            onClick = onDelete,
        ),
    )
    if (article.isArchived || showArchiveAction) {
        add(
            SwipeAction(
                icon = if (article.isArchived) Icons.Filled.Unarchive else Icons.Filled.Archive,
                label = if (article.isArchived) "Unarchiv" else "Archiv",
                color = Color(0xFFF57C00),
                onClick = onToggleArchive,
            ),
        )
    }
    if (article.isFavorite || showFavoriteAction) {
        add(
            SwipeAction(
                icon = if (article.isFavorite) Icons.Outlined.Star else Icons.Filled.Star,
                label = if (article.isFavorite) "Entfernen" else "Favorit",
                color = Color(0xFFFFC107),
                onClick = onToggleFavorite,
            ),
        )
    }
}

/** Gemeinsame Leading-Swipe-Aktion ("Teilen") für [ArticleCard] und [ArticleGridCard]. */
private fun buildShareAction(article: Article, context: android.content.Context): SwipeAction = SwipeAction(
    icon = Icons.Filled.Share,
    label = "Teilen",
    color = Color(0xFF1976D2),
    onClick = {
        // Äquivalent zu ShareSheet/ShareLink (iOS): UIActivityViewController bzw.
        // ShareLink öffnen den System-Share-Sheet – hier das Android-Pendant ACTION_SEND.
        val sendIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(android.content.Intent.EXTRA_TEXT, article.url)
            putExtra(android.content.Intent.EXTRA_SUBJECT, article.displayTitle)
        }
        context.startActivity(android.content.Intent.createChooser(sendIntent, null))
    },
)

/**
 * Äquivalent zu `ArticleCardView.swift` (iOS' Grid/Card-Ansicht): volle Breite, 16:9-Hero-Bild
 * oben, darunter Titel/Meta/Tags. Anders als [ArticleCard] (Zeilen-Layout, kleines Vorschaubild
 * links) ist dies die "echte" Karten-Optik aus dem iOS-Original – Umschalten erfolgt über das
 * Hamburger-Menü in `ArticleListScreen.kt` (bewusst NICHT wie auf iOS im Augen-Icon-Flyout).
 *
 * Swipe-Gesten und Overflow-Menü sind 1:1 von [ArticleCard] übernommen (gleiche Aktionen,
 * gleiche Reihenfolge) statt iOS' eigener Pill-Button-Optik nachzubilden – auf Android gibt es
 * mit [SwipeActionsRow] bereits eine einheitliche Swipe-Komponente für beide Ansichten.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun ArticleGridCard(
    article: Article,
    imageLoader: ImageLoader,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    onToggleArchive: () -> Unit,
    onDelete: () -> Unit,
    onEditTags: () -> Unit,
    activeSwipeKey: MutableState<Any?>,
    modifier: Modifier = Modifier,
    showFavoriteAction: Boolean = true,
    showArchiveAction: Boolean = true,
    /** Hintergrundfarbe hinter dem Logo-Platzhalter in [ArticleThumbnail], siehe `NoImageView.swift`. */
    accentColorHex: String = "#FF3B30",
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val context = LocalContext.current

    val trailingActions = buildTrailingActions(article, showFavoriteAction, showArchiveAction, onToggleArchive, onToggleFavorite, onDelete)
    val leadingAction = buildShareAction(article, context)

    SwipeActionsRow(
        swipeKey = article.id,
        activeSwipeKey = activeSwipeKey,
        leadingAction = leadingAction,
        trailingActions = trailingActions,
        modifier = modifier.fillMaxWidth(),
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(onClick = onClick, onLongClick = { menuExpanded = true }),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
        ) {
            ArticleThumbnail(
                imageUrl = article.imageUrl,
                imageLoader = imageLoader,
                contentDescription = article.displayTitle,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(0.dp)),
                accentColorHex = accentColorHex,
            )

            Column(modifier = Modifier.padding(10.dp)) {
                Row(verticalAlignment = androidx.compose.ui.Alignment.Top) {
                    Text(
                        text = article.displayTitle,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (article.isProcessing) {
                        Spacer(modifier = Modifier.width(6.dp))
                        CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                    }
                }

                Spacer(modifier = Modifier.padding(top = 4.dp))

                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Text(
                        text = article.displaySiteName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (article.readingTime > 0) {
                        Text(
                            text = " · ${article.readingTime} min",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (article.isFavorite) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Filled.Star,
                            contentDescription = "Favorit",
                            tint = Color(0xFFFFC107),
                            modifier = Modifier.size(12.dp),
                        )
                    }
                }

                if (article.tags.isNotEmpty()) {
                    Spacer(modifier = Modifier.padding(top = 6.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        items(article.tags) { tag -> TagChip(tag) }
                    }
                }
            }
        }

        // Overflow-Menü (Favorit/Archiv/Tags/Löschen), analog [ArticleCard] – auf der
        // Hero-Bild-Karte gibt es dafür keinen sichtbaren Platz in der Zeile, daher hier als
        // Overlay oben rechts auf dem Bild statt als eigene Spalte wie im Zeilen-Layout.
        // Dunkler Kreis-Hintergrund sorgt für Lesbarkeit unabhängig vom Bildinhalt darunter.
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = androidx.compose.ui.Alignment.TopEnd) {
            Box(
                modifier = Modifier
                    .padding(6.dp)
                    .clip(androidx.compose.foundation.shape.CircleShape)
                    .background(Color.Black.copy(alpha = 0.35f)),
            ) {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "Weitere Aktionen", tint = Color.White)
                }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    DropdownMenuItem(
                        text = { Text(if (article.isFavorite) "Favorit entfernen" else "Als Favorit markieren") },
                        leadingIcon = { Icon(if (article.isFavorite) Icons.Filled.Star else Icons.Outlined.Star, contentDescription = null) },
                        onClick = { menuExpanded = false; onToggleFavorite() },
                    )
                    DropdownMenuItem(
                        text = { Text(if (article.isArchived) "Aus Archiv entfernen" else "Archivieren") },
                        leadingIcon = { Icon(if (article.isArchived) Icons.Filled.Inventory2 else Icons.Filled.Archive, contentDescription = null) },
                        onClick = { menuExpanded = false; onToggleArchive() },
                    )
                    DropdownMenuItem(
                        text = { Text("Tags bearbeiten…") },
                        leadingIcon = { Icon(Icons.Filled.Tag, contentDescription = null) },
                        onClick = { menuExpanded = false; onEditTags() },
                    )
                    DropdownMenuItem(
                        text = { Text("Löschen") },
                        leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null) },
                        onClick = { menuExpanded = false; onDelete() },
                    )
                }
            }
        }
    }
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
