package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccessibilityNew
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.automation.DeviceTelemetry
import com.example.automation.PermissionSummary
import com.example.data.local.ReminderEntity
import com.example.services.CapturedNotification
import com.example.ui.components.ZoyaGlassCard
import com.example.ui.components.ZoyaTopBar
import com.example.ui.theme.ZoyaAmber
import com.example.ui.theme.ZoyaBorderSubtle
import com.example.ui.theme.ZoyaCardSurface
import com.example.ui.theme.ZoyaCoralError
import com.example.ui.theme.ZoyaElectricBlue
import com.example.ui.theme.ZoyaEmerald
import com.example.ui.theme.ZoyaNeonCyan
import com.example.ui.theme.ZoyaNeonPink
import com.example.ui.theme.ZoyaNeonPurple
import com.example.ui.theme.ZoyaObsidian
import com.example.ui.theme.ZoyaTextMuted
import com.example.ui.theme.ZoyaTextPrimary
import com.example.ui.theme.ZoyaTextSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun FeaturesScreen(
    telemetry: DeviceTelemetry?,
    permissions: PermissionSummary?,
    reminders: List<ReminderEntity>,
    notifications: List<CapturedNotification>,
    onRefreshTelemetry: () -> Unit,
    onCreateReminder: (String, Int) -> Unit,
    onToggleReminder: (ReminderEntity) -> Unit,
    onDeleteReminder: (ReminderEntity) -> Unit,
    onQuickAlarm: (Int, Int) -> Unit,
    onQuickTimer: (Int) -> Unit,
    onRunMorningBrief: () -> Unit,
    onRunVoiceCommand: (String) -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    onOpenNotificationAccessSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showAddReminderDialog by rememberSaveable { mutableStateOf(false) }
    var newReminderTitle by rememberSaveable { mutableStateOf("") }
    var newReminderMinutes by rememberSaveable { mutableStateOf("20") }

    val timeFormatter = SimpleDateFormat("hh:mm a, dd MMM", Locale.getDefault())

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ZoyaObsidian)
    ) {
        ZoyaTopBar(
            title = "FEATURES & AUTOMATION",
            subtitle = "Telemetry, reminders, alarms & smart routines",
            trailing = {
                IconButton(
                    onClick = onRefreshTelemetry,
                    modifier = Modifier.testTag("refresh_telemetry_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = "Refresh device info",
                        tint = ZoyaNeonCyan
                    )
                }
            }
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Live Device Telemetry Card
            item {
                ZoyaGlassCard(borderColor = ZoyaElectricBlue) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.PhoneAndroid,
                            contentDescription = null,
                            tint = ZoyaElectricBlue,
                            modifier = Modifier.size(22.dp)
                        )
                        Text(
                            text = "Live Device Information",
                            style = MaterialTheme.typography.titleMedium,
                            color = ZoyaTextPrimary
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))

                    val info = telemetry
                    if (info != null) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            TelemetryStatChip(
                                label = "BATTERY",
                                value = "${info.battery.percentage}%${if (info.battery.isCharging) " ⚡" else ""}",
                                modifier = Modifier.weight(1f)
                            )
                            TelemetryStatChip(
                                label = "NETWORK",
                                value = info.networkStatus,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            TelemetryStatChip(
                                label = "DEVICE",
                                value = "${info.manufacturer} ${info.model}",
                                modifier = Modifier.weight(1f)
                            )
                            TelemetryStatChip(
                                label = "ANDROID",
                                value = "Android ${info.androidVersion} (API ${info.sdkInt})",
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            TelemetryStatChip(
                                label = "RAM FREE",
                                value = "${info.availableRamMb} / ${info.totalRamMb} MB",
                                modifier = Modifier.weight(1f)
                            )
                            TelemetryStatChip(
                                label = "STORAGE FREE",
                                value = "${info.freeStorageGb} / ${info.totalStorageGb} GB",
                                modifier = Modifier.weight(1f)
                            )
                        }
                    } else {
                        Text(
                            text = "Tap refresh to inspect live device telemetry.",
                            style = MaterialTheme.typography.bodySmall,
                            color = ZoyaTextSecondary
                        )
                    }
                }
            }

            // Smart Automation Routines
            item {
                Text(
                    text = "User-Approved Automation Routines",
                    style = MaterialTheme.typography.titleMedium,
                    color = ZoyaTextPrimary,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    RoutineButton(
                        icon = Icons.Rounded.WbSunny,
                        title = "Morning Brief",
                        subtitle = "Battery, reminders & alerts",
                        accent = ZoyaAmber,
                        onClick = onRunMorningBrief,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("routine_morning_brief")
                    )
                    RoutineButton(
                        icon = Icons.Rounded.BatteryChargingFull,
                        title = "Battery Voice",
                        subtitle = "Speak live battery %",
                        accent = ZoyaEmerald,
                        onClick = { onRunVoiceCommand("Zoya battery kitni hai?") },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("routine_battery_voice")
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    RoutineButton(
                        icon = Icons.Rounded.Alarm,
                        title = "7:00 AM Alarm",
                        subtitle = "Open Android Clock",
                        accent = ZoyaNeonPurple,
                        onClick = { onQuickAlarm(7, 0) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("routine_quick_alarm")
                    )
                    RoutineButton(
                        icon = Icons.Rounded.Timer,
                        title = "10m Focus Timer",
                        subtitle = "Start native timer",
                        accent = ZoyaNeonCyan,
                        onClick = { onQuickTimer(10) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("routine_quick_timer")
                    )
                }
            }

            // Reminders Section (Room + AlarmManager)
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Scheduled Reminders (${reminders.size})",
                        style = MaterialTheme.typography.titleMedium,
                        color = ZoyaTextPrimary
                    )
                    Button(
                        onClick = { showAddReminderDialog = true },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ZoyaNeonPurple,
                            contentColor = ZoyaObsidian
                        ),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.testTag("add_reminder_button")
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("New", fontWeight = FontWeight.Bold)
                    }
                }
            }

            if (reminders.isEmpty()) {
                item {
                    ZoyaGlassCard {
                        Text(
                            text = "No reminders scheduled yet. Try saying \"Zoya, 20 minute baad reminder dena\" or tap New.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = ZoyaTextSecondary
                        )
                    }
                }
            } else {
                items(reminders, key = { it.id }) { reminder ->
                    ZoyaGlassCard(
                        borderColor = if (reminder.isCompleted) ZoyaBorderSubtle else ZoyaNeonPurple
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = reminder.isCompleted,
                                onCheckedChange = { onToggleReminder(reminder) },
                                colors = CheckboxDefaults.colors(
                                    checkedColor = ZoyaEmerald,
                                    uncheckedColor = ZoyaNeonPurple
                                )
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = reminder.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = if (reminder.isCompleted) ZoyaTextMuted else ZoyaTextPrimary,
                                    textDecoration = if (reminder.isCompleted) TextDecoration.LineThrough else null
                                )
                                Text(
                                    text = timeFormatter.format(Date(reminder.triggerAtMillis)),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = ZoyaNeonCyan
                                )
                            }
                            IconButton(onClick = { onDeleteReminder(reminder) }) {
                                Icon(
                                    imageVector = Icons.Rounded.DeleteOutline,
                                    contentDescription = "Delete reminder",
                                    tint = ZoyaCoralError
                                )
                            }
                        }
                    }
                }
            }

            // Optional Accessibility & Notification Reader Live Cards
            item {
                ZoyaGlassCard(
                    borderColor = if (permissions?.notificationListenerEnabled == true) ZoyaEmerald else ZoyaBorderSubtle
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Rounded.NotificationsActive,
                                contentDescription = null,
                                tint = ZoyaNeonCyan,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Notification Reader",
                                style = MaterialTheme.typography.titleSmall,
                                color = ZoyaTextPrimary
                            )
                        }
                        Text(
                            text = if (permissions?.notificationListenerEnabled == true) "ACTIVE" else "OPTIONAL",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (permissions?.notificationListenerEnabled == true) ZoyaEmerald else ZoyaAmber
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    if (permissions?.notificationListenerEnabled == true) {
                        if (notifications.isEmpty()) {
                            Text(
                                text = "No recent unread notifications right now.",
                                style = MaterialTheme.typography.bodySmall,
                                color = ZoyaTextSecondary
                            )
                        } else {
                            notifications.take(3).forEach { notif ->
                                Text(
                                    text = "• ${notif.appName}: ${notif.title} ${notif.text}".trim(),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = ZoyaTextSecondary,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(vertical = 2.dp)
                                )
                            }
                        }
                    } else {
                        Text(
                            text = "Grant optional Notification Access so Zoya can read incoming notifications when you ask.",
                            style = MaterialTheme.typography.bodySmall,
                            color = ZoyaTextSecondary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = onOpenNotificationAccessSettings,
                            border = BorderStroke(1.dp, ZoyaNeonCyan)
                        ) {
                            Text("Manage Notification Access", color = ZoyaNeonCyan)
                        }
                    }
                }
            }

            item {
                ZoyaGlassCard(
                    borderColor = if (permissions?.accessibilityEnabled == true) ZoyaEmerald else ZoyaBorderSubtle
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Rounded.AccessibilityNew,
                                contentDescription = null,
                                tint = ZoyaNeonPink,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Screen Accessibility Control",
                                style = MaterialTheme.typography.titleSmall,
                                color = ZoyaTextPrimary
                            )
                        }
                        Text(
                            text = if (permissions?.accessibilityEnabled == true) "ACTIVE" else "OPTIONAL",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (permissions?.accessibilityEnabled == true) ZoyaEmerald else ZoyaAmber
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Optional service that lets Zoya read visible screen text, tap UI buttons, or scroll when requested.",
                        style = MaterialTheme.typography.bodySmall,
                        color = ZoyaTextSecondary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = onOpenAccessibilitySettings,
                        border = BorderStroke(1.dp, ZoyaNeonPink)
                    ) {
                        Text("Open Accessibility Settings", color = ZoyaNeonPink)
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }

    if (showAddReminderDialog) {
        AlertDialog(
            onDismissRequest = { showAddReminderDialog = false },
            containerColor = ZoyaCardSurface,
            title = { Text("Schedule Zoya Reminder", color = ZoyaTextPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = newReminderTitle,
                        onValueChange = { newReminderTitle = it },
                        label = { Text("Reminder title") },
                        placeholder = { Text("Drink water / Call friend") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ZoyaNeonCyan,
                            unfocusedBorderColor = ZoyaBorderSubtle,
                            focusedTextColor = ZoyaTextPrimary,
                            unfocusedTextColor = ZoyaTextPrimary
                        )
                    )
                    OutlinedTextField(
                        value = newReminderMinutes,
                        onValueChange = { newReminderMinutes = it.filter { ch -> ch.isDigit() } },
                        label = { Text("Minutes from now") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ZoyaNeonCyan,
                            unfocusedBorderColor = ZoyaBorderSubtle,
                            focusedTextColor = ZoyaTextPrimary,
                            unfocusedTextColor = ZoyaTextPrimary
                        )
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val mins = newReminderMinutes.toIntOrNull()?.coerceAtLeast(1) ?: 10
                        onCreateReminder(newReminderTitle, mins)
                        newReminderTitle = ""
                        newReminderMinutes = "20"
                        showAddReminderDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ZoyaNeonPurple,
                        contentColor = ZoyaObsidian
                    )
                ) {
                    Text("Schedule", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showAddReminderDialog = false }) {
                    Text("Cancel", color = ZoyaTextSecondary)
                }
            }
        )
    }
}

@Composable
private fun TelemetryStatChip(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = ZoyaObsidian.copy(alpha = 0.65f),
        border = BorderStroke(1.dp, ZoyaBorderSubtle),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = ZoyaTextMuted
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                color = ZoyaTextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun RoutineButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    accent: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = ZoyaCardSurface,
        border = BorderStroke(1.dp, accent.copy(alpha = 0.45f)),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Icon(imageVector = icon, contentDescription = null, tint = accent, modifier = Modifier.size(22.dp))
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = title, style = MaterialTheme.typography.titleSmall, color = ZoyaTextPrimary)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = ZoyaTextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
