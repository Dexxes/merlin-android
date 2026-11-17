package dev.merlin.android.ui.reader

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import dev.merlin.android.R
import dev.merlin.android.models.ProgressEdge
import dev.merlin.android.models.ReaderFont
import dev.merlin.android.models.ReaderTheme
import dev.merlin.android.ui.appLocale
import dev.merlin.android.ui.screens.labelRes
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
    val customFontFamily by viewModel.readerCustomFontFamily.collectAsState()
    var showFontPicker by remember { mutableStateOf(false) }
    val fontSize by viewModel.readerFontSize.collectAsState()
    val lineHeight by viewModel.lineHeight.collectAsState()
    val accentColorHex by viewModel.accentColorHex.collectAsState()
    val progressEdge by viewModel.progressEdge.collectAsState()
    val handsFreeButton by viewModel.handsFreeButton.collectAsState()
    val quoteMode by viewModel.quoteMode.collectAsState()
    val paperTexture by viewModel.paperTexture.collectAsState()
    val isSystemDark = isSystemInDarkTheme()
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState()

    if (showFontPicker) {
        InstalledFontPickerDialog(
            selected = customFontFamily,
            loadFamilies = { viewModel.installedFontFamilies() },
            onSelect = { family ->
                viewModel.selectInstalledFont(family)
                showFontPicker = false
            },
            onDismiss = { showFontPicker = false },
        )
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        // Scrollbar, damit auf kleinen Displays nichts abgeschnitten wird (wie unter iOS).
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
        ) {
            Text(stringResource(R.string.articleReader_sideMenu_appearance), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.articleReader_appearance_theme),
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ReaderTheme.entries.forEach { entry ->
                    FilterChip(
                        selected = entry == theme,
                        onClick = { scope.launch { viewModel.preferencesStore.setReaderTheme(entry); viewModel.syncAppearanceToServer() } },
                        label = { Text(stringResource(entry.themeLabel())) },
                    )
                }
            }
            // Seit der Verdrahtung in MainActivity.kt steuert diese Auswahl nicht mehr nur die
            // Lese-Ansicht, sondern das gesamte App-Theme (Menüs, Artikelübersicht, Einstellungen).
            Text(
                stringResource(R.string.android_appearance_themeFooter),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )

            Text(
                stringResource(R.string.articleReader_appearance_font),
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ReaderFont.entries.forEach { entry ->
                    FilterChip(
                        // Ist eine installierte Schrift gewählt, ist keine Voreinstellung aktiv markiert.
                        selected = entry == font && customFontFamily == null,
                        onClick = { viewModel.selectPresetFont(entry) },
                        label = { Text(stringResource(entry.fontLabel())) },
                    )
                }
            }
            // Freie Wahl aus allen installierten Schriften (Gegenstück zur iOS-Schriftliste).
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .clip(MaterialTheme.shapes.small)
                    .clickable { showFontPicker = true }
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.articleReader_appearance_installedFont), style = MaterialTheme.typography.bodyMedium)
                    Text(
                        customFontFamily ?: stringResource(R.string.android_appearance_installedFontNoneAbove),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (customFontFamily != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = stringResource(R.string.android_appearance_chooseFont))
            }

            Text(
                stringResource(R.string.articleReader_appearance_fontSize),
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                IconButton(onClick = { scope.launch { viewModel.preferencesStore.setReaderFontSize((fontSize - 1).coerceIn(12, 28)); viewModel.syncAppearanceToServer() } }) {
                    Icon(Icons.Filled.Remove, contentDescription = stringResource(R.string.android_appearance_smaller))
                }
                Text("${fontSize}px", style = MaterialTheme.typography.bodyLarge)
                IconButton(onClick = { scope.launch { viewModel.preferencesStore.setReaderFontSize((fontSize + 1).coerceIn(12, 28)); viewModel.syncAppearanceToServer() } }) {
                    Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.android_appearance_larger))
                }
            }

            Text(
                stringResource(R.string.articleReader_appearance_lineSpacing),
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                IconButton(onClick = { scope.launch { viewModel.preferencesStore.setLineHeight((lineHeight - 0.1).coerceIn(1.2, 2.2)); viewModel.syncAppearanceToServer() } }) {
                    Icon(Icons.Filled.Remove, contentDescription = stringResource(R.string.android_appearance_tighter))
                }
                Text(String.format(appLocale(), "%.1f", lineHeight), style = MaterialTheme.typography.bodyLarge)
                IconButton(onClick = { scope.launch { viewModel.preferencesStore.setLineHeight((lineHeight + 0.1).coerceIn(1.2, 2.2)); viewModel.syncAppearanceToServer() } }) {
                    Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.android_appearance_looser))
                }
            }

            Text(
                stringResource(R.string.articleReader_appearance_accentColor),
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ACCENT_COLORS.forEach { hex ->
                    val color = Color(android.graphics.Color.parseColor(hex))
                    val selected = hex.equals(accentColorHex, ignoreCase = true)
                    // Die Farbe füllt den ganzen Kreis (nicht nur ein Kind-Element, das ohne
                    // Häkchen keine Höhe hätte).
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(color)
                            .border(
                                width = if (selected) 2.dp else 0.dp,
                                color = MaterialTheme.colorScheme.onSurface,
                                shape = CircleShape,
                            )
                            .clickable { scope.launch { viewModel.preferencesStore.setAccentProgressColorHex(hex); viewModel.syncAppearanceToServer() } },
                    ) {
                        if (selected) {
                            Icon(
                                Icons.Filled.Check,
                                contentDescription = stringResource(R.string.android_common_selected),
                                tint = Color.White,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
            }

            Text(
                stringResource(R.string.settings_preferences_progressBarLabel),
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(top = 16.dp, bottom = 16.dp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ProgressEdge.entries.forEach { entry ->
                    FilterChip(
                        selected = entry == progressEdge,
                        onClick = { scope.launch { viewModel.preferencesStore.setProgressEdge(entry); viewModel.syncAppearanceToServer() } },
                        label = { Text(stringResource(entry.labelRes())) },
                    )
                }
            }

            // Papierstruktur als Reader-Hintergrund (Standard: an), wie `paperTextureRow` im
            // iOS-Erscheinungsbild-Sheet. Wirkt nur in Hell/Sepia, im Dunkel-Theme daher ausgegraut.
            // Nur lokal, nicht mit dem Server gesynct.
            val paperApplies = theme != ReaderTheme.DARK && !(theme == ReaderTheme.AUTO && isSystemDark)
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.articleReader_appearance_paperTexture),
                        style = MaterialTheme.typography.labelLarge,
                        color = if (paperApplies) Color.Unspecified else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        stringResource(R.string.android_appearance_paperTextureFooter),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                Switch(
                    checked = paperTexture,
                    onCheckedChange = { viewModel.setPaperTexture(it) },
                    enabled = paperApplies,
                )
            }

            // Freihand-Lesemodus: blendet den Button in der Bottom-Bar ein (Standard: an), wie
            // `handsFreeRow` im iOS-Erscheinungsbild-Sheet. Nur lokal, nicht mit dem Server gesynct.
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.articleReader_appearance_handsFree), style = MaterialTheme.typography.labelLarge)
                    Text(
                        stringResource(R.string.articleReader_appearance_handsFreeFooter),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                Switch(
                    checked = handsFreeButton,
                    onCheckedChange = { viewModel.setHandsFreeButton(it) },
                )
            }

            // Zitat-Tafel: Knopf an der Markierungsleiste (Standard: an), wie `quoteModeRow` im
            // iOS-Erscheinungsbild-Sheet. Nur lokal, nicht mit dem Server gesynct.
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.articleReader_appearance_quoteMode), style = MaterialTheme.typography.labelLarge)
                    Text(
                        stringResource(R.string.articleReader_appearance_quoteModeFooter),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                Switch(
                    checked = quoteMode,
                    onCheckedChange = { viewModel.setQuoteMode(it) },
                )
            }
        }
    }
}

@StringRes
private fun ReaderTheme.themeLabel(): Int = when (this) {
    ReaderTheme.AUTO -> R.string.android_appearance_theme_auto
    ReaderTheme.LIGHT -> R.string.android_appearance_theme_light
    ReaderTheme.DARK -> R.string.android_appearance_theme_dark
    ReaderTheme.SEPIA -> R.string.android_appearance_theme_sepia
}

@StringRes
private fun ReaderFont.fontLabel(): Int = when (this) {
    ReaderFont.SYSTEM -> R.string.android_appearance_font_system
    ReaderFont.SERIF -> R.string.android_appearance_font_serif
    ReaderFont.SANS_SERIF -> R.string.android_appearance_font_sansSerif
    ReaderFont.MONO -> R.string.android_appearance_font_mono
}

/** Feste Akzentfarb-Auswahl, identisch zur iOS-Palette im Erscheinungsbild-Sheet. */
private val ACCENT_COLORS = listOf(
    "#FF3B30", "#FF9500", "#FFCC00", "#34C759", "#007AFF", "#5856D6", "#AF52DE",
)
