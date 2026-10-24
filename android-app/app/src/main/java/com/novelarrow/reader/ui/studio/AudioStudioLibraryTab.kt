package com.novelarrow.reader.ui.studio

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.novelarrow.reader.data.export.AudioLibraryContent
import com.novelarrow.reader.data.export.FileFusionManager
import com.novelarrow.reader.data.model.Novel
import com.novelarrow.reader.playback.AudioExportService
import com.novelarrow.reader.playback.AudioPlaybackCoordinator
import com.novelarrow.reader.playback.PlaybackProgress
import com.novelarrow.reader.playback.PlaybackSource
import com.novelarrow.reader.util.AppLogger

@Composable
internal fun AudioStudioLibraryTab(
    libraryContent: AudioLibraryContent,
    novels: List<Novel>,
    isScanning: Boolean,
    exportState: AudioExportService.ExportState,
    isProgressDismissedLocally: Boolean,
    onDismissProgressLocally: () -> Unit,
    playbackProgress: PlaybackProgress,
    onNavigateToGenerator: () -> Unit,
    onRefreshLibrary: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var expandedFolderKey by remember { mutableStateOf<String?>(null) }
    var activeActionFilePath by remember { mutableStateOf<String?>(null) }

    if (libraryContent.isEmpty && !isScanning) {
        // Empty State View
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                modifier = Modifier.size(96.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.LibraryMusic,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(48.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = "No Audio Files Found",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Convert novels and chapters to MP3 audio files to listen offline anytime with natural Text-to-Speech.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = onNavigateToGenerator,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.height(48.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Generate Audio", fontWeight = FontWeight.Bold)
            }
        }
    } else {
        // Folders Category ordered based on parent folder in the library
        val orderedFolders = remember(libraryContent.folders, novels) {
            val novelTitleOrder = novels.map { it.title.trim().lowercase() }
            libraryContent.folders.sortedWith { f1, f2 ->
                val idx1 = novelTitleOrder.indexOf(f1.name.trim().lowercase())
                val idx2 = novelTitleOrder.indexOf(f2.name.trim().lowercase())
                when {
                    idx1 != -1 && idx2 != -1 -> idx1.compareTo(idx2)
                    idx1 != -1 -> -1
                    idx2 != -1 -> 1
                    else -> com.novelarrow.reader.data.util.NaturalOrderComparator.compare(f1.name, f2.name)
                }
            }
        }

        // Audio Library List
        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Live Export Progress Card if running
            if (exportState is AudioExportService.ExportState.Running && !isProgressDismissedLocally) {
                val runningState = exportState as AudioExportService.ExportState.Running
                item {
                    ExportProgressCard(
                        runningState = runningState,
                        onHide = onDismissProgressLocally,
                        onCancel = { AudioExportService.cancelExport(context) }
                    )
                }
            }

            if (orderedFolders.isNotEmpty()) {
                item {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
                    ) {
                        Icon(
                            Icons.Default.Folder,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Audio Folders (${orderedFolders.size})",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                orderedFolders.forEach { folderItem ->
                    val isExpanded = expandedFolderKey == folderItem.folder.absolutePath
                    item(key = "folder_${folderItem.folder.absolutePath}") {
                        FolderAudioCard(
                            folder = folderItem,
                            isExpanded = isExpanded,
                            isPlayingThis = (playbackProgress.source == PlaybackSource.AUDIO_FILE && playbackProgress.novelTitle == folderItem.name && playbackProgress.isPlaying),
                            onToggleExpand = {
                                expandedFolderKey = if (isExpanded) null else folderItem.folder.absolutePath
                            },
                            onPlayFolder = { AudioPlaybackCoordinator.playAudioFolder(folderItem.folder) }
                        )
                    }

                    if (isExpanded) {
                        if (folderItem.files.isEmpty()) {
                            item(key = "empty_${folderItem.folder.absolutePath}") {
                                Text(
                                    text = "No audio tracks in this folder",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(start = 24.dp, top = 4.dp, bottom = 4.dp)
                                )
                            }
                        } else {
                            items(folderItem.files, key = { "file_${it.file.absolutePath}" }) { fileItem ->
                                FileAudioCard(
                                    file = fileItem,
                                    isPlayingThis = (playbackProgress.source == PlaybackSource.AUDIO_FILE && playbackProgress.currentParagraphText == fileItem.file.name && playbackProgress.isPlaying),
                                    showInlineActions = (activeActionFilePath == fileItem.file.absolutePath),
                                    onPlayFile = {
                                        if (playbackProgress.source == PlaybackSource.AUDIO_FILE && playbackProgress.currentParagraphText == fileItem.file.name && playbackProgress.isPlaying) {
                                            AudioPlaybackCoordinator.pause()
                                        } else {
                                            val folderFiles = folderItem.files.map { it.file }
                                            val fileIdx = folderItem.files.indexOf(fileItem).coerceAtLeast(0)
                                            AudioPlaybackCoordinator.playAudioFile(
                                                file = fileItem.file,
                                                playlist = folderFiles,
                                                index = fileIdx
                                            )
                                        }
                                    },
                                    onLongPress = {
                                        activeActionFilePath = if (activeActionFilePath == fileItem.file.absolutePath) null else fileItem.file.absolutePath
                                    },
                                    onShare = {
                                        try {
                                            val shareIntent = FileFusionManager.createShareIntent(context, fileItem.file, "audio/mpeg")
                                            context.startActivity(Intent.createChooser(shareIntent, "Share Audio Track"))
                                        } catch (e: Exception) {
                                            AppLogger.e("AudioStudio", "Failed to share audio file: ${fileItem.file.absolutePath}", e)
                                            Toast.makeText(context, "Failed to share audio file", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    onDelete = {
                                        try {
                                            if (playbackProgress.source == PlaybackSource.AUDIO_FILE && playbackProgress.currentParagraphText == fileItem.file.name) {
                                                AudioPlaybackCoordinator.stop()
                                            }
                                            if (fileItem.file.exists() && fileItem.file.delete()) {
                                                Toast.makeText(context, "Track deleted", Toast.LENGTH_SHORT).show()
                                                onRefreshLibrary()
                                            } else {
                                                Toast.makeText(context, "Could not delete file", Toast.LENGTH_SHORT).show()
                                            }
                                        } catch (e: Exception) {
                                            AppLogger.e("AudioStudio", "Error deleting file: ${fileItem.file.absolutePath}", e)
                                            Toast.makeText(context, "Error deleting file: ${e.message}", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    modifier = Modifier.padding(start = 16.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Standalone Tracks Category
            if (libraryContent.standaloneFiles.isNotEmpty()) {
                item {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 10.dp, bottom = 4.dp)
                    ) {
                        Icon(
                            Icons.Default.Audiotrack,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Standalone Tracks (${libraryContent.standaloneFiles.size})",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }

                items(libraryContent.standaloneFiles, key = { "standalone_${it.file.absolutePath}" }) { fileItem ->
                    FileAudioCard(
                        file = fileItem,
                        isPlayingThis = (playbackProgress.source == PlaybackSource.AUDIO_FILE && playbackProgress.currentParagraphText == fileItem.file.name && playbackProgress.isPlaying),
                        showInlineActions = (activeActionFilePath == fileItem.file.absolutePath),
                        onPlayFile = {
                            if (playbackProgress.source == PlaybackSource.AUDIO_FILE && playbackProgress.currentParagraphText == fileItem.file.name && playbackProgress.isPlaying) {
                                AudioPlaybackCoordinator.pause()
                            } else {
                                val standaloneFiles = libraryContent.standaloneFiles.map { it.file }
                                val fileIdx = libraryContent.standaloneFiles.indexOf(fileItem).coerceAtLeast(0)
                                AudioPlaybackCoordinator.playAudioFile(
                                    file = fileItem.file,
                                    playlist = standaloneFiles,
                                    index = fileIdx
                                )
                            }
                        },
                        onLongPress = {
                            activeActionFilePath = if (activeActionFilePath == fileItem.file.absolutePath) null else fileItem.file.absolutePath
                        },
                        onShare = {
                            try {
                                val shareIntent = FileFusionManager.createShareIntent(context, fileItem.file, "audio/mpeg")
                                context.startActivity(Intent.createChooser(shareIntent, "Share Audio Track"))
                            } catch (e: Exception) {
                                AppLogger.e("AudioStudio", "Failed to share audio file: ${fileItem.file.absolutePath}", e)
                                Toast.makeText(context, "Failed to share audio file", Toast.LENGTH_SHORT).show()
                            }
                        },
                        onDelete = {
                            try {
                                if (playbackProgress.source == PlaybackSource.AUDIO_FILE && playbackProgress.currentParagraphText == fileItem.file.name) {
                                AudioPlaybackCoordinator.stop()
                                }
                                if (fileItem.file.exists() && fileItem.file.delete()) {
                                    Toast.makeText(context, "Track deleted", Toast.LENGTH_SHORT).show()
                                    onRefreshLibrary()
                                } else {
                                    Toast.makeText(context, "Could not delete file", Toast.LENGTH_SHORT).show()
                                }
                            } catch (e: Exception) {
                                AppLogger.e("AudioStudio", "Error deleting file: ${fileItem.file.absolutePath}", e)
                                Toast.makeText(context, "Error deleting file: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                }
            }
        }
    }
}
