package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ui.ZoyaAvatarState
import com.example.ui.theme.ZoyaAmber
import com.example.ui.theme.ZoyaCoralError
import com.example.ui.theme.ZoyaElectricBlue
import com.example.ui.theme.ZoyaEmerald
import com.example.ui.theme.ZoyaNeonCyan
import com.example.ui.theme.ZoyaNeonPink
import com.example.ui.theme.ZoyaNeonPurple
import com.example.ui.theme.ZoyaObsidian
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Animated ZOYA AI Anime Girl Avatar Stage.
 * Renders the pixel-aligned anime character across all 6 animated states (IDLE, LISTENING,
 * THINKING, SPEAKING, HAPPY, ERROR) with natural breathing, organic sway, periodic blinking,
 * real-time audio-reactive neon halos, orbiting neural rings, and live lip-sync cross-blending.
 */
@Composable
fun ZoyaAvatarStage(
    avatarState: ZoyaAvatarState,
    micAmplitude: Float,
    speechLipSync: Float,
    onAvatarTap: () -> Unit,
    modifier: Modifier = Modifier,
    avatarDiameter: Dp = 256.dp
) {
    val infinite = rememberInfiniteTransition(label = "zoya_avatar_motion")

    // Natural breathing and gentle floating motion
    val breathPhase by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breath_phase"
    )

    // Subtle organic hair/head sway
    val headSway by infinite.animateFloat(
        initialValue = -1.1f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 4800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "head_sway"
    )

    // Continuous ring rotation for thinking & neon energy arcs
    val ringRotation by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 5200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ring_rotation"
    )

    val counterRotation by infinite.animateFloat(
        initialValue = 360f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "counter_rotation"
    )

    // Periodic natural blink pulse (subtle eyelid shimmer in IDLE / LISTENING)
    var blinkAmount by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(avatarState) {
        while (true) {
            delay(3600L)
            if (avatarState == ZoyaAvatarState.IDLE || avatarState == ZoyaAvatarState.LISTENING) {
                blinkAmount = 1f
                delay(120L)
                blinkAmount = 0f
            }
        }
    }

    val primaryRingColor by animateColorAsState(
        targetValue = when (avatarState) {
            ZoyaAvatarState.IDLE -> ZoyaNeonPurple
            ZoyaAvatarState.LISTENING -> ZoyaNeonCyan
            ZoyaAvatarState.THINKING -> ZoyaNeonPink
            ZoyaAvatarState.SPEAKING -> ZoyaElectricBlue
            ZoyaAvatarState.HAPPY -> ZoyaEmerald
            ZoyaAvatarState.ERROR -> ZoyaCoralError
        },
        animationSpec = tween(350),
        label = "primary_ring_color"
    )

    val secondaryRingColor by animateColorAsState(
        targetValue = when (avatarState) {
            ZoyaAvatarState.IDLE -> ZoyaElectricBlue
            ZoyaAvatarState.LISTENING -> ZoyaNeonPurple
            ZoyaAvatarState.THINKING -> ZoyaNeonCyan
            ZoyaAvatarState.SPEAKING -> ZoyaNeonPurple
            ZoyaAvatarState.HAPPY -> ZoyaNeonCyan
            ZoyaAvatarState.ERROR -> ZoyaAmber
        },
        animationSpec = tween(350),
        label = "secondary_ring_color"
    )

    val smoothMic by animateFloatAsState(
        targetValue = if (avatarState == ZoyaAvatarState.LISTENING) micAmplitude else 0f,
        animationSpec = spring(dampingRatio = 0.65f, stiffness = 320f),
        label = "smooth_mic"
    )

    // Real-time lip-sync alpha blending between open-mouth speaking & composed portrait
    val speakingOverlayAlpha by animateFloatAsState(
        targetValue = when (avatarState) {
            ZoyaAvatarState.SPEAKING -> (0.25f + speechLipSync * 0.75f).coerceIn(0f, 1f)
            else -> 0f
        },
        animationSpec = tween(durationMillis = 65, easing = LinearEasing),
        label = "lip_sync_alpha"
    )

    val listeningOverlayAlpha by animateFloatAsState(
        targetValue = if (avatarState == ZoyaAvatarState.LISTENING) 1f else 0f,
        animationSpec = tween(220),
        label = "listening_alpha"
    )

    val happyOverlayAlpha by animateFloatAsState(
        targetValue = if (avatarState == ZoyaAvatarState.HAPPY) 1f else 0f,
        animationSpec = tween(260),
        label = "happy_alpha"
    )

    val energyPulse = when (avatarState) {
        ZoyaAvatarState.LISTENING -> 0.35f + smoothMic * 0.65f
        ZoyaAvatarState.SPEAKING -> 0.3f + speechLipSync * 0.7f
        ZoyaAvatarState.THINKING -> 0.65f + breathPhase * 0.35f
        ZoyaAvatarState.HAPPY -> 0.75f + breathPhase * 0.25f
        ZoyaAvatarState.ERROR -> 0.45f + breathPhase * 0.3f
        ZoyaAvatarState.IDLE -> 0.25f + breathPhase * 0.2f
    }

    val stageSize = avatarDiameter + 72.dp
    val avatarCd = stringResource(R.string.cd_zoya_avatar)

    Box(
        modifier = modifier
            .size(stageSize)
            .testTag("zoya_avatar_stage")
            .semantics { contentDescription = "$avatarCd, state ${avatarState.label}" }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Button,
                onClick = onAvatarTap
            ),
        contentAlignment = Alignment.Center
    ) {
        // Multi-layered Neon Holographic Frame & Audio/Thinking/Happy Rings
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val baseRadius = (avatarDiameter.toPx() / 2f)

            // Ambient deep radial aura behind Zoya
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        primaryRingColor.copy(alpha = 0.36f * energyPulse + 0.14f),
                        secondaryRingColor.copy(alpha = 0.14f * energyPulse),
                        Color.Transparent
                    ),
                    center = center,
                    radius = size.minDimension * 0.52f
                ),
                radius = size.minDimension * 0.52f,
                center = center
            )

            // Outer audio-reactive / state-reactive wave ring
            val outerRadius = baseRadius + 18.dp.toPx() + (energyPulse * 10.dp.toPx())
            drawCircle(
                brush = Brush.sweepGradient(
                    colors = listOf(
                        primaryRingColor.copy(alpha = 0.75f),
                        secondaryRingColor.copy(alpha = 0.18f),
                        primaryRingColor.copy(alpha = 0.85f),
                        secondaryRingColor.copy(alpha = 0.3f),
                        primaryRingColor.copy(alpha = 0.75f)
                    ),
                    center = center
                ),
                radius = outerRadius,
                center = center,
                style = Stroke(width = 2.dp.toPx())
            )

            // Rotating holographic segmented arc (intensified in THINKING & LISTENING)
            val arcRadius = baseRadius + 10.dp.toPx()
            rotate(degrees = ringRotation, pivot = center) {
                val sweep = if (avatarState == ZoyaAvatarState.THINKING) 115f else 75f
                val strokeWidth = if (avatarState == ZoyaAvatarState.THINKING) 4.dp.toPx() else 2.5.dp.toPx()
                for (i in 0 until 3) {
                    drawArc(
                        brush = Brush.linearGradient(listOf(primaryRingColor, secondaryRingColor)),
                        startAngle = i * 120f,
                        sweepAngle = sweep,
                        useCenter = false,
                        topLeft = Offset(center.x - arcRadius, center.y - arcRadius),
                        size = Size(arcRadius * 2f, arcRadius * 2f),
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                    )
                }
            }

            // Counter-rotating neural nodes in THINKING mode
            if (avatarState == ZoyaAvatarState.THINKING) {
                val nodeOrbit = baseRadius + 24.dp.toPx()
                rotate(degrees = counterRotation, pivot = center) {
                    for (i in 0 until 6) {
                        val angleRad = (i * 60.0) * PI / 180.0
                        val nx = center.x + (cos(angleRad) * nodeOrbit).toFloat()
                        val ny = center.y + (sin(angleRad) * nodeOrbit).toFloat()
                        drawCircle(
                            color = if (i % 2 == 0) ZoyaNeonCyan else ZoyaNeonPink,
                            radius = 4.dp.toPx(),
                            center = Offset(nx, ny)
                        )
                    }
                }
            }

            // Live equalizer bars around circular frame during LISTENING or SPEAKING
            if (avatarState == ZoyaAvatarState.LISTENING || avatarState == ZoyaAvatarState.SPEAKING) {
                val barCount = 36
                val amplitude = if (avatarState == ZoyaAvatarState.LISTENING) {
                    0.22f + smoothMic * 0.78f
                } else {
                    0.20f + speechLipSync * 0.80f
                }
                for (i in 0 until barCount) {
                    val angleDeg = i * (360f / barCount) + ringRotation * 0.35f
                    val angleRad = angleDeg * PI / 180.0
                    val waveMod = (sin(i * 0.9 + ringRotation * 0.08) + 1.0).toFloat() * 0.5f
                    val barLen = (5.dp.toPx() + 14.dp.toPx() * amplitude * (0.35f + 0.65f * waveMod))
                    val innerR = baseRadius + 6.dp.toPx()
                    val outerR = innerR + barLen
                    val start = Offset(
                        center.x + (cos(angleRad) * innerR).toFloat(),
                        center.y + (sin(angleRad) * innerR).toFloat()
                    )
                    val end = Offset(
                        center.x + (cos(angleRad) * outerR).toFloat(),
                        center.y + (sin(angleRad) * outerR).toFloat()
                    )
                    drawLine(
                        color = if (i % 2 == 0) primaryRingColor.copy(alpha = 0.85f)
                        else secondaryRingColor.copy(alpha = 0.85f),
                        start = start,
                        end = end,
                        strokeWidth = 2.6.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }
            }

            // Celebratory floating starlight particles during HAPPY state
            if (avatarState == ZoyaAvatarState.HAPPY) {
                for (i in 0 until 8) {
                    val angleRad = ((i * 45f) + ringRotation * 0.5f) * PI / 180.0
                    val dist = baseRadius + 16.dp.toPx() + (breathPhase * 12.dp.toPx())
                    val sx = center.x + (cos(angleRad) * dist).toFloat()
                    val sy = center.y + (sin(angleRad) * dist).toFloat()
                    drawCircle(
                        color = if (i % 2 == 0) ZoyaEmerald else ZoyaNeonCyan,
                        radius = (3.5f + breathPhase * 2f).dp.toPx(),
                        center = Offset(sx, sy)
                    )
                }
            }
        }

        // Circular glowing ZOYA Anime Portrait with Live Breathing, Sway, Expression Cross-blending & Lip-sync
        val scaleBreath = 1.0f + (breathPhase * 0.016f) +
            (if (avatarState == ZoyaAvatarState.SPEAKING) speechLipSync * 0.014f else 0f) +
            (if (avatarState == ZoyaAvatarState.LISTENING) smoothMic * 0.015f else 0f)
        val verticalFloatPx = (breathPhase - 0.5f) * -6f

        Box(
            modifier = Modifier
                .size(avatarDiameter)
                .clip(CircleShape)
                .background(ZoyaObsidian)
                .border(
                    width = 3.dp,
                    brush = Brush.linearGradient(
                        listOf(primaryRingColor, secondaryRingColor, primaryRingColor)
                    ),
                    shape = CircleShape
                )
        ) {
            val characterTransform = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scaleBreath
                    scaleY = scaleBreath
                    translationY = verticalFloatPx
                    rotationZ = if (avatarState == ZoyaAvatarState.THINKING) headSway * 1.4f else headSway * 0.65f
                }

            // Base layer: IDLE composed portrait (always present for zero-flicker blending)
            Image(
                painter = painterResource(id = R.drawable.img_zoya_idle_1795676005655),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = characterTransform
            )

            // LISTENING layer: Attentive electric-blue headphone portrait
            if (listeningOverlayAlpha > 0.01f) {
                Image(
                    painter = painterResource(id = R.drawable.img_zoya_listening_1795676132424),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = characterTransform.graphicsLayer { alpha = listeningOverlayAlpha }
                )
            }

            // SPEAKING layer: Real-time lip-sync blended open-mouth speaking portrait
            if (speakingOverlayAlpha > 0.01f) {
                Image(
                    painter = painterResource(id = R.drawable.img_zoya_speaking_1795676130912),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = characterTransform.graphicsLayer { alpha = speakingOverlayAlpha }
                )
            }

            // HAPPY layer: Radiant smiling celebration portrait
            if (happyOverlayAlpha > 0.01f) {
                Image(
                    painter = painterResource(id = R.drawable.img_zoya_happy_1795676130422),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = characterTransform.graphicsLayer { alpha = happyOverlayAlpha }
                )
            }

            // Subtle state lighting tint (THINKING holographic sheen / ERROR calm amber rim / natural blink)
            Canvas(modifier = Modifier.fillMaxSize()) {
                when (avatarState) {
                    ZoyaAvatarState.THINKING -> {
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    ZoyaNeonPurple.copy(alpha = 0.24f * breathPhase + 0.08f)
                                )
                            )
                        )
                    }
                    ZoyaAvatarState.ERROR -> {
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    ZoyaCoralError.copy(alpha = 0.28f)
                                )
                            )
                        )
                    }
                    else -> Unit
                }

                // Natural subtle blink highlight across the upper eye band
                if (blinkAmount > 0.5f) {
                    drawRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                ZoyaObsidian.copy(alpha = 0.22f),
                                Color.Transparent
                            ),
                            startY = size.height * 0.36f,
                            endY = size.height * 0.48f
                        )
                    )
                }
            }
        }
    }
}
