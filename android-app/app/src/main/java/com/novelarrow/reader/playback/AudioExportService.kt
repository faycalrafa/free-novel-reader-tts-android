package com.novelarrow.reader.playback

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.novelarrow.reader.MainActivity
import com.novelarrow.reader.R
import com.novelarrow.reader.data.export.AudioExportEngine
import com.novelarrow.reader.data.export.FileFusionManager
import com.novelarrow.reader.data.model.Chapter
import com.novelarrow.reader.data.model.Novel
import com.novelarrow.reader.data.repository.LibraryRepository
import com.novelarrow.reader.data.repository.SettingsRepository
import com.novelarrow.reader.util.AppLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

/**
 * Foreground service that runs audio export, text-to-MP3 synthesis, and file fusion/zipping
 * in the background with persistent progress notifications, cancellation, and share actions.
 */
class AudioExportService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var activeJob: Job? = null
    @Volatile private var isCancelled = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: return START_NOT_STICKY

        when (action) {
            ACTION_CANCEL -> {
                AppLogger.i(TAG, "Cancel action received")
                isCancelled = true
                activeJob?.cancel()
                _exportProgressState.value = ExportState.Idle
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
            ACTION_EXPORT_NOVEL -> {
                val novelId = intent.getStringExtra(EXTRA_NOVEL_ID) ?: return START_NOT_STICKY
                val startChap = intent.getIntExtra(EXTRA_START_CHAPTER, 0)
                val endChap = intent.getIntExtra(EXTRA_END_CHAPTER, Int.MAX_VALUE)
                val splitModeStr = intent.getStringExtra(EXTRA_SPLIT_MODE) ?: AudioExportEngine.SplitMode.BY_CHAPTER.name
                val splitMode = try { AudioExportEngine.SplitMode.valueOf(splitModeStr) } catch (_: Exception) { AudioExportEngine.SplitMode.BY_CHAPTER }
                val maxMinutes = intent.getIntExtra(EXTRA_MAX_MINUTES, 30)
                val speed = intent.getFloatExtra(EXTRA_SPEED, 1.0f)

                startNovelExport(novelId, startChap, endChap, splitMode, maxMinutes, speed)
            }
            ACTION_EXPORT_TEXT -> {
                val title = intent.getStringExtra(EXTRA_TITLE) ?: "Shared_Text"
                val text = intent.getStringExtra(EXTRA_TEXT) ?: return START_NOT_STICKY
                val speed = intent.getFloatExtra(EXTRA_SPEED, 1.0f)

                startTextExport(title, text, speed)
            }
            ACTION_FUSE_NOVEL -> {
                val novelId = intent.getStringExtra(EXTRA_NOVEL_ID) ?: return START_NOT_STICKY
                startNovelFusion(novelId)
            }
            ACTION_ZIP_NOVEL -> {
                val novelId = intent.getStringExtra(EXTRA_NOVEL_ID) ?: return START_NOT_STICKY
                startNovelZip(novelId)
            }
        }

        return START_NOT_STICKY
    }

    private fun startNovelExport(
        novelId: String,
        startChap: Int,
        endChap: Int,
        splitMode: AudioExportEngine.SplitMode,
        maxMinutes: Int,
        speed: Float
    ) {
        isCancelled = false
        val initialNotification = buildNotification("Preparing export...", 0)
        startForeground(NOTIFICATION_ID, initialNotification)

        activeJob = serviceScope.launch {
            val libRepo = LibraryRepository(applicationContext)
            val settingsRepo = SettingsRepository(applicationContext)
            val novels = libRepo.getNovels()
            val novel = novels.firstOrNull { it.id == novelId } ?: return@launch
            val allChapters = libRepo.getChapters(novel.id)
            val subChapters = allChapters.filterIndexed { idx, _ -> idx in startChap..endChap }

            if (subChapters.isEmpty()) {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return@launch
            }

            _exportProgressState.value = ExportState.Running(
                title = novel.title,
                percent = 0,
                statusText = "Starting audio synthesis (${subChapters.size} chapters)..."
            )

            val request = AudioExportEngine.ExportRequest(
                novel = novel,
                chapters = subChapters,
                splitMode = splitMode,
                maxMinutesPerFile = maxMinutes,
                speechRate = speed
            )

            val result = AudioExportEngine.exportToMp3(
                context = applicationContext,
                request = request,
                libraryRepository = libRepo,
                settingsRepository = settingsRepo,
                onProgress = { current, total, pct, text ->
                    _exportProgressState.value = ExportState.Running(
                        title = novel.title,
                        percent = pct,
                        statusText = text
                    )
                    updateNotification("Audifying: ${novel.title}", pct, text)
                },
                isCancelled = { isCancelled }
            )

            if (!result.isCancelled && result.outputFiles.isNotEmpty()) {
                _exportProgressState.value = ExportState.Success(result.outputFiles)
                showCompletionNotification(
                    title = "Audifying Complete!",
                    content = "${result.outputFiles.size} MP3 file(s) saved to Downloads/BookReader",
                    fileToShare = result.playlistFile ?: result.outputFiles.first(),
                    mimeType = if (result.playlistFile != null) "audio/x-mpegurl" else "audio/mpeg"
                )
            } else {
                _exportProgressState.value = ExportState.Idle
                stopForeground(STOP_FOREGROUND_REMOVE)
            }
            stopSelf()
        }
    }

    private fun startTextExport(title: String, text: String, speed: Float) {
        isCancelled = false
        val initialNotification = buildNotification("Audifying text...", 0)
        startForeground(NOTIFICATION_ID, initialNotification)

        activeJob = serviceScope.launch {
            _exportProgressState.value = ExportState.Running(
                title = title,
                percent = 0,
                statusText = "Synthesizing text to MP3..."
            )

            val file = AudioExportEngine.synthesizeTextToMp3(
                context = applicationContext,
                title = title,
                text = text,
                speechRate = speed,
                onProgress = { pct, statusText ->
                    _exportProgressState.value = ExportState.Running(
                        title = title,
                        percent = pct,
                        statusText = statusText
                    )
                    updateNotification("Audifying: $title", pct, statusText)
                }
            )

            if (file != null && file.exists()) {
                _exportProgressState.value = ExportState.Success(listOf(file))
                showCompletionNotification(
                    title = "Text MP3 Created!",
                    content = "${file.name} saved to Downloads/BookReader",
                    fileToShare = file,
                    mimeType = "audio/mpeg"
                )
            } else {
                _exportProgressState.value = ExportState.Idle
                stopForeground(STOP_FOREGROUND_REMOVE)
            }
            stopSelf()
        }
    }

    private fun startNovelFusion(novelId: String) {
        isCancelled = false
        val initialNotification = buildNotification("Fusing chapters...", 0)
        startForeground(NOTIFICATION_ID, initialNotification)

        activeJob = serviceScope.launch {
            val libRepo = LibraryRepository(applicationContext)
            val novel = libRepo.getNovels().firstOrNull { it.id == novelId } ?: return@launch
            val chapters = libRepo.getChapters(novel.id)

            _exportProgressState.value = ExportState.Running(
                title = novel.title,
                percent = 0,
                statusText = "Fusing ${chapters.size} chapters..."
            )

            val fused = FileFusionManager.fuseChapters(
                context = applicationContext,
                novel = novel,
                chapters = chapters,
                libraryRepository = libRepo,
                onProgress = { cur, tot ->
                    val pct = ((cur.toFloat() / tot.toFloat()) * 100).toInt()
                    _exportProgressState.value = ExportState.Running(
                        title = novel.title,
                        percent = pct,
                        statusText = "Fusing Chapter $cur/$tot"
                    )
                    updateNotification("Fusing ${novel.title}", pct, "Chapter $cur of $tot")
                }
            )

            _exportProgressState.value = ExportState.Success(listOf(fused))
            val mime = when {
                fused.name.endsWith(".html") -> "text/html"
                fused.name.endsWith(".md") -> "text/markdown"
                else -> "text/plain"
            }
            showCompletionNotification(
                title = "Fusion Complete!",
                content = "${fused.name} ready to share",
                fileToShare = fused,
                mimeType = mime
            )
            stopSelf()
        }
    }

    private fun startNovelZip(novelId: String) {
        isCancelled = false
        val initialNotification = buildNotification("Zipping novel...", 0)
        startForeground(NOTIFICATION_ID, initialNotification)

        activeJob = serviceScope.launch {
            val libRepo = LibraryRepository(applicationContext)
            val novel = libRepo.getNovels().firstOrNull { it.id == novelId } ?: return@launch
            val chapters = libRepo.getChapters(novel.id)

            _exportProgressState.value = ExportState.Running(
                title = novel.title,
                percent = 0,
                statusText = "Zipping ${chapters.size} chapters..."
            )

            val zipFile = FileFusionManager.zipNovelFolder(
                context = applicationContext,
                novel = novel,
                chapters = chapters,
                libraryRepository = libRepo,
                onProgress = { cur, tot ->
                    val pct = ((cur.toFloat() / tot.toFloat()) * 100).toInt()
                    _exportProgressState.value = ExportState.Running(
                        title = novel.title,
                        percent = pct,
                        statusText = "Compressing Chapter $cur/$tot"
                    )
                    updateNotification("Zipping ${novel.title}", pct, "Compressing $cur of $tot")
                }
            )

            _exportProgressState.value = ExportState.Success(listOf(zipFile))
            showCompletionNotification(
                title = "Zip Complete!",
                content = "${zipFile.name} ready to share",
                fileToShare = zipFile,
                mimeType = "application/zip"
            )
            stopSelf()
        }
    }

    private fun buildNotification(title: String, percent: Int, text: String = ""): android.app.Notification {
        val cancelIntent = Intent(this, AudioExportService::class.java).apply {
            action = ACTION_CANCEL
        }
        val cancelPending = PendingIntent.getService(
            this,
            201,
            cancelIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val openIntent = Intent(this, MainActivity::class.java)
        val openPending = PendingIntent.getActivity(
            this,
            202,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_toolbar)
            .setContentTitle(title)
            .setContentText(text.ifBlank { "Export in progress" })
            .setProgress(100, percent, percent == 0)
            .setOngoing(true)
            .setContentIntent(openPending)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Cancel", cancelPending)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateNotification(title: String, percent: Int, text: String) {
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIFICATION_ID, buildNotification(title, percent, text))
    }

    private fun showCompletionNotification(title: String, content: String, fileToShare: File, mimeType: String) {
        stopForeground(STOP_FOREGROUND_REMOVE)

        val shareIntent = FileFusionManager.createShareIntent(this, fileToShare, mimeType)
        val shareChooser = Intent.createChooser(shareIntent, "Share exported file").apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        val sharePending = PendingIntent.getActivity(
            this,
            203,
            shareChooser,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val completeNotification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_toolbar)
            .setContentTitle(title)
            .setContentText(content)
            .setAutoCancel(true)
            .setContentIntent(sharePending)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIFICATION_ID + 1, completeNotification)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "BookReader Export & Audio Studio",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows progress of MP3 creation, file fusion, and folder exports"
                setShowBadge(false)
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    sealed class ExportState {
        object Idle : ExportState()
        data class Running(val title: String, val percent: Int, val statusText: String) : ExportState()
        data class Success(val files: List<File>) : ExportState()
    }

    companion object {
        private const val TAG = "AudioExportService"
        private const val CHANNEL_ID = "bookreader_export_channel"
        private const val NOTIFICATION_ID = 3001

        const val ACTION_CANCEL = "com.novelarrow.reader.export.ACTION_CANCEL"
        const val ACTION_EXPORT_NOVEL = "com.novelarrow.reader.export.ACTION_EXPORT_NOVEL"
        const val ACTION_EXPORT_TEXT = "com.novelarrow.reader.export.ACTION_EXPORT_TEXT"
        const val ACTION_FUSE_NOVEL = "com.novelarrow.reader.export.ACTION_FUSE_NOVEL"
        const val ACTION_ZIP_NOVEL = "com.novelarrow.reader.export.ACTION_ZIP_NOVEL"

        const val EXTRA_NOVEL_ID = "novel_id"
        const val EXTRA_START_CHAPTER = "start_chapter"
        const val EXTRA_END_CHAPTER = "end_chapter"
        const val EXTRA_SPLIT_MODE = "split_mode"
        const val EXTRA_MAX_MINUTES = "max_minutes"
        const val EXTRA_SPEED = "speed"
        const val EXTRA_TITLE = "title"
        const val EXTRA_TEXT = "text"

        private val _exportProgressState = MutableStateFlow<ExportState>(ExportState.Idle)
        val exportProgressState: StateFlow<ExportState> = _exportProgressState.asStateFlow()

        fun cancelExport(context: Context) {
            val intent = Intent(context, AudioExportService::class.java).apply {
                action = ACTION_CANCEL
            }
            context.startService(intent)
        }

        fun startExportNovel(
            context: Context,
            novelId: String,
            startChap: Int = 0,
            endChap: Int = Int.MAX_VALUE,
            splitMode: AudioExportEngine.SplitMode = AudioExportEngine.SplitMode.BY_CHAPTER,
            maxMinutes: Int = 30,
            speed: Float = 1.0f
        ) {
            val intent = Intent(context, AudioExportService::class.java).apply {
                action = ACTION_EXPORT_NOVEL
                putExtra(EXTRA_NOVEL_ID, novelId)
                putExtra(EXTRA_START_CHAPTER, startChap)
                putExtra(EXTRA_END_CHAPTER, endChap)
                putExtra(EXTRA_SPLIT_MODE, splitMode.name)
                putExtra(EXTRA_MAX_MINUTES, maxMinutes)
                putExtra(EXTRA_SPEED, speed)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun startExportText(context: Context, title: String, text: String, speed: Float = 1.0f) {
            val intent = Intent(context, AudioExportService::class.java).apply {
                action = ACTION_EXPORT_TEXT
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_TEXT, text)
                putExtra(EXTRA_SPEED, speed)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun startFuseNovel(context: Context, novelId: String) {
            val intent = Intent(context, AudioExportService::class.java).apply {
                action = ACTION_FUSE_NOVEL
                putExtra(EXTRA_NOVEL_ID, novelId)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun startZipNovel(context: Context, novelId: String) {
            val intent = Intent(context, AudioExportService::class.java).apply {
                action = ACTION_ZIP_NOVEL
                putExtra(EXTRA_NOVEL_ID, novelId)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }
}
