package dev.merlin.android.data

/** Äquivalent zu `ReminderError` in `ReminderService.swift`. */
sealed class ReminderError(message: String) : Exception(message) {
    /** Notification-Berechtigung fehlt oder wurde abgelehnt (Android 13+, `POST_NOTIFICATIONS`). */
    object PermissionDenied : ReminderError("Bitte erlaube Benachrichtigungen in den Einstellungen, um Erinnerungen zu setzen.")

    /** Exakte Alarme sind deaktiviert (Android 12+, `SCHEDULE_EXACT_ALARM`/Nutzer-Opt-out). */
    object ExactAlarmNotPermitted : ReminderError("Bitte erlaube exakte Alarme in den Einstellungen, um Erinnerungen zu setzen.")
}
