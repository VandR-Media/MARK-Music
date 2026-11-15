package com.markmusic.player.visual

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.markmusic.player.LocalVisualActive
import com.markmusic.player.R
import kotlinx.coroutines.isActive
import kotlin.coroutines.coroutineContext
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Photographic Весна-202-1 cassette recorder, based on the original user photo.
 *
 * Static photo: body, metal faceplate, speaker grille, buttons, meters, cassette
 * shell, and label NEVER move. Only the two small round plastic reel hubs inside
 * the existing cassette rotate; the rest of the photo is an immutable GPU layer.
 *
 * No spectrum/FFT, no Internet, no GMS, no extra audio processing.
 */
@Composable
fun VesnaCassettePlayer(
    playing: Boolean,
    progress: Float,
    modifier: Modifier = Modifier
) {
    val photo = ImageBitmap.imageResource(R.drawable.vesna_202_photo)
    val foreground = LocalVisualActive.current.value
    val currentProgress = rememberUpdatedState(progress.coerceIn(0f, 1f))
    val leftDegrees = remember { mutableFloatStateOf(0f) }
    val rightDegrees = remember { mutableFloatStateOf(0f) }

    LaunchedEffect(playing, foreground) {
        if (!playing || !foreground) return@LaunchedEffect
        var lastNs = 0L
        while (coroutineContext.isActive) {
            withFrameNanos { now ->
                if (lastNs != 0L && now > lastNs) {
                    val dt = now - lastNs
                    val p = currentProgress.value
                    // Both cassette hubs turn clockwise in normal play.
                    leftDegrees.floatValue = advanceCassetteHub(
                        leftDegrees.floatValue, dt, p, takeup = false
                    )
                    rightDegrees.floatValue = advanceCassetteHub(
                        rightDegrees.floatValue, dt, p, takeup = true
                    )
                }
                lastNs = now
            }
        }
    }

    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val photoAspect = photo.width.toFloat() / photo.height
        val renderedWidth = minOf(maxWidth, maxHeight * photoAspect)
        val renderedHeight = renderedWidth / photoAspect

        Box(Modifier.size(renderedWidth, renderedHeight)) {
            Canvas(Modifier.fillMaxSize().drawWithCache {
                val widthPx = size.width
                val heightPx = size.height
                onDrawBehind {
                    drawImage(
                        photo, dstSize = IntSize(
                            widthPx.roundToInt(), heightPx.roundToInt()
                        ), filterQuality = FilterQuality.High
                    )
                    // Cover JUST the interior of the two cassette spool hubs.
                    // This prevents the old photographed spokes from showing
                    // through a moving sprite, and never covers the cassette label.
                    for (hub in VESNA_CASSETTE_HUBS) {
                        val center = Offset(
                            widthPx * hub.x / VESNA_IMAGE_WIDTH,
                            heightPx * hub.y / VESNA_IMAGE_HEIGHT
                        )
                        val radius = widthPx * hub.radius / VESNA_IMAGE_WIDTH
                        drawCircle(Color(0xFF0F1012), radius * 1.015f, center)
                    }
                }
            }) {}

            for ((index, hub) in VESNA_CASSETTE_HUBS.withIndex()) {
                val diameter = renderedWidth * hub.radius * 2f / VESNA_IMAGE_WIDTH
                val x = renderedWidth * (hub.x - hub.radius) / VESNA_IMAGE_WIDTH
                val y = renderedHeight * (hub.y - hub.radius) / VESNA_IMAGE_HEIGHT
                CassetteHubPhoto(
                    photo = photo,
                    hub = hub,
                    modifier = Modifier.align(Alignment.TopStart)
                        .offset(x, y)
                        .size(diameter)
                        .graphicsLayer {
                            rotationZ = if (index == 0)
                                leftDegrees.floatValue else rightDegrees.floatValue
                        }
                )
            }

            // Fixed precision spindle shafts, unaffected by reel rotation.
            Canvas(Modifier.fillMaxSize()) {
                for (hub in VESNA_CASSETTE_HUBS) {
                    val c = Offset(
                        size.width * hub.x / VESNA_IMAGE_WIDTH,
                        size.height * hub.y / VESNA_IMAGE_HEIGHT
                    )
                    val unit = size.width / VESNA_IMAGE_WIDTH
                    drawCircle(Color(0xFF101316), 4.8f * unit, c)
                    drawCircle(
                        brush = Brush.radialGradient(
                            listOf(Color(0xFFE7E1D2), Color(0xFF858A8C), Color(0xFF1E2428)),
                            center = c - Offset(1.1f * unit, 1.2f * unit),
                            radius = 4.8f * unit
                        ),
                        radius = 3.3f * unit, center = c
                    )
                    drawCircle(Color(0xFF30363B), 1.35f * unit, c)
                }
            }
        }
    }
}

