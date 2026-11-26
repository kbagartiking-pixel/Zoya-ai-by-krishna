package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.NetworkCheck
import androidx.compose.material.icons.rounded.RadioButtonChecked
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ai.AiModelOption
import com.example.data.prefs.ZoyaSettings
import com.example.ui.ZoyaUiState
import com.example.ui.components.ConnectionStatusPill
import com.example.ui.components.ZoyaGlassCard
import com.example.ui.components.ZoyaTopBar
import com.example.ui.theme.ZoyaAmber
import com.example.ui.theme.ZoyaBorderSubtle
import com.example.ui.theme.ZoyaCoralError
import com.example.ui.theme.ZoyaElectricBlue
import com.example.ui.theme.ZoyaEmerald
import com.example.ui.theme.ZoyaNeonCyan
import com.example.ui.theme.ZoyaNeonPurple
import com.example.ui.theme.ZoyaObsidian
import com.example.ui.theme.ZoyaTextMuted
import com.example.ui.theme.ZoyaTextPrimary
import com.example.ui.theme.ZoyaTextSecondary

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ApiConfigScreen(
    uiState: ZoyaUiState,
    settings: ZoyaSettings,
    availableModels: List<AiModelOption>,
    isOnboardingMode: Boolean,
    onBack: (() -> Unit)?,
    onTestApi: (String, String) -> Unit,
    onCancelApiTest: () -> Unit,
    onSelectModel: (String) -> Unit,
    onSaveApiKey: (String) -> Unit,
    onRemoveApiKey: () -> Unit,
    onOpenModelInfo: () -> Unit,
    onSkipForNow: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var apiKeyInput by rememberSaveable { mutableStateOf("") }
    var showApiKey by rememberSaveable { mutableStateOf(false) }
    var isEditingExisting by rememberSaveable { mutableStateOf(!uiState.hasApiKey) }
    val activeModel = availableModels.firstOrNull { it.id == uiState.activeModelId }
        ?: availableModels.firstOrNull()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ZoyaObsidian)
    ) {
        ZoyaTopBar(
            title = if (isOnboardingMode) "ZOYA API SETUP" else "API CONFIGURATION",
            subtitle = "Active Model: ${uiState.activeModelId}",
            onBack = onBack,
            trailing = {
                ConnectionStatusPill(state = uiState.connectionState, onClick = {})
            }
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 580.dp)
                .align(Alignment.CenterHorizontally)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Welcome Zoya Banner
            ZoyaGlassCard(borderColor = ZoyaNeonPurple) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.img_zoya_idle_1795676005655),
                        contentDescription = stringResource(R.string.cd_zoya_avatar),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .border(
                                2.dp,
                                Brush.linearGradient(listOf(ZoyaNeonPurple, ZoyaElectricBlue)),
                                CircleShape
                            )
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Connect Gemini 3.8 Flash",
                            style = MaterialTheme.typography.titleMedium,
                            color = ZoyaTextPrimary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Enter your Google Gemini API key after installation. Stored strictly on-device inside Android Keystore encryption.",
                            style = MaterialTheme.typography.bodySmall,
                            color = ZoyaTextSecondary
                        )
                    }
                }
            }

            // Live Internet & Key Status Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ZoyaGlassCard(
                    modifier = Modifier.weight(1f),
                    borderColor = if (uiState.isOnline) ZoyaEmerald else ZoyaCoralError
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (uiState.isOnline) Icons.Rounded.Wifi else Icons.Rounded.WifiOff,
                            contentDescription = null,
                            tint = if (uiState.isOnline) ZoyaEmerald else ZoyaCoralError,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (uiState.isOnline) "Online" else "Offline",
                            style = MaterialTheme.typography.labelLarge,
                            color = ZoyaTextPrimary
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = uiState.telemetry?.networkStatus ?: if (uiState.isOnline) "Internet Ready" else "Check Wi-Fi/Data",
                        style = MaterialTheme.typography.bodySmall,
                        color = ZoyaTextSecondary
                    )
                }

                ZoyaGlassCard(
                    modifier = Modifier.weight(1f),
                    borderColor = if (uiState.hasApiKey) ZoyaNeonCyan else ZoyaAmber
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.Lock,
                            contentDescription = null,
                            tint = if (uiState.hasApiKey) ZoyaNeonCyan else ZoyaAmber,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (uiState.hasApiKey) "Key Saved" else "No Key Yet",
                            style = MaterialTheme.typography.labelLarge,
                            color = ZoyaTextPrimary
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = uiState.maskedApiKey,
                        style = MaterialTheme.typography.bodySmall,
                        color = ZoyaTextSecondary
                    )
                }
            }

            // Saved Key Management Card (Change / Remove / Test Live Connection)
            if (uiState.hasApiKey && !isEditingExisting) {
                ZoyaGlassCard(borderColor = ZoyaElectricBlue) {
                    Text(
                        text = "Stored Credential • ${uiState.activeModelId}",
                        style = MaterialTheme.typography.titleSmall,
                        color = ZoyaNeonCyan
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Encrypted Key: ${uiState.maskedApiKey}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = ZoyaTextPrimary
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = { isEditingExisting = true },
                            border = BorderStroke(1.dp, ZoyaNeonPurple),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("change_api_key_button")
                        ) {
                            Icon(Icons.Rounded.Key, contentDescription = null, tint = ZoyaNeonPurple, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Change Key", color = ZoyaTextPrimary)
                        }

                        OutlinedButton(
                            onClick = {
                                onRemoveApiKey()
                                apiKeyInput = ""
                                isEditingExisting = true
                            },
                            border = BorderStroke(1.dp, ZoyaCoralError),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("remove_api_key_button")
                        ) {
                            Icon(Icons.Rounded.DeleteOutline, contentDescription = null, tint = ZoyaCoralError, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Remove", color = ZoyaCoralError)
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { onTestApi("", uiState.activeModelId) },
                            enabled = !uiState.isTestingApi,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ZoyaElectricBlue,
                                contentColor = ZoyaObsidian
                            ),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("test_saved_api_button")
                        ) {
                            if (uiState.isTestingApi) {
                                CircularProgressIndicator(
                                    color = ZoyaObsidian,
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Testing ${uiState.activeModelId}...", fontWeight = FontWeight.Bold)
                            } else {
                                Icon(Icons.Rounded.NetworkCheck, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Test API Connection", fontWeight = FontWeight.Bold)
                            }
                        }

                        if (uiState.isTestingApi) {
                            OutlinedButton(
                                onClick = onCancelApiTest,
                                border = BorderStroke(1.dp, ZoyaCoralError),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .height(48.dp)
                                    .testTag("cancel_api_test_button")
                            ) {
                                Icon(Icons.Rounded.Cancel, contentDescription = "Cancel API test", tint = ZoyaCoralError, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Cancel", color = ZoyaCoralError)
                            }
                        }
                    }
                }
            }

            // API Key Input Card
            if (!uiState.hasApiKey || isEditingExisting) {
                ZoyaGlassCard(borderColor = ZoyaNeonPurple) {
                    Text(
                        text = "Enter Google Gemini API Key",
                        style = MaterialTheme.typography.titleMedium,
                        color = ZoyaTextPrimary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Paste your key from Google AI Studio (aistudio.google.com/apikey) to activate Gemini 3.8 Flash.",
                        style = MaterialTheme.typography.bodySmall,
                        color = ZoyaTextSecondary
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = apiKeyInput,
                        onValueChange = { apiKeyInput = it },
                        label = { Text("Gemini API Key") },
                        placeholder = { Text("AIza...", color = ZoyaTextMuted) },
                        singleLine = true,
                        visualTransformation = if (showApiKey) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(
                                onClick = { showApiKey = !showApiKey },
                                modifier = Modifier.testTag("toggle_api_key_visibility")
                            ) {
                                Icon(
                                    imageVector = if (showApiKey) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                    contentDescription = stringResource(R.string.cd_toggle_api_visibility),
                                    tint = ZoyaNeonCyan
                                )
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ZoyaNeonCyan,
                            unfocusedBorderColor = ZoyaBorderSubtle,
                            focusedTextColor = ZoyaTextPrimary,
                            unfocusedTextColor = ZoyaTextPrimary,
                            focusedLabelColor = ZoyaNeonCyan,
                            unfocusedLabelColor = ZoyaTextSecondary
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("api_key_input")
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (uiState.isTestingApi) {
                            OutlinedButton(
                                onClick = onCancelApiTest,
                                border = BorderStroke(1.dp, ZoyaCoralError),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(50.dp)
                                    .testTag("cancel_api_test_button")
                            ) {
                                Icon(Icons.Rounded.Cancel, contentDescription = "Cancel API test", tint = ZoyaCoralError, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Cancel Test", color = ZoyaCoralError, fontWeight = FontWeight.SemiBold)
                            }
                        } else {
                            OutlinedButton(
                                onClick = { onTestApi(apiKeyInput, uiState.activeModelId) },
                                border = BorderStroke(1.dp, ZoyaElectricBlue),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(50.dp)
                                    .testTag("test_api_button")
                            ) {
                                Icon(Icons.Rounded.NetworkCheck, contentDescription = null, tint = ZoyaElectricBlue, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Test API", color = ZoyaElectricBlue, fontWeight = FontWeight.SemiBold)
                            }
                        }

                        Button(
                            onClick = {
                                onSaveApiKey(apiKeyInput)
                                if (apiKeyInput.isNotBlank()) {
                                    isEditingExisting = false
                                    apiKeyInput = ""
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ZoyaNeonPurple,
                                contentColor = ZoyaObsidian
                            ),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp)
                                .testTag("save_api_button")
                        ) {
                            Icon(Icons.Rounded.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Save Key", fontWeight = FontWeight.Bold)
                        }
                    }

                    if (uiState.hasApiKey) {
                        Spacer(modifier = Modifier.height(6.dp))
                        TextButton(
                            onClick = { isEditingExisting = false },
                            modifier = Modifier.align(Alignment.End)
                        ) {
                            Text("Keep Current Key", color = ZoyaTextSecondary)
                        }
                    }
                }
            }

            // Live API Test / Model Verification / Exact Raw Error Diagnostic Card
            uiState.apiTestFeedback?.let { feedback ->
                val isOk = uiState.apiTestSuccess == true
                val accent = when (uiState.apiTestSuccess) {
                    true -> ZoyaEmerald
                    false -> ZoyaCoralError
                    null -> ZoyaNeonCyan
                }
                ZoyaGlassCard(borderColor = accent) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = if (isOk) Icons.Rounded.CheckCircle else Icons.Rounded.ErrorOutline,
                                contentDescription = null,
                                tint = accent,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = uiState.apiTestTitle ?: if (isOk) "Connected" else "Diagnostic Result",
                                style = MaterialTheme.typography.titleSmall,
                                color = accent,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        uiState.apiTestHttpCode?.let { code ->
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = accent.copy(alpha = 0.16f),
                                border = BorderStroke(1.dp, accent.copy(alpha = 0.55f))
                            ) {
                                Text(
                                    text = "HTTP $code",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = accent,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = feedback,
                        style = MaterialTheme.typography.bodyMedium,
                        color = ZoyaTextPrimary
                    )
                    if (!uiState.apiTestRawError.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = ZoyaObsidian.copy(alpha = 0.78f),
                            border = BorderStroke(1.dp, ZoyaBorderSubtle),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(
                                    text = "Google Gemini API Response:",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = ZoyaTextMuted
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = uiState.apiTestRawError,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = ZoyaTextSecondary
                                )
                            }
                        }
                    }
                }
            }

            // Active Model & Optional User-Selected Alternative Models Card
            ZoyaGlassCard(borderColor = ZoyaNeonCyan) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.Memory,
                            contentDescription = null,
                            tint = ZoyaNeonCyan,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = activeModel?.displayName ?: uiState.activeModelName,
                            style = MaterialTheme.typography.titleMedium,
                            color = ZoyaTextPrimary
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = ZoyaNeonPurple.copy(alpha = 0.22f)
                    ) {
                        Text(
                            text = uiState.activeModelId,
                            style = MaterialTheme.typography.labelSmall,
                            color = ZoyaNeonCyan,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = activeModel?.description.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = ZoyaTextSecondary
                )

                if (activeModel != null && activeModel.capabilities.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        activeModel.capabilities.forEach { cap ->
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = ZoyaObsidian.copy(alpha = 0.65f),
                                border = BorderStroke(1.dp, ZoyaBorderSubtle)
                            ) {
                                Text(
                                    text = cap,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = ZoyaTextSecondary,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Primary & Optional Models (Manual Selection Only)",
                    style = MaterialTheme.typography.labelLarge,
                    color = ZoyaTextPrimary
                )
                Spacer(modifier = Modifier.height(6.dp))
                availableModels.forEach { option ->
                    val selected = option.id == uiState.activeModelId
                    Surface(
                        onClick = { onSelectModel(option.id) },
                        shape = RoundedCornerShape(14.dp),
                        color = if (selected) ZoyaNeonPurple.copy(alpha = 0.18f) else ZoyaObsidian.copy(alpha = 0.5f),
                        border = BorderStroke(1.dp, if (selected) ZoyaNeonCyan else ZoyaBorderSubtle),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .testTag("select_model_${option.id}")
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
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = option.displayName,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = ZoyaTextPrimary
                                    )
                                    Surface(
                                        shape = RoundedCornerShape(50),
                                        color = if (option.isPrimary) ZoyaEmerald.copy(alpha = 0.18f) else ZoyaAmber.copy(alpha = 0.16f)
                                    ) {
                                        Text(
                                            text = option.badge,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (option.isPrimary) ZoyaEmerald else ZoyaAmber,
                                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = option.id,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = ZoyaTextMuted
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Strict Model Policy: gemini-3.8-flash is primary. Zoya never switches or downgrades models silently.",
                    style = MaterialTheme.typography.bodySmall,
                    color = ZoyaAmber
                )

                Spacer(modifier = Modifier.height(10.dp))
                OutlinedButton(
                    onClick = onOpenModelInfo,
                    border = BorderStroke(1.dp, ZoyaNeonCyan),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("open_model_info_button")
                ) {
                    Icon(Icons.Rounded.Info, contentDescription = null, tint = ZoyaNeonCyan, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("View Model Information & Voice Architecture", color = ZoyaNeonCyan)
                }
            }

            // Skip option during initial first-launch onboarding
            if (isOnboardingMode && onSkipForNow != null) {
                OutlinedButton(
                    onClick = onSkipForNow,
                    border = BorderStroke(1.dp, ZoyaBorderSubtle),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("skip_api_setup_button")
                ) {
                    Text(
                        text = "Continue Offline (Phone Commands & Settings)",
                        color = ZoyaTextSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
