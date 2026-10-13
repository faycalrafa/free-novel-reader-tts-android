package com.novelarrow.reader.ui.reader

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novelarrow.reader.data.model.Chapter
import com.novelarrow.reader.data.model.Novel
import com.novelarrow.reader.data.model.Paragraph
import com.novelarrow.reader.data.model.ThemeMode
import com.novelarrow.reader.theme.AccentBlue
import com.novelarrow.reader.theme.AccentPurple
import com.novelarrow.reader.theme.DarkHighlight
import com.novelarrow.reader.theme.LightHighlight
import com.novelarrow.reader.theme.SepiaHighlight
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.text.style.TextAlign
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    viewModel: ReaderViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var showSettingsDialog by remember { mutableStateOf(false) }

    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri?.let { viewModel.scanRootFolder(it) }
    }

    LaunchedEffect(uiState.toastMessage) {
        uiState.toastMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearToast()
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = MaterialTheme.colorScheme.surface,
                modifier = Modifier.width(320.dp)
            ) {
                LibraryDrawerContent(
                    novels = uiState.novels,
                    currentNovel = uiState.currentNovel,
                    chapters = uiState.chapters,
                    currentChapter = uiState.currentChapter,
                    onSelectNovel = {
                        viewModel.selectNovel(it)
                        scope.launch { drawerState.close() }
                    },
                    onSelectChapter = { index ->
                        viewModel.selectChapter(index)
                        scope.launch { drawerState.close() }
                    },
                    onDeleteNovel = { viewModel.deleteNovel(it.id) },
                    onAddFolder = { folderPickerLauncher.launch(null) },
                    onCloseDrawer = { scope.launch { drawerState.close() } }
                )
            }
        }
    ) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                ReaderTopBar(
                    title = uiState.currentChapter?.title ?: "NovelArrow Reader Pro",
                    subtitle = uiState.currentNovel?.let { novel ->
                        val chapIdx = (uiState.currentChapter?.orderIndex ?: 0) + 1
                        "${novel.title} • $chapIdx/${uiState.chapters.size}"
                    } ?: "Ready to read",
                    isTtsDockVisible = uiState.isTtsDockVisible,
                    isAutoLoopActive = uiState.isAutoLoopActive,
                    onMenuClick = { scope.launch { drawerState.open() } },
                    onTtsToggleClick = { viewModel.toggleTtsDock() },
                    onLoopClick = { viewModel.toggleAutoLoop() },
                    onSettingsClick = { showSettingsDialog = true }
                )
            },
            floatingActionButton = {
                val hasNext = uiState.currentChapter != null &&
                        (uiState.currentChapter?.orderIndex ?: 0) < uiState.chapters.size - 1
                if (hasNext && !uiState.isTtsDockVisible) {
                    FloatingActionButton(
                        onClick = { viewModel.nextChapter() },
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = Color.White,
                        shape = CircleShape,
                        modifier = Modifier.padding(bottom = 16.dp)
                    ) {
                        Icon(Icons.Default.SkipNext, contentDescription = "Next Chapter")
                    }
                }
            },
            bottomBar = {
                AnimatedVisibility(
                    visible = uiState.isTtsDockVisible,
                    enter = slideInVertically(initialOffsetY = { it }),
                    exit = slideOutVertically(targetOffsetY = { it })
                ) {
                    TtsDock(
                        currentParagraph = uiState.currentParagraphIndex,
                        totalParagraphs = uiState.paragraphs.size,
                        currentChapterIndex = uiState.currentChapter?.orderIndex ?: 0,
                        totalChapters = uiState.chapters.size,
                        chapterTitle = uiState.currentChapter?.title ?: "",
                        isPlaying = uiState.isPlaying,
                        speechRate = uiState.settings.speechRate,
                        onPlayPauseClick = { viewModel.togglePlayPause() },
                        onPrevParaClick = { viewModel.previousParagraph() },
                        onNextParaClick = { viewModel.nextParagraph() },
                        onPrevChapterClick = { viewModel.previousChapter() },
                        onNextChapterClick = { viewModel.nextChapter() },
                        onStopClick = { viewModel.stopTts() },
                        onSpeedClick = { viewModel.cycleSpeechRate() },
                        onSeekParagraph = { paraIdx -> viewModel.speakAtParagraph(paraIdx) }
                    )
                }
            },
            modifier = modifier
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .background(MaterialTheme.colorScheme.background)
            ) {
                if (uiState.currentNovel == null || uiState.paragraphs.isEmpty()) {
                    EmptyState(
                        novels = uiState.novels,
                        onOpenFolder = { folderPickerLauncher.launch(null) },
                        onResumeNovel = { viewModel.selectNovel(it) },
                        onOpenDrawer = { scope.launch { drawerState.open() } }
                    )
                } else {
                    ReaderContentView(
                        paragraphs = uiState.paragraphs,
                        currentIndex = uiState.currentParagraphIndex,
                        isPlaying = uiState.isPlaying,
                        fontSizeSp = uiState.settings.fontSizeSp,
                        themeMode = uiState.settings.theme,
                        autoScroll = uiState.settings.autoScroll,
                        onParagraphClick = { index -> viewModel.speakAtParagraph(index) }
                    )
                }

                if (uiState.isLoading) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.35f)),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }

    if (showSettingsDialog) {
        SettingsDialog(
            settings = uiState.settings,
            voices = uiState.availableVoices,
            onDismiss = { showSettingsDialog = false },
            onThemeChange = { viewModel.updateTheme(it) },
            onFontSizeChange = { viewModel.updateFontSize(it) },
            onSpeechRateChange = { viewModel.updateSpeechRate(it) },
            onSpeechPitchChange = { viewModel.updateSpeechPitch(it) },
            onVoiceChange = { viewModel.updateVoice(it) },
            onAutoScrollChange = { viewModel.updateAutoScroll(it) },
            onAutoNextChange = { viewModel.updateAutoNext(it) },
            onHeaderSkipChange = { viewModel.updateHeaderSkipString(it) },
            onFooterSkipChange = { viewModel.updateFooterSkipString(it) },
            onContentSelectorChange = { viewModel.updateContentSelector(it) },
            onClearAllData = {
                viewModel.clearAllData()
                showSettingsDialog = false
            }
        )
    }
}

