package au.howesthat.whirl.core.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import java.time.Clock
import javax.inject.Inject

/**
 * [CheckAlarm]'s alarm: starts a check, and sets the next alarm an interval from now in case the check never runs (no
 * network). The check sets it again from its own start when it finishes.
 */
@AndroidEntryPoint
class CheckAlarmReceiver : BroadcastReceiver() {
    @Inject lateinit var alarm: CheckAlarm
    @Inject lateinit var clock: Clock

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != CheckAlarm.ACTION_CHECK) return
        val interval = intent.getIntExtra(CheckAlarm.EXTRA_INTERVAL, 0).takeIf { it > 0 } ?: return
        CheckAlarm.enqueueCheck(context)
        alarm.armAt(clock.millis() + intervalMillis(interval), interval)
    }
}
