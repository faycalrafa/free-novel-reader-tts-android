package com.novelarrow.reader.ui.settings

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novelarrow.reader.data.model.HardwareButtonAction
import com.novelarrow.reader.data.model.HardwareButtonScreenCondition
import com.novelarrow.reader.data.model.HardwareButtonSettings

private enum class EditingButtonSlot {
    VOL_UP_SINGLE,
    VOL_UP_DOUBLE,
    VOL_UP_LONG,
    VOL_DOWN_SINGLE,
    VOL_DOWN_DOUBLE,
    VOL_DOWN_LONG
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HardwareButtonsScreen(
    settings: HardwareButtonSettings,
    onUpdateSettings: (HardwareButtonSettings) -> Unit,
    onNavigateBack: () -> Unit,
    onOpenDrawer: () -> Unit
) {
    var editingSlot by remember { mutableStateOf<EditingButtonSlot?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Hardware Buttons",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )
                },
                navigationIcon = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onOpenDrawer) {
                            Icon(Icons.Default.Menu, contentDescription = "Library Menu")
                        }
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to Settings")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Master Enable Card
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (settings.enabled)
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                    else
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Enable Hardware Button Mapping",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Control TTS reading gestures with physical Volume Up and Volume Down buttons",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Switch(
                        checked = settings.enabled,
                        onCheckedChange = { onUpdateSettings(settings.copy(enabled = it)) }
                    )
                }
            }

            // Screen Condition Card
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.TouchApp,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Active Screen Condition",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Choose whether shortcuts respond when the screen is turned on, off, or both.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    ConditionOptionRow(
                        title = "Always (Screen On & Off)",
                        subtitle = "Active in reader/browser and when listening with screen locked/off",
                        selected = settings.screenCondition == HardwareButtonScreenCondition.ALWAYS,
                        onClick = { onUpdateSettings(settings.copy(screenCondition = HardwareButtonScreenCondition.ALWAYS)) }
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                    ConditionOptionRow(
                        title = "Only when Screen is OFF",
                        subtitle = "Standard volume keys when using the app; shortcuts trigger only when device screen is off",
                        selected = settings.screenCondition == HardwareButtonScreenCondition.SCREEN_OFF_ONLY,
                        onClick = { onUpdateSettings(settings.copy(screenCondition = HardwareButtonScreenCondition.SCREEN_OFF_ONLY)) }
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                    ConditionOptionRow(
                        title = "Only when Screen is ON",
                        subtitle = "Shortcuts trigger only while actively using the NovelArrow app",
                        selected = settings.screenCondition == HardwareButtonScreenCondition.SCREEN_ON_ONLY,
                        onClick = { onUpdateSettings(settings.copy(screenCondition = HardwareButtonScreenCondition.SCREEN_ON_ONLY)) }
                    )
                }
            }

            // Haptic Feedback Card
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Vibration,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Tactile Vibration Feedback",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Vibrate softly when a double press or long press shortcut is recognized",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = settings.hapticFeedback,
                        onCheckedChange = { onUpdateSettings(settings.copy(hapticFeedback = it)) }
                    )
                }
            }

            // Volume Up (+) Mappings
            ButtonMappingGroupCard(
                title = "Volume Up (+)",
                icon = Icons.AutoMirrored.Filled.VolumeUp,
                singlePressAction = settings.volUpSinglePress,
                doublePressAction = settings.volUpDoublePress,
                longPressAction = settings.volUpLongPress,
                onEditSingle = { editingSlot = EditingButtonSlot.VOL_UP_SINGLE },
                onEditDouble = { editingSlot = EditingButtonSlot.VOL_UP_DOUBLE },
                onEditLong = { editingSlot = EditingButtonSlot.VOL_UP_LONG }
            )

            // Volume Down (-) Mappings
            ButtonMappingGroupCard(
                title = "Volume Down (-)",
                icon = Icons.AutoMirrored.Filled.VolumeDown,
                singlePressAction = settings.volDownSinglePress,
                doublePressAction = settings.volDownDoublePress,
                longPressAction = settings.volDownLongPress,
                onEditSingle = { editingSlot = EditingButtonSlot.VOL_DOWN_SINGLE },
                onEditDouble = { editingSlot = EditingButtonSlot.VOL_DOWN_DOUBLE },
                onEditLong = { editingSlot = EditingButtonSlot.VOL_DOWN_LONG }
            )

            // Explanatory Tips Card
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "How It Works",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = "• Double Press: Quick two taps within 320ms.\n" +
                                   "• Long Press: Hold the button for more than 450ms.\n" +
                                   "• Keeping Single Press on 'Default Volume Control' allows normal volume adjustments while enjoying instant double tap and hold shortcuts.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.9f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    // Action Selection Dialog
    editingSlot?.let { slot ->
        val currentAction = when (slot) {
            EditingButtonSlot.VOL_UP_SINGLE -> settings.volUpSinglePress
            EditingButtonSlot.VOL_UP_DOUBLE -> settings.volUpDoublePress
            EditingButtonSlot.VOL_UP_LONG -> settings.volUpLongPress
            EditingButtonSlot.VOL_DOWN_SINGLE -> settings.volDownSinglePress
            EditingButtonSlot.VOL_DOWN_DOUBLE -> settings.volDownDoublePress
            EditingButtonSlot.VOL_DOWN_LONG -> settings.volDownLongPress
        }

        val dialogTitle = when (slot) {
            EditingButtonSlot.VOL_UP_SINGLE -> "Volume Up: Single Press"
            EditingButtonSlot.VOL_UP_DOUBLE -> "Volume Up: Double Press"
            EditingButtonSlot.VOL_UP_LONG -> "Volume Up: Long Press"
            EditingButtonSlot.VOL_DOWN_SINGLE -> "Volume Down: Single Press"
            EditingButtonSlot.VOL_DOWN_DOUBLE -> "Volume Down: Double Press"
            EditingButtonSlot.VOL_DOWN_LONG -> "Volume Down: Long Press"
        }

        ActionPickerDialog(
            title = dialogTitle,
            currentAction = currentAction,
            onSelectAction = { selected ->
                val updated = when (slot) {
                    EditingButtonSlot.VOL_UP_SINGLE -> settings.copy(volUpSinglePress = selected)
                    EditingButtonSlot.VOL_UP_DOUBLE -> settings.copy(volUpDoublePress = selected)
                    EditingButtonSlot.VOL_UP_LONG -> settings.copy(volUpLongPress = selected)
                    EditingButtonSlot.VOL_DOWN_SINGLE -> settings.copy(volDownSinglePress = selected)
                    EditingButtonSlot.VOL_DOWN_DOUBLE -> settings.copy(volDownDoublePress = selected)
                    EditingButtonSlot.VOL_DOWN_LONG -> settings.copy(volDownLongPress = selected)
                }
                onUpdateSettings(updated)
                editingSlot = null
            },
            onDismiss = { editingSlot = null }
        )
    }
}

