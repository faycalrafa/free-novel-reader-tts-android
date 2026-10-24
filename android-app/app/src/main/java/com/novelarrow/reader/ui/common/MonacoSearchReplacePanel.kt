package com.novelarrow.reader.ui.common

import android.webkit.WebView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONObject

@Composable
internal fun MonacoSearchReplacePanel(
    webViewRef: WebView?,
    isReplaceOpen: Boolean,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    replaceQuery: String,
    onReplaceQueryChange: (String) -> Unit,
    matchCase: Boolean,
    onMatchCaseChange: (Boolean) -> Unit,
    searchCurrentIndex: Int,
    searchTotalCount: Int,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFF2A2D2E))
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        // Search Row
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { newQuery ->
                    onSearchQueryChange(newQuery)
                    val safeQ = JSONObject.quote(newQuery)
                    webViewRef?.evaluateJavascript("window.performFind($safeQ, true, $matchCase);", null)
                },
                placeholder = {
                    Text("Find in code...", fontSize = 13.sp, color = Color(0xFF888888))
                },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, color = Color.White),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = Color(0xFF444444),
                    focusedContainerColor = Color(0xFF1E1E1E),
                    unfocusedContainerColor = Color(0xFF1E1E1E)
                ),
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
            )

            Spacer(modifier = Modifier.width(6.dp))

            // Match Count Badge
            Text(
                text = if (searchQuery.isBlank()) "0/0" else "$searchCurrentIndex/$searchTotalCount",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, color = Color(0xFFCCCCCC)),
                modifier = Modifier.padding(horizontal = 4.dp)
            )

            // Match Case Toggle
            FilledIconToggleButton(
                checked = matchCase,
                onCheckedChange = {
                    onMatchCaseChange(it)
                    val safeQ = JSONObject.quote(searchQuery)
                    webViewRef?.evaluateJavascript("window.performFind($safeQ, true, $it);", null)
                },
                colors = IconButtonDefaults.filledIconToggleButtonColors(
                    containerColor = Color.Transparent,
                    checkedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                    contentColor = Color(0xFFCCCCCC),
                    checkedContentColor = MaterialTheme.colorScheme.primary
                ),
                modifier = Modifier.size(32.dp)
            ) {
                Text("Aa", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }

            // Prev Match Button
            IconButton(
                onClick = {
                    val safeQ = JSONObject.quote(searchQuery)
                    webViewRef?.evaluateJavascript("window.performFind($safeQ, false, $matchCase);", null)
                },
                enabled = searchQuery.isNotBlank(),
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    Icons.Default.ChevronLeft,
                    contentDescription = "Previous Match",
                    tint = if (searchQuery.isNotBlank()) Color.White else Color(0xFF666666),
                    modifier = Modifier.size(18.dp)
                )
            }

            // Next Match Button
            IconButton(
                onClick = {
                    val safeQ = JSONObject.quote(searchQuery)
                    webViewRef?.evaluateJavascript("window.performFind($safeQ, true, $matchCase);", null)
                },
                enabled = searchQuery.isNotBlank(),
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    Icons.Default.ChevronRight,
                    contentDescription = "Next Match",
                    tint = if (searchQuery.isNotBlank()) Color.White else Color(0xFF666666),
                    modifier = Modifier.size(18.dp)
                )
            }

            // Close Search Bar
            IconButton(
                onClick = onClose,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Close Search",
                    tint = Color(0xFF888888),
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        // Replace Row (shown when isReplaceOpen is true)
        if (isReplaceOpen) {
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = replaceQuery,
                    onValueChange = onReplaceQueryChange,
                    placeholder = {
                        Text("Replace with...", fontSize = 13.sp, color = Color(0xFF888888))
                    },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, color = Color.White),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = Color(0xFF444444),
                        focusedContainerColor = Color(0xFF1E1E1E),
                        unfocusedContainerColor = Color(0xFF1E1E1E)
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                )

                Spacer(modifier = Modifier.width(6.dp))

                // Replace Single
                Button(
                    onClick = {
                        val safeR = JSONObject.quote(replaceQuery)
                        val safeQ = JSONObject.quote(searchQuery)
                        webViewRef?.evaluateJavascript("window.performReplace($safeR); window.performFind($safeQ, true, $matchCase);", null)
                    },
                    enabled = searchQuery.isNotBlank(),
                    shape = RoundedCornerShape(6.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        disabledContainerColor = Color(0xFF3A3D3E)
                    ),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    modifier = Modifier.height(34.dp)
                ) {
                    Text("Replace", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }

                Spacer(modifier = Modifier.width(4.dp))

                // Replace All
                FilledTonalButton(
                    onClick = {
                        val safeQ = JSONObject.quote(searchQuery)
                        val safeR = JSONObject.quote(replaceQuery)
                        webViewRef?.evaluateJavascript("window.performReplaceAll($safeQ, $safeR, $matchCase);", null)
                    },
                    enabled = searchQuery.isNotBlank(),
                    shape = RoundedCornerShape(6.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    modifier = Modifier.height(34.dp)
                ) {
                    Text("All", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
