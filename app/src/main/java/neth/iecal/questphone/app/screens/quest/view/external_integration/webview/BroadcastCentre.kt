package neth.iecal.questphone.app.screens.quest.view.external_integration.webview

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.webkit.WebView
import androidx.core.content.ContextCompat
import nethical.questphone.data.json

object BroadcastCenter {
    private var receiver: BroadcastReceiver? = null

    fun register(context: Context, webView: WebView, actions: List<String>) {
        unregister(context)

        val filter = IntentFilter()
        actions.forEach { filter.addAction(it) }

        receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                val action = intent?.action ?: return
                val data = intent.getStringExtra("payload") ?: "{}"

                // send to JS — both values are passed as JSON string literals and
                // parsed on the JS side, so a hostile payload can never inject JS
                val actionJson = json.encodeToString(action)
                val dataJson = json.encodeToString(data)
                val jsCode =
                    "try { window.onBroadcast && window.onBroadcast($actionJson, JSON.parse($dataJson)) } catch (e) { console.error('onBroadcast', e) }"
                webView.post { webView.evaluateJavascript(jsCode, null) }
            }
        }
        // RECEIVER_NOT_EXPORTED is mandatory on targetSdk 34+ for non-protected
        // actions (registerReceiver without the flag throws SecurityException on
        // Android 14+), and it also prevents other apps from broadcasting into the
        // integration webview.
        ContextCompat.registerReceiver(
            context,
            receiver!!,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    fun unregister(context: Context) {
        receiver?.let {
            try {
                context.unregisterReceiver(it)
            } catch (_: IllegalArgumentException) {
                // receiver was never registered or already unregistered
            }
            receiver = null
        }
    }
}