@Composable
private fun ConditionOptionRow(
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ButtonMappingGroupCard(
    title: String,
    icon: ImageVector,
    singlePressAction: HardwareButtonAction,
    doublePressAction: HardwareButtonAction,
    longPressAction: HardwareButtonAction,
    onEditSingle: () -> Unit,
    onEditDouble: () -> Unit,
    onEditLong: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            }
            Spacer(modifier = Modifier.height(12.dp))

            ActionSlotRow(
                gesture = "Single Press",
                action = singlePressAction,
                onClick = onEditSingle
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
            ActionSlotRow(
                gesture = "Double Press",
                action = doublePressAction,
                onClick = onEditDouble
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
            ActionSlotRow(
                gesture = "Long Press",
                action = longPressAction,
                onClick = onEditLong
            )
        }
    }
}

@Composable
private fun ActionSlotRow(
    gesture: String,
    action: HardwareButtonAction,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = gesture,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = action.label,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
            )
        }
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
        ) {
            Text(
                text = "Change",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
            )
        }
    }
}

@Composable
private fun ActionPickerDialog(
    title: String,
    currentAction: HardwareButtonAction,
    onSelectAction: (HardwareButtonAction) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                HardwareButtonAction.entries.forEach { action ->
                    val isSelected = (action == currentAction)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectAction(action) }
                            .padding(vertical = 10.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = isSelected,
                            onClick = { onSelectAction(action) }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = action.label,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            ),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
