package com.novelarrow.reader

import android.content.Context
import android.widget.Toast
import androidx.compose.runtime.Composable
import com.novelarrow.reader.data.browser.WebNovelDownloader
import com.novelarrow.reader.data.export.AudioExportEngine
import com.novelarrow.reader.data.parser.ChapterParser
import com.novelarrow.reader.playback.AudioExportService
import com.novelarrow.reader.ui.common.IncomingLinkShareDialog
import com.novelarrow.reader.ui.common.IncomingTextShareDialog
import com.novelarrow.reader.util.AppLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import java.net.HttpURLConnection
import java.net.URL

private const val TAG = "IncomingShareDialogs"

@Composable
fun IncomingShareDialogs(
    context: Context,
    scope: CoroutineScope,
    sharedUrl: String?,
    onClearSharedUrl: () -> Unit,
    sharedText: String?,
    onClearSharedText: () -> Unit,
    onOpenInBrowser: (String) -> Unit,
    onSaveFrozenHtml: (String) -> Unit
) {
    if (sharedUrl != null) {
        IncomingLinkShareDialog(
            url = sharedUrl,
            onDismiss = onClearSharedUrl,
            onOpenInBrowser = onOpenInBrowser,
            onSaveFrozenHtml = onSaveFrozenHtml,
            onSaveAsMp3 = { url, speed ->
                scope.launch(Dispatchers.IO) {
                    try {
                        val conn = URL(url).openConnection() as HttpURLConnection
                        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Android; Mobile; rv:109.0) Gecko/109.0 Firefox/114.0")
                        conn.connectTimeout = 15000
                        conn.readTimeout = 15000
                        val html = conn.inputStream.bufferedReader().use { it.readText() }
                        val doc = Jsoup.parse(html, url)
                        val pageTitle = doc.title().ifBlank { "WebPage" }
                        val info = WebNovelDownloader.detectNovelInfo(url, pageTitle)
                        val paragraphs = ChapterParser.parse(
                            content = html,
                            fileName = "${info.novelTitle}.html",
                            novelUrl = url
                        )
                        val fullText = paragraphs.joinToString("\n\n") { it.spokenText }
                        if (fullText.isNotBlank()) {
                            AudioExportService.startExportText(
                                context = context,
                                title = info.novelTitle.ifBlank { pageTitle },
                                text = fullText,
                                speed = speed
                            )
                        } else {
                            withContext(Dispatchers.Main) {
                                Toast.makeText(context, "Could not extract readable text from link", Toast.LENGTH_LONG).show()
                            }
                        }
                    } catch (e: Exception) {
                        AppLogger.e(TAG, "Failed to audify link: ${e.message}", e)
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "Failed to download link: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
        )
    }

    if (sharedText != null) {
        IncomingTextShareDialog(
            text = sharedText,
            onDismiss = onClearSharedText,
            onSaveAsMp3 = { text, speed ->
                AudioExportService.startExportText(
                    context = context,
                    title = "Shared_Note",
                    text = text,
                    speed = speed,
                    audioFormat = AudioExportEngine.AudioExportFormat.WAV
                )
            }
        )
    }
}
