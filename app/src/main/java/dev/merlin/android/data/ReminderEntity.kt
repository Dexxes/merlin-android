package dev.merlin.android.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import dev.merlin.android.models.Reminder

/** Room-Äquivalent zur JSON-Datei `reminders.json` aus `ReminderService.swift`. */
@Entity(tableName = "reminders")
data class ReminderEntity(
    @PrimaryKey val id: String,
    val articleId: Int,
    val articleTitle: String,
    val triggerAt: Long,
    val status: Reminder.Status,
    val createdAt: Long,
)

fun ReminderEntity.toModel() = Reminder(
    id = id,
    articleId = articleId,
    articleTitle = articleTitle,
    triggerAt = triggerAt,
    status = status,
    createdAt = createdAt,
)

fun Reminder.toEntity() = ReminderEntity(
    id = id,
    articleId = articleId,
    articleTitle = articleTitle,
    triggerAt = triggerAt,
    status = status,
    createdAt = createdAt,
)
