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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccessibilityNew
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.Brightness6
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Chat
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Sms
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.automation.InstalledAppInfo
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

private data class QuickActionTile(
    val title: String,
    val key: String,
    val icon: ImageVector,
    val accent: Color
)

@Composable
fun AppControlScreen(
    installedApps: List<InstalledAppInfo>,
    onLaunchQuickApp: (String) -> Unit,
    onLaunchPackage: (InstalledAppInfo) -> Unit,
    onOpenSetting: (String) -> Unit,
    onPrepareCall: (String) -> Unit,
    onPrepareSms: (String, String) -> Unit,
    modifier: Modifier = Modifier
) {
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var dialNumber by rememberSaveable { mutableStateOf("") }
    var smsNumber by rememberSaveable { mutableStateOf("") }
    var smsMessage by rememberSaveable { mutableStateOf("") }

    val quickApps = remember {
        listOf(
            QuickActionTile("YouTube", "youtube", Icons.Rounded.PlayArrow, ZoyaCoralError),
            QuickActionTile("Instagram", "instagram", Icons.Rounded.CameraAlt, ZoyaNeonPink),
            QuickActionTile("WhatsApp", "whatsapp", Icons.Rounded.Chat, ZoyaEmerald),
            QuickActionTile("Camera", "camera", Icons.Rounded.CameraAlt, ZoyaNeonCyan),
            QuickActionTile("Gallery", "gallery", Icons.Rounded.PhotoLibrary, ZoyaNeonPurple),
            QuickActionTile("Chrome", "chrome", Icons.Rounded.Language, ZoyaElectricBlue)
        )
    }

    val systemSettings = remember {
        listOf(
            QuickActionTile("Wi-Fi", "wifi", Icons.Rounded.Wifi, ZoyaElectricBlue),
            QuickActionTile("Bluetooth", "bluetooth", Icons.Rounded.Bluetooth, ZoyaNeonCyan),
            QuickActionTile("Location", "location", Icons.Rounded.LocationOn, ZoyaEmerald),
            QuickActionTile("Display", "display", Icons.Rounded.Brightness6, ZoyaAmber),
            QuickActionTile("Sound", "sound", Icons.Rounded.VolumeUp, ZoyaNeonPurple),
            QuickActionTile("Battery", "battery", Icons.Rounded.BatteryChargingFull, ZoyaEmerald),
            QuickActionTile("Accessibility", "accessibility", Icons.Rounded.AccessibilityNew, ZoyaNeonPink)
        )
    }

    val filteredApps = remember(installedApps, searchQuery) {
        if (searchQuery.isBlank()) installedApps
        else installedApps.filter {
            it.label.contains(searchQuery, ignoreCase = true) ||
                it.packageName.contains(searchQuery, ignoreCase = true)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ZoyaObsidian)
    ) {
        ZoyaTopBar(
            title = "APP CONTROL",
            subtitle = "Launch apps, open settings & prepare calls/SMS"
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Quick Launch Apps Grid (2 rows x 3 columns)
            item {
                Text(
                    text = "Quick Launch Apps",
                    style = MaterialTheme.typography.titleMedium,
                    color = ZoyaTextPrimary,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    quickApps.chunked(3).forEach { row ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            row.forEach { tile ->
                                Surface(
                                    onClick = { onLaunchQuickApp(tile.key) },
                                    shape = RoundedCornerShape(18.dp),
                                    color = ZoyaCardSurface,
                                    border = BorderStroke(1.dp, tile.accent.copy(alpha = 0.45f)),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(82.dp)
                                        .testTag("quick_app_${tile.key}")
                                ) {
                                    Column(
                                        modifier = Modifier.fillMaxSize(),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Icon(
                                            imageVector = tile.icon,
                                            contentDescription = "Open ${tile.title}",
                                            tint = tile.accent,
                                            modifier = Modifier.size(26.dp)
                                        )
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            text = tile.title,
                                            style = MaterialTheme.typography.labelMedium,
                                            color = ZoyaTextPrimary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Phone Settings Grid
            item {
                Text(
                    text = "Phone Settings",
                    style = MaterialTheme.typography.titleMedium,
                    color = ZoyaTextPrimary,
                    modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
                )
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    systemSettings.chunked(2).forEach { row ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            row.forEach { item ->
                                Surface(
                                    onClick = { onOpenSetting(item.key) },
                                    shape = RoundedCornerShape(14.dp),
                                    color = ZoyaCardSurface,
                                    border = BorderStroke(1.dp, ZoyaBorderSubtle),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(54.dp)
                                        .testTag("setting_tile_${item.key}")
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(horizontal = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = item.icon,
                                            contentDescription = item.title,
                                            tint = item.accent,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Text(
                                            text = item.title,
                                            style = MaterialTheme.typography.labelLarge,
                                            color = ZoyaTextPrimary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                            if (row.size == 1) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }

            // Prepare Call & SMS with Mandatory Confirmation
            item {
                ZoyaGlassCard(borderColor = ZoyaElectricBlue) {
                    Text(
                        text = "Calls & SMS (Confirmation Protected)",
                        style = MaterialTheme.typography.titleMedium,
                        color = ZoyaTextPrimary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Zoya asks for your confirmation before opening your Phone Dialer or SMS composer.",
                        style = MaterialTheme.typography.bodySmall,
                        color = ZoyaTextSecondary
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = dialNumber,
                            onValueChange = { dialNumber = it },
                            placeholder = { Text("Phone number", color = ZoyaTextMuted) },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = ZoyaNeonCyan,
                                unfocusedBorderColor = ZoyaBorderSubtle,
                                focusedTextColor = ZoyaTextPrimary,
                                unfocusedTextColor = ZoyaTextPrimary
                            ),
                            modifier = Modifier.weight(1f)
                        )
                        Button(
                            onClick = { onPrepareCall(dialNumber) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ZoyaElectricBlue,
                                contentColor = ZoyaObsidian
                            ),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .height(54.dp)
                                .testTag("prepare_call_button")
                        ) {
                            Icon(Icons.Rounded.Call, contentDescription = "Prepare Call", modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Dial", fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = smsNumber,
                        onValueChange = { smsNumber = it },
                        placeholder = { Text("SMS recipient number", color = ZoyaTextMuted) },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ZoyaNeonPurple,
                            unfocusedBorderColor = ZoyaBorderSubtle,
                            focusedTextColor = ZoyaTextPrimary,
                            unfocusedTextColor = ZoyaTextPrimary
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = smsMessage,
                            onValueChange = { smsMessage = it },
                            placeholder = { Text("Message text", color = ZoyaTextMuted) },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = ZoyaNeonPurple,
                                unfocusedBorderColor = ZoyaBorderSubtle,
                                focusedTextColor = ZoyaTextPrimary,
                                unfocusedTextColor = ZoyaTextPrimary
                            ),
                            modifier = Modifier.weight(1f)
                        )
                        Button(
                            onClick = { onPrepareSms(smsNumber, smsMessage) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ZoyaNeonPurple,
                                contentColor = ZoyaObsidian
                            ),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .height(54.dp)
                                .testTag("prepare_sms_button")
                        ) {
                            Icon(Icons.Rounded.Sms, contentDescription = "Prepare SMS", modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("SMS", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Search Installed Applications
            item {
                Text(
                    text = "Installed Applications (${filteredApps.size})",
                    style = MaterialTheme.typography.titleMedium,
                    color = ZoyaTextPrimary,
                    modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
                )
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search installed apps...", color = ZoyaTextMuted) },
                    leadingIcon = {
                        Icon(Icons.Rounded.Search, contentDescription = null, tint = ZoyaNeonCyan)
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ZoyaNeonCyan,
                        unfocusedBorderColor = ZoyaBorderSubtle,
                        focusedContainerColor = ZoyaCardSurface,
                        unfocusedContainerColor = ZoyaCardSurface,
                        focusedTextColor = ZoyaTextPrimary,
                        unfocusedTextColor = ZoyaTextPrimary
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("search_installed_apps_input")
                )
            }

            if (filteredApps.isEmpty()) {
                item {
                    ZoyaGlassCard {
                        Text(
                            text = "No matching launchable apps found.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = ZoyaTextSecondary
                        )
                    }
                }
            } else {
                items(filteredApps.take(40), key = { it.packageName }) { app ->
                    Surface(
                        onClick = { onLaunchPackage(app) },
                        shape = RoundedCornerShape(14.dp),
                        color = ZoyaCardSurface,
                        border = BorderStroke(1.dp, ZoyaBorderSubtle),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .background(ZoyaNeonPurple.copy(alpha = 0.2f), RoundedCornerShape(10.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Apps,
                                    contentDescription = null,
                                    tint = ZoyaNeonPurple,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = app.label,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = ZoyaTextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = app.packageName,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = ZoyaTextMuted,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Text(
                                text = "OPEN",
                                style = MaterialTheme.typography.labelSmall,
                                color = ZoyaNeonCyan
                            )
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }
}
