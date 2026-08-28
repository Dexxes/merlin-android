package dev.merlin.android.ui.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import dev.merlin.android.models.ProgressEdge
import dev.merlin.android.models.ReaderFont
import dev.merlin.android.models.ReaderTheme
import dev.merlin.android.viewmodel.ArticleReaderViewModel
import kotlinx.coroutines.launch

/**
 * Äquivalent zum Erscheinungsbild-Sheet aus `ArticleReaderView.swift`
 * (Theme/Schriftart/-größe/Zeilenhöhe/Akzentfarbe/Fortschrittsbalken-Kante).
 * Schreibt direkt über [ArticleReaderViewModel.preferencesStore] – die
 * reaktiven `Flow`s dort lösen im Reader automatisch einen vollen
 * HTML-Rebuild aus (siehe `ReaderHtmlBuilder`-Kommentar).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppearanceSheet(
    onDismiss: () -> Unit,
    viewModel: ArticleReaderViewModel = hiltViewModel(),
) {
    val theme by viewModel.readerTheme.collectAsState()
    val font by viewModel.readerFont.collectAsState()
    val fontSize by viewModel.readerFontSize.collectAsState()
    val lineHeight by viewModel.lineHeight.collectAsState()
    val accentColorHex by viewModel.accentColorHex.collectAsState()
    val progressEdge by viewModel.progressEdge.collectAsState()
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState()

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
            Text("Erscheinungsbild", style = MaterialTheme.typography.titleMedium)
            Text(
                "Theme",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ReaderTheme.entries.forEach { entry ->
                    FilterChip(
                        selected = entry == theme,
                        onClick = { scope.launch { viewModel.preferencesStore.setReaderTheme(entry); viewModel.syncAppearanceToServer() } },
                        label = { Text(entry.themeLabel()) },
                    )
                }
            }
            // Seit der Verdrahtung in MainActivity.kt steuert diese Auswahl nicht mehr nur die
            // Lese-Ansicht, sondern das gesamte App-Theme (Menüs, Artikelübersicht, Einstellungen).
            Text(
                "Gilt auch für Menüs und die Artikelübersicht außerhalb der Lese-Ansicht.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )

            Text(
                "Schriftart",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ReaderFont.entries.forEach { entry ->
                    FilterChip(
                        selected = entry == font,
                        onClick = { scope.launch { viewModel.preferencesStore.setReaderFont(entry); viewModel.syncAppearanceToServer() } },
                        label = { Text(entry.fontLabel()) },
                    )
                }
            }

            Text(
                "Schriftgröße",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                IconButton(onClick = { scope.launch { viewModel.preferencesStore.setReaderFontSize((fontSize - 1).coerceIn(12, 28)); viewModel.syncAppearanceToServer() } }) {
                    Icon(Icons.Filled.Remove, contentDescription = "Kleiner")
                }
                Text("${fontSize}px", style = MaterialTheme.typography.bodyLarge)
                IconButton(onClick = { scope.launch { viewModel.preferencesStore.setReaderFontSize((fontSize + 1).coerceIn(12, 28)); viewModel.syncAppearanceToServer() } }) {
                    Icon(Icons.Filled.Add, contentDescription = "Größer")
                }
            }

            Text(
                "Zeilenhöhe",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                IconButton(onClick = { scope.launch { viewModel.preferencesStore.setLineHeight((lineHeight - 0.1).coerceIn(1.2, 2.2)); viewModel.syncAppearanceToServer() } }) {
                    Icon(Icons.Filled.Remove, contentDescription = "Kompakter")
                }
                Text(String.format("%.1f", lineHeight), style = MaterialTheme.typography.bodyLarge)
                IconButton(onClick = { scope.launch { viewModel.preferencesStore.setLineHeight((lineHeight + 0.1).coerceIn(1.2, 2.2)); viewModel.syncAppearanceToServer() } }) {
                    Icon(Icons.Filled.Add, contentDescription = "Luftiger")
                }
            }

            Text(
                "Akzentfarbe",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ACCENT_COLORS.forEach { hex ->
                    val color = Color(android.graphics.Color.parseColor(hex))
                    val selected = hex.equals(accentColorHex, ignoreCase = true)
                    Card(
                        shape = CircleShape,
                        modifier = Modifier
                            .size(32.dp)
                            .clickable { scope.launch { viewModel.preferencesStore.setAccentProgressColorHex(hex); viewModel.syncAppearanceToServer() } }
                            .border(
                                width = if (selected) 2.dp else 0.dp,
                                color = MaterialTheme.colorScheme.onSurface,
                                shape = CircleShape,
                            ),
                    ) {
                        Column(modifier = Modifier.fillMaxWidth().background(color)) {
                            if (selected) {
                                Icon(
                                    Icons.Filled.Check,
                                    contentDescription = "Ausgewählt",
                                    tint = Color.White,
                                    modifier = Modifier.padding(6.dp),
                                )
                            }
                        }
                    }
                }
            }

            Text(
                "Fortschrittsbalken",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(top = 16.dp, bottom = 16.dp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ProgressEdge.entries.forEach { entry ->
                    FilterChip(
                        selected = entry == progressEdge,
                        onClick = { scope.launch { viewModel.preferencesStore.setProgressEdge(entry); viewModel.syncAppearanceToServer() } },
                        label = { Text(entry.edgeLabel()) },
                    )
                }
            }
        }
    }
}

private fun ReaderTheme.themeLabel(): String = when (this) {
    ReaderTheme.AUTO -> "Automatisch"
    ReaderTheme.LIGHT -> "Hell"
    ReaderTheme.DARK -> "Dunkel"
    ReaderTheme.SEPIA -> "Sepia"
}

private fun ReaderFont.fontLabel(): String = when (this) {
    ReaderFont.SYSTEM -> "Standard"
    ReaderFont.SERIF -> "Serif"
    ReaderFont.SANS_SERIF -> "Sans-Serif"
    ReaderFont.MONO -> "Mono"
}

private fun ProgressEdge.edgeLabel(): String = when (this) {
    ProgressEdge.LEFT -> "Links"
    ProgressEdge.RIGHT -> "Rechts"
    ProgressEdge.TOP -> "Oben"
    ProgressEdge.BOTTOM -> "Unten"
    ProgressEdge.OFF -> "Aus"
}

/** Feste Akzentfarb-Auswahl, identisch zur iOS-Palette im Erscheinungsbild-Sheet. */
private val ACCENT_COLORS = listOf(
    "#FF3B30", "#FF9500", "#FFCC00", "#34C759", "#007AFF", "#5856D6", "#AF52DE",
)
