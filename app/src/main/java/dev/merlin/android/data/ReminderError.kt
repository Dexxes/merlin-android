package dev.merlin.android.data

import dev.merlin.android.R

/** Äquivalent zu `ReminderError` in `ReminderService.swift`. Meldung für Nutzer siehe [userMessage]. */
sealed class ReminderError(message: String, override val messageRes: Int) : Exception(message), LocalizedError {
    /** Notification-Berechtigung fehlt oder wurde abgelehnt (Android 13+, `POST_NOTIFICATIONS`). */
    object PermissionDenied : ReminderError(
        "Notification permission denied",
        R.string.reminders_error_permissionDenied,
    )

    /** Exakte Alarme sind deaktiviert (Android 12+, `SCHEDULE_EXACT_ALARM`/Nutzer-Opt-out). */
    object ExactAlarmNotPermitted : ReminderError(
        "Exact alarms not permitted",
        R.string.android_reminders_error_exactAlarmNotPermitted,
    )
}
