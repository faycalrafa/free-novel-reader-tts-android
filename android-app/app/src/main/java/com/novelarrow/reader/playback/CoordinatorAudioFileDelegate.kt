package com.novelarrow.reader.playback

import com.novelarrow.reader.data.util.NaturalOrderComparator
import com.novelarrow.reader.util.AppLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

internal class CoordinatorAudioFileDelegate(
    private val scope: CoroutineScope,
    private val serviceProvider: () -> NovelAudioPlaybackService?,
    private val isPlayingProvider: () -> Boolean,
    private val onPlaybackStarted: (file: File, index: Int, playlist: List<File>) -> Unit,
    private val onPlaylistFinished: () -> Unit,
    private val progressUpdater: (positionMs: Long, durationMs: Long) -> Unit
) {

    private val TAG = "CoordinatorAudioFileDelegate"

    var currentAudioFile: File? = null
        private set
    var currentPlaylist: List<File> = emptyList()
        private set
    private var audioFileProgressJob: Job? = null

    fun isSupportedAudioFile(name: String?): Boolean {
        if (name == null || name.startsWith(".")) return false
        val ext = name.substringAfterLast('.', "").lowercase()
        return ext in setOf("mp3", "wav", "m4a", "aac", "ogg", "flac")
    }

    fun playAudioFile(file: File, playlist: List<File> = listOf(file), index: Int = 0) {
        val effectivePlaylist = if (playlist.size > 1) {
            playlist
        } else {
            val siblings = file.parentFile?.listFiles { f -> f.isFile && f.length() > 0 && f.extension.equals(file.extension, ignoreCase = true) }
                ?.sortedWith { a, b -> NaturalOrderComparator.compare(a.name, b.name) }
            if (!siblings.isNullOrEmpty()) siblings else listOf(file)
        }

        currentPlaylist = effectivePlaylist
        val resolvedIndex = if (playlist.size > 1) {
            index.coerceIn(0, currentPlaylist.size - 1)
        } else {
            currentPlaylist.indexOfFirst { it.canonicalPath == file.canonicalPath }.coerceAtLeast(0)
        }

        val activeFile = currentPlaylist.getOrNull(resolvedIndex) ?: file
        currentAudioFile = activeFile

        onPlaybackStarted(activeFile, resolvedIndex, currentPlaylist)

        val service = serviceProvider()
        if (service != null) {
            service.playAudioFile(activeFile)
        }

        startAudioFileProgressTicker()
        AppLogger.i(TAG, "playAudioFile: Playing '${activeFile.name}' (track ${resolvedIndex + 1}/${currentPlaylist.size})")
    }

    fun playAudioFolder(folder: File) {
        if (!folder.exists() || !folder.isDirectory) {
            AppLogger.w(TAG, "playAudioFolder: Directory does not exist or is not a directory: ${folder.absolutePath}")
            return
        }
        val mp3Files = folder.listFiles { f -> f.isFile && f.length() > 0 && f.extension.equals("mp3", ignoreCase = true) }
            ?.sortedWith { a, b -> NaturalOrderComparator.compare(a.name, b.name) }
            ?.toList() ?: emptyList()

        if (mp3Files.isNotEmpty()) {
            playAudioFile(file = mp3Files.first(), playlist = mp3Files, index = 0)
        } else {
            AppLogger.w(TAG, "playAudioFolder: No MP3 files found in folder: ${folder.absolutePath}")
        }
    }

    fun onAudioFileCompleted() {
        val activeFile = currentAudioFile ?: return
        val currentIndex = currentPlaylist.indexOfFirst { it.canonicalPath == activeFile.canonicalPath }
        val nextIndex = currentIndex + 1
        if (nextIndex in currentPlaylist.indices) {
            AppLogger.i(TAG, "Track completed, advancing to track ${nextIndex + 1}/${currentPlaylist.size}")
            playAudioFile(currentPlaylist[nextIndex], currentPlaylist, nextIndex)
        } else {
            AppLogger.i(TAG, "Playlist finished in audio folder, playing folder done sound cue")
            AudioCueManager.playFolderDone()
            onPlaylistFinished()
        }
    }

    fun seekAudioFile(positionMs: Long) {
        serviceProvider()?.seekAudioFile(positionMs)
    }

    fun startAudioFileProgressTicker() {
        audioFileProgressJob?.cancel()
        if (serviceProvider() == null) return
        audioFileProgressJob = scope.launch(Dispatchers.Main) {
            while (isPlayingProvider()) {
                val service = serviceProvider()
                if (service != null) {
                    val pos = withContext(Dispatchers.Main) { service.getAudioFilePositionMs() }
                    val dur = withContext(Dispatchers.Main) { service.getAudioFileDurationMs() }
                    progressUpdater(pos, dur)
                }
                delay(500L)
            }
        }
    }

    fun stopTicker() {
        audioFileProgressJob?.cancel()
        audioFileProgressJob = null
    }
}
