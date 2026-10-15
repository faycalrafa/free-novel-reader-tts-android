package com.novelarrow.reader.browser.engine

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.widget.Toast

/**
 * Safe, throttled @JavascriptInterface bridge.
 * Exposes core browser hooks and standard Greasemonkey APIs (GM_notification).
 */
class BrowserBridge(
    private val context: Context? = null,
    private val onParagraphClickedCallback: (Int) -> Unit = {},
    private val onScrolledToBottomCallback: () -> Unit = {}
) {

    private val mainHandler = Handler(Looper.getMainLooper())
    private var lastScrollTimeMs = 0L
    private val scrollThrottleMs = 500L

    @JavascriptInterface
    fun gmNotification(message: String) {
        mainHandler.post {
            context?.let { ctx ->
                Toast.makeText(ctx, message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    @JavascriptInterface
    fun onParagraphClicked(index: Int) {
        mainHandler.post {
            onParagraphClickedCallback.invoke(index)
        }
    }

    @JavascriptInterface
    fun onScrolledToBottom() {
        val now = System.currentTimeMillis()
        if (now - lastScrollTimeMs >= scrollThrottleMs) {
            lastScrollTimeMs = now
            mainHandler.post {
                onScrolledToBottomCallback.invoke()
            }
        }
    }

    companion object {
        const val INTERFACE_NAME = "AndroidBridge"
        const val INTERFACE_NAME_BROWSER = "BrowserBridge"
    }
}
