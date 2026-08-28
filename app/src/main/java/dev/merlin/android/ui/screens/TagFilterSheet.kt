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
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.merlin.android.models.Tag

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
 * Tap auf den Rest der Zeile (Icon + Name) filtert die Artikelliste stattdessen auf genau
 * diesen einen Tag (Äquivalent zu iOS' Tag-Auswahl im Flyout-Menü/Sidebar, siehe
 * `ListFlyoutModifier.swift`/`ArticleListView.swift` dort – auf Android gab es dafür bisher
 * keinen UI-Einstieg, obwohl `ArticlesViewModel.selectTag()` bereits existierte).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TagFilterSheet(
    allTags: List<Tag>,
    excludedTagIds: Set<Int>,
    selectedTagId: Int? = null,
    onToggle: (Int) -> Unit,
    onClearAll: () -> Unit,
    onSelectTag: (Int) -> Unit = {},
    onClearTagFilter: () -> Unit = {},
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.padding(horizontal = 4.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            ) {
                Text("Tag-Filter", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                if (selectedTagId != null) {
                    TextButton(onClick = onClearTagFilter) { Text("Filter zurücksetzen") }
                }
                if (excludedTagIds.isNotEmpty()) {
                    TextButton(onClick = onClearAll) { Text("Alle einblenden") }
                }
            }

            if (allTags.isEmpty()) {
                EmptyTagFilterState()
            } else {
                LazyColumn(contentPadding = PaddingValues(bottom = 8.dp)) {
                    items(allTags, key = { it.id }) { tag ->
                        val isExcluded = tag.id in excludedTagIds
                        val isSelected = tag.id == selectedTagId
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { onSelectTag(tag.id) }
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Tag,
                                    contentDescription = null,
                                    tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.width(24.dp),
                                )
                                Text(
                                    tag.name,
                                    modifier = Modifier.weight(1f).padding(start = 8.dp),
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                )
                            }
                            IconButton(onClick = { onToggle(tag.id) }) {
                                Icon(
                                    imageVector = if (isExcluded) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                    contentDescription = if (isExcluded) "Eingeblendet" else "Ausgeblendet",
                                    tint = if (isExcluded) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
                                )
                            }
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
                            if (count == 1) "1 Tag ausgeblendet" else "$count Tags ausgeblendet",
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
            "Keine Tags vorhanden",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(top = 12.dp),
        )
        Text(
            "Füge Artikel mit Tags hinzu, um sie hier filtern zu können.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}