@Composable
fun ReaderTopBar(
    title: String,
    subtitle: String,
    isTtsDockVisible: Boolean,
    isAutoLoopActive: Boolean,
    onMenuClick: () -> Unit,
    onTtsToggleClick: () -> Unit,
    onLoopClick: () -> Unit,
    onSettingsClick: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
        shadowElevation = 4.dp
    ) {
        CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 6.dp, vertical = 6.dp)
            ) {
                IconButton(
                    onClick = onMenuClick,
                    modifier = Modifier.size(38.dp)
                ) {
                    Icon(Icons.Default.Menu, contentDescription = "Library Drawer", modifier = Modifier.size(20.dp))
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 4.dp)
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                IconButton(
                    onClick = onTtsToggleClick,
                    modifier = Modifier.size(38.dp)
                ) {
                    Icon(
                        Icons.Default.Headphones,
                        contentDescription = "Toggle TTS Dock",
                        tint = if (isTtsDockVisible) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(20.dp)
                    )
                }

                IconButton(
                    onClick = onLoopClick,
                    modifier = Modifier.size(38.dp)
                ) {
                    Icon(
                        Icons.Default.Repeat,
                        contentDescription = "Toggle Loop",
                        tint = if (isAutoLoopActive) AccentPurple else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        modifier = Modifier.size(20.dp)
                    )
                }

                IconButton(
                    onClick = onSettingsClick,
                    modifier = Modifier.size(38.dp)
                ) {
                    Icon(Icons.Default.Settings, contentDescription = "Settings", modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

@Composable
fun ReaderContentView(
    paragraphs: List<Paragraph>,
    currentIndex: Int,
    isPlaying: Boolean,
    fontSizeSp: Float,
    themeMode: ThemeMode,
    autoScroll: Boolean,
    onParagraphClick: (Int) -> Unit
) {
    val listState = rememberLazyListState()

    LaunchedEffect(currentIndex) {
        if (autoScroll && currentIndex in paragraphs.indices) {
            listState.animateScrollToItem(
                index = currentIndex,
                scrollOffset = -150
            )
        }
    }

    val highlightColor = when (themeMode) {
        ThemeMode.DARK -> DarkHighlight
        ThemeMode.SEPIA -> SepiaHighlight
        ThemeMode.LIGHT -> LightHighlight
    }

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        itemsIndexed(paragraphs, key = { index, _ -> index }) { index, paragraph ->
            val isCurrent = index == currentIndex
            val backgroundColor by animateColorAsState(
                targetValue = if (isCurrent) highlightColor else Color.Transparent,
                label = "highlight_anim"
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(backgroundColor)
                    .clickable { onParagraphClick(index) }
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isCurrent) {
                        Box(
                            modifier = Modifier
                                .width(3.dp)
                                .height(20.dp)
                                .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp))
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                    }

                    Text(
                        text = paragraph.rawText,
                        style = if (paragraph.isHeading) {
                            MaterialTheme.typography.titleMedium.copy(
                                fontSize = (fontSizeSp + 4).sp,
                                fontWeight = FontWeight.Bold,
                                lineHeight = (fontSizeSp + 10).sp
                            )
                        } else {
                            MaterialTheme.typography.bodyLarge.copy(
                                fontSize = fontSizeSp.sp,
                                lineHeight = (fontSizeSp * 1.6f).sp
                            )
                        },
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
            }
        }

        // Bottom space so dock doesn't obscure last lines
        item {
            Spacer(modifier = Modifier.height(140.dp))
        }
    }
}

@Composable
fun TtsDock(
    currentParagraph: Int,
    totalParagraphs: Int,
    currentChapterIndex: Int = 0,
    totalChapters: Int = 0,
    chapterTitle: String,
    isPlaying: Boolean,
    speechRate: Float = 1.0f,
    onPlayPauseClick: () -> Unit,
    onPrevParaClick: () -> Unit,
    onNextParaClick: () -> Unit,
    onPrevChapterClick: () -> Unit,
    onNextChapterClick: () -> Unit,
    onStopClick: () -> Unit,
    onSpeedClick: () -> Unit = {},
    onSeekParagraph: (Int) -> Unit
) {
    var isDragging by remember { mutableStateOf(false) }
    var dragParaIndex by remember(currentParagraph) { mutableFloatStateOf(currentParagraph.toFloat()) }

    val displayPara = if (isDragging) {
        dragParaIndex.toInt().coerceIn(0, (totalParagraphs - 1).coerceAtLeast(0))
    } else currentParagraph

    val progressPercent = if (totalParagraphs > 0) {
        ((displayPara + 1).toFloat() / totalParagraphs.toFloat() * 100).toInt()
    } else 0

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.98f),
        shadowElevation = 8.dp,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            // Slider strictly controls progress within the current chapter
            Slider(
                value = if (isDragging) dragParaIndex else currentParagraph.toFloat().coerceIn(0f, (totalParagraphs - 1).coerceAtLeast(1).toFloat()),
                onValueChange = { newVal ->
                    isDragging = true
                    dragParaIndex = newVal
                },
                onValueChangeFinished = {
                    isDragging = false
                    val target = dragParaIndex.toInt().coerceIn(0, (totalParagraphs - 1).coerceAtLeast(0))
                    onSeekParagraph(target)
                },
                valueRange = 0f .. (totalParagraphs - 1).coerceAtLeast(1).toFloat(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(30.dp),
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                    inactiveTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f)
                )
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Metadata row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f, fill = false)) {
                    Text(
                        text = if (totalChapters > 0) "Ch. ${currentChapterIndex + 1}/$totalChapters • $chapterTitle" else chapterTitle,
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (isDragging) {
                        Text(
                            text = "Release to jump to ¶ ${displayPara + 1} ($progressPercent%)",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Quick speed pill
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                        modifier = Modifier.clickable { onSpeedClick() }
                    ) {
                        Text(
                            text = "${"%.2f".format(speechRate).trimEnd('0').trimEnd('.')}x",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            ),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    Text(
                        text = "¶ ${displayPara + 1} / $totalParagraphs ($progressPercent%)",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Control buttons row: wrap in LocalMinimumInteractiveComponentSize = 0.dp so all buttons fit on small screens
            CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onPrevChapterClick,
                        modifier = Modifier.size(38.dp)
                    ) {
                        Icon(Icons.Default.SkipPrevious, contentDescription = "Prev Chapter", modifier = Modifier.size(22.dp))
                    }
                    IconButton(
                        onClick = onPrevParaClick,
                        modifier = Modifier.size(38.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Prev Paragraph", modifier = Modifier.size(22.dp))
                    }

                    // Play / Pause round button
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    listOf(AccentBlue, AccentPurple)
                                )
                            )
                            .clickable { onPlayPauseClick() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = "Play/Pause",
                            tint = Color.White,
                            modifier = Modifier.size(26.dp)
                        )
                    }

                    IconButton(
                        onClick = onNextParaClick,
                        modifier = Modifier.size(38.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Next Paragraph", modifier = Modifier.size(22.dp))
                    }
                    IconButton(
                        onClick = onNextChapterClick,
                        modifier = Modifier.size(38.dp)
                    ) {
                        Icon(Icons.Default.SkipNext, contentDescription = "Next Chapter", modifier = Modifier.size(22.dp))
                    }
                    IconButton(
                        onClick = onStopClick,
                        modifier = Modifier.size(38.dp)
                    ) {
                        Icon(Icons.Default.Stop, contentDescription = "Stop", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(22.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun EmptyState(
    novels: List<Novel>,
    onOpenFolder: () -> Unit,
    onResumeNovel: (Novel) -> Unit,
    onOpenDrawer: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(88.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(
                    Brush.linearGradient(
                        listOf(AccentBlue, AccentPurple)
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.AutoMirrored.Filled.MenuBook,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(44.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "NovelArrow Reader Pro",
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

            Spacer(modifier = Modifier.height(16.dp))
            val lastNovel = novels.first()
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
                        text = "Chapter ${lastNovel.currentChapterIndex + 1} / ${lastNovel.chapterCount}",
                        style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
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

@Composable
fun LibraryDrawerContent(
    novels: List<Novel>,
    currentNovel: Novel?,
    chapters: List<Chapter>,
    currentChapter: Chapter?,
    onSelectNovel: (Novel) -> Unit,
    onSelectChapter: (Int) -> Unit,
    onDeleteNovel: (Novel) -> Unit,
    onAddFolder: () -> Unit,
    onCloseDrawer: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // Drawer Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Library",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
            Row {
                IconButton(onClick = onAddFolder) {
                    Icon(Icons.Default.Folder, contentDescription = "Add root folder")
                }
                IconButton(onClick = onCloseDrawer) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }
        }

        // Tabs: Library vs Chapters
        TabRow(selectedTabIndex = selectedTab) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text("Library (${novels.size})") }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text("Chapters (${chapters.size})") }
            )
        }

        HorizontalDivider()

        if (selectedTab == 0) {
            // Novels list
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(novels.size) { i ->
                    val novel = novels[i]
                    val isCurrent = novel.id == currentNovel?.id

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectNovel(novel) }
                            .background(if (isCurrent) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else Color.Transparent)
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Book,
                            contentDescription = null,
                            tint = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = novel.title,
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${novel.chapterCount} chapters • ${novel.currentChapterIndex + 1}/${novel.chapterCount}",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                        IconButton(onClick = { onDeleteNovel(novel) }) {
                            Icon(Icons.Default.Delete, contentDescription = "Remove", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
                }
            }
        } else {
            // Chapters list
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(chapters.size) { i ->
                    val chapter = chapters[i]
                    val isCurrent = chapter.id == currentChapter?.id

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectChapter(i) }
                            .background(if (isCurrent) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else Color.Transparent)
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${i + 1}",
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.width(32.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = chapter.title,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (chapter.isCompleted) {
                                Text(
                                    text = "Done ✓",
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp, color = MaterialTheme.colorScheme.primary)
                                )
                            }
                        }
                        if (chapter.isCompleted) {
                            Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsDialog(
    settings: com.novelarrow.reader.data.model.ReaderSettings,
    voices: List<android.speech.tts.Voice>,
    onDismiss: () -> Unit,
    onThemeChange: (ThemeMode) -> Unit,
    onFontSizeChange: (Float) -> Unit,
    onSpeechRateChange: (Float) -> Unit,
    onSpeechPitchChange: (Float) -> Unit,
    onVoiceChange: (String?) -> Unit,
    onAutoScrollChange: (Boolean) -> Unit,
    onAutoNextChange: (Boolean) -> Unit,
    onHeaderSkipChange: (String) -> Unit,
    onFooterSkipChange: (String) -> Unit,
    onContentSelectorChange: (String) -> Unit,
    onClearAllData: () -> Unit
) {
    var voiceExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Settings") },
        text = {
            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                item {
                    // Theme selector: each button weighted equally so they never overflow
                    Text("Reading Theme", style = MaterialTheme.typography.titleSmall)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(
                            ThemeMode.LIGHT to "☀️ Light",
                            ThemeMode.SEPIA to "📜 Sepia",
                            ThemeMode.DARK to "🌙 Dark"
                        ).forEach { (mode, label) ->
                            val isSelected = settings.theme == mode
                            Button(
                                onClick = { onThemeChange(mode) },
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                    contentColor = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface
                                ),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(label, fontSize = 11.sp, maxLines = 1, softWrap = false)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Font size slider
                    Text("Font Size: ${settings.fontSizeSp.toInt()} sp", style = MaterialTheme.typography.titleSmall)
                    Slider(
                        value = settings.fontSizeSp,
                        onValueChange = onFontSizeChange,
                        valueRange = 12f..28f,
                        steps = 15
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Speech Rate Slider (accounts for 0.5x, 0.75x, 1.0x, etc.)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Speech Speed: ${"%.2f".format(settings.speechRate)}x", style = MaterialTheme.typography.titleSmall)
                        Text(
                            text = if (settings.speechRate <= 0.52f) "0.5x (Half speed)" else if (settings.speechRate >= 1.98f) "2.0x (Double)" else "",
                            style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        )
                    }

                    Slider(
                        value = settings.speechRate,
                        onValueChange = { newVal ->
                            val stepped = (kotlin.math.round(newVal * 20f) / 20f).coerceIn(0.5f, 2.5f)
                            onSpeechRateChange(stepped)
                        },
                        valueRange = 0.5f..2.5f,
                        steps = 39
                    )

                    // Quick Speed Preset Chips (accounts for 0.5x directly)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f).forEach { rate ->
                            val isSelected = kotlin.math.abs(settings.speechRate - rate) < 0.03f
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { onSpeechRateChange(rate) }
                            ) {
                                Text(
                                    text = "${rate}x",
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(vertical = 6.dp),
                                    textAlign = TextAlign.Center,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Speech Pitch
                    Text("Speech Pitch: ${"%.2f".format(settings.speechPitch)}x", style = MaterialTheme.typography.titleSmall)
                    Slider(
                        value = settings.speechPitch,
                        onValueChange = onSpeechPitchChange,
                        valueRange = 0.5f..1.5f,
                        steps = 9
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Voice Picker Dropdown
                    Text("Voice", style = MaterialTheme.typography.titleSmall)
                    ExposedDropdownMenuBox(
                        expanded = voiceExpanded,
                        onExpandedChange = { voiceExpanded = !voiceExpanded }
                    ) {
                        OutlinedTextField(
                            value = settings.selectedVoiceName ?: "Default Device Voice",
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = voiceExpanded) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor()
                        )
                        ExposedDropdownMenu(
                            expanded = voiceExpanded,
                            onDismissRequest = { voiceExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Default Device Voice") },
                                onClick = {
                                    onVoiceChange(null)
                                    voiceExpanded = false
                                }
                            )
                            voices.forEach { voice ->
                                DropdownMenuItem(
                                    text = { Text("${voice.name} (${voice.locale.displayName})") },
                                    onClick = {
                                        onVoiceChange(voice.name)
                                        voiceExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Auto-scroll toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Auto-scroll to follow speech", style = MaterialTheme.typography.bodyMedium)
                        Switch(
                            checked = settings.autoScroll,
                            onCheckedChange = onAutoScrollChange
                        )
                    }

                    // Auto-next chapter toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Auto-advance next chapter", style = MaterialTheme.typography.bodyMedium)
                        Switch(
                            checked = settings.autoNextChapter,
                            onCheckedChange = onAutoNextChange
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                    Spacer(modifier = Modifier.height(14.dp))

                    // Header & Footer Skip Filters
                    Text("Content & Skip Filters", style = MaterialTheme.typography.titleSmall)
                    Spacer(modifier = Modifier.height(6.dp))

                    OutlinedTextField(
                        value = settings.headerSkipString,
                        onValueChange = onHeaderSkipChange,
                        label = { Text("Header String Skip") },
                        placeholder = { Text("e.g. Chapter 1 or START") },
                        supportingText = { Text("Starts reading chapter from this string (skips preceding text)", fontSize = 10.sp) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = settings.footerSkipString,
                        onValueChange = onFooterSkipChange,
                        label = { Text("Footer String Skip") },
                        placeholder = { Text("e.g. Next Chapter or Comments") },
                        supportingText = { Text("Ends reading chapter at this string (skips following text)", fontSize = 10.sp) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = settings.contentSelector,
                        onValueChange = onContentSelectorChange,
                        label = { Text("HTML Content Selector (Optional)") },
                        placeholder = { Text("e.g. #content, .chapter-text") },
                        supportingText = { Text("CSS selector for main story container (auto-detected if empty)", fontSize = 10.sp) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    // Clear all button
                    OutlinedButton(
                        onClick = onClearAllData,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Clear All Library & Progress")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}
