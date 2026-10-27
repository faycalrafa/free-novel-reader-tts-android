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
    private val onScrolledToBottomCallback: () -> Unit = {},
    private val onLongPressClickableCallback: (selector: String, text: String, hostname: String) -> Unit = { _, _, _ -> },
    private val onRegisterCustomButtonCallback: (String) -> Unit = {},
    private val onOpenDownloadDialogCallback: () -> Unit = {},
    private val onSaveDownloadedChapterCallback: (novelTitle: String, chapterTitle: String, chapterNumber: Int, htmlContent: String) -> Unit = { _, _, _, _ -> },
    private val onSyncFolderCallback: (String?) -> Unit = {},
    private val onBase64DownloadCallback: (dataUri: String, fileName: String, mimeType: String) -> Unit = { _, _, _ -> },
    private val onAutoLoopStateChangedCallback: (active: Boolean, remaining: Int, status: String) -> Unit = { _, _, _ -> }
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

    @JavascriptInterface
    fun onLongPressClickable(selector: String, text: String, hostname: String) {
        mainHandler.post {
            onLongPressClickableCallback.invoke(selector, text, hostname)
        }
    }

    @JavascriptInterface
    fun registerCustomButton(payload: String) {
        mainHandler.post {
            onRegisterCustomButtonCallback.invoke(payload)
        }
    }

    @JavascriptInterface
    fun openDownloadDialog() {
        mainHandler.post {
            onOpenDownloadDialogCallback.invoke()
        }
    }

    @JavascriptInterface
    fun saveDownloadedChapter(novelTitle: String, chapterTitle: String, chapterNumber: Int, htmlContent: String) {
        mainHandler.post {
            onSaveDownloadedChapterCallback.invoke(novelTitle, chapterTitle, chapterNumber, htmlContent)
        }
    }

    @JavascriptInterface
    fun syncFolder(novelTitle: String?) {
        mainHandler.post {
            onSyncFolderCallback.invoke(novelTitle)
        }
    }

    @JavascriptInterface
    fun handleBase64Download(dataUri: String, fileName: String, mimeType: String) {
        mainHandler.post {
            onBase64DownloadCallback.invoke(dataUri, fileName, mimeType)
        }
    }

    @JavascriptInterface
    fun onAutoLoopStateChanged(active: Boolean, remaining: Int, status: String) {
        mainHandler.post {
            onAutoLoopStateChangedCallback.invoke(active, remaining, status)
        }
    }

    companion object {
        const val INTERFACE_NAME = "AndroidBridge"
        const val INTERFACE_NAME_BROWSER = "BrowserBridge"
    }
}
