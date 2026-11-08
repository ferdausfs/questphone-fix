package neth.iecal.questphone.core.utils.reminder.simpleAlarm
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build

class AlarmHelper(private val context: Context) {

    fun setAlarm(triggerAtMillis: Long, title: String, message: String) {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra("title", title)
            putExtra("message", message)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            triggerAtMillis.hashCode(), // unique ID
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Schedule exactly once. The previous code called setExactAndAllowWhileIdle
            // unconditionally BEFORE this permission check, throwing SecurityException
            // when SCHEDULE_EXACT_ALARM was denied (default on targetSdk 33+).
            if (alarmManager.canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
            } else {
                // Inexact fallback keeps reminders working without the exact-alarm permission
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
            }
        } else {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                pendingIntent
            )
        }

    }
}
