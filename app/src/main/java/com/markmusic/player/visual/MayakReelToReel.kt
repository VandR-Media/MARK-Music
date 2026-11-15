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
import androidx.compose.ui.graphics.PathFillType
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
 * The actual Mayak-203, photographed straight-on: unlike 0.7.7 there are no
 * plastic lid, street background or perspective-distorted reels to animate.
 *
 * Two accurately registered, circular metal reel masks rotate over a stationary
 * photo of the deck. The dark holes in the metal are transparent, revealing the
 * magnetic tape pack underneath instead of rotating arbitrary photograph pixels.
 */
@Composable
fun MayakReelToReel(playing: Boolean, progress: Float, modifier: Modifier = Modifier) {
    val photo = ImageBitmap.imageResource(R.drawable.mayak_photo)
    val foreground = LocalVisualActive.current.value
    val currentProgress = rememberUpdatedState(progress.coerceIn(0f, 1f))
    val supplyAngle = remember { mutableFloatStateOf(0f) }
    val takeupAngle = remember { mutableFloatStateOf(0f) }

    LaunchedEffect(playing, foreground) {
        if (!playing || !foreground) return@LaunchedEffect
        var lastNs = 0L
        while (coroutineContext.isActive) {
            withFrameNanos { frameNs ->
                if (lastNs > 0 && frameNs > lastNs) {
                    val elapsed = frameNs - lastNs
                    val p = currentProgress.value
                    supplyAngle.floatValue = advanceMayakReel(supplyAngle.floatValue, elapsed, p, false)
                    takeupAngle.floatValue = advanceMayakReel(takeupAngle.floatValue, elapsed, p, true)
                }
                lastNs = frameNs
            }
        }
    }

    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        // No perspective warping or image stretching: keep the real photo ratio.
        val photoAspect = photo.width.toFloat() / photo.height
        val photoWidth = minOf(maxWidth, maxHeight * photoAspect)
        val photoHeight = photoWidth / photoAspect
        Box(Modifier.size(photoWidth, photoHeight)) {
            Canvas(Modifier.fillMaxSize().drawWithCache {
                onDrawBehind {
                    drawImage(photo, dstSize = IntSize(
                        size.width.roundToInt(), size.height.roundToInt()
                    ), filterQuality = FilterQuality.Medium)
                    // Erase the two still photographed reel faces, not the
                    // entire upper panel. A real reel can rotate only here.
                    for (spec in MAYAK_REEL_SPECS) {
                        val center = Offset(
                            size.width * spec.x / MAYAK_PHOTO_WIDTH,
                            size.height * spec.y / MAYAK_PHOTO_HEIGHT
                        )
                        val radius = size.width * spec.radius / MAYAK_PHOTO_WIDTH
                        drawCircle(Color(0xFF07090C), radius + size.width * .002f,
                            center)
                        drawCircle(Brush.radialGradient(
                            listOf(Color(0xFF383332), Color(0xFF151719),
                                Color(0xFF0B0C10), Color(0xFF06080B)),
                            center = center, radius = radius
                        ), radius, center)
                        for (ring in 0..50) {
                            val rr = radius * (.31f + ring * .0131f)
                            drawCircle(Color(0xFFA18162).copy(alpha = if (ring % 5 == 0) .065f else .022f),
                                rr, center, style = Stroke(width = size.width * .00045f))
                        }
                    }
                }
            }) { }

            // Each moving surface has its own composited GPU layer; no part of
            // the controls, wooden housing or record head can rotate.
            for ((index, spec) in MAYAK_REEL_SPECS.withIndex()) {
                val diameter = photoWidth * (spec.radius * 2f / MAYAK_PHOTO_WIDTH)
                val left = photoWidth * ((spec.x - spec.radius) / MAYAK_PHOTO_WIDTH)
                val top = photoHeight * ((spec.y - spec.radius) / MAYAK_PHOTO_HEIGHT)
                MayakMetalReel(
                    photo = photo,
                    spec = spec,
                    modifier = Modifier.align(Alignment.TopStart)
                        .offset(left, top)
                        .size(diameter)
                        .graphicsLayer {
                            rotationZ = if (index == 0) supplyAngle.floatValue else takeupAngle.floatValue
                        }
                )
            }
        }
    }
}

