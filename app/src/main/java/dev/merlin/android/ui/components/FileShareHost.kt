package dev.merlin.android.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.merlin.android.share.FileShareService

/**
 * Oberfläche zum Teilen einer Datei ([FileShareService]): während des Ladens ein kleiner Dialog
 * mit „Abbrechen“, danach der System-Teilen-Dialog oder eine Fehlermeldung – wie
 * `FileSharePresenter` in merlin-ios. Einmal in der `MainActivity` eingebunden, damit sie auch aus
 * Kontextmenüs und Swipe-Aktionen funktioniert, die beim Ende des Downloads längst zu sind.
 */
@Composable
fun FileShareHost(service: FileShareService) {
    val state by service.state.collectAsState()
    val context = LocalContext.current

    when (val current = state) {
        FileShareService.State.Idle -> Unit
        FileShareService.State.Loading -> AlertDialog(
            onDismissRequest = {},
            text = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 3.dp)
                    Text("Datei wird geladen …")
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { service.reset() }) { Text("Abbrechen") } },
        )
        FileShareService.State.Failed -> AlertDialog(
            onDismissRequest = { service.reset() },
            title = { Text("Fehler") },
            text = { Text("Die Datei konnte nicht geladen werden.") },
            confirmButton = { TextButton(onClick = { service.reset() }) { Text("OK") } },
        )
        is FileShareService.State.Ready -> LaunchedEffect(current) {
            runCatching { FileShareService.shareFile(current, context) }
            service.reset()
        }
    }
}
