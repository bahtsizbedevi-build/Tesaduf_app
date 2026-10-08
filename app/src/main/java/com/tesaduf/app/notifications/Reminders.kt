package com.tesaduf.app.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.tesaduf.app.MainActivity
import com.tesaduf.app.R
import com.tesaduf.app.data.AppPreferences
import java.time.LocalTime
import java.util.concurrent.TimeUnit
import android.graphics.Bitmap
import android.graphics.BitmapFactory

/**
 * Re-engagement reminders, fully on-device (no server, no push token):
 * a periodic worker posts one friendly nudge when the user hasn't opened TESADÜF for
 * a while — never at night and at most once per [MIN_GAP_MS].
 */
object Reminders {
    const val CHANNEL_ID = "reminders"
    private const val WORK_NAME = "tesaduf-reminder"
    private const val NOTIFICATION_ID = 1001
    const val INACTIVE_MS = 20L * 60 * 60 * 1000
    const val MIN_GAP_MS = 22L * 60 * 60 * 1000
    private val QuietStart: LocalTime = LocalTime.of(22, 30)
    private val QuietEnd: LocalTime = LocalTime.of(10, 0)

    fun createChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notif_channel_reminders),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply { description = context.getString(R.string.notif_channel_reminders_desc) }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    /** Idempotent: keeps the existing schedule if already enqueued. */
    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<ReminderWorker>(6, TimeUnit.HOURS, 1, TimeUnit.HOURS).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    /** Debug builds only: fire one reminder now, ignoring time rules (for manual testing). */
    fun fireNowForDebug(context: Context) {
        val request = OneTimeWorkRequestBuilder<ReminderWorker>().setInputData(workDataOf(KEY_FORCE to true)).build()
        WorkManager.getInstance(context).enqueue(request)
    }

    fun canPost(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun shouldRemind(prefs: AppPreferences, now: Long, time: LocalTime): Boolean {
        if (!prefs.reminders.value || !prefs.onboardingDone) return false
        if (time.isAfter(QuietStart) || time.isBefore(QuietEnd)) return false
        if (now - prefs.lastOpenedAt < INACTIVE_MS) return false
        return now - prefs.lastReminderAt >= MIN_GAP_MS
    }

    internal fun post(context: Context) {
        if (!canPost(context)) return
        val messages = context.resources.getStringArray(R.array.reminder_messages)
        val intent = Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pending = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_tesaduf)
            .setLargeIcon(logoBitmap(context))
            .setColor(0xFF7C3AED.toInt())
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText(messages.random())
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        @Suppress("MissingPermission") // checked by canPost()
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }

    internal const val KEY_FORCE = "force"

    /** The real TESADÜF logo, decoded small (no crop, no recolour) for the notification. */
    private fun logoBitmap(context: Context): Bitmap? = runCatching {
        BitmapFactory.decodeResource(context.resources, R.drawable.tesaduf_logo, BitmapFactory.Options().apply { inSampleSize = 8 })
    }.getOrNull()
}

class ReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val prefs = AppPreferences(applicationContext)
        val force = inputData.getBoolean(Reminders.KEY_FORCE, false)
        if (force || Reminders.shouldRemind(prefs, System.currentTimeMillis(), LocalTime.now())) {
            Reminders.post(applicationContext)
            prefs.markReminded()
        }
        return Result.success()
    }
}
