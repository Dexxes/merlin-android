package dev.merlin.android.ui.reader

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.merlin.android.R
import dev.merlin.android.models.FileMetadata

/**
 * Abschnitt „Metadaten“ eines Datei-Eintrags, den die Reader-WebView nicht selbst zeigt
 * (PDF-Einträge rendern nativ, siehe [PdfReaderView]). Gleicher Inhalt und gleiche Gruppierung
 * wie der HTML-Abschnitt vom Server; Äquivalent zu `FileMetadataSection` (merlin-ios), dort
 * ebenfalls als Sheet aus dem PDF-Reader geöffnet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileMetadataSheet(metadata: FileMetadata, onDismiss: () -> Unit) {
    val collapsed = remember(metadata) { mutableStateListOf<Int>() }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState()) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(metadata.title, style = MaterialTheme.typography.titleMedium)
            metadata.groups.forEachIndexed { index, group ->
                val expanded = index !in collapsed
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { if (expanded) collapsed.add(index) else collapsed.remove(index) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            group.title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f),
                        )
                        Icon(
                            if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                            contentDescription = stringResource(if (expanded) R.string.android_common_collapse else R.string.android_common_expand),
                        )
                    }
                    if (expanded) {
                        SelectionContainer {
                            Column(
                                modifier = Modifier.padding(top = 6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                group.entries.forEach { entry ->
                                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                        Text(
                                            entry.label,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.weight(0.38f),
                                        )
                                        Text(
                                            entry.value,
                                            style = MaterialTheme.typography.bodySmall,
                                            modifier = Modifier.weight(0.62f),
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
}
