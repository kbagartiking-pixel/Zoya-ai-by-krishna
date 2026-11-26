package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Stop
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ai.AiConnectionState
import com.example.ai.AiModelOption
import com.example.data.prefs.ZoyaSettings
import com.example.ui.ZoyaAvatarState
import com.example.ui.ZoyaUiState
import com.example.ui.components.ConnectionStatusPill
import com.example.ui.components.ZoyaAvatarStage
import com.example.ui.components.ZoyaGlassCard
import com.example.ui.theme.ZoyaAmber
import com.example.ui.theme.ZoyaBorderSubtle
import com.example.ui.theme.ZoyaCardSurface
import com.example.ui.theme.ZoyaCoralError
import com.example.ui.theme.ZoyaDeepSurface
import com.example.ui.theme.ZoyaElectricBlue
import com.example.ui.theme.ZoyaEmerald
import com.example.ui.theme.ZoyaNeonCyan
import com.example.ui.theme.ZoyaNeonPink
import com.example.ui.theme.ZoyaNeonPurple
import com.example.ui.theme.ZoyaObsidian
import com.example.ui.theme.ZoyaTextMuted
import com.example.ui.theme.ZoyaTextPrimary
import com.example.ui.theme.ZoyaTextSecondary

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ZoyaHomeScreen(
    uiState: ZoyaUiState,
    settings: ZoyaSettings,
    availableModels: List<AiModelOption>,
    micAmplitude: Float,
    speechLipSync: Float,
    onTapToTalk: () -> Unit,
    onStop: () -> Unit,
    onCancelRequest: () -> Unit,
    onRetryRequest: () -> Unit,
    onSelectAlternativeAndRetry: (String) -> Unit,
    onSendText: (String) -> Boolean,
    onPickImage: () -> Unit,
    onClearAttachedImage: () -> Unit,
    onOpenApiConfig: () -> Unit,
    onOpenModelInfo: () -> Unit,
    onOpenAppControl: () -> Unit,
    onOpenFeatures: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showKeyboardInput by rememberSaveable { mutableStateOf(false) }
    var typedInput by rememberSaveable { mutableStateOf("") }

    val statusColor by animateColorAsState(
        targetValue = when (uiState.avatarState) {
            ZoyaAvatarState.IDLE -> ZoyaNeonPurple
            ZoyaAvatarState.LISTENING -> ZoyaNeonCyan
            ZoyaAvatarState.THINKING -> ZoyaNeonPink
            ZoyaAvatarState.SPEAKING -> ZoyaElectricBlue
            ZoyaAvatarState.HAPPY -> ZoyaEmerald
            ZoyaAvatarState.ERROR -> ZoyaCoralError
        },
        animationSpec = tween(300),
        label = "status_color"
    )

    val isActiveVoice = uiState.avatarState == ZoyaAvatarState.LISTENING ||
        uiState.avatarState == ZoyaAvatarState.SPEAKING ||
        uiState.avatarState == ZoyaAvatarState.THINKING ||
        uiState.isRequestInFlight

    val canSubmitText = !uiState.isRequestInFlight &&
        (typedInput.isNotBlank() || uiState.attachedImage != null)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0A0618),
                        ZoyaObsidian,
                        Color(0xFF070A16)
                    )
                )
            )
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            val isCompactHeight = maxHeight < 680.dp
            val avatarSize = if (isCompactHeight) 192.dp else 232.dp

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 580.dp)
                    .align(Alignment.TopCenter)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(8.dp))

                // Top Brand Bar: ZOYA AI Logo + Connection Status Pill + Settings Button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = stringResource(R.string.zoya_brand_title),
                            style = MaterialTheme.typography.titleLarge,
                            color = ZoyaTextPrimary
                        )
                        // Active Model Indicator (Tappable to open Model Info)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier
                                .clickable(onClick = onOpenModelInfo)
                                .padding(top = 2.dp)
                                .testTag("active_model_badge")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Memory,
                                contentDescription = "Active AI model",
                                tint = ZoyaNeonCyan,
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = uiState.activeModelName,
                                style = MaterialTheme.typography.labelMedium,
                                color = ZoyaNeonCyan
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ConnectionStatusPill(
                            state = uiState.connectionState,
                            onClick = onOpenApiConfig
                        )
                        Surface(
                            onClick = onOpenSettings,
                            shape = CircleShape,
                            color = ZoyaCardSurface,
                            border = BorderStroke(1.dp, ZoyaBorderSubtle),
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("home_settings_button")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Rounded.Settings,
                                    contentDescription = stringResource(R.string.cd_settings),
                                    tint = ZoyaTextPrimary,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(if (isCompactHeight) 6.dp else 12.dp))

                // Centerpiece: Animated Anime Girl ZOYA AI Avatar
                ZoyaAvatarStage(
                    avatarState = uiState.avatarState,
                    micAmplitude = micAmplitude,
                    speechLipSync = speechLipSync,
                    onAvatarTap = {
                        if (isActiveVoice) onStop() else onTapToTalk()
                    },
                    avatarDiameter = avatarSize
                )

                // Live State & Language Indicator Pill
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = statusColor.copy(alpha = 0.16f),
                        border = BorderStroke(1.dp, statusColor.copy(alpha = 0.65f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(7.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(statusColor)
                            )
                            Text(
                                text = uiState.avatarState.label,
                                style = MaterialTheme.typography.labelSmall,
                                color = statusColor
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(50),
                        color = ZoyaCardSurface,
                        border = BorderStroke(1.dp, ZoyaBorderSubtle)
                    ) {
                        Text(
                            text = uiState.detectedLanguage.label,
                            style = MaterialTheme.typography.labelMedium,
                            color = ZoyaTextSecondary,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                        )
                    }

                    uiState.lastCommandBadge?.let { badge ->
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = ZoyaNeonPurple.copy(alpha = 0.18f),
                            border = BorderStroke(1.dp, ZoyaNeonPurple.copy(alpha = 0.45f))
                        ) {
                            Text(
                                text = badge,
                                style = MaterialTheme.typography.labelMedium,
                                color = ZoyaTextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .widthIn(max = 155.dp)
                                    .padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Active Request & Retry Progress Banner + Cancel Request Button
                AnimatedVisibility(visible = uiState.isRequestInFlight) {
                    ZoyaGlassCard(
                        borderColor = ZoyaNeonPink,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            CircularProgressIndicator(
                                color = ZoyaNeonCyan,
                                strokeWidth = 2.5.dp,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = uiState.retryStatusText
                                    ?: "Contacting ${uiState.activeModelId}...",
                                style = MaterialTheme.typography.bodySmall,
                                color = ZoyaTextPrimary,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedButton(
                                onClick = onCancelRequest,
                                border = BorderStroke(1.dp, ZoyaCoralError),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = ZoyaCoralError.copy(alpha = 0.14f)
                                ),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .height(48.dp)
                                    .testTag("cancel_request_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Cancel,
                                    contentDescription = "Cancel API request",
                                    tint = ZoyaCoralError,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = "Cancel Request",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = ZoyaCoralError,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                // Structured API Error Diagnostic, Preserved Prompt, Retry & Optional Alternative Model Card
                AnimatedVisibility(
                    visible = !uiState.isRequestInFlight && uiState.lastErrorTitle != null
                ) {
                    val isTempIssue = uiState.connectionState == AiConnectionState.SERVER_OVERLOADED ||
                        uiState.connectionState == AiConnectionState.TIMEOUT
                    val cardAccent = if (isTempIssue) ZoyaAmber else ZoyaCoralError
                    val needsApiSettings = uiState.connectionState == AiConnectionState.INVALID_KEY ||
                        uiState.connectionState == AiConnectionState.NOT_CONFIGURED ||
                        uiState.connectionState == AiConnectionState.QUOTA_EXCEEDED

                    ZoyaGlassCard(
                        borderColor = cardAccent,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 10.dp)
                            .testTag("api_error_diagnostic_card")
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
                                    imageVector = Icons.Rounded.ErrorOutline,
                                    contentDescription = null,
                                    tint = cardAccent,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = uiState.lastErrorTitle.orEmpty(),
                                    style = MaterialTheme.typography.titleSmall,
                                    color = cardAccent,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            uiState.lastErrorHttpCode?.let { httpCode ->
                                Surface(
                                    shape = RoundedCornerShape(50),
                                    color = cardAccent.copy(alpha = 0.18f),
                                    border = BorderStroke(1.dp, cardAccent.copy(alpha = 0.6f))
                                ) {
                                    Text(
                                        text = "HTTP $httpCode",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = cardAccent,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                            }
                        }

                        uiState.errorBanner?.let { explanation ->
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = explanation,
                                style = MaterialTheme.typography.bodySmall,
                                color = ZoyaTextPrimary
                            )
                        }

                        if (!uiState.lastRawApiError.isNullOrBlank()) {
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
                                        text = uiState.lastRawApiError,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = ZoyaTextSecondary,
                                        modifier = Modifier.testTag("raw_gemini_error_text")
                                    )
                                }
                            }
                        }

                        if (!uiState.failedPrompt.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Saved message: \"${uiState.failedPrompt}\"${if (uiState.failedImage != null) " + Photo" else ""}",
                                style = MaterialTheme.typography.bodySmall,
                                color = ZoyaNeonCyan,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.testTag("preserved_message_text")
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (!uiState.failedPrompt.isNullOrBlank()) {
                                Button(
                                    onClick = onRetryRequest,
                                    enabled = !uiState.isRequestInFlight,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = ZoyaElectricBlue,
                                        contentColor = ZoyaObsidian
                                    ),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .testTag("retry_request_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Refresh,
                                        contentDescription = "Retry request",
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Retry", fontWeight = FontWeight.Bold)
                                }
                            }

                            if (needsApiSettings) {
                                OutlinedButton(
                                    onClick = onOpenApiConfig,
                                    border = BorderStroke(1.dp, ZoyaNeonPurple),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .testTag("error_open_api_config_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Key,
                                        contentDescription = "API settings",
                                        tint = ZoyaNeonPurple,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("API Key", color = ZoyaTextPrimary)
                                }
                            } else if (!uiState.failedPrompt.isNullOrBlank()) {
                                OutlinedButton(
                                    onClick = {
                                        typedInput = uiState.failedPrompt
                                        showKeyboardInput = true
                                    },
                                    border = BorderStroke(1.dp, ZoyaBorderSubtle),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .height(48.dp)
                                        .testTag("edit_preserved_message_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Edit,
                                        contentDescription = "Edit saved message",
                                        tint = ZoyaTextSecondary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Edit", color = ZoyaTextPrimary)
                                }
                            }
                        }

                        // Optional user-controlled alternative model selection (NEVER automatic)
                        val alternativeModels = availableModels.filter { !it.isPrimary }
                        if (uiState.showAlternativeModelOffer && alternativeModels.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "Optional: Try an alternative model (Zoya never switches automatically):",
                                style = MaterialTheme.typography.labelSmall,
                                color = ZoyaAmber
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                alternativeModels.forEach { alt ->
                                    OutlinedButton(
                                        onClick = { onSelectAlternativeAndRetry(alt.id) },
                                        enabled = !uiState.isRequestInFlight,
                                        border = BorderStroke(1.dp, ZoyaNeonCyan),
                                        shape = RoundedCornerShape(12.dp),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                        modifier = Modifier
                                            .height(48.dp)
                                            .testTag("alt_model_${alt.id}")
                                    ) {
                                        Text(
                                            text = "Try ${alt.displayName}",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = ZoyaNeonCyan
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Attached Image Chip for Vision Understanding
                AnimatedVisibility(visible = uiState.attachedImage != null) {
                    uiState.attachedImage?.let { img ->
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = ZoyaElectricBlue.copy(alpha = 0.16f),
                            border = BorderStroke(1.dp, ZoyaElectricBlue),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 10.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Image,
                                    contentDescription = null,
                                    tint = ZoyaNeonCyan,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "${img.label} ready for ${uiState.activeModelName} vision — tap Talk or type!",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = ZoyaTextPrimary,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(
                                    onClick = onClearAttachedImage,
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Close,
                                        contentDescription = "Remove attached photo",
                                        tint = ZoyaTextSecondary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // Live Caption & Streaming Response Card
                ZoyaGlassCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 92.dp),
                    borderColor = if (uiState.errorBanner != null) ZoyaCoralError else statusColor
                ) {
                    if (uiState.liveTranscript.isNotBlank()) {
                        Text(
                            text = "\"${uiState.liveTranscript}\"",
                            style = MaterialTheme.typography.bodySmall,
                            color = ZoyaNeonCyan,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                    }

                    Text(
                        text = uiState.zoyaReplyText.ifBlank {
                            if (uiState.avatarState == ZoyaAvatarState.THINKING) {
                                "${uiState.activeModelName} thinking..."
                            } else {
                                uiState.avatarState.tagline
                            }
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        color = ZoyaTextPrimary,
                        maxLines = 5,
                        overflow = TextOverflow.Ellipsis
                    )

                    if (!uiState.hasApiKey) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Tap 'SET API KEY' above to activate ${uiState.activeModelName}. Local phone commands work offline!",
                            style = MaterialTheme.typography.bodySmall,
                            color = ZoyaAmber,
                            modifier = Modifier.clickable(onClick = onOpenApiConfig)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Optional Quick Text Input Bar (Duplicate send protected while request is active)
                AnimatedVisibility(visible = showKeyboardInput || uiState.attachedImage != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = typedInput,
                            onValueChange = { typedInput = it },
                            placeholder = {
                                Text(
                                    if (uiState.attachedImage != null) "Ask about this photo..."
                                    else "Ask ${uiState.activeModelName} in English, Hindi or Hinglish...",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = ZoyaTextMuted
                                )
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
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                            keyboardActions = KeyboardActions(
                                onSend = {
                                    if (canSubmitText && onSendText(typedInput)) {
                                        typedInput = ""
                                    }
                                }
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("home_text_input")
                        )
                        Surface(
                            onClick = {
                                if (canSubmitText && onSendText(typedInput)) {
                                    typedInput = ""
                                }
                            },
                            enabled = canSubmitText,
                            shape = RoundedCornerShape(16.dp),
                            color = if (canSubmitText) ZoyaNeonPurple else ZoyaCardSurface,
                            border = if (canSubmitText) null else BorderStroke(1.dp, ZoyaBorderSubtle),
                            modifier = Modifier
                                .size(54.dp)
                                .testTag("home_send_button")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                if (uiState.isRequestInFlight) {
                                    CircularProgressIndicator(
                                        color = ZoyaNeonCyan,
                                        strokeWidth = 2.dp,
                                        modifier = Modifier.size(20.dp)
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Rounded.Send,
                                        contentDescription = stringResource(R.string.cd_send_message),
                                        tint = if (canSubmitText) ZoyaObsidian else ZoyaTextMuted
                                    )
                                }
                            }
                        }
                    }
                }

                // Primary Futuristic Voice Action Row: Tap to Talk + Stop + Vision Photo + Type toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = onTapToTalk,
                        shape = RoundedCornerShape(20.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.Transparent,
                            contentColor = ZoyaObsidian
                        ),
                        contentPadding = PaddingValues(0.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(
                                Brush.horizontalGradient(
                                    listOf(ZoyaNeonPurple, ZoyaElectricBlue)
                                )
                            )
                            .testTag("tap_to_talk_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.GraphicEq,
                            contentDescription = stringResource(R.string.cd_tap_to_talk),
                            tint = ZoyaObsidian,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (uiState.avatarState == ZoyaAvatarState.LISTENING) "LISTENING..." else "TAP TO TALK",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = ZoyaObsidian
                        )
                    }

                    OutlinedButton(
                        onClick = {
                            if (uiState.isRequestInFlight) onCancelRequest() else onStop()
                        },
                        shape = RoundedCornerShape(20.dp),
                        border = BorderStroke(
                            1.5.dp,
                            if (isActiveVoice) ZoyaCoralError else ZoyaBorderSubtle
                        ),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = if (isActiveVoice) ZoyaCoralError.copy(alpha = 0.16f) else ZoyaCardSurface
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp),
                        modifier = Modifier
                            .height(56.dp)
                            .testTag("stop_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Stop,
                            contentDescription = stringResource(R.string.cd_stop_assistant),
                            tint = if (isActiveVoice) ZoyaCoralError else ZoyaTextSecondary,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Surface(
                        onClick = onPickImage,
                        shape = RoundedCornerShape(20.dp),
                        color = if (uiState.attachedImage != null) ZoyaElectricBlue.copy(alpha = 0.22f) else ZoyaCardSurface,
                        border = BorderStroke(
                            1.dp,
                            if (uiState.attachedImage != null) ZoyaElectricBlue else ZoyaBorderSubtle
                        ),
                        modifier = Modifier
                            .size(56.dp)
                            .testTag("attach_image_button")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Rounded.AddPhotoAlternate,
                                contentDescription = "Attach photo for vision",
                                tint = if (uiState.attachedImage != null) ZoyaNeonCyan else ZoyaTextSecondary
                            )
                        }
                    }

                    Surface(
                        onClick = { showKeyboardInput = !showKeyboardInput },
                        shape = RoundedCornerShape(20.dp),
                        color = if (showKeyboardInput) ZoyaNeonPurple.copy(alpha = 0.22f) else ZoyaCardSurface,
                        border = BorderStroke(
                            1.dp,
                            if (showKeyboardInput) ZoyaNeonPurple else ZoyaBorderSubtle
                        ),
                        modifier = Modifier
                            .size(56.dp)
                            .testTag("toggle_keyboard_button")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Rounded.Keyboard,
                                contentDescription = "Toggle text command input",
                                tint = if (showKeyboardInput) ZoyaNeonCyan else ZoyaTextSecondary
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Quick Command Suggestions
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val quickPrompts = listOf(
                        "YouTube kholo",
                        "Battery kitni hai?",
                        "20 minute baad reminder dena",
                        "Open Wi-Fi settings",
                        "Device info"
                    )
                    quickPrompts.forEach { sample ->
                        Surface(
                            onClick = { onSendText(sample) },
                            enabled = !uiState.isRequestInFlight,
                            shape = RoundedCornerShape(50),
                            color = ZoyaDeepSurface,
                            border = BorderStroke(1.dp, ZoyaBorderSubtle)
                        ) {
                            Text(
                                text = sample,
                                style = MaterialTheme.typography.bodySmall,
                                color = ZoyaTextSecondary,
                                modifier = Modifier.padding(horizontal = 13.dp, vertical = 9.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Quick Navigation Deck: App Control, Features, Settings
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    QuickDeckCard(
                        icon = Icons.Rounded.Apps,
                        label = "App Control",
                        accent = ZoyaElectricBlue,
                        onClick = onOpenAppControl,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("home_app_control_card")
                    )
                    QuickDeckCard(
                        icon = Icons.Rounded.AutoAwesome,
                        label = "Features",
                        accent = ZoyaNeonPurple,
                        onClick = onOpenFeatures,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("home_features_card")
                    )
                    QuickDeckCard(
                        icon = Icons.Rounded.Settings,
                        label = "Settings",
                        accent = ZoyaNeonPink,
                        onClick = onOpenSettings,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("home_settings_card")
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))
            }
        }
    }
}

@Composable
private fun QuickDeckCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        color = ZoyaCardSurface,
        border = BorderStroke(1.dp, accent.copy(alpha = 0.4f)),
        modifier = modifier.height(74.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = accent,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = ZoyaTextPrimary,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
