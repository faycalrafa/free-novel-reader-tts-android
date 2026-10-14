package com.novelarrow.reader.browser.engine

import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface

/**
 * Safe, throttled @JavascriptInterface bridge.
 * Prevents IPC Binder transaction buffer overflows and runaway event floods.
 */
class BrowserBridge(
    private val onParagraphClickedCallback: (Int) -> Unit,
    private val onScrolledToBottomCallback: () -> Unit
) {

    private val mainHandler = Handler(Looper.getMainLooper())
    private var lastScrollTimeMs = 0L
    private val scrollThrottleMs = 500L

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
    }
}
