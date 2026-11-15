package dev.merlin.android.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Tag
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.merlin.android.models.Tag
import dev.merlin.android.models.TagTree

/**
 * „Verschieben nach…“ für verschachtelte Tags, Äquivalent zu `MoveTagSheet.swift`: wählt den
 * neuen Eltern-Tag. Der Tag selbst und seine Unter-Tags stehen nicht zur Wahl (sonst entstünde
 * ein Kreis, siehe [TagTree.canMove]). `onMove(null)` = oberste Ebene.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoveTagSheet(
    tag: Tag,
    tree: TagTree,
    onMove: (parentId: Int?) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selection by remember(tag.id) { mutableStateOf(tag.parentId?.takeIf { tree.tag(it) != null }) }
    val excluded = remember(tag.id, tree) { tree.scope(tag.id) }
    val targets = remember(tag.id, tree) { tree.rows().filter { it.tag.id !in excluded } }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            ) {
                TextButton(onClick = onDismiss) { Text("Abbrechen") }
                Text(
                    "Tag verschieben",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                )
                Button(
                    onClick = { onMove(selection); onDismiss() },
                    enabled = selection != tag.parentId,
                ) { Text("Verschieben") }
            }
            Text(
                if (tree.hasChildren(tag.id)) "„${tag.name}“ mit seinen Unter-Tags verschieben unter:"
                else "„${tag.name}“ verschieben unter:",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
            LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                item(key = "top") {
                    MoveTargetRow(
                        label = "Oberste Ebene",
                        depthPadding = 0.dp,
                        showIcon = false,
                        italic = true,
                        selected = selection == null,
                        onClick = { selection = null },
                    )
                }
                items(targets, key = { it.tag.id }) { row ->
                    MoveTargetRow(
                        label = row.tag.name,
                        depthPadding = 20.dp * row.depth,
                        showIcon = true,
                        italic = false,
                        selected = selection == row.tag.id,
                        onClick = { selection = row.tag.id },
                    )
                }
            }
        }
    }
}

@Composable
private fun MoveTargetRow(
    label: String,
    depthPadding: Dp,
    showIcon: Boolean,
    italic: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 20.dp + depthPadding, end = 20.dp, top = 14.dp, bottom = 14.dp),
    ) {
        if (showIcon) {
            Icon(Icons.Outlined.Tag, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
        }
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            fontStyle = if (italic) FontStyle.Italic else null,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (selected) {
            Icon(
                Icons.Filled.Check,
                contentDescription = "Ausgewählt",
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
