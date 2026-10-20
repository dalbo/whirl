package au.howesthat.whirl.core.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import au.howesthat.whirl.core.data.ApplicationScope
import au.howesthat.whirl.core.data.WatchAction
import au.howesthat.whirl.core.model.ThreadId
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * The notification's Mark read and Unwatch buttons: sends the action for every thread the notification listed (ids are
 * a String array; 2.14.12 crashed reading them with the wrong type), then removes the notification. On failure the
 * notification stays and a toast says so.
 */
@AndroidEntryPoint
class NotificationActionReceiver : BroadcastReceiver() {
    @Inject lateinit var notifier: WatchedNotifier

    @Inject @ApplicationScope
    lateinit var scope: CoroutineScope

    override fun onReceive(context: Context, intent: Intent) {
        val (action, ids) = parse(intent) ?: return
        val result = goAsync()
        scope.launch {
            try {
                if (!notifier.act(action, ids)) {
                    withContext(Dispatchers.Main) { Toast.makeText(context, R.string.notify_action_failed, Toast.LENGTH_LONG).show() }
                }
            } finally {
                result.finish()
            }
        }
    }

    companion object {
        const val ACTION_MARK_READ = "au.howesthat.whirl.action.MARK_READ"
        const val ACTION_UNWATCH = "au.howesthat.whirl.action.UNWATCH"
        const val EXTRA_IDS = "au.howesthat.whirl.extra.THREAD_IDS"

        internal fun parse(intent: Intent): Pair<WatchAction, List<ThreadId>>? {
            val action = when (intent.action) {
                ACTION_MARK_READ -> WatchAction.MARK_READ
                ACTION_UNWATCH -> WatchAction.UNWATCH
                else -> return null
            }
            val ids = intent.getStringArrayExtra(EXTRA_IDS)?.map(::ThreadId)?.takeIf { it.isNotEmpty() } ?: return null
            return action to ids
        }
    }
}
