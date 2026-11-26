package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.domain.model.AnimationIntensity
import com.example.ui.theme.LocalRiderColors
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun rememberIsAppResumed(): Boolean {
    val lifecycleOwner = LocalLifecycleOwner.current
    var isResumed by remember {
        mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
    }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, _ ->
            isResumed = lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    return isResumed
}

@Composable
fun rememberRunningBorderPhase(
    animationIntensity: AnimationIntensity,
    durationMs: Int = 5200
): Float {
    val isResumed = rememberIsAppResumed()
    if (!isResumed || animationIntensity == AnimationIntensity.MINIMAL) {
        return 0.15f
    }
    val adjustedDuration = if (animationIntensity == AnimationIntensity.REDUCED) {
        (durationMs * 1.6f).toInt()
    } else {
        durationMs
    }
    val transition = rememberInfiniteTransition(label = "running_border_phase")
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = adjustedDuration, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "border_angle"
    )
    return angle
}

enum class GlowTone {
    CYAN_BLUE,
    ORANGE_PRIORITY,
    SUCCESS,
    WARNING,
    ERROR
}

@Composable
fun GlassTelemetryCard(
    modifier: Modifier = Modifier,
    glowTone: GlowTone = GlowTone.CYAN_BLUE,
    animationIntensity: AnimationIntensity = AnimationIntensity.FULL,
    cornerRadius: Dp = 20.dp,
    contentPadding: Dp = 16.dp,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val colors = LocalRiderColors.current
    val angle = rememberRunningBorderPhase(animationIntensity = animationIntensity)
    val shape = RoundedCornerShape(cornerRadius)

    val (primaryGlow, secondaryGlow) = when (glowTone) {
        GlowTone.CYAN_BLUE -> colors.neonCyan to colors.electricBlue
        GlowTone.ORANGE_PRIORITY -> colors.premiumOrange to Color(0xFFFFC107)
        GlowTone.SUCCESS -> colors.successGreen to colors.neonCyan
        GlowTone.WARNING -> colors.warningAmber to colors.premiumOrange
        GlowTone.ERROR -> colors.errorRed to colors.premiumOrange
    }

    val cardModifier = modifier
        .clip(shape)
        .background(
            brush = Brush.verticalGradient(
                colors = listOf(
                    colors.glassCardElevated.copy(alpha = if (colors.isDark) 0.92f else 0.98f),
                    colors.glassCard.copy(alpha = if (colors.isDark) 0.84f else 0.95f)
                )
            )
        )
        .drawWithCache {
            val radiusPx = cornerRadius.toPx()
            val strokePx = 1.4.dp.toPx()
            val outerGlowStrokePx = 3.5.dp.toPx()
            val rad = Math.toRadians(angle.toDouble())
            val dx = cos(rad).toFloat() * size.width * 0.6f
            val dy = sin(rad).toFloat() * size.height * 0.6f
            val center = Offset(size.width / 2f, size.height / 2f)

            val runningBrush = Brush.linearGradient(
                colors = listOf(
                    primaryGlow.copy(alpha = 0.08f),
                    secondaryGlow.copy(alpha = 0.35f),
                    primaryGlow.copy(alpha = 0.95f),
                    secondaryGlow.copy(alpha = 0.25f),
                    primaryGlow.copy(alpha = 0.08f)
                ),
                start = center - Offset(dx, dy),
                end = center + Offset(dx, dy)
            )

            val softHaloBrush = Brush.linearGradient(
                colors = listOf(
                    Color.Transparent,
                    primaryGlow.copy(alpha = 0.22f),
                    Color.Transparent
                ),
                start = center - Offset(dx, dy),
                end = center + Offset(dx, dy)
            )

            onDrawWithContent {
                drawContent()
                if (animationIntensity != AnimationIntensity.MINIMAL) {
                    drawRoundRect(
                        brush = softHaloBrush,
                        cornerRadius = CornerRadius(radiusPx, radiusPx),
                        style = Stroke(width = outerGlowStrokePx)
                    )
                }
                drawRoundRect(
                    brush = runningBrush,
                    cornerRadius = CornerRadius(radiusPx, radiusPx),
                    style = Stroke(width = strokePx)
                )
            }
        }
        .let { base ->
            if (onClick != null) {
                base.clickable(onClick = onClick)
            } else {
                base
            }
        }
        .padding(contentPadding)

    Box(modifier = cardModifier, content = content)
}

