package dev.merlin.android.data

import android.Manifest
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.merlin.android.models.Article
import dev.merlin.android.models.Reminder
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Äquivalent zu `ReminderService.swift`. Persistiert Reminder in Room (statt
 * JSON-Datei) und plant exakte Alarme über [AlarmManager] statt
 * `UNCalendarNotificationTrigger` – Android kennt keinen "Termin-Notification"-
 * Mechanismus, daher feuert der Alarm [ReminderBroadcastReceiver], der die
 * eigentliche Notification baut/postet und den Reminder als `FIRED` markiert.
 * Mutex-isoliert als Kotlin-Äquivalent zu Swifts `actor`.
 */
@Singleton
class ReminderService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val reminderDao: ReminderDao,
) {
    private val mutex = Mutex()
    private val alarmManager get() = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    /** Alle wartenden Reminder, sortiert nach Auslösezeitpunkt (am nächsten zuerst). */
    suspend fun all(): List<Reminder> = mutex.withLock {
        reminderDao.getAll()
            .map { it.toModel() }
            .filter { it.status == Reminder.Status.PENDING }
            .sortedBy { it.triggerAt }
    }

    /** Der wartende Reminder für einen bestimmten Artikel, oder null. */
    suspend fun reminder(articleId: Int): Reminder? = mutex.withLock {
        reminderDao.getAll()
            .map { it.toModel() }
            .firstOrNull { it.articleId == articleId && it.status == Reminder.Status.PENDING }
    }

    /**
     * Prüft Berechtigungen, ersetzt einen ggf. bestehenden Reminder für den
     * Artikel und plant einen exakten Alarm für [triggerAtMillis].
     */
    suspend fun schedule(article: Article, triggerAtMillis: Long) {
        if (!hasNotificationPermission()) throw ReminderError.PermissionDenied
        if (!canScheduleExactAlarms()) throw ReminderError.ExactAlarmNotPermitted

        mutex.withLock {
            val existing = reminderDao.getAll()
                .map { it.toModel() }
                .firstOrNull { it.articleId == article.id && it.status == Reminder.Status.PENDING }
            if (existing != null) {
                cancelAlarm(existing)
                reminderDao.upsert(existing.copy(status = Reminder.Status.CANCELLED).toEntity())
            }

            val reminder = Reminder(
                articleId = article.id,
                articleTitle = article.displayTitle,
                triggerAt = triggerAtMillis,
            )
            scheduleAlarm(reminder)
            reminderDao.upsert(reminder.toEntity())
        }
    }

    /** Storniert den wartenden Reminder für einen Artikel (No-op, falls keiner existiert). */
    suspend fun cancel(articleId: Int) = mutex.withLock {
        val existing = reminderDao.getAll()
            .map { it.toModel() }
            .firstOrNull { it.articleId == articleId && it.status == Reminder.Status.PENDING }
            ?: return@withLock
        cancelAlarm(existing)
        reminderDao.upsert(existing.copy(status = Reminder.Status.CANCELLED).toEntity())
    }

    /** Wird vom [ReminderBroadcastReceiver] aufgerufen, sobald die Notification gepostet wurde. */
    suspend fun markFired(reminderId: String) = mutex.withLock {
        val entity = reminderDao.getById(reminderId) ?: return@withLock
        reminderDao.upsert(entity.copy(status = Reminder.Status.FIRED))
    }

    fun hasNotificationPermission(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        }
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    fun canScheduleExactAlarms(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return alarmManager.canScheduleExactAlarms()
        }
        return true
    }

    private fun scheduleAlarm(reminder: Reminder) {
        val pendingIntent = pendingIntentFor(reminder)
        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, reminder.triggerAt, pendingIntent)
    }

    private fun cancelAlarm(reminder: Reminder) {
        alarmManager.cancel(pendingIntentFor(reminder))
    }

    private fun pendingIntentFor(reminder: Reminder): PendingIntent {
        val intent = Intent(context, ReminderBroadcastReceiver::class.java).apply {
            putExtra(ReminderBroadcastReceiver.EXTRA_REMINDER_ID, reminder.id)
            putExtra(ReminderBroadcastReceiver.EXTRA_ARTICLE_ID, reminder.articleId)
            putExtra(ReminderBroadcastReceiver.EXTRA_ARTICLE_TITLE, reminder.articleTitle)
        }
        return PendingIntent.getBroadcast(
            context,
            reminder.id.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
