package au.howesthat.whirl

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.work.Configuration
import au.howesthat.whirl.core.data.AppVisits
import au.howesthat.whirl.core.data.ApplicationScope
import au.howesthat.whirl.core.data.ForumRepository
import au.howesthat.whirl.core.notifications.NotificationScheduler
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@HiltAndroidApp
class WhirlApplication : Application(), Configuration.Provider {
    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var notificationScheduler: NotificationScheduler
    @Inject lateinit var appVisits: AppVisits
    @Inject lateinit var forums: ForumRepository
    @Inject @ApplicationScope lateinit var appScope: CoroutineScope

    // WorkManager starts on demand with Hilt's worker factory (its startup initializer is removed in the manifest).
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        notificationScheduler.start()
        appScope.launch { forums.forgetLearnedPrivateOnce() }
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) = appVisits.onForeground()
        })
    }
}
