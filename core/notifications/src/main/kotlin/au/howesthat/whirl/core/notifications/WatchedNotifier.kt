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
import au.howesthat.whirl.core.data.NotifyStyle
import au.howesthat.whirl.core.data.Settings
import au.howesthat.whirl.core.data.SettingsRepository
import au.howesthat.whirl.core.data.WatchAction
import au.howesthat.whirl.core.data.WatchedRepository
import au.howesthat.whirl.core.database.NotifiedDao
import au.howesthat.whirl.core.database.NotifiedEntity
import au.howesthat.whirl.core.model.ThreadId
import au.howesthat.whirl.core.model.WatchedThread
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The watched-thread notifications (PLAN.md §1.3): one `get=watched&watchedmode=0` call per check, alerting only when
 * some thread has a reply newer than the one last notified for it. Settings › Notification style picks
 * - [NotifyStyle.SUMMARY]: one inbox-style notification listing the unread threads, with Mark read and Unwatch for
 *   all of them; tapping opens Watched and removes it, or with "keep until read" leaves it up until they are read;
 * - [NotifyStyle.PER_THREAD]: one notification per thread (tagged with its id, grouped under a summary), each with its
 *   own Mark read and Unwatch; tapping opens that thread and removes it. Only threads with a new reply alert.
 *
 * Between checks, [sync] keeps whatever is showing in step with the watched list in the app (read, marked read or
 * unwatched there), without alerting and without bringing back a dismissed notification.
 */
