package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.AccessibilityNew
import androidx.compose.material.icons.rounded.BatteryAlert
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.SpatialAudioOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.prefs.ZoyaSettings
import com.example.ui.ZoyaUiState
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

@Composable
fun SettingsHubScreen(
    uiState: ZoyaUiState,
    settings: ZoyaSettings,
    historyCount: Int,
    onOpenApiConfig: () -> Unit,
    onOpenModelInfo: () -> Unit,
    onOpenVoiceLanguage: () -> Unit,
    onOpenBackgroundWake: () -> Unit,
    onOpenAccessibilityNotifications: () -> Unit,
    onOpenPermissionsPrivacy: () -> Unit,
    onOpenBatteryOptimization: () -> Unit,
    onOpenHistory: () -> Unit,
    onClearData: () -> Unit,
    onOpenAbout: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ZoyaObsidian)
    ) {
        ZoyaTopBar(
            title = "SETTINGS",
            subtitle = "${uiState.activeModelName} • Voice, Automation & Privacy"
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                val keyCount = uiState.savedApiKeys.size
                val keySubtitle = when {
                    uiState.hasApiKey && !uiState.activeKeyLabel.isNullOrBlank() ->
                        "Active: ${uiState.activeKeyLabel} (${uiState.maskedApiKey}) • ${uiState.connectionMessage}"
                    uiState.hasApiKey ->
                        "${uiState.maskedApiKey} • ${uiState.connectionMessage}"
                    keyCount > 0 ->
                        "$keyCount keys saved — select an active key to connect"
                    else ->
                        "Not configured — tap to add Gemini API keys"
                }
                val keyBadge = when {
                    keyCount > 1 -> "$keyCount KEYS"
                    uiState.hasApiKey -> "SAVED"
                    else -> "SETUP"
                }
                SettingsRowCard(
                    icon = Icons.Rounded.Key,
                    accent = ZoyaNeonPurple,
                    title = "API Configuration",
                    subtitle = keySubtitle,
                    badge = keyBadge,
                    badgeColor = if (uiState.hasApiKey) ZoyaEmerald else ZoyaAmber,
                    onClick = onOpenApiConfig,
                    tag = "settings_api_config"
                )
            }

            item {
                SettingsRowCard(
                    icon = Icons.Rounded.Info,
                    accent = ZoyaNeonCyan,
                    title = "Model Information",
                    subtitle = "${uiState.activeModelName} (${uiState.activeModelId}) • Vision, Reasoning & Function Calling",
                    badge = "3.8 FLASH",
                    badgeColor = ZoyaNeonCyan,
                    onClick = onOpenModelInfo,
                    tag = "settings_model_info"
                )
            }

            item {
                SettingsRowCard(
                    icon = Icons.Rounded.RecordVoiceOver,
                    accent = ZoyaElectricBlue,
                    title = "Voice & Language Selection",
                    subtitle = "${settings.languageMode.displayName} • Pitch ${"%.2f".format(settings.speechPitch)} • Rate ${"%.2f".format(settings.speechRate)}",
                    onClick = onOpenVoiceLanguage,
                    tag = "settings_voice_language"
                )
            }

            item {
                SettingsRowCard(
                    icon = Icons.Rounded.SpatialAudioOff,
                    accent = ZoyaNeonCyan,
                    title = "Wake Word & Background Assistant",
                    subtitle = "Hey Zoya: ${if (settings.wakeWordEnabled) "On" else "Off"} • Background Service: ${if (uiState.permissions?.backgroundServiceRunning == true) "Running" else "Off"}",
                    badge = if (uiState.permissions?.backgroundServiceRunning == true) "ACTIVE" else null,
                    badgeColor = ZoyaEmerald,
                    onClick = onOpenBackgroundWake,
                    tag = "settings_background_wake"
                )
            }

            item {
                SettingsRowCard(
                    icon = Icons.Rounded.AccessibilityNew,
                    accent = ZoyaNeonPink,
                    title = "Accessibility & Screen Navigation",
                    subtitle = if (uiState.permissions?.accessibilityEnabled == true) "Enabled — screen reading & click actions ready" else "Optional — tap to view permissions & setup",
                    badge = if (uiState.permissions?.accessibilityEnabled == true) "ON" else "OFF",
                    badgeColor = if (uiState.permissions?.accessibilityEnabled == true) ZoyaEmerald else ZoyaTextMuted,
                    onClick = onOpenAccessibilityNotifications,
                    tag = "settings_accessibility"
                )
            }

            item {
                SettingsRowCard(
                    icon = Icons.Rounded.NotificationsActive,
                    accent = ZoyaElectricBlue,
                    title = "Notification Access",
                    subtitle = if (uiState.permissions?.notificationListenerEnabled == true) "Enabled — Zoya can read incoming notifications" else "Optional — user-controlled notification reader",
                    badge = if (uiState.permissions?.notificationListenerEnabled == true) "ON" else "OFF",
                    badgeColor = if (uiState.permissions?.notificationListenerEnabled == true) ZoyaEmerald else ZoyaTextMuted,
                    onClick = onOpenAccessibilityNotifications,
                    tag = "settings_notification_access"
                )
            }

            item {
                SettingsRowCard(
                    icon = Icons.Rounded.Security,
                    accent = ZoyaEmerald,
                    title = "Permissions & Privacy",
                    subtitle = "Microphone, Notifications, local storage & zero telemetry",
                    onClick = onOpenPermissionsPrivacy,
                    tag = "settings_permissions_privacy"
                )
            }

            item {
                SettingsRowCard(
                    icon = Icons.Rounded.BatteryAlert,
                    accent = ZoyaAmber,
                    title = "Battery Optimization",
                    subtitle = if (uiState.permissions?.batteryOptimizationIgnored == true) "Unrestricted background recovery enabled" else "Inspect Android battery optimization settings",
                    onClick = onOpenBatteryOptimization,
                    tag = "settings_battery_optimization"
                )
            }

            item {
                SettingsRowCard(
                    icon = Icons.Rounded.History,
                    accent = ZoyaNeonPurple,
                    title = "Conversation History",
                    subtitle = "$historyCount saved voice & command entries on this device",
                    onClick = onOpenHistory,
                    tag = "settings_conversation_history"
                )
            }

            item {
                SettingsRowCard(
                    icon = Icons.Rounded.DeleteSweep,
                    accent = ZoyaCoralError,
                    title = "Clear Local Data",
                    subtitle = "Erase conversation history, reminders & encrypted API key",
                    onClick = onClearData,
                    tag = "settings_clear_data"
                )
            }

            item {
                SettingsRowCard(
                    icon = Icons.Rounded.Info,
                    accent = ZoyaNeonCyan,
                    title = "About ZOYA AI & Setup Guide",
                    subtitle = "Version 1.0.0 • API setup & permission walkthrough",
                    onClick = onOpenAbout,
                    tag = "settings_about"
                )
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun SettingsRowCard(
    icon: ImageVector,
    accent: Color,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    tag: String,
    badge: String? = null,
    badgeColor: Color = ZoyaNeonCyan
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        color = ZoyaCardSurface,
        border = BorderStroke(1.dp, ZoyaBorderSubtle),
        modifier = Modifier
            .fillMaxWidth()
            .testTag(tag)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .background(accent.copy(alpha = 0.16f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = ZoyaTextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = ZoyaTextSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (!badge.isNullOrBlank()) {
                Spacer(modifier = Modifier.width(8.dp))
                Surface(
                    shape = RoundedCornerShape(50),
                    color = badgeColor.copy(alpha = 0.16f),
                    border = BorderStroke(1.dp, badgeColor.copy(alpha = 0.55f))
                ) {
                    Text(
                        text = badge,
                        style = MaterialTheme.typography.labelSmall,
                        color = badgeColor,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(6.dp))
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = null,
                tint = ZoyaTextMuted
            )
        }
    }
}
