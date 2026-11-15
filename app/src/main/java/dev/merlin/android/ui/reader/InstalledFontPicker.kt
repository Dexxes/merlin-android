package dev.merlin.android.ui.reader

import android.graphics.Typeface
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.merlin.android.models.InstalledFontFamily

/**
 * Auswahl aus allen installierten Schriftfamilien (siehe `InstalledFonts`), jede in ihrer eigenen
 * Schrift dargestellt und durchsuchbar. `onSelect(null)` = zurück zur Voreinstellung.
 */
@Composable
fun InstalledFontPickerDialog(
    selected: String?,
    loadFamilies: suspend () -> List<InstalledFontFamily>,
    onSelect: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    var families by remember { mutableStateOf<List<InstalledFontFamily>?>(null) }
    var query by remember { mutableStateOf("") }
    LaunchedEffect(Unit) { families = loadFamilies() }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            modifier = Modifier.fillMaxWidth(0.92f).fillMaxHeight(0.85f),
        ) {
            Column(modifier = Modifier.padding(top = 20.dp)) {
                Text(
                    "Installierte Schrift",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    placeholder = { Text("Suchen") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                )
                val list = families
                // Volle Breite, sonst ist die Box nur so breit wie ihr Inhalt und der Ladeindikator klebt links.
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    if (list == null) {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    } else {
                        val filtered = list.filter { query.isBlank() || it.name.contains(query.trim(), ignoreCase = true) }
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            if (query.isBlank()) {
                                item {
                                    FontRow(
                                        label = "Keine – Voreinstellung nutzen",
                                        fontFamily = null,
                                        selected = selected == null,
                                        onClick = { onSelect(null) },
                                    )
                                    HorizontalDivider()
                                }
                            }
                            items(filtered, key = { it.name }) { family ->
                                val fontFamily = remember(family.name) {
                                    runCatching { FontFamily(Typeface.createFromFile(family.previewFace.path)) }.getOrNull()
                                }
                                FontRow(
                                    label = family.name,
                                    fontFamily = fontFamily,
                                    selected = family.name.equals(selected, ignoreCase = true),
                                    onClick = { onSelect(family.name) },
                                )
                            }
                            if (filtered.isEmpty()) {
                                item {
                                    Text(
                                        "Keine passende Schrift gefunden.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(20.dp),
                                    )
                                }
                            }
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDismiss) { Text("Schließen") }
                }
            }
        }
    }
}

@Composable
private fun FontRow(label: String, fontFamily: FontFamily?, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            fontFamily = fontFamily,
            fontSize = 17.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        if (selected) {
            Icon(
                Icons.Filled.Check,
                contentDescription = "Ausgewählt",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}
