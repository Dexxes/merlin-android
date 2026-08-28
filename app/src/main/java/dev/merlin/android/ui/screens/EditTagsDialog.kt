package dev.merlin.android.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.merlin.android.models.Article
import dev.merlin.android.models.Tag
import kotlinx.coroutines.launch

/**
 * Äquivalent zu iOS' `ArticleTagSheet` (`ArticleReaderView.swift`, MARK „Tag editing
 * sheet"): `ModalBottomSheet` (statt iOS' eigenständigem `.sheet`/`NavigationStack`)
 * mit Chip-Auswahl aller bekannten Tags plus inline-Erstellung neuer Tags – 1:1 dasselbe
 * `pendingTags`-Konzept wie [AddArticleSheet] (Chips für ausgewählte/neue Tags, Speichern
 * legt neue Namen erst dann serverseitig an). Ersetzt die v1-Checkbox-`AlertDialog`-Variante.
 *
 * Bewusst ohne direkte ViewModel-Injection (anders als [AddArticleSheet]): wird sowohl aus
 * der Artikelliste ([ArticlesViewModel]) als auch aus dem Reader-Drawer ([ArticleReaderViewModel])
 * heraus geöffnet – beide ViewModels haben eine eigene, leicht unterschiedliche
 * `saveTags(...)`-Implementierung. [onSave] kapselt das.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditTagsDialog(
    article: Article,
    allTags: List<Tag>,
    onDismiss: () -> Unit,
    onSave: suspend (selectedTagIds: Set<Int>, pendingTagNames: List<String>) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()
    val scope = rememberCoroutineScope()

    var selectedTagIds by remember(article.id) { mutableStateOf(article.tags.map { it.id }.toSet()) }
    var pendingTagNames by remember(article.id) { mutableStateOf(emptyList<String>()) }
    var newTagText by remember { mutableStateOf("") }
    var isSaving by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    fun addPendingTag() {
        val name = newTagText.trim()
        if (name.isEmpty()) return
        val alreadyExisting = allTags.firstOrNull { it.name.equals(name, ignoreCase = true) }
        if (alreadyExisting != null) {
            selectedTagIds = selectedTagIds + alreadyExisting.id
        } else if (name !in pendingTagNames) {
            pendingTagNames = pendingTagNames + name
        }
        newTagText = ""
    }

    fun save() {
        if (isSaving) return
        isSaving = true
        errorMessage = null
        scope.launch {
            try {
                onSave(selectedTagIds, pendingTagNames)
                onDismiss()
            } catch (e: Exception) {
                errorMessage = e.message ?: "Unbekannter Fehler"
            } finally {
                isSaving = false
            }
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
            Text("Tags bearbeiten", style = MaterialTheme.typography.titleMedium)

            if (allTags.isNotEmpty()) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(top = 16.dp),
                ) {
                    items(allTags, key = { it.id }) { tag ->
                        FilterChip(
                            selected = tag.id in selectedTagIds,
                            onClick = {
                                selectedTagIds = if (tag.id in selectedTagIds) selectedTagIds - tag.id else selectedTagIds + tag.id
                            },
                            label = { Text(tag.name) },
                        )
                    }
                }
            } else {
                Text(
                    "Noch keine Tags vorhanden.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 16.dp),
                )
            }

            if (pendingTagNames.isNotEmpty()) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(top = 8.dp),
                ) {
                    items(pendingTagNames) { name ->
                        FilterChip(
                            selected = true,
                            onClick = { pendingTagNames = pendingTagNames - name },
                            label = { Text(name) },
                            trailingIcon = {
                                Icon(Icons.Filled.Close, contentDescription = "„$name“ entfernen", modifier = Modifier.size(16.dp))
                            },
                        )
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
            ) {
                OutlinedTextField(
                    value = newTagText,
                    onValueChange = { newTagText = it },
                    placeholder = { Text("Neuer Tag…") },
                    singleLine = true,
                    enabled = !isSaving,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = ::addPendingTag, enabled = newTagText.isNotBlank() && !isSaving) {
                    Icon(Icons.Filled.Add, contentDescription = "Tag hinzufügen")
                }
            }

            errorMessage?.let { message ->
                Text(
                    message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }

            Row(
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 16.dp),
            ) {
                TextButton(onClick = onDismiss, enabled = !isSaving) { Text("Abbrechen") }
                Spacer(Modifier.width(8.dp))
                if (isSaving) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Button(onClick = ::save) { Text("Speichern") }
                }
            }
        }
    }
}
