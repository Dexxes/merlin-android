package dev.merlin.android.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import dagger.hilt.android.AndroidEntryPoint
import dev.merlin.android.MainActivity
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Vom [AlarmManager]-Alarm ausgelöster Empfänger – Android kennt anders als
 * `UNCalendarNotificationTrigger` (iOS) keine vom System selbst gepostete
 * Termin-Notification, daher baut/postet dieser Receiver sie manuell und
 * markiert den Reminder anschließend als `FIRED` (Äquivalent zu
 * `AppDelegate.userNotificationCenter(didReceive:)` + `ReminderService.markFired`).
 */
@AndroidEntryPoint
class ReminderBroadcastReceiver : BroadcastReceiver() {

    @Inject
    lateinit var reminderService: ReminderService

    override fun onReceive(context: Context, intent: Intent) {
        val reminderId = intent.getStringExtra(EXTRA_REMINDER_ID) ?: return
        val articleId = intent.getIntExtra(EXTRA_ARTICLE_ID, -1)
        val articleTitle = intent.getStringExtra(EXTRA_ARTICLE_TITLE) ?: return
        if (articleId == -1) return

        ensureChannel(context)
        postNotification(context, reminderId, articleId, articleTitle)

        // onReceive läuft synchron auf dem Main-Thread – goAsync() hält den
        // Receiver-Prozess am Leben, bis die suspend-Persistenz fertig ist.
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                reminderService.markFired(reminderId)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun postNotification(context: Context, reminderId: String, articleId: Int, articleTitle: String) {
        // Deep-Link: MainActivity öffnet direkt den Artikel (Äquivalent zu
        // AppNavigator.articleIdToOpen, gesetzt über den Intent-Extra).
        val contentIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_OPEN_ARTICLE_ID, articleId)
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            articleId,
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            // Platzhalter-Icon, bis ein eigenes App-/Statusbar-Icon hinterlegt ist
            // (siehe Structure.md "Bekannte offene Punkte").
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(articleTitle)
            .setContentText("Dein gespeicherter Artikel wartet auf dich.")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(contentPendingIntent)
            .build()

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(reminderId.hashCode(), notification)
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Erinnerungen", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Erinnerungen an gespeicherte Artikel"
            },
        )
    }

    companion object {
        const val EXTRA_REMINDER_ID = "reminder_id"
        const val EXTRA_ARTICLE_ID = "article_id"
        const val EXTRA_ARTICLE_TITLE = "article_title"
        private const val CHANNEL_ID = "merlin_reminders"
    }
}
