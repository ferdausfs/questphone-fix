package neth.iecal.questphone.core.utils.managers

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import neth.iecal.questphone.data.CommonQuestInfo
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Calendar

class QuestHelper(val context: Context) {
    val sharedPreferences: SharedPreferences = context.getSharedPreferences(
        PREF_NAME,
        Context.MODE_PRIVATE
    )

    fun isQuestRunning(title: String): Boolean {
        return sharedPreferences.getBoolean(QUEST_IS_RUNNING_SUFFIX + title, false)
    }

    fun setQuestRunning(title: String, isRunning: Boolean) {
        sharedPreferences.edit { putBoolean(QUEST_IS_RUNNING_SUFFIX + title, isRunning) }
    }


    companion object {
        private const val PREF_NAME = "all_quest_preferences"
        private const val QUEST_IS_RUNNING_SUFFIX = "quest_state_"

        /**
         * time_range can be shorter than 2 elements (imported JSON, template or
         * restored backup). getOrElse keeps these helpers total — they are called
         * from the HomeScreen sort comparator, i.e. on every launcher render.
         */
        private fun startHour(baseData: CommonQuestInfo): Int = baseData.time_range.getOrElse(0) { 0 }
        private fun endHour(baseData: CommonQuestInfo): Int = baseData.time_range.getOrElse(1) { 24 }

        fun isInTimeRange(baseData: CommonQuestInfo): Boolean {
            val currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
            return currentHour in startHour(baseData)..endHour(baseData)
        }


        fun isNeedAutoDestruction(baseData: CommonQuestInfo): Boolean {
            val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
            val today = LocalDate.now()
            // auto_destruct can be "", "null" or any garbage string (older versions
            // and imports). Unparseable = never auto-destruct.
            val autoDestruct = runCatching {
                LocalDate.parse(baseData.auto_destruct, formatter)
            }.getOrNull() ?: return false
            return today >= autoDestruct
        }

        fun isTimeOver(baseData: CommonQuestInfo): Boolean {
            val currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
            return currentHour > endHour(baseData)
        }
    }
}


