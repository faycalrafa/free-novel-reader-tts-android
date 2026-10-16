package com.novelarrow.reader.ui.archive

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import com.novelarrow.reader.ui.common.AppLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.novelarrow.reader.data.model.Chapter
import com.novelarrow.reader.data.model.Novel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArchiveScreen(
    archivedNovels: List<Novel>,
    onRestoreNovel: (Novel) -> Unit,
    onDeleteNovel: (Novel, deleteFromDisk: Boolean) -> Unit,
    onUpdateNovelTitle: (String, String) -> Unit,
    onGetChapters: suspend (String) -> List<Chapter>,
    onDeleteChapter: suspend (novelId: String, chapter: Chapter, deleteFromDisk: Boolean) -> List<Chapter>,
    onNavigateBack: () -> Unit,
    onOpenDrawer: () -> Unit
) {
    var novelToDelete by remember { mutableStateOf<Novel?>(null) }
    var deleteNovelFromDisk by remember { mutableStateOf(false) }

    var novelToRename by remember { mutableStateOf<Novel?>(null) }
    var renameText by remember { mutableStateOf("") }

    var chaptersNovel by remember { mutableStateOf<Novel?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Archived Novels",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onOpenDrawer) {
                        Icon(Icons.Default.Menu, contentDescription = "Library Menu")
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to Reader")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        if (archivedNovels.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        Icons.Default.Archive,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "No Archived Novels",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Novels you archive from the Library Drawer will be kept safely here without deleting files or reading progress.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(archivedNovels, key = { it.id }) { novel ->
                    ArchivedNovelCard(
                        novel = novel,
                        onRestore = { onRestoreNovel(novel) },
                        onRename = {
                            novelToRename = novel
                            renameText = novel.title
                        },
                        onViewChapters = {
                            chaptersNovel = novel
                        },
                        onDelete = {
                            novelToDelete = novel
                            deleteNovelFromDisk = false
                        }
                    )
                }
            }
        }
    }

    // Rename Dialog
    novelToRename?.let { targetNovel ->
        AlertDialog(
            onDismissRequest = { novelToRename = null },
            title = { Text("Rename Novel") },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    label = { Text("Novel Title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val trimmed = renameText.trim()
                        if (trimmed.isNotBlank()) {
                            onUpdateNovelTitle(targetNovel.id, trimmed)
                        }
                        novelToRename = null
                    },
                    enabled = renameText.isNotBlank()
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { novelToRename = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Permanent Delete Confirmation Dialog
    novelToDelete?.let { targetNovel ->
        AlertDialog(
            onDismissRequest = { novelToDelete = null },
            title = { Text("Permanently Delete?") },
            text = {
                Column {
                    Text("Are you sure you want to permanently delete '${targetNovel.title}'?")
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { deleteNovelFromDisk = !deleteNovelFromDisk }
                    ) {
                        Checkbox(
                            checked = deleteNovelFromDisk,
                            onCheckedChange = { deleteNovelFromDisk = it }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Also delete chapter files from storage",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteNovel(targetNovel, deleteNovelFromDisk)
                        novelToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete Permanently")
                }
            },
            dismissButton = {
                TextButton(onClick = { novelToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Archived Chapters Dialog
    chaptersNovel?.let { targetNovel ->
        ArchivedChaptersDialog(
            novel = targetNovel,
            onDismiss = { chaptersNovel = null },
            onGetChapters = onGetChapters,
            onDeleteChapter = { chapter, deleteFromDisk ->
                onDeleteChapter(targetNovel.id, chapter, deleteFromDisk)
            }
        )
    }
}

@Composable
private fun ArchivedNovelCard(
    novel: Novel,
    onRestore: () -> Unit,
    onRename: () -> Unit,
    onViewChapters: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Book,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = novel.title,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${novel.chapterCount} chapters • Progress: ${novel.currentChapterIndex + 1}/${novel.chapterCount}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            Spacer(modifier = Modifier.height(10.dp))

            // Action row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Restore button
                Button(
                    onClick = onRestore,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.Unarchive, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Restore")
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    // Chapters button
                    OutlinedButton(
                        onClick = onViewChapters,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.List, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Chapters")
                    }

                    // Rename button
                    IconButton(onClick = onRename) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = "Rename",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Delete button
                    IconButton(onClick = onDelete) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Delete",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ArchivedChaptersDialog(
    novel: Novel,
    onDismiss: () -> Unit,
    onGetChapters: suspend (String) -> List<Chapter>,
    onDeleteChapter: suspend (Chapter, Boolean) -> List<Chapter>
) {
    val scope = rememberCoroutineScope()
    var chapters by remember { mutableStateOf<List<Chapter>?>(null) }
    var chapterToDelete by remember { mutableStateOf<Chapter?>(null) }
    var deleteChapterFromDisk by remember { mutableStateOf(false) }

    LaunchedEffect(novel.id) {
        chapters = onGetChapters(novel.id)
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 520.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = novel.title,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Archived Chapters (${chapters?.size ?: 0})",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                val currentChapters = chapters
                if (currentChapters == null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        AppLoadingIndicator(modifier = Modifier.size(48.dp))
                    }
                } else if (currentChapters.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "No chapters found.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(modifier = Modifier.weight(1f)) {
                        items(currentChapters, key = { it.id }) { chapter ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${chapter.orderIndex + 1}",
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.width(32.dp)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = chapter.title,
                                        style = MaterialTheme.typography.bodyMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (chapter.isCompleted) {
                                        Text(
                                            text = "Completed",
                                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                                IconButton(
                                    onClick = {
                                        chapterToDelete = chapter
                                        deleteChapterFromDisk = false
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = "Delete chapter",
                                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
                        }
                    }
                }
            }
        }
    }

    chapterToDelete?.let { chapter ->
        AlertDialog(
            onDismissRequest = { chapterToDelete = null },
            title = { Text("Delete Chapter?") },
            text = {
                Column {
                    Text("Delete '${chapter.title}' from this archived novel?")
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { deleteChapterFromDisk = !deleteChapterFromDisk }
                    ) {
                        Checkbox(
                            checked = deleteChapterFromDisk,
                            onCheckedChange = { deleteChapterFromDisk = it }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Also delete file from storage",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val target = chapter
                        chapterToDelete = null
                        scope.launch {
                            val updated = onDeleteChapter(target, deleteChapterFromDisk)
                            chapters = updated
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { chapterToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}
