package dev.merlin.android.models

import java.util.UUID

/**
 * Äquivalent zu `Reminder.swift`. [triggerAt]/[createdAt] als Epoch-Millis
 * statt `Date`, da das direkt mit `AlarmManager`/`WorkManager` weiterverwendet
 * werden kann (siehe ReminderService-Portierung).
 */
data class Reminder(
    val id: String = UUID.randomUUID().toString(),
    val articleId: Int,
    val articleTitle: String,
    var triggerAt: Long,
    var status: Status = Status.PENDING,
    val createdAt: Long = System.currentTimeMillis(),
) {
    enum class Status {
        PENDING,
        FIRED,
        CANCELLED,
    }
}
