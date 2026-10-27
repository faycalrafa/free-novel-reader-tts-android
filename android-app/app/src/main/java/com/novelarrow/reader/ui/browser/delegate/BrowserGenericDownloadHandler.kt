package com.novelarrow.reader.ui.browser.delegate

import android.app.Application
import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.util.Base64
import android.webkit.CookieManager
import android.webkit.URLUtil
import com.novelarrow.reader.data.model.DownloadHistoryEntry
import com.novelarrow.reader.data.repository.DownloadHistoryRepository
import com.novelarrow.reader.ui.browser.BrowserUiState
import com.novelarrow.reader.util.AppLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URLDecoder

/**
 * Handles generic browser file downloads, with dedicated handling for
 * HTTP/HTTPS (DownloadManager), blob: URIs (WebView FileReader base64 bridge),
 * and data: URIs (in-memory byte decode).
 */
class BrowserGenericDownloadHandler(
    private val application: Application,
    private val scope: CoroutineScope,
    private val uiState: MutableStateFlow<BrowserUiState>,
    val downloadHistoryRepository: DownloadHistoryRepository,
    private val dispatchEvaluateJs: (String, ((String?) -> Unit)?) -> Unit
) {
    companion object {
        private const val TAG = "BrowserGenericDownload"
    }

    fun handleGenericFileDownload(
        url: String,
        userAgent: String,
        contentDisposition: String,
        mimetype: String,
        contentLength: Long
    ) {
        val trimmedUrl = url.trim()
        val fileName = URLUtil.guessFileName(trimmedUrl, contentDisposition, mimetype).ifBlank {
            "download_${System.currentTimeMillis()}.html"
        }

        when {
            trimmedUrl.startsWith("blob:", ignoreCase = true) -> {
                handleBlobDownload(trimmedUrl, fileName, mimetype)
            }
            trimmedUrl.startsWith("data:", ignoreCase = true) -> {
                handleDataUriDownload(trimmedUrl, fileName, mimetype)
            }
            trimmedUrl.startsWith("http://", ignoreCase = true) || trimmedUrl.startsWith("https://", ignoreCase = true) -> {
                handleHttpDownload(trimmedUrl, fileName, userAgent, mimetype, contentLength)
            }
            else -> {
                AppLogger.w(TAG, "Unsupported download URI scheme: $trimmedUrl ($fileName)")
                uiState.update { it.copy(toastMessage = "Cannot download unsupported URI scheme") }
            }
        }
    }

    private fun handleBlobDownload(blobUrl: String, fileName: String, mimetype: String) {
        AppLogger.i(TAG, "Intercepted blob: URI download ($fileName). Resolving blob bytes via WebView JavaScript bridge...")
        val escapedUrl = blobUrl.replace("'", "\\'")
        val escapedName = fileName.replace("'", "\\'")
        val escapedMime = mimetype.replace("'", "\\'")

        val script = """
            (function() {
                var bUrl = '$escapedUrl';
                var fName = '$escapedName';
                var mMime = '$escapedMime';
                fetch(bUrl)
                    .then(function(response) { return response.blob(); })
                    .then(function(blob) {
                        var reader = new FileReader();
                        reader.onloadend = function() {
                            var b64Data = reader.result;
                            if (window.BrowserBridge && typeof window.BrowserBridge.handleBase64Download === 'function') {
                                window.BrowserBridge.handleBase64Download(b64Data, fName, mMime);
                            } else if (window.AndroidBridge && typeof window.AndroidBridge.handleBase64Download === 'function') {
                                window.AndroidBridge.handleBase64Download(b64Data, fName, mMime);
                            }
                        };
                        reader.readAsDataURL(blob);
                    })
                    .catch(function(err) {
                        console.error('Failed to convert blob URL for download', err);
                    });
            })();
        """.trimIndent()

        dispatchEvaluateJs(script, null)
    }

    private fun handleDataUriDownload(dataUri: String, fileName: String, mimetype: String) {
        scope.launch(Dispatchers.IO) {
            try {
                AppLogger.i(TAG, "Decoding data: URI download ($fileName)...")
                val isBase64 = dataUri.substringBefore(",").contains(";base64", ignoreCase = true)
                val rawPayload = dataUri.substringAfter(",")
                val bytes = if (isBase64) {
                    Base64.decode(rawPayload, Base64.DEFAULT)
                } else {
                    URLDecoder.decode(rawPayload, "UTF-8").toByteArray(Charsets.UTF_8)
                }
                saveBytesToFile(bytes, fileName, dataUri, mimetype)
            } catch (e: Exception) {
                AppLogger.e(TAG, "Failed decoding data URI download: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    uiState.update { it.copy(toastMessage = "Data URI download failed: ${e.message}") }
                }
            }
        }
    }

    private fun handleHttpDownload(
        url: String,
        fileName: String,
        userAgent: String,
        mimetype: String,
        contentLength: Long
    ) {
        try {
            val request = DownloadManager.Request(Uri.parse(url)).apply {
                if (mimetype.isNotBlank()) setMimeType(mimetype)
                if (userAgent.isNotBlank()) addRequestHeader("User-Agent", userAgent)
                val cookies = CookieManager.getInstance().getCookie(url)
                if (!cookies.isNullOrBlank()) {
                    addRequestHeader("Cookie", cookies)
                }
                setDescription("Downloading $fileName")
                setTitle(fileName)
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
            }
            val dm = application.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
            dm?.enqueue(request)

            val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val targetFile = File(downloadDir, fileName)

            scope.launch(Dispatchers.IO) {
                downloadHistoryRepository.recordDownload(
                    DownloadHistoryEntry(
                        fileName = fileName,
                        filePath = targetFile.absolutePath,
                        fileSizeBytes = if (contentLength > 0) contentLength else 0L,
                        downloadUrl = url,
                        isBatchNovelChapter = false,
                        mimeType = mimetype
                    )
                )
            }
            uiState.update { it.copy(toastMessage = "Downloading $fileName to Downloads...") }
            AppLogger.i(TAG, "Enqueued generic download via DownloadManager: $fileName ($url)")
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed enqueuing download: ${e.message}", e)
            uiState.update { it.copy(toastMessage = "Download failed: ${e.message}") }
        }
    }

    fun handleBase64Download(dataUri: String, fileName: String, mimeType: String) {
        scope.launch(Dispatchers.IO) {
            try {
                val payload = if (dataUri.contains(",")) dataUri.substringAfter(",") else dataUri
                val bytes = Base64.decode(payload, Base64.DEFAULT)
                saveBytesToFile(bytes, fileName, "blob:download", mimeType)
            } catch (e: Exception) {
                AppLogger.e(TAG, "Failed processing base64 download payload: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    uiState.update { it.copy(toastMessage = "Blob download failed: ${e.message}") }
                }
            }
        }
    }

    private suspend fun saveBytesToFile(
        bytes: ByteArray,
        fileName: String,
        sourceUrl: String,
        mimeType: String
    ) = withContext(Dispatchers.IO) {
        val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        if (!downloadDir.exists()) downloadDir.mkdirs()
        val targetFile = File(downloadDir, fileName)

        try {
            targetFile.writeBytes(bytes)
            AppLogger.i(TAG, "Saved ${bytes.size} bytes directly to ${targetFile.absolutePath}")

            downloadHistoryRepository.recordDownload(
                DownloadHistoryEntry(
                    fileName = fileName,
                    filePath = targetFile.absolutePath,
                    fileSizeBytes = bytes.size.toLong(),
                    downloadUrl = sourceUrl,
                    isBatchNovelChapter = false,
                    mimeType = mimeType
                )
            )

            withContext(Dispatchers.Main) {
                uiState.update { it.copy(toastMessage = "Downloaded $fileName to Downloads") }
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error writing bytes to ${targetFile.absolutePath}: ${e.message}", e)
            withContext(Dispatchers.Main) {
                uiState.update { it.copy(toastMessage = "Download failed: ${e.message}") }
            }
        }
    }
}
