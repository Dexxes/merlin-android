package dev.merlin.android.ui.reader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import dev.merlin.android.viewmodel.ArticleReaderViewModel
import dev.merlin.android.viewmodel.ReportFeedback

/**
 * Äquivalent zu `ReportArticleSheet.swift`. Wie `ReminderSheet.kt` ein
 * Material3-`ModalBottomSheet` statt des iOS-`.sheet`/`NavigationStack`-Aufbaus
 * (Toolbar-Buttons dort entsprechen hier den Buttons in der letzten Zeile).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportArticleSheet(
    articleUrl: String,
    onDismiss: () -> Unit,
    viewModel: ArticleReaderViewModel = hiltViewModel(),
) {
    val isSending by viewModel.reportSending.collectAsState()
    val feedback by viewModel.reportFeedback.collectAsState()
    val sheetState = rememberModalBottomSheetState()

    var comment by remember { mutableStateOf("") }

    // Äquivalent zu `onDismiss: { reportComment = "" }` im iOS-`.sheet`-Modifier.
    DisposableEffect(Unit) {
        onDispose { viewModel.clearReportFeedback() }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
            Text("Artikel melden", style = MaterialTheme.typography.titleMedium)

            Text(
                "Artikel",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 16.dp, bottom = 2.dp),
            )
            Text(
                articleUrl,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            Text(
                "Kommentar (optional)",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 16.dp, bottom = 6.dp),
            )
            OutlinedTextField(
                value = comment,
                onValueChange = { comment = it },
                placeholder = { Text("Was stimmt nicht?") },
                enabled = !isSending && feedback == null,
                minLines = 3,
                maxLines = 5,
                modifier = Modifier.fillMaxWidth(),
            )

            feedback?.let { fb ->
                val (msg, color) = when (fb) {
                    is ReportFeedback.Success -> "Danke – Artikel wurde gemeldet." to MaterialTheme.colorScheme.primary
                    is ReportFeedback.Failure -> fb.message to MaterialTheme.colorScheme.error
                }
                Text(
                    msg,
                    style = MaterialTheme.typography.bodySmall,
                    color = color,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }

            Row(
                horizontalArrangement = Arrangement.End,
                modifier = Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 16.dp),
            ) {
                TextButton(onClick = onDismiss, enabled = !isSending) { Text("Abbrechen") }
                Spacer(Modifier.width(8.dp))
                when {
                    isSending -> CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    feedback != null -> Button(onClick = onDismiss) { Text("Schließen") }
                    else -> Button(onClick = { viewModel.sendReport(comment) }) { Text("Melden") }
                }
            }
        }
    }
}