@Composable
fun BoostNowButton(
    isRunning: Boolean,
    animationIntensity: AnimationIntensity,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalRiderColors.current
    val haptic = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = spring(dampingRatio = 0.65f, stiffness = 400f),
        label = "boost_btn_scale"
    )
    val phase = rememberRunningBorderPhase(animationIntensity = animationIntensity, durationMs = 3400)
    val shape = RoundedCornerShape(22.dp)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .scale(scale)
            .minimumInteractiveComponentSize()
            .clip(shape)
            .background(
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        colors.premiumOrange.copy(alpha = 0.92f),
                        Color(0xFFFF6D00),
                        colors.electricBlue.copy(alpha = 0.9f)
                    )
                )
            )
            .drawWithCache {
                val radiusPx = 22.dp.toPx()
                val strokePx = 2.dp.toPx()
                val center = Offset(size.width / 2f, size.height / 2f)
                val rad = Math.toRadians(phase.toDouble())
                val dx = cos(rad).toFloat() * size.width * 0.7f
                val dy = sin(rad).toFloat() * size.height * 0.7f
                val borderBrush = Brush.linearGradient(
                    colors = listOf(
                        colors.neonCyan.copy(alpha = 0.3f),
                        Color.White.copy(alpha = 0.95f),
                        colors.premiumOrange,
                        colors.neonCyan.copy(alpha = 0.3f)
                    ),
                    start = center - Offset(dx, dy),
                    end = center + Offset(dx, dy)
                )
                onDrawWithContent {
                    drawContent()
                    drawRoundRect(
                        brush = borderBrush,
                        cornerRadius = CornerRadius(radiusPx, radiusPx),
                        style = Stroke(width = strokePx)
                    )
                }
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = !isRunning
            ) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onClick()
            }
            .padding(vertical = 18.dp, horizontal = 20.dp)
            .testTag("boost_now_button"),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = if (isRunning) "⚡ RUNNING 10-STEP DIAGNOSTICS..." else "⚡ BOOST NOW",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold
                ),
                color = Color.White
            )
        }
    }
}

@Composable
fun LiveStatusPill(
    label: String,
    toneColor: Color,
    pulse: Boolean,
    animationIntensity: AnimationIntensity,
    modifier: Modifier = Modifier
) {
    val isResumed = rememberIsAppResumed()
    val dotAlpha = if (pulse && isResumed && animationIntensity != AnimationIntensity.MINIMAL) {
        val transition = rememberInfiniteTransition(label = "pill_pulse")
        val alpha by transition.animateFloat(
            initialValue = 0.35f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(900),
                repeatMode = RepeatMode.Reverse
            ),
            label = "pill_alpha"
        )
        alpha
    } else {
        1f
    }

    val animatedColor by animateColorAsState(targetValue = toneColor, label = "pill_color")

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = animatedColor.copy(alpha = 0.14f),
        border = androidx.compose.foundation.BorderStroke(1.dp, animatedColor.copy(alpha = 0.45f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(animatedColor.copy(alpha = dotAlpha))
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = animatedColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun SectionTitleBar(
    title: String,
    subtitle: String? = null,
    icon: ImageVector? = null,
    accentColor: Color = LocalRiderColors.current.neonCyan,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(accentColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = accentColor,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun TelemetryStatTile(
    label: String,
    value: String,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
    modifier: Modifier = Modifier
) {
    val colors = LocalRiderColors.current
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(colors.navyBackground.copy(alpha = if (colors.isDark) 0.72f else 0.9f))
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
            color = valueColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
