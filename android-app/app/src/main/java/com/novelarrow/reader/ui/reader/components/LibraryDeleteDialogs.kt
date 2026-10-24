package com.novelarrow.reader.ui.reader.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.novelarrow.reader.data.model.Chapter
import com.novelarrow.reader.data.model.Novel

@Composable
internal fun NovelDeleteDialog(
    novel: Novel,
    onConfirm: (deleteFromDisk: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    var deleteFilesFromDisk by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Remove Folder",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Are you sure you want to remove \"${novel.title}\" from the library?",
                    style = MaterialTheme.typography.bodyMedium
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { deleteFilesFromDisk = !deleteFilesFromDisk }
                        .padding(vertical = 4.dp)
                ) {
                    Checkbox(
                        checked = deleteFilesFromDisk,
                        onCheckedChange = { deleteFilesFromDisk = it }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Delete files in memory as well",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (deleteFilesFromDisk) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(deleteFilesFromDisk) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (deleteFilesFromDisk) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                )
            ) {
                Text("Delete")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
internal fun ChapterDeleteDialog(
    chapter: Chapter,
    onConfirm: (deleteFromDisk: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    var deleteChapterFromDisk by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Delete Chapter",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Are you sure you want to remove \"${chapter.title}\" from this playlist?",
                    style = MaterialTheme.typography.bodyMedium
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { deleteChapterFromDisk = !deleteChapterFromDisk }
                        .padding(vertical = 4.dp)
                ) {
                    Checkbox(
                        checked = deleteChapterFromDisk,
                        onCheckedChange = { deleteChapterFromDisk = it }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Delete file from device storage as well",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (deleteChapterFromDisk) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(deleteChapterFromDisk) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (deleteChapterFromDisk) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                )
            ) {
                Text("Delete")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
