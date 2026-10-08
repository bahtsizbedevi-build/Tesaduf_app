package com.tesaduf.app.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.android.gms.tasks.Task
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.tesaduf.app.MainActivity
import com.tesaduf.app.R
import com.tesaduf.app.TesadufApplication
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * FCM: message pushes for chats while the app is closed or the chat is not on screen.
 * The server sends data-only messages; we build the notification here so we control
 * the icon and can skip it when that very chat is open.
 */
object Push {
    const val CHANNEL_ID = "messages"
    const val EXTRA_MATCH_ID = "open_match_id"

    fun createChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notif_channel_messages),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply { description = context.getString(R.string.notif_channel_messages_desc) }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    /** Current FCM token, or null if Firebase/Play services are unavailable. */
    suspend fun currentToken(): String? = runCatching { FirebaseMessaging.getInstance().token.awaitOrNull() }.getOrNull()

    fun showMessage(context: Context, matchId: String, sender: String, body: String) {
        if (!Reminders.canPost(context)) return
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(EXTRA_MATCH_ID, matchId)
        val pending = PendingIntent.getActivity(
            context, matchId.hashCode(), intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_tesaduf)
            .setColor(0xFF00E5FF.toInt())
            .setContentTitle(context.getString(R.string.notif_message_title, sender))
            .setContentText(body.ifBlank { context.getString(R.string.notif_message_hidden) })
            .setStyle(NotificationCompat.BigTextStyle().bigText(body.ifBlank { context.getString(R.string.notif_message_hidden) }))
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        @Suppress("MissingPermission") // checked by canPost()
        NotificationManagerCompat.from(context).notify(matchId.hashCode(), notification)
    }

    fun cancelFor(context: Context, matchId: String) {
        NotificationManagerCompat.from(context).cancel(matchId.hashCode())
    }
}

private suspend fun <T> Task<T>.awaitOrNull(): T? = suspendCancellableCoroutine { cont ->
    addOnCompleteListener { task -> cont.resume(if (task.isSuccessful) task.result else null) }
}

class TesadufMessagingService : FirebaseMessagingService() {
    private val container get() = (application as TesadufApplication).container

    override fun onNewToken(token: String) {
        container.repository.registerPushToken(token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data
        if (data["type"] != "message") return
        val matchId = data["match_id"] ?: return
        // Already looking at this chat: the message appears live, no notification.
        if (container.activeChatId == matchId) return
        Push.showMessage(this, matchId, "#${data["sender"].orEmpty()}", data["body"].orEmpty())
    }
}
