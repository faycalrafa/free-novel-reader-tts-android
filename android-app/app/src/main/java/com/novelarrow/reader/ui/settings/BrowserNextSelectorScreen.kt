package com.novelarrow.reader.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun BrowserNextSelectorScreen(
    currentSelector: String,
    onSaveSelector: (String) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectorText by remember(currentSelector) { mutableStateOf(currentSelector) }
    var hasUnsavedChanges by remember(selectorText, currentSelector) {
        mutableStateOf(selectorText.trim() != currentSelector.trim())
    }

    val presets = remember {
        listOf(
            "button[title*=\"Next Chapter\"], a[title*=\"Next Chapter\"]",
            "button[title*=\"Next chapter\"], a[title*=\"Next chapter\"]",
            "#next_chap, .next-chap, .next-chapter",
            ".btn-next, a.next, a[rel=\"next\"]",
            ".chapter-next a, #next-chapter, .nav-next a",
            ".nav-links .nav-next a, .next a"
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Next Chapter Selector",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            val defaultSelector = "button[title*=\"Next Chapter\"], a[title*=\"Next Chapter\"], button[title*=\"Next chapter\"], a[title*=\"Next chapter\"], button[title=\"Next\"], a[title=\"Next\"], #next_chap, .next-chap, .next-chapter, .btn-next, a.next, a[rel=\"next\"], .chapter-next a, #next-chapter, .nav-next a"
                            selectorText = defaultSelector
                            onSaveSelector(defaultSelector)
                        }
                    ) {
                        Icon(Icons.Default.RestartAlt, contentDescription = "Reset to Defaults")
                    }
                }
            )
        },
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(modifier = Modifier.height(4.dp))

            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "This CSS selector is used by live reading and batch scraping to find and trigger the next chapter on novel websites. If left blank, automatic button detection is used.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            // Text input for CSS selector
            OutlinedTextField(
                value = selectorText,
                onValueChange = { selectorText = it },
                label = { Text("Active Next Chapter CSS Selector") },
                placeholder = { Text("e.g. #next_chap, .next-chap, a.next") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                maxLines = 6,
                shape = RoundedCornerShape(12.dp),
                textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 13.sp)
            )

            // Save Button
            Button(
                onClick = {
                    onSaveSelector(selectorText.trim())
                    hasUnsavedChanges = false
                },
                enabled = hasUnsavedChanges,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(if (hasUnsavedChanges) "Save Next Chapter Selector" else "Saved")
            }

            HorizontalDivider()

            // Presets
            Text(
                text = "Popular Site Presets",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
            )
            Text(
                text = "Tap a preset below to insert it into the selector configuration:",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            presets.forEach { preset ->
                OutlinedCard(
                    onClick = {
                        selectorText = preset
                        onSaveSelector(preset)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = preset,
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
