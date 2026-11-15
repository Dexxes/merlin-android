package dev.merlin.android.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.HeadsetMic
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.OndemandVideo
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.outlined.Tag
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.merlin.android.R
import dev.merlin.android.models.ArticleFilter
import dev.merlin.android.models.ContentGroup
import dev.merlin.android.models.Tag
import dev.merlin.android.network.ArticleCounts

/** Icon je Filter, Material-Gegenstück zu `ArticleFilter.systemImage` (Swift). */
val ArticleFilter.icon: ImageVector
    get() = when (this) {
        ArticleFilter.PAGES_CONTINUE -> Icons.AutoMirrored.Filled.MenuBook
        ArticleFilter.PAGES_UNREAD -> Icons.Filled.Inbox
        ArticleFilter.VIDEOS_CONTINUE -> Icons.Filled.PlayCircle
        ArticleFilter.VIDEOS_UNREAD -> Icons.Filled.OndemandVideo
        ArticleFilter.AUDIO_CONTINUE -> Icons.Filled.HeadsetMic
        ArticleFilter.AUDIO_UNREAD -> Icons.Filled.Headphones
        ArticleFilter.PAGES_FAVORITES, ArticleFilter.VIDEOS_FAVORITES, ArticleFilter.AUDIO_FAVORITES -> Icons.Filled.StarBorder
        ArticleFilter.PAGES_ARCHIVE, ArticleFilter.VIDEOS_ARCHIVE, ArticleFilter.AUDIO_ARCHIVE -> Icons.Filled.Inventory2
    }

/**
 * Filter-Beschriftung mit Server-Zähler, wie `filterLabel(_:)` in `ListFlyoutModifier.swift`.
 * „Weiterlesen/-schauen/-hören“ wird rein client-seitig gefiltert und hat keinen Zähler.
 */
private fun filterLabel(filter: ArticleFilter, counts: ArticleCounts): String {
    val group = when (filter.group) {
        ContentGroup.PAGES -> counts.pages
        ContentGroup.VIDEOS -> counts.videos
        ContentGroup.AUDIO -> counts.audio
    }
    val count = when (filter.kind) {
        ArticleFilter.Kind.CONTINUE -> return filter.label
        ArticleFilter.Kind.UNREAD -> group.unread
        ArticleFilter.Kind.FAVORITES -> group.favorites
        ArticleFilter.Kind.ARCHIVE -> group.archived
    }
    return "${filter.label} ($count)"
}

/**
 * Seitenmenü der Artikelliste, Äquivalent zu `ListFlyoutModifier.swift`: oben Tabs für die
 * Medienart (Text/Video/Audio), darunter deren Ansichten mit Zählern, ausklappbare Tag-Liste,
 * Listen-/Kartenansicht, Erinnerungen und Einstellungen; unten das Merlin-Logo.
 *
 * Ein Tab-Wechsel lädt nichts, erst die Wahl einer Zeile (wie auf iOS). Der Tab steht beim
 * Öffnen auf der Gruppe des aktiven Filters ([initialGroup], vom Aufrufer bei jedem Öffnen neu
 * neu gesetzt über [resetKey]).
 */
@Composable
fun ListDrawerContent(
    initialGroup: ContentGroup,
    /** Wechselt bei jedem Öffnen, damit der Tab wieder auf [initialGroup] springt. */
    resetKey: Any,
    selectedFilter: ArticleFilter,
    selectedTagId: Int?,
    counts: ArticleCounts,
    allTags: List<Tag>,
    isCardView: Boolean,
    onSelectFilter: (ArticleFilter) -> Unit,
    onSelectTag: (Int?) -> Unit,
    onToggleCardView: () -> Unit,
    onRemindersClick: () -> Unit,
    onSettingsClick: () -> Unit,
) {
    var menuGroup by remember(initialGroup, resetKey) { mutableStateOf(initialGroup) }
    var tagsExpanded by rememberSaveable { mutableStateOf(false) }

    ModalDrawerSheet(modifier = Modifier.width(300.dp)) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(top = 12.dp),
        ) {
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
            ) {
                val groups = ContentGroup.entries
                groups.forEachIndexed { index, group ->
                    SegmentedButton(
                        selected = menuGroup == group,
                        onClick = { menuGroup = group },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = groups.size),
                        icon = {},
                    ) { Text(group.label) }
                }
            }

            ArticleFilter.entries.filter { it.group == menuGroup }.forEach { filter ->
                DrawerRow(
                    icon = filter.icon,
                    label = filterLabel(filter, counts),
                    selected = filter == selectedFilter && selectedTagId == null,
                    onClick = { onSelectFilter(filter) },
                )
            }

            if (allTags.isNotEmpty()) {
                DrawerDivider()
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { tagsExpanded = !tagsExpanded }
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                ) {
                    Icon(Icons.Outlined.Tag, contentDescription = null, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(14.dp))
                    Text("Tags", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Icon(
                        Icons.Filled.ChevronRight,
                        contentDescription = if (tagsExpanded) "Zuklappen" else "Aufklappen",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.rotate(if (tagsExpanded) 90f else 0f),
                    )
                }
                AnimatedVisibility(visible = tagsExpanded) {
                    Column {
                        allTags.sortedBy { it.name.lowercase() }.forEach { tag ->
                            val isSelected = tag.id == selectedTagId
                            DrawerRow(
                                icon = if (isSelected) Icons.Filled.Tag else Icons.Outlined.Tag,
                                label = tag.name,
                                selected = isSelected,
                                indented = true,
                                onClick = { onSelectTag(tag.id) },
                            )
                        }
                        if (selectedTagId != null) {
                            DrawerRow(
                                icon = Icons.Filled.Cancel,
                                label = "Tag-Filter aufheben",
                                tint = MaterialTheme.colorScheme.error,
                                indented = true,
                                onClick = { onSelectTag(null) },
                            )
                        }
                    }
                }
            }

            DrawerDivider()
            DrawerRow(
                icon = if (isCardView) Icons.AutoMirrored.Filled.ViewList else Icons.Filled.GridView,
                label = if (isCardView) "Listenansicht" else "Kartenansicht",
                onClick = onToggleCardView,
            )

            DrawerDivider()
            DrawerRow(icon = Icons.Filled.Notifications, label = "Erinnerungen", onClick = onRemindersClick)
            DrawerRow(icon = Icons.Filled.Settings, label = "Einstellungen", onClick = onSettingsClick)
        }

        // Merlin-Logo, immer am unteren Rand sichtbar (wie im iOS-Seitenmenü).
        Image(
            bitmap = ImageBitmap.imageResource(R.drawable.merlin_logo),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            filterQuality = FilterQuality.None,
            modifier = Modifier
                .padding(start = 20.dp, top = 14.dp, bottom = 14.dp)
                .height(60.dp)
                .alpha(0.22f),
        )
    }
}

@Composable
private fun DrawerRow(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    selected: Boolean = false,
    indented: Boolean = false,
    tint: Color? = null,
) {
    val color = tint ?: if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = if (indented) 40.dp else 20.dp, end = 20.dp, top = 14.dp, bottom = 14.dp),
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(14.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun DrawerDivider() {
    HorizontalDivider(modifier = Modifier.padding(start = 20.dp))
}
