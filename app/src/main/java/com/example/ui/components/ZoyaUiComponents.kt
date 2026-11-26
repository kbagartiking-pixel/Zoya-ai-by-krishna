package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Sms
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ai.AiConnectionState
import com.example.ui.PendingConfirmation
import com.example.ui.theme.ZoyaAmber
import com.example.ui.theme.ZoyaBorderSubtle
import com.example.ui.theme.ZoyaCardSurface
import com.example.ui.theme.ZoyaCoralError
import com.example.ui.theme.ZoyaDeepSurface
import com.example.ui.theme.ZoyaElectricBlue
import com.example.ui.theme.ZoyaEmerald
import com.example.ui.theme.ZoyaNeonCyan
import com.example.ui.theme.ZoyaNeonPurple
import com.example.ui.theme.ZoyaObsidian
import com.example.ui.theme.ZoyaTextPrimary
import com.example.ui.theme.ZoyaTextSecondary

@Composable
fun ZoyaGlassCard(
    modifier: Modifier = Modifier,
    borderColor: Color = ZoyaBorderSubtle,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(20.dp)
    val baseModifier = modifier
        .clip(shape)
        .background(
            Brush.verticalGradient(
                listOf(
                    ZoyaCardSurface.copy(alpha = 0.92f),
                    ZoyaDeepSurface.copy(alpha = 0.96f)
                )
            )
        )
        .border(BorderStroke(1.dp, borderColor.copy(alpha = 0.65f)), shape)
        .let { if (onClick != null) it.clickable(onClick = onClick) else it }
        .padding(16.dp)

    Column(modifier = baseModifier, content = content)
}

@Composable
fun ZoyaTopBar(
    title: String,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (onBack != null) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("back_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = stringResource(R.string.cd_back),
                    tint = ZoyaTextPrimary
                )
            }
            Spacer(modifier = Modifier.width(6.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = ZoyaTextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = ZoyaTextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (trailing != null) {
            trailing()
        }
    }
}

@Composable
fun ConnectionStatusPill(
    state: AiConnectionState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val (dotColor, label) = when (state) {
        AiConnectionState.CONNECTED -> ZoyaEmerald to "AI READY"
        AiConnectionState.CHECKING -> ZoyaNeonCyan to "CHECKING"
        AiConnectionState.OFFLINE -> ZoyaAmber to "OFFLINE"
        AiConnectionState.NOT_CONFIGURED -> ZoyaAmber to "SET API KEY"
        AiConnectionState.INVALID_KEY -> ZoyaCoralError to "KEY ERROR"
        AiConnectionState.QUOTA_EXCEEDED -> ZoyaCoralError to "QUOTA LIMIT"
        AiConnectionState.SERVER_OVERLOADED -> ZoyaAmber to "HIGH LOAD"
        AiConnectionState.MODEL_UNAVAILABLE -> ZoyaCoralError to "MODEL UNVAIL"
        AiConnectionState.TIMEOUT -> ZoyaAmber to "TIMEOUT"
        AiConnectionState.ERROR -> ZoyaCoralError to "AI ERROR"
    }

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = ZoyaCardSurface.copy(alpha = 0.9f),
        border = BorderStroke(1.dp, dotColor.copy(alpha = 0.55f)),
        modifier = modifier.testTag("connection_status_pill")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(dotColor)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = ZoyaTextPrimary
            )
        }
    }
}

@Composable
fun ZoyaConfirmationDialog(
    pending: PendingConfirmation,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val (icon, tint, title, body, confirmLabel) = when (pending) {
        is PendingConfirmation.Call -> Tuple5(
            Icons.Rounded.Call,
            ZoyaNeonCyan,
            "Confirm Phone Dialer",
            "Zoya will open your phone dialer ready for '${pending.displayLabel}'. You stay in full control before placing the call.",
            "Open Dialer"
        )
        is PendingConfirmation.Sms -> Tuple5(
            Icons.Rounded.Sms,
            ZoyaNeonPurple,
            "Confirm SMS Composer",
            "Zoya will open your messaging app addressed to '${pending.phoneNumber.ifBlank { "recipient" }}'${if (pending.body.isNotBlank()) " with message: \"${pending.body}\"" else ""}.",
            "Prepare SMS"
        )
        PendingConfirmation.ClearHistory -> Tuple5(
            Icons.Rounded.WarningAmber,
            ZoyaAmber,
            "Clear Conversation History?",
            "This permanently removes all saved Zoya AI voice and text transcripts from this device.",
            "Clear History"
        )
        PendingConfirmation.ClearAllData -> Tuple5(
            Icons.Rounded.DeleteForever,
            ZoyaCoralError,
            "Clear All Local Data?",
            "This deletes your encrypted API key, reminders, conversation history, and preferences from this device.",
            "Erase All"
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ZoyaCardSurface,
        icon = { Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(30.dp)) },
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                color = ZoyaTextPrimary
            )
        },
        text = {
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = ZoyaTextSecondary
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (pending == PendingConfirmation.ClearAllData) ZoyaCoralError else ZoyaNeonPurple,
                    contentColor = ZoyaObsidian
                ),
                modifier = Modifier.testTag("confirm_dialog_ok")
            ) {
                Text(confirmLabel, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                border = BorderStroke(1.dp, ZoyaBorderSubtle),
                modifier = Modifier.testTag("confirm_dialog_cancel")
            ) {
                Text("Cancel", color = ZoyaTextPrimary)
            }
        }
    )
}

private data class Tuple5<A, B, C, D, E>(
    val a: A,
    val b: B,
    val c: C,
    val d: D,
    val e: E
)
