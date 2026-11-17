package dev.merlin.android.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.merlin.android.R
import dev.merlin.android.models.TagTree

/**
 * Äquivalent zu `TagFilterSheet.swift`: Verwaltung ausgeblendeter Tags
 * (filtert Artikel mit diesen Tags aus `ArticleListScreen`/`ArticlesViewModel.filteredArticles`
 * heraus). Wie `AddArticleSheet.kt`/`ReminderSheet.kt` ein Material3-`ModalBottomSheet`
 * statt des iOS-`NavigationStack`-Sheets mit `presentationDetents([.medium, .large])`.
 *
 * Tap auf das Augen-Icon entspricht iOS' `onToggle(tag.id)`; es zeigt den aktuellen
 * Zustand (`eye` = sichtbar, `eye.slash` = ausgeblendet). „Alle einblenden“ (nur sichtbar,
 * wenn etwas ausgeblendet ist) entspricht `onClearAll`.
 *
 * Tags stehen als eingerückter Baum (verschachtelte Tags). Ein ausgeblendeter Tag blendet auch
 * seine Unter-Tags aus; diese sind deshalb abgeblendet, auch wenn sie selbst nicht ausgeblendet
 * sind (wie `inheritedHiddenIds` in `TagFilterSheet.swift`).
 *
 * Wie auf iOS schaltet ein Tap auf die ganze Zeile die Sichtbarkeit um. Die Auswahl eines
 * einzelnen Tags liegt im Seitenmenü ([ListDrawerContent]), nicht mehr hier.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TagFilterSheet(
    tagTree: TagTree,
    excludedTagIds: Set<Int>,
    onToggle: (Int) -> Unit,
    onClearAll: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()
    val inheritedHidden = excludedTagIds.flatMapTo(mutableSetOf()) { tagTree.descendantIds(it) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.padding(horizontal = 4.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            ) {
                Text(stringResource(R.string.tagFilterSheet_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                if (excludedTagIds.isNotEmpty()) {
                    TextButton(onClick = onClearAll) { Text(stringResource(R.string.tagFilterSheet_clearAll)) }
                }
            }

            if (tagTree.tags.isEmpty()) {
                EmptyTagFilterState()
            } else {
                LazyColumn(contentPadding = PaddingValues(bottom = 8.dp)) {
                    items(tagTree.rows(), key = { it.tag.id }) { row ->
                        val tag = row.tag
                        val isExcluded = tag.id in excludedTagIds
                        val isInherited = !isExcluded && tag.id in inheritedHidden
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .alpha(if (isInherited) 0.45f else 1f)
                                .clickable { onToggle(tag.id) }
                                .padding(start = 16.dp + 20.dp * row.depth, end = 16.dp, top = 14.dp, bottom = 14.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Tag,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.width(24.dp),
                            )
                            Text(
                                tag.name,
                                modifier = Modifier.weight(1f).padding(start = 8.dp),
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Icon(
                                imageVector = if (isExcluded || isInherited) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                contentDescription = stringResource(
                                    when {
                                        isExcluded -> R.string.android_tagFilter_hidden
                                        isInherited -> R.string.android_tagFilter_hiddenWithParent
                                        else -> R.string.android_tagFilter_shown
                                    },
                                ),
                                tint = if (isExcluded || isInherited) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
                            )
                        }
                        HorizontalDivider()
                    }
                }

                if (excludedTagIds.isNotEmpty()) {
                    Row(
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.VisibilityOff,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.width(16.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        val count = excludedTagIds.size
                        Text(
                            pluralStringResource(R.plurals.tagFilterSheet_hiddenSummary, count, count),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Spacer(Modifier.width(8.dp))
        }
    }
}

@Composable
private fun EmptyTagFilterState() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp, horizontal = 24.dp),
    ) {
        Icon(
            imageVector = Icons.Filled.Tag,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            stringResource(R.string.tagFilterSheet_emptyTitle),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(top = 12.dp),
        )
        Text(
            stringResource(R.string.tagFilterSheet_emptyMessage),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}
