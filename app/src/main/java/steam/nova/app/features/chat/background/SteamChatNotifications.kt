package steam.nova.app.features.chat.background

import android.annotation.SuppressLint
import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.Person
import androidx.core.content.edit
import steam.nova.app.R
import steam.nova.app.core.ModuleLocalization
import steam.nova.app.features.chat.diagnostics.SteamChatDiagnostics
import org.json.JSONObject

/** Optional notifications owned by Steam; notification failures never block a chat request. */
internal class SteamChatNotifications(context: Context) {
    private val app = context.applicationContext
    private val manager: NotificationManager get() = app.getSystemService(NotificationManager::class.java)
    private val preferences get() = app.getSharedPreferences("millennium-chat-background", Context.MODE_PRIVATE)
    private val notified = linkedSetOf<String>()
    private val noticeIds = hashSetOf<Int>()
    private val noticeTimes = hashMapOf<Int, Long>()

    private fun channel() {
        manager.createNotificationChannel(NotificationChannel(CHANNEL, ModuleLocalization.text(app, R.string.chat_notification_channel), NotificationManager.IMPORTANCE_HIGH))
    }

    fun enabled(): Boolean = runCatching { preferences.getBoolean("enabled", false) }.getOrDefault(false)

    private fun permitted(): Boolean = runCatching {
        channel()
        manager.areNotificationsEnabled() && manager.getNotificationChannel(CHANNEL).importance != NotificationManager.IMPORTANCE_NONE &&
                (Build.VERSION.SDK_INT < 33 || app.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)
    }.getOrDefault(false)

    fun status(): JSONObject = JSONObject().put("enabled", enabled()).put("permitted", permitted())

    fun enable(): JSONObject {
        runCatching { if (permitted()) preferences.edit { putBoolean("enabled", true) } }
            .onFailure { SteamChatDiagnostics.failure("notification.enable", it) }
        return status()
    }

    fun disable(): JSONObject {
        runCatching { preferences.edit { putBoolean("enabled", false) } }
            .onFailure { SteamChatDiagnostics.failure("notification.disable", it) }
        clear()
        return status()
    }

    // Called under the runtime's session lock, so a cleared account cannot post a late notification.
    // Runs in Steam, whose manifest declares POST_NOTIFICATIONS. permitted() checks its grant.
    @SuppressLint("MissingPermission", "NotificationPermission")
    fun post(account: String, key: String, message: JSONObject) {
        runCatching {
            if (!enabled() || !permitted()) return
            val dedup = "$account:$key:${message.getString("id")}"
            if (dedup in notified) return
            val id = noticeId(account, key)
            val timestamp = message.optLong("timestamp")
            // A delayed older message must not replace a newer notification or move its read cutoff back.
            if (timestamp < (noticeTimes[id] ?: 0)) return
            val launch = app.packageManager.getLaunchIntentForPackage(app.packageName) ?: return
            launch.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra("millennium_chat_target", key).putExtra("millennium_chat_account", account)
            val click = PendingIntent.getActivity(app, id, launch, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val title = message.optJSONObject("profile")?.optString("name").orEmpty().ifBlank { message.optString("sender") }
            val style = NotificationCompat.MessagingStyle(Person.Builder().setName(ModuleLocalization.text(app, R.string.chat_notification_self)).build())
                .addMessage(message.optString("text").take(2000), message.optLong("timestamp") * 1000, Person.Builder().setName(title).build())
            val notice = NotificationCompat.Builder(app, CHANNEL).setSmallIcon(android.R.drawable.stat_notify_chat)
                .setContentTitle(title).setContentText(message.optString("text").take(200)).setStyle(style)
                .setAutoCancel(true).setContentIntent(click).setCategory(Notification.CATEGORY_MESSAGE)
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE).build()
            // A private tag avoids cancelling Steam's own notifications with the same numeric ID.
            manager.notify(TAG, id, notice)
            noticeIds.add(id); noticeTimes[id] = timestamp; notified.add(dedup)
            while (notified.size > 500) notified.remove(notified.first())
        }.onFailure { SteamChatDiagnostics.failure("notification.post", it) }
    }

    fun cancel(account: String, key: String) {
        val id = noticeId(account, key)
        runCatching { manager.cancel(TAG, id) }
        noticeIds.remove(id)
        noticeTimes.remove(id)
    }

    fun cancelThrough(account: String, key: String, timestamp: Long) {
        val time = noticeTimes[noticeId(account, key)] ?: return
        if (time <= timestamp) cancel(account, key)
    }

    fun clear() {
        noticeIds.forEach { id -> runCatching { manager.cancel(TAG, id) } }
        noticeIds.clear(); noticeTimes.clear(); notified.clear()
    }

    private fun noticeId(account: String, key: String) = "$account:$key".hashCode() and 0x3fffffff

    companion object {
        private const val CHANNEL = "millennium_chat_messages"
        private const val TAG = "millennium.chat"
    }
}
