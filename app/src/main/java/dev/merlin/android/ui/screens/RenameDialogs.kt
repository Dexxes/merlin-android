package dev.merlin.android.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import dev.merlin.android.R
import dev.merlin.android.models.Article
import kotlinx.coroutines.launch

/**
 * „Datei umbenennen“ für Einträge aus „Merlin Dateien“, Äquivalent zu `RenameFileAlert.swift`:
 * fragt den neuen Namen ohne Endung ab (die behält der Server) und benennt Datei in Nextcloud
 * und Eintrag um. Geteilt von Liste, Karte und Reader. [onRename] liefert eine Fehlermeldung
 * (z. B. Name schon vergeben) oder `null` bei Erfolg; bei einem Fehler bleibt der Dialog offen.
 */
@Composable
fun RenameFileDialog(
    article: Article,
    onRename: suspend (name: String) -> String?,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val baseName = remember(article.id, article.title) { fileBaseName(article.title) }
    val extension = remember(article.id, article.title) { fileExtension(article.title) }
    var field by remember(article.id) { mutableStateOf(TextFieldValue(baseName, TextRange(0, baseName.length))) }
    var errorMessage by remember(article.id) { mutableStateOf<String?>(null) }
    var isSaving by remember { mutableStateOf(false) }

    val newName = field.text.trim()
    val canSave = newName.isNotEmpty() && newName != baseName && !isSaving

    fun save() {
        if (!canSave) return
        isSaving = true
        errorMessage = null
        scope.launch {
            val error = onRename(newName)
            isSaving = false
            if (error == null) onDismiss() else errorMessage = error
        }
    }

    AlertDialog(
        onDismissRequest = { if (!isSaving) onDismiss() },
        title = { Text(stringResource(R.string.fileRename_title)) },
        text = {
            Column {
                OutlinedTextField(
                    value = field,
                    onValueChange = { field = it; errorMessage = null },
                    placeholder = { Text(stringResource(R.string.fileRename_placeholder)) },
                    singleLine = true,
                    enabled = !isSaving,
                    isError = errorMessage != null,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.None,
                        autoCorrectEnabled = false,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(onDone = { save() }),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (extension.isNotEmpty()) {
                    Text(
                        stringResource(R.string.fileRename_message, extension),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                errorMessage?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = ::save, enabled = canSave) { Text(stringResource(R.string.fileRename_confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSaving) { Text(stringResource(R.string.common_cancel)) }
        },
    )
}

/** Endung eines Dateinamens ohne Punkt („Quittung.pdf“ → „pdf“), leer ohne Endung. */
private fun fileExtension(fileName: String): String {
    val dot = fileName.lastIndexOf('.')
    return if (dot > 0 && dot < fileName.lastIndex) fileName.substring(dot + 1) else ""
}

/** Dateiname ohne Endung (was das Textfeld bearbeitet). */
private fun fileBaseName(fileName: String): String {
    val ext = fileExtension(fileName)
    return if (ext.isEmpty()) fileName else fileName.dropLast(ext.length + 1)
}

/**
 * „Tag umbenennen“ (langes Drücken auf einen Tag im Seitenmenü). [onRename] liefert eine
 * Fehlermeldung (z. B. Name schon vergeben) oder `null` bei Erfolg; bei einem Fehler bleibt
 * der Dialog offen.
 */
@Composable
fun RenameTagDialog(
    tag: dev.merlin.android.models.Tag,
    onRename: suspend (name: String) -> String?,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var field by remember(tag.id) { mutableStateOf(TextFieldValue(tag.name, TextRange(0, tag.name.length))) }
    var errorMessage by remember(tag.id) { mutableStateOf<String?>(null) }
    var isSaving by remember { mutableStateOf(false) }

    val newName = field.text.trim()
    val canSave = newName.isNotEmpty() && newName != tag.name && !isSaving

    fun save() {
        if (!canSave) return
        isSaving = true
        errorMessage = null
        scope.launch {
            val error = onRename(newName)
            isSaving = false
            if (error == null) onDismiss() else errorMessage = error
        }
    }

    AlertDialog(
        onDismissRequest = { if (!isSaving) onDismiss() },
        title = { Text(stringResource(R.string.android_tags_renameTitle)) },
        text = {
            Column {
                OutlinedTextField(
                    value = field,
                    onValueChange = { field = it; errorMessage = null },
                    placeholder = { Text(stringResource(R.string.fileRename_placeholder)) },
                    singleLine = true,
                    enabled = !isSaving,
                    isError = errorMessage != null,
                    keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { save() }),
                    modifier = Modifier.fillMaxWidth(),
                )
                errorMessage?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = ::save, enabled = canSave) { Text(stringResource(R.string.fileRename_confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSaving) { Text(stringResource(R.string.common_cancel)) }
        },
    )
}
