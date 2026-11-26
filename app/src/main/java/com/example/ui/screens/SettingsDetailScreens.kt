package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RadioButtonChecked
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.automation.PermissionSummary
import com.example.data.local.ChatMessageEntity
import com.example.data.prefs.ZoyaLanguageMode
import com.example.data.prefs.ZoyaSettings
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
import com.example.voice.VoiceOption
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun VoiceLanguageScreen(
    settings: ZoyaSettings,
    voices: List<VoiceOption>,
    onBack: () -> Unit,
    onSelectLanguage: (ZoyaLanguageMode) -> Unit,
    onSelectVoice: (String) -> Unit,
    onUpdateRate: (Float) -> Unit,
    onUpdatePitch: (Float) -> Unit,
    onTestVoice: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ZoyaObsidian)
    ) {
        ZoyaTopBar(
            title = "VOICE & LANGUAGE",
            subtitle = "Hindi, English, Hinglish & natural female TTS",
            onBack = onBack
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                ZoyaGlassCard(borderColor = ZoyaNeonPurple) {
                    Text("Language Mode", style = MaterialTheme.typography.titleMedium, color = ZoyaTextPrimary)
                    Spacer(modifier = Modifier.height(8.dp))
                    ZoyaLanguageMode.entries.forEach { mode ->
                        val selected = settings.languageMode == mode
                        Surface(
                            onClick = { onSelectLanguage(mode) },
                            shape = RoundedCornerShape(14.dp),
                            color = if (selected) ZoyaNeonPurple.copy(alpha = 0.2f) else ZoyaObsidian.copy(alpha = 0.5f),
                            border = BorderStroke(1.dp, if (selected) ZoyaNeonPurple else ZoyaBorderSubtle),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (selected) Icons.Rounded.RadioButtonChecked else Icons.Rounded.RadioButtonUnchecked,
                                    contentDescription = null,
                                    tint = if (selected) ZoyaNeonCyan else ZoyaTextMuted
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(mode.displayName, style = MaterialTheme.typography.titleSmall, color = ZoyaTextPrimary)
                                    Text(mode.subtitle, style = MaterialTheme.typography.bodySmall, color = ZoyaTextSecondary)
                                }
                            }
                        }
                    }
                }
            }

            item {
                ZoyaGlassCard(borderColor = ZoyaElectricBlue) {
                    Text("Voice Tuning", style = MaterialTheme.typography.titleMedium, color = ZoyaTextPrimary)
                    Spacer(modifier = Modifier.height(10.dp))
                    Text("Speech Rate: ${"%.2f".format(settings.speechRate)}x", style = MaterialTheme.typography.bodySmall, color = ZoyaTextSecondary)
                    Slider(
                        value = settings.speechRate,
                        onValueChange = onUpdateRate,
                        valueRange = 0.7f..1.5f,
                        colors = SliderDefaults.colors(
                            thumbColor = ZoyaNeonCyan,
                            activeTrackColor = ZoyaElectricBlue
                        )
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("Voice Pitch: ${"%.2f".format(settings.speechPitch)}", style = MaterialTheme.typography.bodySmall, color = ZoyaTextSecondary)
                    Slider(
                        value = settings.speechPitch,
                        onValueChange = onUpdatePitch,
                        valueRange = 0.75f..1.4f,
                        colors = SliderDefaults.colors(
                            thumbColor = ZoyaNeonPurple,
                            activeTrackColor = ZoyaNeonPurple
                        )
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = onTestVoice,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ZoyaElectricBlue,
                            contentColor = ZoyaObsidian
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("test_voice_button")
                    ) {
                        Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Preview Zoya's Voice", fontWeight = FontWeight.Bold)
                    }
                }
            }

            item {
                Text(
                    text = "Installed Device Voices (${voices.size})",
                    style = MaterialTheme.typography.titleMedium,
                    color = ZoyaTextPrimary
                )
            }

            if (voices.isEmpty()) {
                item {
                    ZoyaGlassCard {
                        Text(
                            text = "Using system default natural female voice for English & Hindi.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = ZoyaTextSecondary
                        )
                    }
                }
            } else {
                items(voices, key = { it.name }) { voice ->
                    val selected = settings.selectedVoiceName == voice.name
                    Surface(
                        onClick = { onSelectVoice(voice.name) },
                        shape = RoundedCornerShape(14.dp),
                        color = if (selected) ZoyaElectricBlue.copy(alpha = 0.18f) else ZoyaCardSurface,
                        border = BorderStroke(1.dp, if (selected) ZoyaElectricBlue else ZoyaBorderSubtle),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (selected) Icons.Rounded.RadioButtonChecked else Icons.Rounded.RadioButtonUnchecked,
                                contentDescription = null,
                                tint = if (selected) ZoyaNeonCyan else ZoyaTextMuted
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(voice.displayLabel, style = MaterialTheme.typography.titleSmall, color = ZoyaTextPrimary)
                                Text(voice.name, style = MaterialTheme.typography.bodySmall, color = ZoyaTextMuted)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun BackgroundAssistantScreen(
    settings: ZoyaSettings,
    permissions: PermissionSummary?,
    onBack: () -> Unit,
    onToggleWakeWord: (Boolean) -> Unit,
    onToggleContinuous: (Boolean) -> Unit,
    onToggleBackgroundService: (Boolean) -> Unit,
    onRequestMicPermission: () -> Unit,
    onOpenBatteryOptimization: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ZoyaObsidian)
    ) {
        ZoyaTopBar(
            title = "WAKE WORD & BACKGROUND",
            subtitle = "User-controlled listening & persistent service",
            onBack = onBack
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            ZoyaGlassCard(borderColor = ZoyaNeonCyan) {
                ToggleSettingRow(
                    title = "\"Hey Zoya\" Wake Word Handling",
                    subtitle = "Recognize 'Hey Zoya' / 'Zoya' prefix and greet automatically.",
                    checked = settings.wakeWordEnabled,
                    onCheckedChange = onToggleWakeWord,
                    tag = "switch_wake_word"
                )
                Spacer(modifier = Modifier.height(12.dp))
                ToggleSettingRow(
                    title = "Continuous Voice Conversation",
                    subtitle = "Automatically resume listening after Zoya finishes speaking.",
                    checked = settings.continuousConversation,
                    onCheckedChange = onToggleContinuous,
                    tag = "switch_continuous"
                )
            }

            ZoyaGlassCard(borderColor = ZoyaNeonPurple) {
                ToggleSettingRow(
                    title = "Background Assistant Service",
                    subtitle = "Runs a visible Android foreground notification with quick Talk & Stop controls. Never secretly records audio.",
                    checked = permissions?.backgroundServiceRunning == true,
                    onCheckedChange = onToggleBackgroundService,
                    tag = "switch_background_service"
                )

                if (permissions?.microphoneGranted != true) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Microphone permission is required before starting the background assistant service.",
                        style = MaterialTheme.typography.bodySmall,
                        color = ZoyaAmber
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = onRequestMicPermission,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ZoyaNeonPurple,
                            contentColor = ZoyaObsidian
                        )
                    ) {
                        Text("Grant Microphone Permission", fontWeight = FontWeight.Bold)
                    }
                }
            }

            ZoyaGlassCard(borderColor = ZoyaAmber) {
                Text("Battery Optimization & Recovery", style = MaterialTheme.typography.titleMedium, color = ZoyaTextPrimary)
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = if (permissions?.batteryOptimizationIgnored == true) {
                        "Zoya AI is excluded from aggressive battery restriction, improving reminder reliability and service stability."
                    } else {
                        "Android may pause background services when battery optimization is strict. You can optionally allow unrestricted battery usage in Android Settings."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = ZoyaTextSecondary
                )
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedButton(
                    onClick = onOpenBatteryOptimization,
                    border = BorderStroke(1.dp, ZoyaAmber),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Open Battery Optimization Settings", color = ZoyaAmber)
                }
            }
        }
    }
}

