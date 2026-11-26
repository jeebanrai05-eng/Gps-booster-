package com.example.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.domain.model.AnimationIntensity
import com.example.domain.model.GpsFixStatus
import com.example.ui.theme.LocalRiderColors
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun AnimatedRadarHero(
    fixStatus: GpsFixStatus,
    satellitesUsed: Int,
    satellitesTotal: Int,
    animationIntensity: AnimationIntensity,
    modifier: Modifier = Modifier
) {
    val colors = LocalRiderColors.current
    val isResumed = rememberIsAppResumed()
    val shouldAnimate = isResumed && animationIntensity != AnimationIntensity.MINIMAL

    val sweepAngle: Float
    val pulseFraction: Float
    val orbitAngle: Float

    if (shouldAnimate) {
        val transition = rememberInfiniteTransition(label = "radar_hero")
        val sweep by transition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(
                    durationMillis = if (animationIntensity == AnimationIntensity.REDUCED) 6400 else 4200,
                    easing = LinearEasing
                ),
                repeatMode = RepeatMode.Restart
            ),
            label = "radar_sweep"
        )
        val pulse by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 2200, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "radar_pulse"
        )
        val orbit by transition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 14000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "satellite_orbit"
        )
        sweepAngle = sweep
        pulseFraction = pulse
        orbitAngle = orbit
    } else {
        sweepAngle = 45f
        pulseFraction = 0.4f
        orbitAngle = 315f
    }

    val beaconColor = when (fixStatus) {
        GpsFixStatus.FIX_ACQUIRED -> colors.successGreen
        GpsFixStatus.SEARCHING -> colors.neonCyan
        GpsFixStatus.STALE_FIX -> colors.warningAmber
        GpsFixStatus.GPS_DISABLED,
        GpsFixStatus.PERMISSION_DENIED -> colors.errorRed
        GpsFixStatus.HARDWARE_UNAVAILABLE -> colors.warningAmber
    }

    Box(
        modifier = modifier.size(136.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val maxRadius = size.minDimension * 0.45f

            // Ambient radial backdrop
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        beaconColor.copy(alpha = 0.16f),
                        colors.electricBlue.copy(alpha = 0.06f),
                        Color.Transparent
                    ),
                    center = center,
                    radius = maxRadius * 1.1f
                ),
                center = center,
                radius = maxRadius * 1.1f
            )

            // Concentric orbital rings
            val ringRadii = listOf(0.32f, 0.60f, 0.88f, 1.0f)
            ringRadii.forEachIndexed { idx, factor ->
                drawCircle(
                    color = if (idx == 2) colors.neonCyan.copy(alpha = 0.35f)
                    else colors.electricBlue.copy(alpha = 0.22f),
                    radius = maxRadius * factor,
                    center = center,
                    style = Stroke(width = if (idx == 3) 1.5.dp.toPx() else 1.dp.toPx())
                )
            }

            // Crosshair grid lines
            drawLine(
                color = colors.electricBlue.copy(alpha = 0.18f),
                start = Offset(center.x - maxRadius, center.y),
                end = Offset(center.x + maxRadius, center.y),
                strokeWidth = 1.dp.toPx()
            )
            drawLine(
                color = colors.electricBlue.copy(alpha = 0.18f),
                start = Offset(center.x, center.y - maxRadius),
                end = Offset(center.x, center.y + maxRadius),
                strokeWidth = 1.dp.toPx()
            )

            // Expanding signal wave ring
            val waveRadius = maxRadius * (0.2f + 0.78f * pulseFraction)
            drawCircle(
                color = beaconColor.copy(alpha = (1f - pulseFraction) * 0.55f),
                radius = waveRadius,
                center = center,
                style = Stroke(width = 2.dp.toPx())
            )

            // Sweeping radar cone & beam
            rotate(degrees = sweepAngle, pivot = center) {
                drawArc(
                    brush = Brush.sweepGradient(
                        0.0f to Color.Transparent,
                        0.78f to Color.Transparent,
                        0.96f to colors.neonCyan.copy(alpha = 0.22f),
                        1.0f to colors.neonCyan.copy(alpha = 0.55f),
                        center = center
                    ),
                    startAngle = 0f,
                    sweepAngle = 360f,
                    useCenter = true,
                    topLeft = Offset(center.x - maxRadius, center.y - maxRadius),
                    size = Size(maxRadius * 2f, maxRadius * 2f)
                )
                drawLine(
                    color = colors.neonCyan.copy(alpha = 0.85f),
                    start = center,
                    end = Offset(center.x + maxRadius, center.y),
                    strokeWidth = 2.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }

            // Rotating 3D-style satellite node on outer orbit
            val satRad = Math.toRadians(orbitAngle.toDouble())
            val satPos = Offset(
                center.x + (maxRadius * 0.88f * cos(satRad)).toFloat(),
                center.y + (maxRadius * 0.88f * sin(satRad)).toFloat()
            )
            rotate(degrees = orbitAngle + 45f, pivot = satPos) {
                // Solar wings
                drawLine(
                    color = colors.electricBlue,
                    start = Offset(satPos.x - 10.dp.toPx(), satPos.y),
                    end = Offset(satPos.x + 10.dp.toPx(), satPos.y),
                    strokeWidth = 3.5.dp.toPx(),
                    cap = StrokeCap.Round
                )
                // Satellite core
                drawCircle(
                    color = colors.neonCyan,
                    radius = 4.dp.toPx(),
                    center = satPos
                )
                // Dish triangle
                val dish = Path().apply {
                    moveTo(satPos.x, satPos.y + 3.dp.toPx())
                    lineTo(satPos.x - 4.dp.toPx(), satPos.y + 8.dp.toPx())
                    lineTo(satPos.x + 4.dp.toPx(), satPos.y + 8.dp.toPx())
                    close()
                }
                drawPath(dish, color = colors.premiumOrange)
            }

            // Center GPS beacon
            drawCircle(
                color = beaconColor.copy(alpha = 0.28f),
                radius = 14.dp.toPx(),
                center = center
            )
            drawCircle(
                color = beaconColor,
                radius = 6.dp.toPx(),
                center = center
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Bottom,
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = if (satellitesTotal > 0) "$satellitesUsed/$satellitesTotal SATS" else "GNSS RADAR",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = beaconColor
            )
        }
    }
}
