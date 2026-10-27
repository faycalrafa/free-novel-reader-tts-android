package com.novelarrow.reader.ui.reader

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Science
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novelarrow.reader.R
import com.novelarrow.reader.data.model.Novel
import com.novelarrow.reader.playback.AudioPlaybackCoordinator
import com.novelarrow.reader.playback.PlaybackSource

@Composable
fun EmptyState(
    novels: List<Novel>,
    lastActiveNovelId: String? = null,
    onOpenFolder: () -> Unit,
    onLoadSampleNovel: () -> Unit,
    onResumeNovel: (Novel) -> Unit,
    onOpenDrawer: () -> Unit,
    onOpenBrowser: () -> Unit = {},
    onResumeBrowserSession: ((com.novelarrow.reader.data.repository.BrowserPlaybackSession) -> Unit)? = null
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Image(
            painter = painterResource(id = R.drawable.ic_app_icon_round),
            contentDescription = "BookReader Icon",
            modifier = Modifier.size(96.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "BookReader Pro",
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Pick a root folder on your device. Every subfolder becomes its own playlist with individually tracked progress and offline text-to-speech.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
            modifier = Modifier.padding(horizontal = 16.dp),
            lineHeight = 20.sp
        )

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = onOpenFolder,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Default.FolderOpen, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Set root folder")
        }

        if (novels.isEmpty()) {
            Spacer(modifier = Modifier.height(10.dp))

            OutlinedButton(
                onClick = onLoadSampleNovel,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Science, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Try Sample: Quantum Mechanics")
            }

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedButton(
                onClick = onOpenBrowser,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Language, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Browse & Download Web Novels")
            }
        }

        if (novels.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = onOpenDrawer,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Book, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Open Library (${novels.size})")
            }
        }

        val context = androidx.compose.ui.platform.LocalContext.current
        val lastSource = remember { com.novelarrow.reader.data.repository.BrowserPlaybackSessionManager.getLastPlaybackSource(context) }
        val browserSession = remember { com.novelarrow.reader.data.repository.BrowserPlaybackSessionManager.getLastSession(context) }
        val hasBrowserSession = browserSession != null && (browserSession.url.isNotBlank() || browserSession.chapterTitle.isNotBlank())
        val shouldShowBrowserResume = (lastSource == com.novelarrow.reader.data.repository.BrowserPlaybackSessionManager.SOURCE_BROWSER || novels.isEmpty()) && hasBrowserSession && onResumeBrowserSession != null

        if (shouldShowBrowserResume) {
            val session = browserSession ?: return
            Spacer(modifier = Modifier.height(16.dp))
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(0.85f)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Language,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "CONTINUE READING (WEB NOVEL)",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = session.novelTitle.ifBlank { session.chapterTitle.ifBlank { "Web Novel" } },
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${session.chapterTitle.ifBlank { "Chapter" }} • Paragraph ${session.paragraphIndex + 1}${if (session.totalParagraphs > 0) " of ${session.totalParagraphs}" else ""}",
                        style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = { onResumeBrowserSession(session) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Resume")
                    }
                }
            }
        } else if (novels.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            val activeProgress by AudioPlaybackCoordinator.playbackProgressState.collectAsState()
            val isPlayingActive = activeProgress.source == PlaybackSource.READER && activeProgress.novelId != null
            val effectiveNovelId = if (isPlayingActive) activeProgress.novelId else lastActiveNovelId
            val lastNovel = novels.firstOrNull { it.id == effectiveNovelId }
                ?: novels.maxByOrNull { it.lastUpdated }
                ?: novels.first()
            val effectiveChapterIndex = if (isPlayingActive && activeProgress.novelId == lastNovel.id) {
                activeProgress.chapterIndex
            } else {
                lastNovel.currentChapterIndex
            }
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(0.85f)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "CONTINUE READING",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = lastNovel.title,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                    )
                    Text(
                        text = "Chapter ${effectiveChapterIndex + 1} / ${lastNovel.chapterCount}" +
                            if (isPlayingActive && activeProgress.novelId == lastNovel.id && activeProgress.chapterTitle.isNotBlank()) " • ${activeProgress.chapterTitle}" else "",
                        style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = { onResumeNovel(lastNovel) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Resume")
                    }
                }
            }
        }
    }
}
