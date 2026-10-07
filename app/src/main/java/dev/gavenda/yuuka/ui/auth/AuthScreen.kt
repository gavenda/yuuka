package dev.gavenda.yuuka.ui.auth

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.gavenda.yuuka.BuildConfig
import dev.gavenda.yuuka.R
import dev.gavenda.yuuka.ui.common.ScreenPreview
import dev.gavenda.yuuka.ui.common.catPattern
import androidx.compose.ui.tooling.preview.Preview
import kotlin.math.abs
import kotlin.math.ceil

/** Mirrors `LoginView.vue`. */
@Composable
fun AuthScreen(onLogin: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = modifier
            .fillMaxSize()
            .halftonePattern(colors.primary.copy(alpha = 0.16f).compositeOver(colors.background))
            // Translucent, so the print still shows where the halftone is solid.
            .catPattern()
            .safeDrawingPadding(),
    ) {
        // The mark, the name, what it is, and one button, in the middle of the window. The column is capped
        // so a tablet does not stretch the button from edge to edge, and scrolls for a phone on its side.
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .verticalScroll(rememberScrollState())
                .widthIn(max = 360.dp)
                .padding(horizontal = 24.dp, vertical = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // A mark, as in the rail: the name beneath it is what a screen reader should say.
            Image(
                painter = painterResource(R.drawable.yuuka_logo),
                contentDescription = null,
                modifier = Modifier.size(112.dp).clip(CircleShape),
            )
            Text(
                stringResource(R.string.brand_name),
                style = MaterialTheme.typography.headlineLarge,
                modifier = Modifier.padding(top = 24.dp),
            )
            Text(
                stringResource(R.string.auth_tagline),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp, bottom = 40.dp),
            )
            Button(onClick = onLogin, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                Text(stringResource(R.string.action_log_in), style = MaterialTheme.typography.titleMedium)
            }
        }
        Text(
            "v${BuildConfig.VERSION_NAME}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp),
        )
    }
}

/**
 * The backdrop: a halftone rising from the foot of the window to a zigzag edge. A grid of dots that are nothing
 * at the edge and grow with their depth beneath it, until they run together into a flat tint. [color] has to be
 * opaque, or the dots would darken where they overlap. `LoginView.vue` draws the same one.
 */
private fun Modifier.halftonePattern(color: Color): Modifier = drawBehind {
    val step = 12.dp.toPx()
    val fade = size.height * HALFTONE_FADE
    val columns = ceil(center.x / step).toInt()
    var y = size.height - step / 2
    while (y > size.height * HALFTONE_PEAK) {
        for (column in -columns until columns) {
            val x = center.x + (column + 0.5f) * step
            val depth = y - zigzagEdge(x / size.width) * size.height
            val radius = step * HALFTONE_FULL * (depth / fade).coerceIn(0f, 1f)
            if (radius > 0.25f) drawCircle(color, radius, Offset(x, y))
        }
        y -= step
    }
}

/** How far down the window the halftone's edge is at [x], both as fractions: a peak near the left, a valley near the right. */
private fun zigzagEdge(x: Float): Float {
    val phase = ((x - 0.15f) / 0.6f).mod(2f)
    return HALFTONE_PEAK + (HALFTONE_VALLEY - HALFTONE_PEAK) * (1f - abs(phase - 1f))
}

private const val HALFTONE_PEAK = 0.58f
private const val HALFTONE_VALLEY = 0.9f

/** The depth, as a fraction of the window's height, at which the dots are full size. */
private const val HALFTONE_FADE = 0.3f

/** A full-size dot's radius in grid steps: just past the 0.707 at which neighbours close the gaps between them. */
private const val HALFTONE_FULL = 0.72f

@Preview(showBackground = true)
@Composable
private fun AuthScreenPreview() {
    ScreenPreview {
        AuthScreen(onLogin = {})
    }
}
