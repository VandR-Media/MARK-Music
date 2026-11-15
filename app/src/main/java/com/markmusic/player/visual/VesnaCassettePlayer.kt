package com.markmusic.player.visual

import android.graphics.Paint
import android.graphics.Typeface
import android.text.TextPaint
import android.text.TextUtils
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
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
 * shell, and MARK AUDIO paper labels NEVER move. Only two small reel hubs inside
 * the existing cassette rotate; the rest of the photo is an immutable GPU layer.
 *
 * No spectrum/FFT, no Internet, no GMS, no extra audio processing.
 */
@Composable
fun VesnaCassettePlayer(
    playing: Boolean,
    progress: Float,
    trackTitle: String,
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

            // Two matte adhesive pieces rest ON the photographed cassette.
            // They change with the music title but remain perfectly stationary
            // while only the two plastic spindle hubs rotate underneath.
            Canvas(Modifier.fillMaxSize()) {
                drawBrandedCassetteSticker(cassetteStickerTitle(trackTitle))
            }
        }
    }
}

/**
 * Two die-cut matte paper parts of a physical cassette label. The space between
 * these strips stays uncovered so the REAL transparent tape mechanism, both
 * spinning hub apertures and the vintage cassette's depth remain visible.
 */
internal data class CassetteLabelStrip(
    val x: Float, val y: Float, val width: Float, val height: Float
)

internal val VESNA_LABEL_STRIPS = listOf(
    CassetteLabelStrip(574f, 438f, 275f, 36f), // brand, above spinning reels
    CassetteLabelStrip(575f, 522f, 274f, 35f)  // song title, below reels
)

internal fun cassetteStickerTitle(value: String): String =
    value.trim().replace(Regex("\\s+"), " ").ifEmpty { "Без названия" }

/** Geometric guard against a repeat of the 0.7.7 rotating/cropped-photo issue. */
internal fun cassetteStickerAvoidsHubWindows(): Boolean =
    VESNA_LABEL_STRIPS.all { label ->
        VESNA_CASSETTE_HUBS.all { hub ->
            val nearestX = hub.x.coerceIn(label.x, label.x + label.width)
            val nearestY = hub.y.coerceIn(label.y, label.y + label.height)
            val dx = nearestX - hub.x
            val dy = nearestY - hub.y
            dx * dx + dy * dy > hub.radius * hub.radius
        }
    }

/** Original retro hi-fi MARK AUDIO cassette paper; text is live track metadata. */
private fun DrawScope.drawBrandedCassetteSticker(title: String) {
    val unit = size.width / VESNA_IMAGE_WIDTH
    fun p(x: Float, y: Float) = Offset(x * unit, y * unit)
    for ((index, paper) in VESNA_LABEL_STRIPS.withIndex()) {
        val dimensions = Size(paper.width * unit, paper.height * unit)
        val corner = CornerRadius(unit * 2.7f)
        drawRoundRect(
            color = Color(0x9906090D),
            topLeft = p(paper.x + 1.6f, paper.y + 2.6f),
            size = dimensions,
            cornerRadius = corner
        )
        drawRoundRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color(0xFFE9E4D8), Color(0xFFD8D2C3), Color(0xFFCAC4B7)),
                startY = paper.y * unit,
                endY = (paper.y + paper.height) * unit
            ),
            topLeft = p(paper.x, paper.y),
            size = dimensions,
            cornerRadius = corner
        )
        drawRoundRect(
            color = Color(0xFF8F8779).copy(alpha = .46f),
            topLeft = p(paper.x, paper.y),
            size = dimensions,
            cornerRadius = corner,
            style = Stroke(width = .75f * unit)
        )
        drawLine(
            color = Color.White.copy(alpha = .40f),
            start = p(paper.x + 4f, paper.y + 2.2f),
            end = p(paper.x + paper.width - 4f, paper.y + 2.2f),
            strokeWidth = .7f * unit
        )
        // Low-contrast print fibers keep the label photographic, not neon/UI.
        repeat(18) { n ->
            val x = paper.x + 7f + ((n * 31 + index * 13) % 248)
            val y = paper.y + 6f + ((n * 17) % (paper.height.toInt() - 11))
            drawLine(
                color = Color(0xFF6A594F).copy(alpha = .062f),
                start = p(x, y),
                end = p(x + 4.3f, y),
                strokeWidth = .65f * unit
            )
        }
    }
    // Tiny burgundy brand stripe printed into the upper adhesive piece.
    drawRoundRect(
        color = Color(0xFF9F484E),
        topLeft = p(580f, 443f),
        size = Size(4.1f * unit, 25f * unit),
        cornerRadius = CornerRadius(.5f * unit)
    )
    drawIntoCanvas { canvas ->
        val ink = android.graphics.Color.rgb(36, 34, 32)
        val brandPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ink
            typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
            textSize = 23f * unit
        }
        canvas.nativeCanvas.drawText(
            "MARK AUDIO", 590f * unit, 464f * unit, brandPaint
        )

        val songPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ink
            typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
            textSize = 25.5f * unit
        }
        val printed = TextUtils.ellipsize(
            title, songPaint, 251f * unit, TextUtils.TruncateAt.END
        ).toString()
        canvas.nativeCanvas.drawText(
            printed, 587f * unit, 548f * unit, songPaint
        )
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