@Singleton
class WatchedNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: SettingsRepository,
    private val watched: WatchedRepository,
    private val notified: NotifiedDao,
) {
    private val manager = NotificationManagerCompat.from(context)

    /** A check and a sync can overlap; each posts from what it read, so they take turns. */
    private val posting = Mutex()

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
        posting.withLock { render(plan.threads, plan.newIds, s) }
        notified.put(plan.threads.map { NotifiedEntity(it.id.value, it.lastDate.toInstant().toEpochMilli()) })
        notified.keepOnly(plan.threads.map { it.id.value })
    }

    /** Brings what is showing in line with [unread] (the app's Unread list) and the style setting, silently. */
    suspend fun sync(unread: List<WatchedThread>) {
        val s = settings.settings.first()
        if (!s.watchedNotify || !canNotify()) return
        val threads = notifiable(unread, settings.account.first()?.userId, s.ignoreOwnRepliesWatched)
        posting.withLock { render(threads, newIds = emptySet(), s) }
    }

    /** Runs [sync] whenever the app's Unread list or the notification settings change. Called once at startup. */
    fun keepInSync(scope: CoroutineScope) {
        scope.launch {
            val shape = settings.settings.map { listOf(it.notifyStyle, it.notifyKeepUntilRead, it.ignoreOwnRepliesWatched, it.watchedNotify) }
                .distinctUntilChanged()
            combine(watched.watched(unreadOnly = true).map { it.value }.distinctUntilChanged(), shape) { unread, _ -> unread }
                .collect { sync(it) }
        }
    }

    fun cancel() = manager.cancelAll()

    /** A notification button: sends [action] for [ids] at once and removes them from the notifications. False if it failed (rolled back). */
    suspend fun act(action: WatchAction, ids: List<ThreadId>): Boolean {
        val sent = watched.perform(action, ids, undoable = false).error == null
        if (sent) {
            // The summary is gone along with its threads; a thread's own notification takes the rest with it.
            manager.cancel(NOTIFICATION_ID)
            ids.forEach { manager.cancel(it.value, THREAD_NOTIFICATION_ID) }
            sync(watched.watched(unreadOnly = true).first().value)
        }
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

    /**
     * Shows [threads]: the ones in [newIds] with an alert, the rest only where something is already showing for them.
     * Switching style carries what was showing over to the new style.
     */
    private fun render(threads: List<WatchedThread>, newIds: Set<ThreadId>, s: Settings) {
        if (threads.isEmpty()) return cancel()
        val active = context.getSystemService(NotificationManager::class.java).activeNotifications
        val summaryShowing = active.any { it.id == NOTIFICATION_ID && it.tag == null }
        val threadsShowing = active.filter { it.id == THREAD_NOTIFICATION_ID }.mapNotNull { it.tag }.toSet()
        createChannel()
        when (s.notifyStyle) {
            NotifyStyle.SUMMARY -> {
                val showing = summaryShowing || threadsShowing.isNotEmpty()
                manager.cancel(GROUP_SUMMARY_ID)
                threadsShowing.forEach { manager.cancel(it, THREAD_NOTIFICATION_ID) }
                when {
                    newIds.isNotEmpty() -> showSummary(threads, alert = true, s.notifyKeepUntilRead)
                    showing -> showSummary(threads, alert = false, s.notifyKeepUntilRead)
                }
            }
            NotifyStyle.PER_THREAD -> {
                // Coming from the summary style: everything it listed was showing.
                if (summaryShowing) manager.cancel(NOTIFICATION_ID)
                val ids = threads.map { it.id.value }.toSet()
                (threadsShowing - ids).forEach { manager.cancel(it, THREAD_NOTIFICATION_ID) }
                val shown = threads.filter { it.id in newIds || summaryShowing || it.id.value in threadsShowing }
                shown.forEach { showThread(it, alert = it.id in newIds) }
                if (shown.isEmpty()) manager.cancel(GROUP_SUMMARY_ID) else showGroupSummary(shown)
            }
        }
    }

    private fun canNotify(): Boolean =
        (Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) &&
            manager.areNotificationsEnabled()

    /** Title, text and inbox lines for a list of threads. */
    private fun NotificationCompat.Builder.listing(threads: List<WatchedThread>): NotificationCompat.Builder {
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
        return setContentTitle(title).setContentText(text).setStyle(style).setNumber(posts)
    }

    private fun builder(alert: Boolean) = NotificationCompat.Builder(context, CHANNEL_WATCHED)
        .setSmallIcon(R.drawable.ic_stat_whirl)
        .setCategory(NotificationCompat.CATEGORY_SOCIAL)
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setOnlyAlertOnce(!alert)
        .setSilent(!alert)

    private fun NotificationCompat.Builder.actions(ids: List<ThreadId>, key: String): NotificationCompat.Builder {
        val raw = ids.map { it.value }.toTypedArray()
        return addAction(0, context.getString(R.string.notify_mark_read), action(NotificationActionReceiver.ACTION_MARK_READ, raw, key))
            .addAction(0, context.getString(R.string.notify_unwatch), action(NotificationActionReceiver.ACTION_UNWATCH, raw, key))
    }

    // canNotify() checks POST_NOTIFICATIONS; lint does not follow the helper.
    @SuppressLint("MissingPermission")
    private fun showSummary(threads: List<WatchedThread>, alert: Boolean, keepUntilRead: Boolean) {
        val notification = builder(alert)
            .listing(threads)
            .setAutoCancel(!keepUntilRead)
            .setContentIntent(openWatched())
            .actions(threads.map { it.id }, key = "")
            .build()
        manager.notify(NOTIFICATION_ID, notification)
    }

    @SuppressLint("MissingPermission")
    private fun showGroupSummary(threads: List<WatchedThread>) {
        val notification = builder(alert = false)
            .listing(threads)
            .setGroup(GROUP_WATCHED)
            .setGroupSummary(true)
            .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_CHILDREN)
            .setContentIntent(openWatched())
            .build()
        manager.notify(GROUP_SUMMARY_ID, notification)
    }

    @SuppressLint("MissingPermission")
    private fun showThread(thread: WatchedThread, alert: Boolean) {
        val posts = thread.unreadCount
        val notification = builder(alert)
            .setContentTitle(thread.title)
            .setContentText(context.resources.getQuantityString(R.plurals.notify_unread_posts, posts, posts))
            .setSubText(thread.forumName)
            .setWhen(thread.lastDate.toInstant().toEpochMilli())
            .setShowWhen(true)
            .setNumber(posts)
            .setGroup(GROUP_WATCHED)
            .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_CHILDREN)
            .setAutoCancel(true)
            .setContentIntent(openThread(thread))
            .actions(listOf(thread.id), key = thread.id.value)
            .build()
        manager.notify(thread.id.value, THREAD_NOTIFICATION_ID, notification)
    }

    private fun launchIntent() = (context.packageManager.getLaunchIntentForPackage(context.packageName) ?: Intent())
        .setPackage(context.packageName)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    private fun openWatched(): PendingIntent =
        PendingIntent.getActivity(
            context, 0, launchIntent().putExtra(EXTRA_OPEN_WATCHED, true),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    /** Opens [thread] as a tap in Watched would (MainActivity reads the extras). */
    private fun openThread(thread: WatchedThread): PendingIntent {
        val intent = launchIntent()
            .putExtra(EXTRA_THREAD_ID, thread.id.value)
            .putExtra(EXTRA_FORUM_ID, thread.forumId)
            .putExtra(EXTRA_LAST_PAGE, thread.lastPage)
            .putExtra(EXTRA_LAST_READ_POST, thread.lastReadPost)
        return PendingIntent.getActivity(
            context, thread.id.value.hashCode(), intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    /** [key] keeps one thread's buttons from replacing another's (PendingIntents match on action and request code). */
    private fun action(name: String, ids: Array<String>, key: String): PendingIntent {
        val intent = Intent(context, NotificationActionReceiver::class.java).setAction(name).putExtra(NotificationActionReceiver.EXTRA_IDS, ids)
        return PendingIntent.getBroadcast(context, (name + key).hashCode(), intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    companion object {
        const val CHANNEL_WATCHED = "watched_threads"
        /** The summary style's notification. */
        const val NOTIFICATION_ID = 1
        /** The per-thread style's group summary. */
        const val GROUP_SUMMARY_ID = 2
        /** The per-thread style's notifications, tagged with the thread id. */
        const val THREAD_NOTIFICATION_ID = 3
        private const val GROUP_WATCHED = "au.howesthat.whirl.WATCHED"

        /** Boolean extra on the launch intent: open the Watched screen. */
        const val EXTRA_OPEN_WATCHED = "au.howesthat.whirl.OPEN_WATCHED"

        /** Extras on the launch intent from a thread's notification: open that thread as Watched would. */
        const val EXTRA_THREAD_ID = "au.howesthat.whirl.THREAD_ID"
        const val EXTRA_FORUM_ID = "au.howesthat.whirl.FORUM_ID"
        const val EXTRA_LAST_PAGE = "au.howesthat.whirl.LAST_PAGE"
        const val EXTRA_LAST_READ_POST = "au.howesthat.whirl.LAST_READ_POST"

        private const val MAX_LINES = 6
    }
}
