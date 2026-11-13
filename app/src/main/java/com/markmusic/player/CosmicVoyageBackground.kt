package com.markmusic.player

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.isActive
import kotlin.coroutines.coroutineContext
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import kotlin.math.sin

/**
 * A slow, procedurally drawn cosmic cruise with 3 layers of parallax.
 * No bitmap downloads or hardware permission. Sensor state is read in
 * DrawScope: music library and playback controls are not recomposed at 60Hz.
 */
@Composable
internal fun CosmicVoyageBackground(
    palette: MarkPalette,
    speed: Float = 1f,
    steering: Float = 1f,
    modifier: Modifier = Modifier
) {
    val tilt = LocalGlassTilt.current
    // Persistent phase: changing the slider does not restart the voyage.
    // The triangle wave reverses seamlessly, including at zero speed.
    var phase by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(speed) {
        var previousFrame = 0L
        while (coroutineContext.isActive) {
            withFrameNanos { now ->
                if (previousFrame != 0L && speed > 0f) {
                    val seconds = ((now - previousFrame) / 1_000_000_000f).coerceIn(0f, .1f)
                    phase = (phase + seconds * speed / 110f) % 2f
                }
                previousFrame = now
            }
        }
    }
    Canvas(modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val dx = tilt.value.x * steering
        val dy = tilt.value.y * steering
        val t = if (phase <= 1f) phase else 2f - phase
        val isBright = palette.key in setOf(
            "TROPICAL_FLARE", "CYAN_SUNRISE", "LIME_MAGENTA", "CORAL_SKY",
            "GOLDEN_ORCHID", "MINT_PEACH", "LAVENDER_DAY", "SKY_PINK",
            "SOLAR_MINT", "SAPPHIRE_CITRUS"
        )
        drawRect(Brush.verticalGradient(if (isBright) listOf(
            palette.start, palette.start, palette.end, palette.end
        ) else listOf(
            Color(0xFF061127), palette.start, palette.end, Color(0xFF090D29)
        )))
        // Diffuse, very distant dust clouds move more slowly than the stars.
        drawCircle(
            Brush.radialGradient(listOf(palette.accent.copy(alpha = .16f), Color.Transparent),
                center = Offset(w * (.79f + dx * .09f + t * .025f), h * (.22f + dy * .055f)),
                radius = w * .85f),
            radius = w * .85f,
            center = Offset(w * (.79f + dx * .09f + t * .025f), h * (.22f + dy * .055f))
        )
        drawCircle(
            Brush.radialGradient(listOf(palette.secondary.copy(alpha = .18f), Color.Transparent),
                center = Offset(w * (.09f - dx * .07f), h * (.82f - dy * .06f - t * .022f)),
                radius = w * .75f),
            radius = w * .75f,
            center = Offset(w * (.09f - dx * .07f), h * (.82f - dy * .06f - t * .022f))
        )
        // Three physical depths: the near layer responds most to tilt.
        for (layer in 0..2) {
            val parallax = (layer + 1) * 16f
            val drift = (layer + 1) * t * 18f
            for (i in 0 until 29) {
                val seed = i * 73 + layer * 191
                val px = ((seed * 47 + 83) % 997) / 997f * w
                val py = ((seed * 67 + 167) % 991) / 991f * h
                val x = wrap(px + dx * parallax * density + drift * density, w)
                val y = wrap(py + dy * parallax * density + t * (layer + 1) * 13f * density, h)
                val radius = (0.45f + layer * .24f + (i % 4) * .14f) * density
                val brightness = .12f + layer * .055f + (i % 5) * .018f
                drawCircle(Color.White.copy(alpha = brightness), radius, Offset(x, y))
            }
        }
        // Gentle vignette to keep album art and all labels legible.
        drawRect(Brush.verticalGradient(if (isBright) listOf(
            Color(0x35020A1B), Color(0x21020A1B), Color(0x45030B1B)
        ) else listOf(
            Color(0x20020A1B), Color.Transparent, Color(0x65030B1B)
        )))
    }
}

private fun wrap(value: Float, extent: Float): Float {
    if (extent <= 0f) return 0f
    val m = value % extent
    return if (m < 0f) m + extent else m
}
