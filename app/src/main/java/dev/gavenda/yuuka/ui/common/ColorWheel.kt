package dev.gavenda.yuuka.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * A hue/saturation wheel plus a brightness strip, built from `Canvas` gradient
 * shaders and drag gestures rather than a colour-picker library — one control
 * didn't seem worth a new dependency in an app that otherwise has none for UI.
 *
 * The wheel is two overlaid Skia shaders, not a rasterised bitmap: a sweep
 * gradient for hue and a white-to-transparent radial gradient for saturation.
 * Shaders stay smooth at any size, where a fixed-resolution bitmap scaled up
 * would show its pixel grid at the edge.
 */
@Composable
fun ColorWheelPicker(color: Color, onColorChange: (Color) -> Unit, modifier: Modifier = Modifier) {
    val hsv = remember(color) {
        val out = FloatArray(3)
        android.graphics.Color.RGBToHSV((color.red * 255).toInt(), (color.green * 255).toInt(), (color.blue * 255).toInt(), out)
        out
    }
    val hue = hsv[0]
    val saturation = hsv[1]
    val value = hsv[2]

    Column(modifier = modifier) {
        HueSaturationWheel(
            hue = hue,
            saturation = saturation,
            onHueSaturationChange = { newHue, newSaturation -> onColorChange(Color.hsv(newHue, newSaturation, value)) },
        )
        Spacer(modifier = Modifier.height(12.dp))
        BrightnessSlider(
            hue = hue,
            saturation = saturation,
            value = value,
            onValueChange = { newValue -> onColorChange(Color.hsv(hue, saturation, newValue)) },
        )
    }
}

private val HUE_SWEEP_COLORS = listOf(
    Color.Red,
    Color.Yellow,
    Color.Green,
    Color.Cyan,
    Color.Blue,
    Color.Magenta,
    Color.Red,
)

@Composable
private fun HueSaturationWheel(hue: Float, saturation: Float, onHueSaturationChange: (Float, Float) -> Unit) {
    var sizePx by remember { mutableFloatStateOf(0f) }

    fun updateFromOffset(offset: Offset) {
        if (sizePx <= 0f) return
        val center = sizePx / 2f
        val dx = offset.x - center
        val dy = offset.y - center
        val radius = sizePx / 2f
        val distance = sqrt(dx * dx + dy * dy)
        val newSaturation = (distance / radius).coerceIn(0f, 1f)
        // Matches SweepGradient's own convention: 0° at three o'clock, increasing
        // clockwise — atan2(dy, dx) already does that once y grows downward.
        var angle = Math.toDegrees(atan2(dy, dx).toDouble()).toFloat()
        if (angle < 0) angle += 360f
        onHueSaturationChange(angle, newSaturation)
    }

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .onSizeChanged { sizePx = minOf(it.width, it.height).toFloat() }
            .pointerInput(Unit) { detectTapGestures { offset -> updateFromOffset(offset) } }
            .pointerInput(Unit) {
                detectDragGestures { change, _ ->
                    change.consume()
                    updateFromOffset(change.position)
                }
            },
    ) {
        val radius = size.minDimension / 2f
        val center = Offset(size.width / 2f, size.height / 2f)

        drawCircle(brush = Brush.sweepGradient(colors = HUE_SWEEP_COLORS, center = center), radius = radius, center = center)
        drawCircle(
            brush = Brush.radialGradient(colors = listOf(Color.White, Color.White.copy(alpha = 0f)), center = center, radius = radius),
            radius = radius,
            center = center,
        )

        val angleRad = Math.toRadians(hue.toDouble())
        val indicatorRadius = saturation * radius
        val indicatorCenter = Offset(
            x = center.x + (cos(angleRad) * indicatorRadius).toFloat(),
            y = center.y + (sin(angleRad) * indicatorRadius).toFloat(),
        )
        drawCircle(color = Color.White, radius = 10.dp.toPx(), center = indicatorCenter, style = Stroke(width = 3.dp.toPx()))
        drawCircle(color = Color.Black.copy(alpha = 0.35f), radius = 11.5.dp.toPx(), center = indicatorCenter, style = Stroke(width = 1.dp.toPx()))
    }
}

@Composable
private fun BrightnessSlider(hue: Float, saturation: Float, value: Float, onValueChange: (Float) -> Unit) {
    val brush = remember(hue, saturation) { Brush.horizontalGradient(listOf(Color.Black, Color.hsv(hue, saturation, 1f))) }
    var widthPx by remember { mutableFloatStateOf(0f) }

    fun updateFromX(x: Float) {
        if (widthPx <= 0f) return
        onValueChange((x / widthPx).coerceIn(0f, 1f))
    }

    val thumbOffset = with(LocalDensity.current) { (value * widthPx).toDp() - 12.dp }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(28.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(brush)
            .onSizeChanged { widthPx = it.width.toFloat() }
            .pointerInput(Unit) { detectTapGestures { offset -> updateFromX(offset.x) } }
            .pointerInput(Unit) {
                detectDragGestures { change, _ ->
                    change.consume()
                    updateFromX(change.position.x)
                }
            },
    ) {
        Box(
            modifier = Modifier
                .offset(x = thumbOffset)
                .size(24.dp)
                .align(Alignment.CenterStart)
                .clip(CircleShape)
                .background(Color.hsv(hue, saturation, value))
                .border(2.dp, Color.White, CircleShape),
        )
    }
}
