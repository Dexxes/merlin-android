package dev.merlin.android.ui.reader

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import dev.merlin.android.viewmodel.ArticleReaderViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Verwaltung des öffentlichen Share-Links: anlegen, Passwort/Ablaufdatum
 * setzen, Link kopieren/teilen, regenerieren, widerrufen. Äquivalent zu
 * ShareLinkSheet.swift (iOS/iPad) und ShareLinkDialog.vue (Web-UI). Ein
 * Artikel hat höchstens einen Link (siehe ArticleShareResponse/ShareController
 * im Backend).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareLinkSheet(
    onDismiss: () -> Unit,
    viewModel: ArticleReaderViewModel,
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val sheetState = rememberModalBottomSheetState()

    val share by viewModel.share.collectAsState()
    val loading by viewModel.shareLoading.collectAsState()
    val busy by viewModel.shareBusy.collectAsState()
    val error by viewModel.shareError.collectAsState()

    var createPasswordEnabled by remember { mutableStateOf(false) }
    var createPassword by remember { mutableStateOf("") }
    var createExpiryEnabled by remember { mutableStateOf(false) }
    var createExpiryMillis by remember { mutableStateOf<Long?>(null) }
    var showCreateExpiryPicker by remember { mutableStateOf(false) }

    var editingPassword by remember { mutableStateOf(false) }
    var newPassword by remember { mutableStateOf("") }
    var showExpiryPicker by remember { mutableStateOf(false) }

    // Beim ersten Öffnen des Sheets den aktuellen Share-Status laden
    // (Äquivalent zu `.task { await load() }` im iOS-Original).
    LaunchedEffect(Unit) {
        viewModel.loadShare()
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
            Text("Öffentlicher Link", style = MaterialTheme.typography.titleMedium)

            error?.let { msg ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 12.dp)) {
                    Icon(Icons.Filled.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.width(8.dp))
                    Text(msg, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }

            if (loading) {
                Row(
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                ) {
                    CircularProgressIndicator()
                }
            } else if (!share.enabled) {
                // ── Anlegen ────────────────────────────────────────────────
                Text(
                    "Jeder mit diesem Link kann den Artikel lesen – inklusive deiner Markierungen.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 12.dp, bottom = 12.dp),
                )

                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text("Mit Passwort schützen", modifier = Modifier.weight(1f))
                    Switch(checked = createPasswordEnabled, onCheckedChange = { createPasswordEnabled = it })
                }
                if (createPasswordEnabled) {
                    OutlinedTextField(
                        value = createPassword,
                        onValueChange = { createPassword = it },
                        label = { Text("Passwort") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 8.dp),
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text("Link läuft an einem bestimmten Datum ab", modifier = Modifier.weight(1f))
                    Switch(
                        checked = createExpiryEnabled,
                        onCheckedChange = {
                            createExpiryEnabled = it
                            if (it && createExpiryMillis == null) showCreateExpiryPicker = true
                        },
                    )
                }
                if (createExpiryEnabled) {
                    OutlinedButton(onClick = { showCreateExpiryPicker = true }, modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)) {
                        Text(createExpiryMillis?.let { isoDateFormatter.format(Date(it)) } ?: "Datum wählen")
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.End,
                    modifier = Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 16.dp),
                ) {
                    if (busy) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Button(onClick = {
                            viewModel.createShare(
                                password = if (createPasswordEnabled) createPassword else null,
                                expiresAt = if (createExpiryEnabled) createExpiryMillis?.let { isoDateFormatter.format(Date(it)) } else null,
                            )
                        }) { Text("Öffentlichen Link erstellen") }
                    }
                }
            } else {
                // ── Verwalten ──────────────────────────────────────────────
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                ) {
                    Text(
                        share.url.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { share.url?.let { clipboard.setText(AnnotatedString(it)) } }) {
                        Icon(Icons.Filled.ContentCopy, contentDescription = "Link kopieren")
                    }
                    IconButton(onClick = {
                        share.url?.let { url ->
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, url)
                            }
                            context.startActivity(Intent.createChooser(intent, "Link teilen"))
                        }
                    }) {
                        Icon(Icons.Filled.Share, contentDescription = "Link teilen")
                    }
                }

                Divider(modifier = Modifier.padding(vertical = 12.dp))

                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text("Mit Passwort schützen", modifier = Modifier.weight(1f))
                    Switch(
                        checked = share.hasPassword == true,
                        onCheckedChange = { isOn ->
                            if (isOn) {
                                editingPassword = true
                                newPassword = ""
                            } else {
                                editingPassword = false
                                viewModel.removeSharePassword()
                            }
                        },
                    )
                }
                if (editingPassword) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                        OutlinedTextField(
                            value = newPassword,
                            onValueChange = { newPassword = it },
                            label = { Text("Neues Passwort") },
                            visualTransformation = PasswordVisualTransformation(),
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(8.dp))
                        TextButton(
                            enabled = newPassword.isNotEmpty() && !busy,
                            onClick = {
                                viewModel.setSharePassword(newPassword)
                                editingPassword = false
                                newPassword = ""
                            },
                        ) { Text("Speichern") }
                    }
                }

                Divider(modifier = Modifier.padding(vertical = 12.dp))

                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text("Link läuft an einem bestimmten Datum ab", modifier = Modifier.weight(1f))
                    Switch(
                        checked = share.expiresAt != null,
                        onCheckedChange = { isOn ->
                            if (isOn) {
                                showExpiryPicker = true
                            } else {
                                viewModel.removeShareExpiry()
                            }
                        },
                    )
                }
                share.expiresAt?.let { expiresAt ->
                    parseIsoDate(expiresAt)?.let { date ->
                        Text(
                            "Läuft ab am ${isoDateFormatter.format(date)}",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }

                Divider(modifier = Modifier.padding(vertical = 12.dp))

                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                ) {
                    OutlinedButton(onClick = { viewModel.regenerateShare() }, enabled = !busy) {
                        Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Erneuern")
                    }
                    OutlinedButton(
                        onClick = { viewModel.revokeShare() },
                        enabled = !busy,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    ) {
                        Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Widerrufen")
                    }
                }
            }
        }
    }

    if (showCreateExpiryPicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = createExpiryMillis,
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean = utcTimeMillis >= startOfTodayUtcMillis()
            },
        )
        DatePickerDialog(
            onDismissRequest = { showCreateExpiryPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { createExpiryMillis = it }
                    showCreateExpiryPicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showCreateExpiryPicker = false
                    if (createExpiryMillis == null) createExpiryEnabled = false
                }) { Text("Abbrechen") }
            },
        ) { DatePicker(state = datePickerState) }
    }

    if (showExpiryPicker) {
        val datePickerState = rememberDatePickerState(
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean = utcTimeMillis >= startOfTodayUtcMillis()
            },
        )
        DatePickerDialog(
            onDismissRequest = { showExpiryPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        viewModel.setShareExpiry(isoDateFormatter.format(Date(millis)))
                    }
                    showExpiryPicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showExpiryPicker = false }) { Text("Abbrechen") } },
        ) { DatePicker(state = datePickerState) }
    }
}

// MARK: – Datum-Hilfsfunktionen
//
// Datumsformat bewusst nur "yyyy-MM-dd" (kein Uhrzeit-/Zeitzonenanteil) –
// PHPs `new \DateTime($expiresAt)` parst das problemlos als Mitternacht des
// Tages, und es ist dasselbe Format, das die Web-UI über `<input type="date">`
// sendet.

private val isoDateFormatter = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

private fun parseIsoDate(value: String): Date? =
    runCatching {
        // Server liefert ISO-8601 mit Zeit/Zone (`DateTime::format('c')`) –
        // hier reicht uns nur das Datum für die Anzeige.
        val fmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US)
        fmt.parse(value)
    }.getOrNull() ?: runCatching { isoDateFormatter.parse(value) }.getOrNull()

private fun startOfTodayUtcMillis(): Long {
    val cal = java.util.Calendar.getInstance(TimeZone.getTimeZone("UTC"))
    val local = java.util.Calendar.getInstance()
    cal.set(local.get(java.util.Calendar.YEAR), local.get(java.util.Calendar.MONTH), local.get(java.util.Calendar.DAY_OF_MONTH), 0, 0, 0)
    cal.set(java.util.Calendar.MILLISECOND, 0)
    return cal.timeInMillis
}
