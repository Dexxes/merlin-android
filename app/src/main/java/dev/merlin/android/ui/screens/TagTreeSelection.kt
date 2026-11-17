package dev.merlin.android.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.merlin.android.R
import dev.merlin.android.models.TagTree

/**
 * Tag-Auswahl als eingerückter Baum (Unter-Tags unter ihrem Eltern-Tag), Äquivalent zu
 * `TagTreeSelectionList.swift`. Antippen folgt [TagTree.toggling]: Ein Unter-Tag wählt seine
 * Eltern-Tags mit aus, Abwählen nimmt die Unter-Tags mit. Genutzt von [EditTagsDialog] und
 * [AddArticleSheet]; scrollt selbst, damit lange Tag-Listen das Sheet nicht sprengen.
 */
@Composable
fun TagTreeSelectionList(
    tree: TagTree,
    selection: Set<Int>,
    onSelectionChange: (Set<Int>) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val rows = tree.rows()
    Column(
        modifier = modifier
            .heightIn(max = 280.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        rows.forEachIndexed { index, row ->
            TagSelectionRow(
                name = row.tag.name,
                color = row.tag.color,
                depth = row.depth,
                selected = row.tag.id in selection,
                enabled = enabled,
                onClick = { onSelectionChange(tree.toggling(row.tag.id, selection)) },
            )
            if (index < rows.lastIndex) {
                HorizontalDivider(modifier = Modifier.padding(start = 12.dp + 22.dp * row.depth))
            }
        }
    }
}

/** Eine Zeile wie in der Share-Extension: Kreis/Häkchen, Name, eingerückt nach Tiefe. */
@Composable
private fun TagSelectionRow(
    name: String,
    color: String?,
    depth: Int,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val tagColor = color?.let(::parseHexColor) ?: MaterialTheme.colorScheme.primary
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(start = 12.dp + 22.dp * depth, end = 12.dp, top = 11.dp, bottom = 11.dp),
    ) {
        Icon(
            if (selected) Icons.Filled.CheckCircle else Icons.Outlined.Circle,
            contentDescription = if (selected) stringResource(R.string.android_common_selected) else null,
            tint = if (selected) tagColor else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(10.dp))
        Text(name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * Auswahl „Unter“ für neu angelegte Tags (verschachtelte Tags), Äquivalent zu
 * `TagParentPicker.swift`: oberste Ebene oder ein bestehender Tag, eingerückt in
 * Baumreihenfolge.
 */
@Composable
fun TagParentPicker(
    tree: TagTree,
    selection: Int?,
    onSelect: (Int?) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    var open by remember { mutableStateOf(false) }
    val selectedName = selection?.let { tree.tag(it) }?.let { tree.path(it) } ?: stringResource(R.string.tagParentPicker_topLevel)
    Box(modifier = modifier) {
        TextButton(onClick = { open = true }, enabled = enabled) {
            Text("${stringResource(R.string.tagParentPicker_label)}: $selectedName", maxLines = 1, overflow = TextOverflow.Ellipsis)
            Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.tagParentPicker_topLevel), fontStyle = FontStyle.Italic) },
                onClick = { open = false; onSelect(null) },
            )
            tree.rows().forEach { row ->
                DropdownMenuItem(
                    text = {
                        Text(
                            row.tag.name,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(start = 16.dp * row.depth),
                        )
                    },
                    onClick = { open = false; onSelect(row.tag.id) },
                )
            }
        }
    }
}

private fun parseHexColor(hex: String): Color? = runCatching {
    val clean = hex.removePrefix("#")
    if (clean.length != 6) return@runCatching null
    Color(0xFF000000 or clean.toLong(16))
}.getOrNull()
