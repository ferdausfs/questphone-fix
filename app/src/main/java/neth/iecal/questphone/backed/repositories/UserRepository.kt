package neth.iecal.questphone.backed.repositories

import android.content.Context
import android.util.Log
import androidx.core.content.edit
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.flow.MutableStateFlow
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.UUID
import neth.iecal.questphone.backed.triggerProfileSync
import neth.iecal.questphone.core.Supabase
import nethical.questphone.core.core.utils.getCurrentDate
import nethical.questphone.core.core.utils.getFullTimeAfter
import nethical.questphone.core.core.utils.isTimeOver
import nethical.questphone.data.UserInfo
import nethical.questphone.data.game.InventoryItem
import nethical.questphone.data.game.StreakFreezerReturn
import nethical.questphone.data.json
import nethical.questphone.data.xpToLevelUp
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.ExperimentalTime

@EntryPoint
@InstallIn(SingletonComponent::class)
interface UserRepositoryEntryPoint {
    fun userRepository(): UserRepository
}

@Singleton
class UserRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val statsRepository: StatsRepository,
    private val questRepository: QuestRepository
) {
    var userInfo: UserInfo = loadUserInfo()
    var coinsState = MutableStateFlow(userInfo.coins)
    var currentStreakState = MutableStateFlow(userInfo.streak.currentStreak)

    var activeBoostsState = MutableStateFlow(userInfo.active_boosts)
    
    // the below variables act as a trigger for launching the reward dialog declared in the MainActivity from a
    // different SubScreen.
    @Synchronized
    fun getUserId(): String {
        // Local-only build: no accounts exist, so a stable local identifier is
        // generated once and kept on-device instead of a Supabase user id.
        if (userInfo.isAnonymous) {
            val sp = context.getSharedPreferences("local_user", Context.MODE_PRIVATE)
            var id = sp.getString("key", null)
            if (id != null) return id
            id = UUID.randomUUID().toString()
            sp.edit { putString("key", id) }
            return id
        }
        val sp = context.getSharedPreferences("authtoken", Context.MODE_PRIVATE)
        var id = sp.getString("key",null)
        if(id!= null) return id
        id = Supabase.supabase.auth.currentUserOrNull()?.id ?: return ""
        sp.edit { putString("key",id) }
        return Supabase.supabase.auth.currentUserOrNull()?.id ?: id
    }

    fun addXp(xp: Int) {
        removeInactiveBooster()
        val multiplier = if (isBoosterActive(InventoryItem.XP_BOOSTER)) 2 else 1
        userInfo.xp += xp * multiplier
        while (userInfo.xp >= xpToLevelUp(userInfo.level )) {
            userInfo.xp -= xpToLevelUp(userInfo.level)
            userInfo.level++
        }
        saveUserInfo()
    }

    fun removeInactiveBooster() {
        userInfo.active_boosts.entries.removeIf { isTimeOver(it.value) }
        activeBoostsState.value = HashMap(userInfo.active_boosts)
        saveUserInfo()
    }

    fun activateBoost(item: InventoryItem, hoursToAdd: Long, minsToAdd: Long){
        // Use the requested item (previously XP_BOOSTER was hardcoded).
        userInfo.active_boosts.put(item, getFullTimeAfter(hoursToAdd, minsToAdd))
        saveUserInfo()
        //update state
        activeBoostsState.value = HashMap(userInfo.active_boosts)
    }
    fun isBoosterActive(reward: InventoryItem): Boolean {
        if (userInfo.active_boosts.contains(reward)) {
            val isActive = !isTimeOver(userInfo.active_boosts.getOrDefault(reward, ""))
            if (!isActive) removeInactiveBooster()
            return isActive
        }
        return false
    }

    fun addItemsToInventory(items: HashMap<InventoryItem, Int>) {
        items.forEach {
            userInfo.inventory[it.key] = it.value + getInventoryItemCount(it.key)
        }
        saveUserInfo()
    }

    fun saveUserInfo(isSetLastUpdated: Boolean = true) {
        if (isSetLastUpdated && !userInfo.isAnonymous) {
            userInfo.last_updated = System.currentTimeMillis()
            userInfo.needsSync = true
            triggerProfileSync(context)
        }
        context.getSharedPreferences("user_info", Context.MODE_PRIVATE)
            .edit { putString("user_info", json.encodeToString(snapshotUserInfo())) }
        coinsState.value = userInfo.coins
    }

    /**
     * Deep copy of the current profile. Background threads (backup worker, JS
     * bridge) must encode THIS, never the live object — the live HashMaps are
     * concurrently mutated on the main thread and iterating them elsewhere
     * throws ConcurrentModificationException.
     */
    fun snapshotUserInfo(): UserInfo {
        return userInfo.copy(
            inventory = HashMap(userInfo.inventory),
            active_boosts = HashMap(userInfo.active_boosts),
            blockedAndroidPackages = userInfo.blockedAndroidPackages?.toSet(),
            unlockedAndroidPackages = userInfo.unlockedAndroidPackages?.let { HashMap(it) },
            fcm_tokens = userInfo.fcm_tokens.toList(),
            streak = userInfo.streak.copy(
                streakFailureHistory = HashMap(userInfo.streak.streakFailureHistory)
            ),
            achievements = userInfo.achievements.toList(),
            customization_info = userInfo.customization_info.copy(
                themeData = HashMap(userInfo.customization_info.themeData),
                purchasedThemes = HashSet(userInfo.customization_info.purchasedThemes),
                purchasedWidgets = HashSet(userInfo.customization_info.purchasedWidgets)
            )
        )
    }

    fun getInventoryItemCount(item: InventoryItem): Int {
        return userInfo.inventory.getOrDefault(item, 0)
    }

    fun updateBlockedAppsSet(set: Set<String>){
        userInfo.blockedAndroidPackages = set
        saveUserInfo()
    }

    fun updateUnlockedAppsSet(set: Map<String,Long>){
        userInfo.unlockedAndroidPackages = set.toMutableMap()
        saveUserInfo()
    }
    fun deductFromInventory(item: InventoryItem, count: Int = 1) {
        val available = getInventoryItemCount(item)
        if (available > 0) {
            val newCount = (available - count.coerceAtLeast(0))
            if (newCount <= 0) {
                userInfo.inventory.remove(item)
            } else {
                userInfo.inventory[item] = newCount
            }
            saveUserInfo()
        }
    }
    fun getBlockedPackages():Set<String>{
        return userInfo.blockedAndroidPackages?:emptySet()
    }
    fun getUnlockedPackages(): MutableMap<String, Long>{
        return userInfo.unlockedAndroidPackages ?: mutableMapOf()
    }

    fun useCoins(number: Int) {
        userInfo.coins = (userInfo.coins - number).coerceAtLeast(0)
        coinsState.value = userInfo.coins
        saveUserInfo()
    }

    fun addCoins(addedCoins: Int) {
        userInfo.coins += addedCoins
        coinsState.value+=addedCoins
        saveUserInfo()
    }

    /**
     * @return failing for how many days or null if not failing
     */
    fun checkIfStreakFailed(): Int? {
        val today = LocalDate.now()
        val streakData = userInfo.streak
        // lastCompletedDate may be garbage (restored backup / old locale-digit bug);
        // treat unparseable dates as "never completed" instead of crashing.
        val lastCompleted = runCatching { LocalDate.parse(streakData.lastCompletedDate) }
            .getOrDefault(LocalDate.of(1, 1, 1))
        val daysSince = ChronoUnit.DAYS.between(lastCompleted, today)
        Log.d("streak day since", daysSince.toString())

        if (daysSince > 1) {
            return daysSince.toInt()
        }
            return null

    }



    fun tryUsingStreakFreezers(daysSince:Int): StreakFreezerReturn {
        val requiredFreezers = (daysSince -1).coerceAtLeast(0)
        val today = LocalDate.now()
        if (getInventoryItemCount(InventoryItem.STREAK_FREEZER) >= requiredFreezers) {
            deductFromInventory(InventoryItem.STREAK_FREEZER, requiredFreezers)

            val oldStreak = userInfo.streak.currentStreak
            userInfo.streak.currentStreak += requiredFreezers
            userInfo.streak.lastCompletedDate = today.minusDays(1).format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
            currentStreakState.value = userInfo.streak.currentStreak
            saveUserInfo()
            return StreakFreezerReturn(isOngoing = true,streakFreezersUsed = requiredFreezers, lastStreak = oldStreak)
        } else {
            // User failed streak
            val oldStreak = userInfo.streak.currentStreak
            userInfo.streak.longestStreak = maxOf(userInfo.streak.currentStreak, userInfo.streak.longestStreak)
            userInfo.streak.currentStreak = 0
            currentStreakState.value = userInfo.streak.currentStreak
            updateStreakHistory(oldStreak)
            saveUserInfo()
            return StreakFreezerReturn(isOngoing = false,streakDaysLost = oldStreak)
        }
    }

    private fun updateStreakHistory(oldStreak: Int){
        val streakHistory = userInfo.streak.streakFailureHistory.toMutableMap()
        streakHistory[getCurrentDate()] = oldStreak
        userInfo.streak.streakFailureHistory = streakHistory
    }
    fun continueStreak(): Boolean {
        val today = LocalDate.now()
        // Unparseable/missing date is treated as "never completed" (year 1) so the
        // streak simply continues instead of crashing with DateTimeParseException.
        val lastCompleted = runCatching { LocalDate.parse(userInfo.streak.lastCompletedDate) }
            .getOrDefault(LocalDate.of(1, 1, 1))
        val daysSince = ChronoUnit.DAYS.between(lastCompleted, today)

        Log.d("daysSince",daysSince.toString())
        if(daysSince!=0L && daysSince > 0){
            userInfo.streak.currentStreak += 1
            userInfo.streak.longestStreak = maxOf(userInfo.streak.currentStreak, userInfo.streak.longestStreak)
            userInfo.streak.lastCompletedDate = getCurrentDate()
            currentStreakState.value = userInfo.streak.currentStreak

            saveUserInfo()
            return true
        }
        return false
    }


    fun calculateLevelUpInvRewards(): HashMap<InventoryItem, Int> {
        val rewards = hashMapOf<InventoryItem, Int>()
        rewards[InventoryItem.QUEST_SKIPPER] = 1
        if(userInfo.level == 2) rewards[InventoryItem.REWARD_TIME_EDITOR] = 1
        if (userInfo.level % 2 == 0) rewards[InventoryItem.XP_BOOSTER] = 1
        if (userInfo.level % 5 == 0) rewards[InventoryItem.STREAK_FREEZER] = 1
        return rewards
    }
    fun calculateLevelUpCoinsRewards(): Int {
        return maxOf(userInfo.level.times(userInfo.level),50)
    }


    @OptIn(ExperimentalTime::class)
    fun loadUserInfo(): UserInfo {
        val sharedPreferences = context.getSharedPreferences("user_info", Context.MODE_PRIVATE)
        val userInfoJson = sharedPreferences.getString("user_info", null)
        val info = userInfoJson?.let {
            try {
                json.decodeFromString<UserInfo>(it)
            } catch (e: Exception) {
                Log.e("UserRepository", "Corrupted user_info cache, starting fresh", e)
                UserInfo()
            }
        } ?: UserInfo()
        // This build is fully offline/local: there is no login or signup and no
        // Supabase session. Forcing the anonymous flag disables every cloud sync
        // path (they are all gated on !isAnonymous) and keeps all data on-device.
        info.isAnonymous = true
        info.needsSync = false
        sanitizeUserInfo(info)
        return info
    }

    companion object {

        /**
         * Repairs every free-form date field that can crash downstream parsers
         * (LocalDate.parse / LocalDateTime.parse). Needed because older app
         * versions wrote locale-localized dates (e.g. Bengali digits
         * "২০২৬-১১-০৮") and backups/foreign data can contain anything.
         * @param resetInvalidStreak also reset currentStreak when the date is
         *   unparseable (used for restored backups — a corrupt date means the
         *   streak state is meaningless)
         */
        fun sanitizeUserInfo(info: UserInfo, resetInvalidStreak: Boolean = false) {
            val isoDate = DateTimeFormatter.ofPattern("yyyy-MM-dd")
            runCatching { LocalDate.parse(info.streak.lastCompletedDate) }.onFailure {
                if (resetInvalidStreak) {
                    info.streak.currentStreak = 0
                }
                info.streak.lastCompletedDate = "0001-01-01"
            }

            // streakFailureHistory keys must be ISO dates
            val badHistoryKeys = info.streak.streakFailureHistory.keys.filter { key ->
                runCatching { LocalDate.parse(key, isoDate) }.isFailure
            }
            if (badHistoryKeys.isNotEmpty()) {
                info.streak.streakFailureHistory =
                    info.streak.streakFailureHistory.filterKeys { it !in badHistoryKeys }
            }

            // boost timestamps use the app-internal "yyyy-dd-MM-HH-mm" pattern;
            // unparseable entries would be treated as "never expires" — drop them.
            val boostFormatter = DateTimeFormatter.ofPattern("yyyy-dd-MM-HH-mm")
            val badBoosts = info.active_boosts.entries.filter { (_, v) ->
                runCatching { LocalDateTime.parse(v, boostFormatter) }.isFailure
            }.map { it.key }
            badBoosts.forEach { info.active_boosts.remove(it) }

            runCatching { info.created_on }.onFailure {
                info.created_on = java.time.Clock.systemDefaultZone().instant()
            }
        }
    }

    /**
     * Replaces the in-memory profile (used when restoring a local backup) and
     * refreshes every exposed state flow.
     */
    fun replaceUserInfo(info: UserInfo) {
        info.isAnonymous = true
        info.needsSync = false
        sanitizeUserInfo(info, resetInvalidStreak = true)
        userInfo = info
        coinsState.value = userInfo.coins
        currentStreakState.value = userInfo.streak.currentStreak
        activeBoostsState.value = HashMap(userInfo.active_boosts)
        saveUserInfo(false)
    }

    private fun deleteLocalUserInfoCache(){
        val sharedPreferences = context.getSharedPreferences("user_info", Context.MODE_PRIVATE)
        sharedPreferences.edit { remove("user_info") }
    }

    suspend fun signOut() {
        context.deleteSharedPreferences("crnt_pg_onboard")
        context.deleteSharedPreferences("onboard")
        deleteLocalUserInfoCache()

        questRepository.deleteAll()
        statsRepository.deleteAll()

        Supabase.supabase.auth.signOut()

    }

    fun saveFcmToken(string: String) {
        val tokens = userInfo.fcm_tokens.toMutableList()
        tokens.add(string)
        userInfo.fcm_tokens = tokens
        saveUserInfo()
        Log.d("UserRepository", "FCM token saved (count=${tokens.size})")
    }
}