private const val MAYAK_PHOTO_WIDTH = 1200f
private const val MAYAK_PHOTO_HEIGHT = 922f

internal data class MayakHole(val x: Float, val y: Float, val radius: Float)
internal data class MayakReelSpec(
    val x: Float, val y: Float, val radius: Float,
    val holes: List<MayakHole>
)

// Measured against the owner's exact straight-on 1200×922 reference image.
// These circles cover only the physical spool rims, NOT square photo patches.
internal val MAYAK_REEL_SPECS = listOf(
    MayakReelSpec(366f, 296f, 216f, listOf(
        MayakHole(249f, 227f, 60f), MayakHole(485f, 229f, 56f),
        MayakHole(364f, 422f, 59f), MayakHole(334f, 291f, 27f),
        MayakHole(401f, 289f, 27f), MayakHole(365f, 345f, 27f)
    )),
    MayakReelSpec(818f, 299f, 217f, listOf(
        MayakHole(700f, 220f, 60f), MayakHole(927f, 222f, 59f),
        MayakHole(810f, 423f, 64f), MayakHole(778f, 285f, 26f),
        MayakHole(862f, 279f, 29f), MayakHole(813f, 349f, 25f)
    ))
)

/** True for features that are wholly inside the photographed reel rim. */
internal fun mayakHoleWithinReel(spec: MayakReelSpec, hole: MayakHole): Boolean {
    val dx = spec.x - hole.x
    val dy = spec.y - hole.y
    return dx * dx + dy * dy + hole.radius * hole.radius <= spec.radius * spec.radius * 1.08f
}

@Composable
private fun MayakMetalReel(photo: ImageBitmap, spec: MayakReelSpec, modifier: Modifier) {
    Canvas(modifier.drawWithCache {
        val diameter = spec.radius * 2f
        val pxScale = size.width / diameter
        val clip = Path().apply {
            fillType = PathFillType.EvenOdd
            addOval(Rect(Offset.Zero, size))
            for (hole in spec.holes) {
                val hx = (hole.x - spec.x + spec.radius) * pxScale
                val hy = (hole.y - spec.y + spec.radius) * pxScale
                val rad = hole.radius * pxScale
                addOval(Rect(hx - rad, hy - rad, hx + rad, hy + rad))
            }
        }
        // Exact crop of a photographed reel, never of an arbitrary picture area.
        val left = ((spec.x - spec.radius) / MAYAK_PHOTO_WIDTH * photo.width).roundToInt()
        val top = ((spec.y - spec.radius) / MAYAK_PHOTO_HEIGHT * photo.height).roundToInt()
        val sourceW = (diameter / MAYAK_PHOTO_WIDTH * photo.width).roundToInt()
        val sourceH = (diameter / MAYAK_PHOTO_HEIGHT * photo.height).roundToInt()
        onDrawBehind {
            clipPath(clip) {
                drawImage(
                    image = photo,
                    srcOffset = IntOffset(left, top),
                    srcSize = IntSize(sourceW, sourceH),
                    dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
                    filterQuality = FilterQuality.High
                )
            }
        }
    }) { }
}

/** Conservation of tape-pack area: a full reel becomes empty as the other fills. */
internal fun mayakTapePackRadius(progress: Float, takeup: Boolean): Float {
    val p = progress.coerceIn(0f, 1f)
    val filled = if (takeup) p else 1f - p
    val hub = .145f
    val full = .386f
    return sqrt(hub * hub + (full * full - hub * hub) * filled)
}

/** Tape moves at 19.05 cm/s; the angular velocities differ with winding radius. */
internal fun advanceMayakReel(
    currentDegrees: Float, elapsedNs: Long, progress: Float, takeup: Boolean
): Float {
    if (elapsedNs <= 0L) return currentDegrees
    val radiusMm = mayakTapePackRadius(progress, takeup) * 186.0
    val degreesPerSecond = (190.5 * 180.0) / (PI * radiusMm)
    val updated = (currentDegrees.toDouble() -
        elapsedNs * degreesPerSecond / 1_000_000_000.0) % 360.0
    return (if (updated < 0.0) updated + 360.0 else updated).toFloat()
}