@Composable
fun AccessibilityNotificationScreen(
    permissions: PermissionSummary?,
    onBack: () -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    onOpenNotificationAccess: () -> Unit,
    onTestReadScreen: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ZoyaObsidian)
    ) {
        ZoyaTopBar(
            title = "ACCESSIBILITY & NOTIFICATIONS",
            subtitle = "Optional user-approved system integrations",
            onBack = onBack
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            ZoyaGlassCard(
                borderColor = if (permissions?.accessibilityEnabled == true) ZoyaEmerald else ZoyaNeonPink
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Accessibility Assistant", style = MaterialTheme.typography.titleMedium, color = ZoyaTextPrimary)
                    Text(
                        text = if (permissions?.accessibilityEnabled == true) "ENABLED" else "DISABLED",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (permissions?.accessibilityEnabled == true) ZoyaEmerald else ZoyaAmber
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Why this permission is optional:\n• Read visible screen text only when you say 'Read screen'\n• Tap buttons or scroll supported apps on your voice command\n• Never collects passwords or runs hidden actions",
                    style = MaterialTheme.typography.bodySmall,
                    color = ZoyaTextSecondary
                )
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = onOpenAccessibilitySettings,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ZoyaNeonPink,
                        contentColor = ZoyaObsidian
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Text("Open Android Accessibility Settings", fontWeight = FontWeight.Bold)
                }
                if (permissions?.accessibilityEnabled == true) {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = onTestReadScreen,
                        border = BorderStroke(1.dp, ZoyaNeonCyan),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Test Read Current Screen", color = ZoyaNeonCyan)
                    }
                }
            }

            ZoyaGlassCard(
                borderColor = if (permissions?.notificationListenerEnabled == true) ZoyaEmerald else ZoyaElectricBlue
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Notification Listener", style = MaterialTheme.typography.titleMedium, color = ZoyaTextPrimary)
                    Text(
                        text = if (permissions?.notificationListenerEnabled == true) "ENABLED" else "DISABLED",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (permissions?.notificationListenerEnabled == true) ZoyaEmerald else ZoyaAmber
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "When enabled, Zoya can read your latest notifications out loud whenever you ask 'Read my notifications' or 'Meri notifications padho'. Notification contents stay on-device.",
                    style = MaterialTheme.typography.bodySmall,
                    color = ZoyaTextSecondary
                )
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = onOpenNotificationAccess,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ZoyaElectricBlue,
                        contentColor = ZoyaObsidian
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Text("Open Notification Access Settings", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun PermissionsPrivacyScreen(
    settings: ZoyaSettings,
    permissions: PermissionSummary?,
    onBack: () -> Unit,
    onRequestMic: () -> Unit,
    onRequestNotifications: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onToggleSaveHistory: (Boolean) -> Unit,
    onClearHistory: () -> Unit,
    onClearAllData: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ZoyaObsidian)
    ) {
        ZoyaTopBar(
            title = "PERMISSIONS & PRIVACY",
            subtitle = "Transparent, user-controlled security",
            onBack = onBack
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            ZoyaGlassCard(borderColor = ZoyaEmerald) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Shield, contentDescription = null, tint = ZoyaEmerald)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Privacy Guarantees", style = MaterialTheme.typography.titleMedium, color = ZoyaTextPrimary)
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "• API keys are encrypted locally using Android Keystore (AES-256-GCM)\n• Zero hardcoded keys, zero API keys in logs\n• Sensitive calls & SMS always require explicit confirmation\n• No analytics, trackers, or unauthorized background recording",
                    style = MaterialTheme.typography.bodySmall,
                    color = ZoyaTextSecondary
                )
            }

            ZoyaGlassCard(borderColor = ZoyaBorderSubtle) {
                Text("Permission Status", style = MaterialTheme.typography.titleMedium, color = ZoyaTextPrimary)
                Spacer(modifier = Modifier.height(10.dp))
                PermissionActionRow(
                    label = "Microphone (Voice Input)",
                    granted = permissions?.microphoneGranted == true,
                    onAction = onRequestMic
                )
                Spacer(modifier = Modifier.height(8.dp))
                PermissionActionRow(
                    label = "Post Notifications (Reminders & Service)",
                    granted = permissions?.notificationsGranted == true,
                    onAction = onRequestNotifications
                )
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedButton(
                    onClick = onOpenAppSettings,
                    border = BorderStroke(1.dp, ZoyaBorderSubtle),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Open Android App Permissions", color = ZoyaTextPrimary)
                }
            }

            ZoyaGlassCard(borderColor = ZoyaCoralError) {
                ToggleSettingRow(
                    title = "Save Local Conversation History",
                    subtitle = "Store voice/text exchanges in on-device Room database for context.",
                    checked = settings.saveHistoryEnabled,
                    onCheckedChange = onToggleSaveHistory,
                    tag = "switch_save_history"
                )
                Spacer(modifier = Modifier.height(14.dp))
                OutlinedButton(
                    onClick = onClearHistory,
                    border = BorderStroke(1.dp, ZoyaAmber),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Rounded.DeleteOutline, contentDescription = null, tint = ZoyaAmber, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Clear Conversation History", color = ZoyaAmber)
                }
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = onClearAllData,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ZoyaCoralError,
                        contentColor = ZoyaObsidian
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Clear All Local Data & Keys", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun ConversationHistoryScreen(
    messages: List<ChatMessageEntity>,
    onBack: () -> Unit,
    onClearHistory: () -> Unit
) {
    val formatter = SimpleDateFormat("hh:mm a, dd MMM", Locale.getDefault())

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ZoyaObsidian)
    ) {
        ZoyaTopBar(
            title = "CONVERSATION HISTORY",
            subtitle = "${messages.size} local entries",
            onBack = onBack,
            trailing = {
                if (messages.isNotEmpty()) {
                    OutlinedButton(
                        onClick = onClearHistory,
                        border = BorderStroke(1.dp, ZoyaCoralError)
                    ) {
                        Text("Clear", color = ZoyaCoralError)
                    }
                }
            }
        )

        if (messages.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "No conversations recorded yet",
                    style = MaterialTheme.typography.titleMedium,
                    color = ZoyaTextPrimary
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Your voice commands and Zoya's replies will appear here when Save History is enabled.",
                    style = MaterialTheme.typography.bodySmall,
                    color = ZoyaTextSecondary
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(messages, key = { it.id }) { msg ->
                    val isUser = msg.role == "user"
                    ZoyaGlassCard(
                        borderColor = if (isUser) ZoyaElectricBlue else ZoyaNeonPurple
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (isUser) "YOU" else "ZOYA AI",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isUser) ZoyaNeonCyan else ZoyaNeonPurple
                            )
                            Text(
                                text = formatter.format(Date(msg.timestampMillis)),
                                style = MaterialTheme.typography.bodySmall,
                                color = ZoyaTextMuted
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = msg.content,
                            style = MaterialTheme.typography.bodyMedium,
                            color = ZoyaTextPrimary
                        )
                        msg.commandBadge?.let { badge ->
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Action: $badge",
                                style = MaterialTheme.typography.labelMedium,
                                color = ZoyaEmerald
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AboutZoyaScreen(
    onBack: () -> Unit,
    onOpenApiConfig: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ZoyaObsidian)
    ) {
        ZoyaTopBar(
            title = "ABOUT ZOYA AI",
            subtitle = "Version 1.0.0 • Native Android Assistant",
            onBack = onBack
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(
                painter = painterResource(id = R.drawable.img_zoya_happy_1795676130422),
                contentDescription = stringResource(R.string.cd_zoya_avatar),
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(132.dp)
                    .clip(CircleShape)
                    .border(
                        3.dp,
                        Brush.linearGradient(listOf(ZoyaNeonPurple, ZoyaElectricBlue)),
                        CircleShape
                    )
            )
            Text("ZOYA AI", style = MaterialTheme.typography.displaySmall, color = ZoyaTextPrimary)
            Text(
                text = "Personal Android AI Assistant • Kotlin & Jetpack Compose",
                style = MaterialTheme.typography.bodySmall,
                color = ZoyaNeonCyan
            )

            ZoyaGlassCard(borderColor = ZoyaNeonPurple) {
                Text("API Setup Instructions", style = MaterialTheme.typography.titleMedium, color = ZoyaTextPrimary)
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "1. Visit aistudio.google.com/apikey and generate a Gemini API key.\n2. Open Settings → API Configuration inside Zoya AI.\n3. Paste your key, tap 'Test API' to verify connectivity and quota, then tap 'Save Key'.\n4. Your key is encrypted with Android Keystore AES-256-GCM on this device.",
                    style = MaterialTheme.typography.bodySmall,
                    color = ZoyaTextSecondary
                )
                Spacer(modifier = Modifier.height(10.dp))
                Button(
                    onClick = onOpenApiConfig,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ZoyaNeonPurple,
                        contentColor = ZoyaObsidian
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Go to API Configuration", fontWeight = FontWeight.Bold)
                }
            }

            ZoyaGlassCard(borderColor = ZoyaElectricBlue) {
                Text("Permission Setup Instructions", style = MaterialTheme.typography.titleMedium, color = ZoyaTextPrimary)
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "• Microphone: Required for Tap-to-Talk and voice commands.\n• Notifications: Used for local Reminders and Background Assistant notification.\n• Accessibility (Optional): Enable in Android Settings → Accessibility → Zoya AI Screen Assistant to read or navigate screens on command.\n• Notification Access (Optional): Enable in Android Settings → Notification Access so Zoya can read incoming notifications when requested.",
                    style = MaterialTheme.typography.bodySmall,
                    color = ZoyaTextSecondary
                )
            }
        }
    }
}

