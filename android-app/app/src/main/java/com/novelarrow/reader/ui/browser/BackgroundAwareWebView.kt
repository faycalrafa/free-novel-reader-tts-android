package com.novelarrow.reader.ui.browser

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.os.Message
import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import com.novelarrow.reader.browser.engine.BrowserBridge
import com.novelarrow.reader.util.AppLogger

private const val TAG = "BackgroundAwareWebView"

/**
 * A custom WebView that preserves timer and script execution when the app is backgrounded
 * during active live reading or batch downloading.
 */
class BackgroundAwareWebView(context: Context) : WebView(context) {
    var keepAliveInBackground: Boolean = false

    override fun onWindowVisibilityChanged(visibility: Int) {
        if (keepAliveInBackground && (visibility == View.GONE || visibility == View.INVISIBLE)) {
            // Keep Chromium rendering pipeline and JS timers running while backgrounded
            super.onWindowVisibilityChanged(View.VISIBLE)
        } else {
            super.onWindowVisibilityChanged(visibility)
        }
    }
}

@Suppress("DEPRECATION")
@SuppressLint("SetJavaScriptEnabled")
fun createConfiguredWebView(
    context: Context,
    viewModel: BrowserViewModel
): BackgroundAwareWebView {
    AppLogger.d(TAG, "Creating configured BackgroundAwareWebView")
    return BackgroundAwareWebView(context).apply {
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )

        // Give the webview dark background initially to prevent white flashing
        setBackgroundColor(android.graphics.Color.TRANSPARENT)

        settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
            useWideViewPort = true
            loadWithOverviewMode = true
            cacheMode = WebSettings.LOAD_DEFAULT
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            // Modern Chrome user agent
            userAgentString = userAgentString.replace("wv", "")
        }

        val bridge = BrowserBridge(
            context = context,
            onParagraphClickedCallback = { index -> viewModel.seekToWebParagraph(index) },
            onScrolledToBottomCallback = {
                if (viewModel.uiState.value.settings.autoClickOnScroll) {
                    viewModel.clickNextChapter()
                }
            },
            onLongPressClickableCallback = { selector, text, hostname ->
                viewModel.onLongPressClickable(selector, text, hostname)
            },
            onRegisterCustomButtonCallback = { payload ->
                viewModel.registerCustomToolbarButton(payload)
            },
            onSaveDownloadedChapterCallback = { novelTitle, chTitle, chNum, html ->
                viewModel.saveDownloadedChapterFromScript(novelTitle, chTitle, chNum, html)
            }
        )
        addJavascriptInterface(bridge, BrowserBridge.INTERFACE_NAME)
        addJavascriptInterface(bridge, BrowserBridge.INTERFACE_NAME_BROWSER)

        setDownloadListener { url, userAgent, contentDisposition, mimetype, contentLength ->
            viewModel.handleGenericFileDownload(url, userAgent, contentDisposition, mimetype, contentLength)
        }

        webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                url?.let { viewModel.onPageStarted(it) }
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                url?.let {
                    viewModel.onPageFinished(
                        url = it,
                        title = view?.title,
                        canGoBack = view?.canGoBack() == true,
                        canGoForward = view?.canGoForward() == true
                    )
                }
            }
        }

        webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                super.onProgressChanged(view, newProgress)
                viewModel.onPageProgress(newProgress)
            }

            override fun onReceivedTitle(view: WebView?, title: String?) {
                super.onReceivedTitle(view, title)
                viewModel.onReceivedTitle(title)
            }

            override fun onCreateWindow(
                view: WebView?,
                isDialog: Boolean,
                isUserGesture: Boolean,
                resultMsg: Message?
            ): Boolean {
                if (resultMsg != null) {
                    val transport = resultMsg.obj as? WebView.WebViewTransport
                    if (transport != null) {
                        transport.webView = view
                        resultMsg.sendToTarget()
                        return true
                    }
                }
                return super.onCreateWindow(view, isDialog, isUserGesture, resultMsg)
            }
        }
    }
}
