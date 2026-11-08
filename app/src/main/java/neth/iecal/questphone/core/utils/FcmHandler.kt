package neth.iecal.questphone.core.utils

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.core.content.edit
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import neth.iecal.questphone.app.screens.quest.setup.external_integration.ExternalIntegrationQuestVM.Companion.ACTION_QUEST_CREATED
import neth.iecal.questphone.backed.repositories.UserRepository
import neth.iecal.questphone.backed.triggerProfileSync
import neth.iecal.questphone.backed.triggerQuestSync
import neth.iecal.questphone.backed.triggerStatsSync
import neth.iecal.questphone.core.Supabase
import nethical.questphone.data.game.InventoryItem
import nethical.questphone.data.json

object FcmHandler {
    fun handleData(context: Context, data: Map<String, String>?,userRepository: UserRepository) {
        if (data.isNullOrEmpty()) return

        // Local-only build: no accounts exist. Remote commands (gifts, coins,
        // logout/data-wipe) were only meaningful for cloud-synced users. Bail out
        // for anonymous users so a stale session from an upgraded 2.7.x install
        // can never re-enable these paths — including the unsigned remote wipe.
        if (userRepository.userInfo.isAnonymous) return

        val handler = CoroutineExceptionHandler { _, e ->
            android.util.Log.e("FcmHandler", "Remote command failed", e)
        }

        CoroutineScope(Dispatchers.IO + handler).launch {
            val userId = Supabase.awaitSession()
            if (userId == null) {
                return@launch
            }

            if (data.containsKey("refreshQuestId")) {
                triggerQuestSync(context, pullForQuest = data["refreshQuestId"])
                triggerStatsSync(context, pullAllForToday = true)

                if (data.containsKey("tokenId")) {
                    val prefs = context.getSharedPreferences("externalIntToken", Context.MODE_PRIVATE)
                    prefs.edit { remove("token") }
                    val intent = Intent(ACTION_QUEST_CREATED)
                    context.sendBroadcast(intent)
                    showToast("The new integration will be reflected shortly",context)
                } else {
                    showToast("refreshing quest data",context)
                }
            }
            if (data.containsKey("refreshProfile")) {
                triggerProfileSync(context)
                showToast("refreshing profile",context)
            }
            if(data.containsKey("gifts")){
                val items = runCatching {
                    json.decodeFromString<HashMap<InventoryItem,Int>>(data.get("gifts").toString())
                }.getOrNull() ?: return@launch
                userRepository.addItemsToInventory(items)
                showToast("Added New gifts!!",context)
            }
            if(data.containsKey("gift_coins")){
                val coins = data["coins"]?.toIntOrNull() ?: 0
                userRepository.addCoins(coins)
                showToast("Added $coins coins",context)
            }
            if(data.containsKey("logout")){
                userRepository.signOut()
                val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
                activityManager.clearApplicationUserData()
            }
            if(data.containsKey("deduct_coins")){
                val coins = data["coins"]?.toIntOrNull() ?: 0
                userRepository.useCoins(coins)
                showToast("Removed $coins coins",context)
            }
        }

    }
    private fun showToast(msg: String,context: Context){
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        }

    }
}
