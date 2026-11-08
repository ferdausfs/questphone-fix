package neth.iecal.questphone

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import neth.iecal.questphone.core.services.reloadServiceInfo
import neth.iecal.questphone.core.Supabase
import neth.iecal.questphone.core.backup.LocalBackupWorker
import nethical.questphone.core.core.utils.CrashLogger
import nethical.questphone.core.core.utils.VibrationHelper
import androidx.work.BackoffPolicy
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit


// The Hilt Gradle plugin is applied, so the canonical no-arg form is required —
// the plugin bytecode-transforms MyApp to extend the generated Hilt_MyApp.
// (@HiltAndroidApp(Application::class) + aggregating task = build error)
@HiltAndroidApp
class MyApp : Application() {

    override fun onCreate() {
        super.onCreate()
        Supabase.initialize(this)
        VibrationHelper.init(this)
        reloadServiceInfo(this)

        scheduleDailyLocalBackup()

        Thread.setDefaultUncaughtExceptionHandler(CrashLogger(this))
    }

    /**
     * Every day the full local data set (profile, quests, stats, reminders) is
     * exported to phone storage so nothing is ever lost. A periodic worker runs
     * ~every 24h; a one-time catch-up job covers the case where the device was
     * off/rebooted for more than a day since the last backup.
     */
    private fun scheduleDailyLocalBackup() {
        val workManager = WorkManager.getInstance(this)

        val periodic = PeriodicWorkRequestBuilder<LocalBackupWorker>(24, TimeUnit.HOURS)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES)
            .build()

        workManager.enqueueUniquePeriodicWork(
            LocalBackupWorker.PERIODIC_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            periodic
        )

        if (LocalBackupWorker.isBackupDue(this)) {
            val catchUp = OneTimeWorkRequestBuilder<LocalBackupWorker>()
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES)
                .build()
            workManager.enqueueUniqueWork(
                LocalBackupWorker.CATCH_UP_NAME,
                ExistingWorkPolicy.KEEP,
                catchUp
            )
        }
    }

}
