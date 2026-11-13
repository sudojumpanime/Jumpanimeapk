package com.example.kitty.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.center
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.kitty.theme.KittyAccentGlow
import com.example.kitty.theme.KittyMuted

/**
 * Soft living search glyph — gentle orbit pulse + glass rim sheen.
 * Replaces the static Material search icon in search fields / nav cues.
 */
@Composable
fun AnimatedSearchIcon(
    modifier: Modifier = Modifier,
    tint: Color = KittyMuted,
    size: Dp = 22.dp,
    active: Boolean = true,
) {
    val transition = rememberInfiniteTransition(label = "searchIcon")
    val orbit by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(5200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "orbit",
    )
    val pulse by transition.animateFloat(
        initialValue = 0.86f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulse",
    )
    val sheen by transition.animateFloat(
        initialValue = 0.15f,
        targetValue = 0.55f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "sheen",
    )

    Canvas(modifier = modifier.size(size)) {
        val stroke = (this.size.minDimension * 0.09f).coerceAtLeast(2.2f)
        val lensR = this.size.minDimension * 0.28f * if (active) pulse else 1f
        val center = Offset(
            x = this.size.center.x - this.size.minDimension * 0.06f,
            y = this.size.center.y - this.size.minDimension * 0.06f,
        )
        // Soft glow disc behind the lens.
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    KittyAccentGlow.copy(alpha = if (active) 0.22f * sheen else 0.08f),
                    Color.Transparent,
                ),
                center = center,
                radius = lensR * 2.1f,
            ),
            radius = lensR * 1.85f,
            center = center,
        )
        // Glass lens rim.
        drawCircle(
            color = tint.copy(alpha = 0.92f),
            radius = lensR,
            center = center,
            style = Stroke(width = stroke, cap = StrokeCap.Round),
        )
        // Orbiting highlight tick on the rim.
        if (active) {
            rotate(orbit, pivot = center) {
                drawArc(
                    color = tint.copy(alpha = 0.55f * sheen),
                    startAngle = -28f,
                    sweepAngle = 48f,
                    useCenter = false,
                    topLeft = Offset(center.x - lensR, center.y - lensR),
                    size = androidx.compose.ui.geometry.Size(lensR * 2f, lensR * 2f),
                    style = Stroke(width = stroke * 0.85f, cap = StrokeCap.Round),
                )
            }
        }
        // Handle.
        val handleStart = Offset(
            center.x + lensR * 0.72f,
            center.y + lensR * 0.72f,
        )
        val handleEnd = Offset(
            center.x + lensR * 1.55f,
            center.y + lensR * 1.55f,
        )
        drawLine(
            color = tint.copy(alpha = 0.95f),
            start = handleStart,
            end = handleEnd,
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
        // Soft tip sparkle.
        drawCircle(
            color = tint.copy(alpha = if (active) 0.45f + sheen * 0.35f else 0.25f),
            radius = stroke * 0.55f,
            center = handleEnd,
        )
    }
}
