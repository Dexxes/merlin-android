package dev.merlin.android.ui.share

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import dev.merlin.android.models.Tag
import dev.merlin.android.viewmodel.ShareViewModel
import dev.merlin.android.ui.screens.OnboardingScreen
import kotlinx.coroutines.delay

/**
 * Äquivalent zur Card-UI in `ShareViewController.swift` (Settings/Staging/
 * Saving/Erfolg/Fehler/Rate-Limit), hier als simple Compose-`Card` statt
 * manuellem Auto-Layout. Läuft in [dev.merlin.android.ui.share.ShareActivity]
 * mit transparentem/abgedunkeltem Hintergrund, damit der darunterliegende
 * Bildschirm (die teilende App) durchscheint – Äquivalent zum
 * `UIColor.black.withAlphaComponent(0.4)`-Hintergrund im iOS-Original.
 */
@Composable
fun ShareScreen(
    sharedText: String?,
    onClose: () -> Unit,
    viewModel: ShareViewModel = hiltViewModel(),
) {
    val mode by viewModel.mode.collectAsState()
    val pendingUrl by viewModel.pendingUrl.collectAsState()
    val availableTags by viewModel.availableTags.collectAsState()
    val selectedTagIds by viewModel.selectedTagIds.collectAsState()
    val statusMessage by viewModel.statusMessage.collectAsState()
    val finished by viewModel.finished.collectAsState()

    LaunchedEffect(finished) { if (finished) onClose() }
    // Sobald wir im Extracting-Modus sind (konfiguriert oder gerade frisch eingeloggt),
    // einmalig den geteilten Text auswerten.
    LaunchedEffect(mode) {
        if (mode == ShareViewModel.Mode.EXTRACTING) viewModel.handleSharedText(sharedText)
    }

    Box(modifier = Modifier.fillMaxWidth()) {
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
        ) {
            when (mode) {
                ShareViewModel.Mode.ONBOARDING -> OnboardingScreen(onLoginSuccess = { viewModel.onLoginSuccess() })
                ShareViewModel.Mode.EXTRACTING -> LoadingCard("Lese geteilten Inhalt …")
                ShareViewModel.Mode.STAGING -> StagingCard(
                    url = pendingUrl,
                    availableTags = availableTags,
                    selectedTagIds = selectedTagIds,
                    onToggleTag = viewModel::toggleTag,
                    onConfirm = viewModel::confirmSave,
                )
                ShareViewModel.Mode.SAVING -> LoadingCard("Wird zu Merlin gespeichert …")
                ShareViewModel.Mode.SUCCESS -> LoadingCard("Gespeichert!")
                ShareViewModel.Mode.ERROR -> MessageCard(
                    title = "Konnte nicht speichern",
                    message = statusMessage ?: "Unbekannter Fehler",
                )
                ShareViewModel.Mode.RATE_LIMITED -> MessageCard(
                    title = "Rate-Limit",
                    message = statusMessage ?: "Bitte kurz warten.",
                )
            }
        }
    }

    // Fehler-/Rate-Limit-Anzeigen schließen sich von selbst, wie im iOS-Original
    // (3s bzw. 2.5s), danach zurück zur Staging-Ansicht bzw. ganz schließen.
    LaunchedEffect(mode) {
        when (mode) {
            ShareViewModel.Mode.ERROR -> {
                delay(3000)
                viewModel.finish()
            }
            ShareViewModel.Mode.RATE_LIMITED -> {
                delay(2500)
                viewModel.backToStaging()
            }
            ShareViewModel.Mode.SUCCESS -> {
                delay(600)
                viewModel.finish()
            }
            else -> {}
        }
    }
}

@Composable
private fun LoadingCard(message: String) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator()
        Text(text = message, modifier = Modifier.padding(top = 16.dp), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun MessageCard(title: String, message: String) {
    Column(modifier = Modifier.fillMaxWidth().padding(24.dp)) {
        Text(text = title, style = MaterialTheme.typography.titleMedium)
        Text(text = message, modifier = Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun StagingCard(
    url: String,
    availableTags: List<Tag>,
    selectedTagIds: Set<Int>,
    onToggleTag: (Int) -> Unit,
    onConfirm: (newTagNames: String) -> Unit,
) {
    var newTagText by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
        Text(text = "Zur Leseliste hinzufügen", style = MaterialTheme.typography.titleMedium)
        Text(
            text = url,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
        )

        if (availableTags.isEmpty()) {
            Text(
                text = "Noch keine Tags vorhanden",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(availableTags) { tag ->
                    FilterChip(
                        selected = selectedTagIds.contains(tag.id),
                        onClick = { onToggleTag(tag.id) },
                        label = { Text(tag.name) },
                    )
                }
            }
        }

        OutlinedTextField(
            value = newTagText,
            onValueChange = { newTagText = it },
            label = { Text("Neuer Tag (optional)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        )

        Button(
            onClick = { onConfirm(newTagText) },
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
        ) {
            Text("In Merlin speichern")
        }
    }
}
