package neth.iecal.questphone.core.utils.reminder.streak

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import neth.iecal.questphone.app.screens.game.handleStreakFreezers
import neth.iecal.questphone.core.utils.reminder.HiltBroadcastReceiver
import neth.iecal.questphone.core.utils.scheduleDailyNotification
import neth.iecal.questphone.backed.repositories.UserRepository
import javax.inject.Inject

@AndroidEntryPoint(BroadcastReceiver::class)
class StreakReminderReceiver : HiltBroadcastReceiver() {
    @Inject lateinit var userRepository: UserRepository

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)

        // A receiver exception kills the whole process. This fires every morning
        // at 9 AM — a single bad streak date must never crash the app.
        try {
            if (userRepository.userInfo.streak.currentStreak != 0) {
                val daysSince = userRepository.checkIfStreakFailed()
                if(daysSince!=null){
                    handleStreakFreezers(userRepository.tryUsingStreakFreezers(daysSince))
                }
            }
            generateStreakReminder(userRepository, context)
        } catch (e: Exception) {
            android.util.Log.e("StreakReminderReceiver", "Streak reminder failed", e)
        }

        // re-schedule for next day
        scheduleDailyNotification(context, 9, 0) // set your fixed time
    }
}