@Composable
private fun ToggleSettingRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    tag: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = ZoyaTextPrimary)
            Spacer(modifier = Modifier.height(2.dp))
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = ZoyaTextSecondary)
        }
        Spacer(modifier = Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = ZoyaObsidian,
                checkedTrackColor = ZoyaNeonCyan,
                uncheckedThumbColor = ZoyaTextSecondary,
                uncheckedTrackColor = ZoyaCardSurface
            ),
            modifier = Modifier.testTag(tag)
        )
    }
}

@Composable
private fun PermissionActionRow(
    label: String,
    granted: Boolean,
    onAction: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Icon(
                imageVector = Icons.Rounded.CheckCircle,
                contentDescription = null,
                tint = if (granted) ZoyaEmerald else ZoyaTextMuted,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(label, style = MaterialTheme.typography.bodyMedium, color = ZoyaTextPrimary)
        }
        if (granted) {
            Text("GRANTED", style = MaterialTheme.typography.labelSmall, color = ZoyaEmerald)
        } else {
            OutlinedButton(
                onClick = onAction,
                border = BorderStroke(1.dp, ZoyaNeonCyan),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Text("Allow", color = ZoyaNeonCyan)
            }
        }
    }
}

@Composable
fun ModelInfoScreen(
    uiState: com.example.ui.ZoyaUiState,
    modelOption: com.example.ai.AiModelOption,
    availableModels: List<com.example.ai.AiModelOption>,
    onBack: () -> Unit,
    onTestConnection: () -> Unit,
    onSelectModel: (String) -> Unit,
    onOpenApiConfig: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ZoyaObsidian)
    ) {
        ZoyaTopBar(
            title = "MODEL INFORMATION",
            subtitle = "Active AI Brain & Reliability Policy",
            onBack = onBack,
            trailing = {
                com.example.ui.components.ConnectionStatusPill(
                    state = uiState.connectionState,
                    onClick = onOpenApiConfig
                )
            }
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            ZoyaGlassCard(borderColor = ZoyaNeonCyan) {
                Text("Active Model", style = MaterialTheme.typography.labelSmall, color = ZoyaNeonCyan)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = uiState.activeModelName,
                    style = MaterialTheme.typography.headlineMedium,
                    color = ZoyaTextPrimary
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Official Model ID: ${uiState.activeModelId}",
                    style = MaterialTheme.typography.titleSmall,
                    color = ZoyaNeonPurple
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Endpoint: generativelanguage.googleapis.com/v1beta/models/${uiState.activeModelId}",
                    style = MaterialTheme.typography.bodySmall,
                    color = ZoyaTextSecondary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Connection Status: ${uiState.connectionMessage}",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (uiState.connectionState == com.example.ai.AiConnectionState.CONNECTED) ZoyaEmerald else ZoyaAmber
                )
                uiState.verifiedModelVersion?.let { verified ->
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Last Verified Response Model: $verified${uiState.lastLatencyMs?.let { " (${it}ms)" }.orEmpty()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = ZoyaEmerald
                    )
                }

                if (!modelOption.isPrimary) {
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = { onSelectModel(com.example.ai.PRIMARY_GEMINI_MODEL_ID) },
                        border = BorderStroke(1.dp, ZoyaEmerald),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("switch_back_primary_model_button")
                    ) {
                        Text("Return to Primary (${com.example.ai.PRIMARY_GEMINI_MODEL_ID})", color = ZoyaEmerald, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = onTestConnection,
                        enabled = !uiState.isTestingApi,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ZoyaElectricBlue,
                            contentColor = ZoyaObsidian
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("model_info_test_button")
                    ) {
                        Text(
                            text = if (uiState.isTestingApi) "Testing..." else "Test Connection",
                            fontWeight = FontWeight.Bold
                        )
                    }
                    OutlinedButton(
                        onClick = onOpenApiConfig,
                        border = BorderStroke(1.dp, ZoyaNeonPurple),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                    ) {
                        Text("API Key", color = ZoyaTextPrimary)
                    }
                }

                uiState.apiTestFeedback?.let { feedback ->
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "${uiState.apiTestTitle?.let { "$it: " }.orEmpty()}$feedback",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (uiState.apiTestSuccess == true) ZoyaEmerald else ZoyaCoralError
                    )
                }
                if (!uiState.apiTestRawError.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Gemini API: ${uiState.apiTestRawError}",
                        style = MaterialTheme.typography.bodySmall,
                        color = ZoyaTextSecondary
                    )
                }
            }

            ZoyaGlassCard(borderColor = ZoyaNeonPurple) {
                Text("Supported Capabilities", style = MaterialTheme.typography.titleMedium, color = ZoyaTextPrimary)
                Spacer(modifier = Modifier.height(8.dp))
                modelOption.capabilities.forEach { capability ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 3.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.CheckCircle,
                            contentDescription = null,
                            tint = ZoyaEmerald,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(capability, style = MaterialTheme.typography.bodyMedium, color = ZoyaTextPrimary)
                    }
                }
            }

            ZoyaGlassCard(borderColor = ZoyaElectricBlue) {
                Text("Reliability & Error Recovery", style = MaterialTheme.typography.titleMedium, color = ZoyaTextPrimary)
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "• Automatic bounded retries (up to 3 attempts, 1s → 2s → 4s backoff) for temporary 429 spikes, 500, 502, 503, and 504 errors\n• Separate detection for Invalid API Key, Hard Quota Exceeded, Temporary Server Overload, Unsupported Model (404), and Timeouts\n• Displays the real Google Gemini API error message and HTTP status code\n• Preserves your message on failure with 1-tap Retry & Cancel Request controls",
                    style = MaterialTheme.typography.bodySmall,
                    color = ZoyaTextSecondary
                )
            }

            ZoyaGlassCard(borderColor = ZoyaElectricBlue) {
                Text("Voice Pipeline Architecture", style = MaterialTheme.typography.titleMedium, color = ZoyaTextPrimary)
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = uiState.voicePipelineMode.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = ZoyaNeonCyan
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = modelOption.voiceArchitectureNote.ifBlank { uiState.voicePipelineMode.description },
                    style = MaterialTheme.typography.bodySmall,
                    color = ZoyaTextSecondary
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Direct Gemini Live Audio: Not used by gemini-3.8-flash. Zoya's modular voice bridge allows plugging in a separate Live Audio model anytime.",
                    style = MaterialTheme.typography.bodySmall,
                    color = ZoyaTextMuted
                )
            }

            ZoyaGlassCard(borderColor = ZoyaAmber) {
                Text("No Silent Downgrade Guarantee", style = MaterialTheme.typography.titleMedium, color = ZoyaTextPrimary)
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Zoya AI keeps gemini-3.8-flash as its primary model and never downgrades silently. You may manually pick an optional alternative below if gemini-3.8-flash is temporarily unavailable:",
                    style = MaterialTheme.typography.bodySmall,
                    color = ZoyaTextSecondary
                )
                Spacer(modifier = Modifier.height(8.dp))
                availableModels.forEach { option ->
                    val selected = option.id == uiState.activeModelId
                    Surface(
                        onClick = { onSelectModel(option.id) },
                        shape = RoundedCornerShape(12.dp),
                        color = if (selected) ZoyaNeonPurple.copy(alpha = 0.2f) else ZoyaObsidian.copy(alpha = 0.5f),
                        border = BorderStroke(1.dp, if (selected) ZoyaNeonCyan else ZoyaBorderSubtle),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (selected) Icons.Rounded.RadioButtonChecked else Icons.Rounded.RadioButtonUnchecked,
                                contentDescription = null,
                                tint = if (selected) ZoyaNeonCyan else ZoyaTextMuted
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(option.displayName, style = MaterialTheme.typography.titleSmall, color = ZoyaTextPrimary)
                                Text("${option.id} • ${option.badge}", style = MaterialTheme.typography.bodySmall, color = ZoyaTextMuted)
                            }
                        }
                    }
                }
            }
        }
    }
}