/** Measured solely inside the cassette hubs of the owner's 1024×1009 photo. */
internal const val VESNA_IMAGE_WIDTH = 1024f
internal const val VESNA_IMAGE_HEIGHT = 1009f
internal data class CassetteHubSpec(val x: Float, val y: Float, val radius: Float)
internal val VESNA_CASSETTE_HUBS = listOf(
    CassetteHubSpec(x = 645f, y = 500f, radius = 20f),
    CassetteHubSpec(x = 788f, y = 499f, radius = 20f)
)

internal fun cassetteHubInsideCassetteWindow(hub: CassetteHubSpec): Boolean =
    hub.x - hub.radius > 530f &&
    hub.x + hub.radius < 881f &&
    hub.y - hub.radius > 407f &&
    hub.y + hub.radius < 616f

@Composable
private fun CassetteHubPhoto(photo: ImageBitmap, hub: CassetteHubSpec, modifier: Modifier) {
    Canvas(modifier.drawWithCache {
        val circle = Path().apply { addOval(Rect(Offset.Zero, size)) }
        // The crop is registered to the source and never includes the label,
        // unrelated deck controls, background, or other cassette parts.
        val sx = ((hub.x - hub.radius) * photo.width / VESNA_IMAGE_WIDTH).roundToInt()
        val sy = ((hub.y - hub.radius) * photo.height / VESNA_IMAGE_HEIGHT).roundToInt()
        val sw = (hub.radius * 2f * photo.width / VESNA_IMAGE_WIDTH).roundToInt()
        val sh = (hub.radius * 2f * photo.height / VESNA_IMAGE_HEIGHT).roundToInt()
        onDrawBehind {
            clipPath(circle) {
                drawImage(
                    image = photo,
                    srcOffset = IntOffset(sx, sy),
                    srcSize = IntSize(sw, sh),
                    dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
                    filterQuality = FilterQuality.High
                )
            }
        }
    }) {}
}

/** 4.76 cm/s normal cassette speed, 12..24 mm spool radii by track position. */
internal fun cassetteTapePackRadiusMm(progress: Float, takeup: Boolean): Double {
    val p = progress.coerceIn(0f, 1f).toDouble()
    val fraction = if (takeup) p else 1.0 - p
    val hubRadius = 12.0
    val fullRadius = 24.0
    return sqrt(hubRadius * hubRadius +
        (fullRadius * fullRadius - hubRadius * hubRadius) * fraction)
}

/** Based on VSYNC intervals, including 120 Hz: no skipped 8.33 ms frames. */
internal fun advanceCassetteHub(
    currentDegrees: Float, elapsedNs: Long, progress: Float, takeup: Boolean
): Float {
    if (elapsedNs <= 0L) return currentDegrees
    val radiusMm = cassetteTapePackRadiusMm(progress, takeup)
    val degreesPerSecond = (47.6 * 180.0) / (PI * radiusMm)
    return ((currentDegrees.toDouble() +
        elapsedNs.toDouble() * degreesPerSecond / 1_000_000_000.0) % 360.0).toFloat()
}
