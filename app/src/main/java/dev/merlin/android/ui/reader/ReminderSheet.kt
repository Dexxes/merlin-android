package dev.merlin.android.ui.reader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import dev.merlin.android.viewmodel.ArticleReaderViewModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Äquivalent zu `ReminderSheet.swift`. iOS nutzt einen kombinierten grafischen
 * `DatePicker` (`.graphical`, Datum+Uhrzeit in einer Ansicht) – dafür gibt es
 * in Compose keinen Standardbaustein, daher zwei separate Material3-Dialoge
 * (`DatePickerDialog` + `TimePicker`-`AlertDialog`), die nacheinander
 * geöffnet werden, ähnlich wie native Android-Datum/Uhrzeit-Eingaben sonst
 * üblich sind.
 *
 * `isSaving` wird hier lokal gehalten statt im ViewModel (anders als z. B.
 * `ArticlesViewModel`), weil dieser Zustand rein UI-seitig ist: er existiert
 * nur, um den Speichern-Button zu sperren und das Sheet nach Abschluss zu
 * schließen – das ViewModel selbst bleibt fire-and-forget wie im
 * iOS-Original (`Task { ... }` in `scheduleReminder()`/`cancelReminder()`).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReminderSheet(
    onDismiss: () -> Unit,
    viewModel: ArticleReaderViewModel = hiltViewModel(),
) {
    val currentReminder by viewModel.currentReminder.collectAsState()
    val reminderError by viewModel.reminderError.collectAsState()
    val sheetState = rememberModalBottomSheetState()

    // Pre-Fill mit vorhandenem Reminder-Zeitpunkt (Äquivalent zu `.onAppear` im
    // iOS-Original) – `remember` ohne Key erfasst absichtlich nur den Wert bei
    // erster Komposition des Sheets, nicht spätere Änderungen von `currentReminder`.
    var selectedMillis by remember { mutableStateOf(currentReminder?.triggerAt ?: defaultReminderMillis()) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }

    // Reagiert auf das Ergebnis von scheduleReminder(): Erfolg → Sheet schließen
    // (wie `dismiss()` nach erfolgreichem `try` im Original), Fehler → Anzeige bleibt offen.
    LaunchedEffect(currentReminder, reminderError) {
        if (isSaving) {
            isSaving = false
            if (reminderError == null) onDismiss()
        }
    }

    DisposableEffect(Unit) {
        onDispose { viewModel.clearReminderError() }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
            Text(
                if (currentReminder == null) "Erinnerung setzen" else "Erinnerung ändern",
                style = MaterialTheme.typography.titleMedium,
            )

            Text(
                "Datum & Uhrzeit",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { showDatePicker = true }, enabled = !isSaving) {
                    Text(dateFormatter.format(Date(selectedMillis)))
                }
                OutlinedButton(onClick = { showTimePicker = true }, enabled = !isSaving) {
                    Text(timeFormatter.format(Date(selectedMillis)))
                }
            }

            reminderError?.let { msg ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 16.dp),
                ) {
                    Icon(Icons.Filled.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.width(8.dp))
                    Text(msg, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }

            if (currentReminder != null) {
                TextButton(
                    onClick = {
                        // Fire-and-forget wie im iOS-Original: UI sofort zurücksetzen,
                        // Stornierung läuft async im Hintergrund (ReminderService.cancel).
                        viewModel.cancelReminder()
                        onDismiss()
                    },
                    modifier = Modifier.padding(top = 16.dp),
                ) {
                    Icon(Icons.Filled.NotificationsOff, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.width(8.dp))
                    Text("Erinnerung entfernen", color = MaterialTheme.colorScheme.error)
                }
            }

            Row(
                horizontalArrangement = Arrangement.End,
                modifier = Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 16.dp),
            ) {
                TextButton(onClick = onDismiss, enabled = !isSaving) { Text("Abbrechen") }
                Spacer(Modifier.width(8.dp))
                if (isSaving) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Button(onClick = {
                        isSaving = true
                        viewModel.scheduleReminder(selectedMillis)
                    }) { Text("Speichern") }
                }
            }
        }
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = selectedMillis,
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                    utcTimeMillis >= startOfTodayUtcMillis()
            },
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { selectedMillis = combineDateKeepTime(it, selectedMillis) }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Abbrechen") } },
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showTimePicker) {
        val current = remember(showTimePicker) { Calendar.getInstance().apply { timeInMillis = selectedMillis } }
        val timePickerState = rememberTimePickerState(
            initialHour = current.get(Calendar.HOUR_OF_DAY),
            initialMinute = current.get(Calendar.MINUTE),
            is24Hour = true,
        )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    selectedMillis = combineTimeKeepDate(timePickerState.hour, timePickerState.minute, selectedMillis)
                    showTimePicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showTimePicker = false }) { Text("Abbrechen") } },
            text = { TimePicker(state = timePickerState) },
        )
    }
}

// MARK: – Datum/Zeit-Hilfsfunktionen

private val dateFormatter = SimpleDateFormat("EEE, d. MMM yyyy", Locale.GERMANY)
private val timeFormatter = SimpleDateFormat("HH:mm", Locale.GERMANY)

/** Default-Reminder-Zeitpunkt: nächster Tag, 09:00 (Äquivalent zu `Date.defaultReminderDate`). */
private fun defaultReminderMillis(): Long {
    val cal = Calendar.getInstance()
    cal.add(Calendar.DAY_OF_YEAR, 1)
    cal.set(Calendar.HOUR_OF_DAY, 9)
    cal.set(Calendar.MINUTE, 0)
    cal.set(Calendar.SECOND, 0)
    cal.set(Calendar.MILLISECOND, 0)
    return cal.timeInMillis
}

/** Übernimmt Jahr/Monat/Tag aus dem (UTC-basierten) DatePicker-Ergebnis, behält die bisherige Uhrzeit. */
private fun combineDateKeepTime(utcDateMillis: Long, currentMillis: Long): Long {
    val utcCal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = utcDateMillis }
    val result = Calendar.getInstance().apply { timeInMillis = currentMillis }
    result.set(Calendar.YEAR, utcCal.get(Calendar.YEAR))
    result.set(Calendar.MONTH, utcCal.get(Calendar.MONTH))
    result.set(Calendar.DAY_OF_MONTH, utcCal.get(Calendar.DAY_OF_MONTH))
    return result.timeInMillis
}

/** Übernimmt Stunde/Minute aus dem TimePicker-Ergebnis, behält das bisherige Datum. */
private fun combineTimeKeepDate(hour: Int, minute: Int, currentMillis: Long): Long {
    val result = Calendar.getInstance().apply {
        timeInMillis = currentMillis
        set(Calendar.HOUR_OF_DAY, hour)
        set(Calendar.MINUTE, minute)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    return result.timeInMillis
}

/** UTC-Mitternacht des heutigen (lokalen) Datums – passend zur UTC-Semantik von [SelectableDates]. */
private fun startOfTodayUtcMillis(): Long {
    val localNow = Calendar.getInstance()
    val utcCal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
    utcCal.set(localNow.get(Calendar.YEAR), localNow.get(Calendar.MONTH), localNow.get(Calendar.DAY_OF_MONTH), 0, 0, 0)
    utcCal.set(Calendar.MILLISECOND, 0)
    return utcCal.timeInMillis
}
