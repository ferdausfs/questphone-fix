package neth.iecal.questphone.app.screens.quest.view.external_integration.webview

import android.content.Context
import android.content.Intent
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.io.IOException
import neth.iecal.questphone.BuildConfig
import neth.iecal.questphone.app.screens.quest.view.external_integration.ExternalIntegrationQuestViewVM
import neth.iecal.questphone.core.utils.reminder.simpleAlarm.AlarmHelper
import nethical.questphone.data.json
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File


class WebAppInterface(private val context: Context, private val webView: WebView, private val viewQuestVM: ExternalIntegrationQuestViewVM) {

    private val client = OkHttpClient()

    @JavascriptInterface
    fun onQuestCompleted() {
        try {
            viewQuestVM.saveMarkedQuestToDb()
            Log.d("WebAppInterface", "Quest Completed")
            Toast.makeText(context, "Quest completed!", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            // Any exception on the JavaBridge thread kills the process.
            Log.e("WebAppInterface", "onQuestCompleted failed", e)
        }
    }

    @JavascriptInterface
    fun toast(msg: String) {
        Log.d("WebAppInterfaceToast",msg)
        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
    }
    @JavascriptInterface
    fun sendBroadcast(action: String, jsonData: String?) {
        // Block ALL app-internal launcher broadcasts: a hostile integration page must
        // not be able to unlock apps, refresh the blocker, or start/stop deep focus
        // via this bridge (INTENT_ACTION_* constants all live under "launcher.launcher.").
        // Legitimate integrations only RECEIVE broadcasts — they never need to send these.
        if (action.startsWith("launcher.launcher.")) {
            Log.e("WebAppInterface", "Blocked privileged broadcast from webview: $action")
            return
        }
        val intent = Intent(action)
        intent.putExtra("payload", jsonData ?: "{}")
        context.sendBroadcast(intent)
    }
    @JavascriptInterface
    fun getUserData(): String {
        return try {
            viewQuestVM.getUserData()
        } catch (e: Exception) {
            Log.e("WebAppInterface", "getUserData failed", e)
            "{}"
        }
    }


    @JavascriptInterface
    fun registerReceiver(actionsJson: String) {
        try {
            val actions: List<String> = json.decodeFromString(actionsJson)
            BroadcastCenter.register(context, webView, actions)
        } catch (e: Exception) {
            // Malformed JSON from web content must not crash the process.
            Log.e("WebAppInterface", "registerReceiver failed", e)
        }
    }

    @JavascriptInterface
    fun unregisterAll() {
        BroadcastCenter.unregister(context)
    }

    @JavascriptInterface
    fun isQuestCompleted():Boolean{
        return viewQuestVM.isQuestComplete.value
    }
    @JavascriptInterface
    fun enableFullScreen() {
        viewQuestVM.isFullScreen.value = true
    }
    @JavascriptInterface
    fun disableFullScreen(){
        viewQuestVM.isFullScreen.value = false
    }
    @JavascriptInterface
    fun getVersion(): Int {
        return BuildConfig.VERSION_CODE
    }
    
    @JavascriptInterface
    fun setAlarmedNotification(triggerMillis: Long,title:String,description: String){
        try {
            val alarmManager = AlarmHelper(context)
            alarmManager.setAlarm(triggerMillis,title,description)
        } catch (e: Exception) {
            Log.e("WebAppInterface", "setAlarmedNotification failed", e)
        }
    }
    @JavascriptInterface
    fun openApp(packageName: String) {
        try {
            val pm = context.packageManager
            val intent = pm.getLaunchIntentForPackage(packageName)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            } else {
                // App not installed, open Play Store
                val playIntent = Intent(Intent.ACTION_VIEW).apply {
                    data = "https://play.google.com/store/apps/details?id=$packageName".toUri()
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(playIntent)
            }
        } catch (e: Exception) {
            // No browser/store on the device → ActivityNotFoundException.
            Log.e("WebAppInterface", "openApp failed", e)
        }
    }
    @JavascriptInterface
    fun shareText(title: String, text: String) {
        try {
            val shareIntent = Intent().apply {
                action = Intent.ACTION_SEND
                putExtra(Intent.EXTRA_TITLE, title)
                putExtra(Intent.EXTRA_TEXT, text)
                type = "text/plain"
            }
            val chooser = Intent.createChooser(shareIntent, "Share via")
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
        } catch (e: Exception) {
            Log.e("WebAppInterface", "shareText failed", e)
        }
    }
    @JavascriptInterface
    fun shareImage(base64: String) {
        try {
            // Remove the header if present
            val base64Data = base64.substringAfter("base64,", base64)
            val bytes = android.util.Base64.decode(base64Data, android.util.Base64.DEFAULT)

            // Save to cache directory
            val file = File(context.cacheDir, "shared_image.png")
            file.outputStream().use {
                it.write(bytes)
            }

            // Get content URI using FileProvider
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)

            // Share Intent
            val shareIntent = Intent().apply {
                action = Intent.ACTION_SEND
                putExtra(Intent.EXTRA_STREAM, uri)
                type = "image/*"
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            context.startActivity(Intent.createChooser(shareIntent, "Share Image via"))

        } catch (e: Exception) {
            Log.e("WebAppInterface", "Failed to share image", e)
        }
    }

    @JavascriptInterface
    fun getCoinRewardRatio():Int{
        val sp = context.getSharedPreferences("minutes_per_5", Context.MODE_PRIVATE)
        return sp.getInt("minutes_per_5",10)
    }
    @JavascriptInterface
    fun getAllStatsForQuest() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                viewQuestVM.getQuestStats {
                    webView.post {
                        webView.evaluateJavascript("onStatsReceived(${JSONObject.quote(it)});", null)
                    }
                }
            } catch (e: Exception) {
                Log.e("WebAppInterface", "getAllStatsForQuest failed", e)
            }
        }
    }
    @JavascriptInterface
    fun fetchDataWithoutCorsAsync(url: String, headersJson: String?, callback: String) {
        // The URL must be https and the callback name a plain JS identifier —
        // both are interpolated into evaluated JavaScript, so unvalidated values
        // would let a page inject arbitrary JS into this privileged context.
        if (!url.startsWith("https://", ignoreCase = true)) {
            Log.e("WebAppInterface", "Blocked non-https fetch request")
            return
        }
        if (!Regex("^[A-Za-z0-9_.]{1,64}$").matches(callback)) {
            Log.e("WebAppInterface", "Blocked invalid JS callback name")
            return
        }
        Log.d("Webview","Fetching without cors")
        Thread {
            val result = try {
                val builder = Request.Builder().url(url)

                // Parse headers JSON from JS
                headersJson?.let {
                    val json = JSONObject(it)
                    val headersBuilder = Headers.Builder()
                    json.keys().forEach { key ->
                        headersBuilder.add(key, json.getString(key))
                    }
                    builder.headers(headersBuilder.build())
                }

                val request = builder.build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        "{\"error\":\"${response.code}\"}"
                    } else {
                        response.body?.string() ?: "{}"
                    }
                }
            } catch (e: IOException) {
                "{\"error\":\"${e.message}\"}"
            }

            // Post result back to JS callback on UI thread
            webView.post {
                webView.evaluateJavascript("$callback(${JSONObject.quote(result)});", null)
            }
        }.start()
    }
}
