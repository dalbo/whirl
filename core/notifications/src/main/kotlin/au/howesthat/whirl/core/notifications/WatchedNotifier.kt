package au.howesthat.whirl.core.notifications

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import au.howesthat.whirl.core.data.SettingsRepository
import au.howesthat.whirl.core.data.WatchAction
import au.howesthat.whirl.core.data.WatchedRepository
import au.howesthat.whirl.core.database.NotifiedDao
import au.howesthat.whirl.core.database.NotifiedEntity
import au.howesthat.whirl.core.model.ThreadId
import au.howesthat.whirl.core.model.WatchedThread
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The watched-thread notification (PLAN.md §1.3): one `get=watched&watchedmode=0` call per check, an inbox-style
 * notification listing the unread threads, alerting only when some thread has a reply newer than the one last
 * notified for it, with Mark read and Unwatch actions for all listed threads. Tapping opens the Watched screen.
 */
@Singleton
class WatchedNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: SettingsRepository,
    private val watched: WatchedRepository,
    private val notified: NotifiedDao,
) {
    private val manager = NotificationManagerCompat.from(context)

    /** One check. Network and API failures propagate (the worker decides whether to retry). */
    suspend fun check() {
        val s = settings.settings.first()
        if (!s.watchedNotify || settings.apiKey() == null) {
            cancel()
            return
        }
        // Blocked: skip the call and record nothing, so the threads still alert once notifications are allowed.
        if (!canNotify()) return
        val me = settings.account.first()?.userId
        val before = notified.all().associate { it.threadId to it.lastDate }
        val plan = planAlert(watched.fetchUnread(), before, me, s.ignoreOwnRepliesWatched)
        when {
            plan.threads.isEmpty() -> cancel()
            plan.hasNew -> show(plan.threads, alert = true)
            // Keep a notification the user has not dismissed in step with the counts, without alerting again.
            isShowing() -> show(plan.threads, alert = false)
        }
        notified.put(plan.threads.map { NotifiedEntity(it.id.value, it.lastDate.toInstant().toEpochMilli()) })
        notified.keepOnly(plan.threads.map { it.id.value })
    }

    fun cancel() = manager.cancel(NOTIFICATION_ID)

    /** A notification button: sends [action] for [ids] at once and removes the notification. False if it failed (rolled back). */
    suspend fun act(action: WatchAction, ids: List<ThreadId>): Boolean {
        val sent = watched.perform(action, ids, undoable = false).error == null
        if (sent) cancel()
        return sent
    }

    /** Creates the channel; safe to call repeatedly. Called at startup so its settings exist before the first alert. */
    fun createChannel() {
        manager.createNotificationChannel(
            NotificationChannelCompat.Builder(CHANNEL_WATCHED, NotificationManagerCompat.IMPORTANCE_HIGH)
                .setName(context.getString(R.string.channel_watched))
                .setDescription(context.getString(R.string.channel_watched_description))
                .build(),
        )
    }

    private fun isShowing(): Boolean =
        context.getSystemService(NotificationManager::class.java).activeNotifications.any { it.id == NOTIFICATION_ID }

    private fun canNotify(): Boolean =
        (Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) &&
            manager.areNotificationsEnabled()

    // canNotify() checks POST_NOTIFICATIONS; lint does not follow the helper.
    @SuppressLint("MissingPermission")
    private fun show(threads: List<WatchedThread>, alert: Boolean) {
        createChannel()
        val res = context.resources
        val posts = threads.sumOf { it.unreadCount }
        val single = threads.singleOrNull()
        val title = single?.title ?: res.getQuantityString(R.plurals.notify_title, posts)
        val text = if (single != null) {
            res.getQuantityString(R.plurals.notify_unread_posts, posts, posts)
        } else {
            context.getString(
                R.string.notify_replies_in_threads,
                res.getQuantityString(R.plurals.notify_replies, posts, posts),
                res.getQuantityString(R.plurals.notify_threads, threads.size, threads.size),
            )
        }
        val style = NotificationCompat.InboxStyle().setBigContentTitle(title)
        threads.take(MAX_LINES).forEach { style.addLine(context.getString(R.string.notify_line, it.title, it.unreadCount)) }
        if (threads.size > MAX_LINES) style.setSummaryText(context.getString(R.string.notify_more, threads.size - MAX_LINES))
        val ids = threads.map { it.id.value }.toTypedArray()
        val notification = NotificationCompat.Builder(context, CHANNEL_WATCHED)
            .setSmallIcon(R.drawable.ic_stat_whirl)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(style)
            .setNumber(posts)
            .setCategory(NotificationCompat.CATEGORY_SOCIAL)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setOnlyAlertOnce(!alert)
            .setSilent(!alert)
            .setContentIntent(openWatched())
            .addAction(0, context.getString(R.string.notify_mark_read), action(NotificationActionReceiver.ACTION_MARK_READ, ids))
            .addAction(0, context.getString(R.string.notify_unwatch), action(NotificationActionReceiver.ACTION_UNWATCH, ids))
            .build()
        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun openWatched(): PendingIntent {
        val intent = (context.packageManager.getLaunchIntentForPackage(context.packageName) ?: Intent())
            .setPackage(context.packageName)
            .putExtra(EXTRA_OPEN_WATCHED, true)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private fun action(name: String, ids: Array<String>): PendingIntent {
        val intent = Intent(context, NotificationActionReceiver::class.java).setAction(name).putExtra(NotificationActionReceiver.EXTRA_IDS, ids)
        return PendingIntent.getBroadcast(context, name.hashCode(), intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    companion object {
        const val CHANNEL_WATCHED = "watched_threads"
        const val NOTIFICATION_ID = 1

        /** Boolean extra on the launch intent: open the Watched screen. */
        const val EXTRA_OPEN_WATCHED = "au.howesthat.whirl.OPEN_WATCHED"

        private const val MAX_LINES = 6
    }
}
