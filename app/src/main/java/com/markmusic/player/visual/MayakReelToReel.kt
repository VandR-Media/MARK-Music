package com.markmusic.player.visual

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.unit.dp
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
 * Genuine photographed Mayak-203, not a fake procedural/vectored imitation.
 * Photograph (public domain) is bundled at APK build time from Wikimedia Commons.
 * Each reel shows a circular crop of the same photo. A RenderNode GPU layer
 * rotates only that part every VSYNC; the chassis and controls never redraw.
 * Offline playback and the existing audio engine are unaffected.
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
        var previousNs = 0L
        while (coroutineContext.isActive) {
            withFrameNanos { now ->
                if (previousNs > 0L && now > previousNs) {
                    val elapsed = now - previousNs
                    // Unlike a vinyl LP, the reel RPM changes as tape packs up.
                    val p = currentProgress.value
                    supplyAngle.floatValue =
                        advanceMayakReel(supplyAngle.floatValue, elapsed, p, false)
                    takeupAngle.floatValue =
                        advanceMayakReel(takeupAngle.floatValue, elapsed, p, true)
                }
                previousNs = now
            }
        }
    }

    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        // Preserve the aspect ratio of an authentic photograph. In particular,
        // wooden side rails and round reels must never be stretched to a square.
        val aspect = photo.width.toFloat() / photo.height
        val machineWidth = minOf(maxWidth, maxHeight * aspect)
        val machineHeight = machineWidth / aspect
        val widePhoto = aspect > 1.18f
        // Both museum photo and the user's reference capture show the same
        // real Mayak-203, but are framed at different aspect ratios.
        val leftRatio = if (widePhoto) .30f else .278f
        val rightRatio = if (widePhoto) .684f else .720f
        val yRatio = if (widePhoto) .322f else .290f
        val diameterRatio = if (widePhoto) .354f else .382f
        val diameter = machineWidth * diameterRatio
        val reelY = machineHeight * yRatio
        val leftX = machineWidth * leftRatio
        val rightX = machineWidth * rightRatio

        Box(Modifier.size(machineWidth, machineHeight)) {
            Canvas(Modifier.fillMaxSize()) {
                drawImage(
                    image = photo,
                    dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
                    filterQuality = FilterQuality.High
                )
                // The original reel image is completely covered by moving photo
                // layers. The stationary controls / branding remain photographic.
                for (x in listOf(leftRatio, rightRatio)) {
                    drawCircle(Color(0xFF101315), radius = size.width * diameterRatio * .5f,
                        center = Offset(size.width * x, size.height * yRatio))
                }
            }
            MayakPhotoReel(
                photo, leftRatio, yRatio, diameterRatio,
                Modifier.align(Alignment.TopStart)
                    .offset(x = leftX - diameter / 2f, y = reelY - diameter / 2f)
                    .size(diameter)
                    .graphicsLayer { rotationZ = supplyAngle.floatValue }
            )
            MayakPhotoReel(
                photo, rightRatio, yRatio, diameterRatio,
                Modifier.align(Alignment.TopStart)
                    .offset(x = rightX - diameter / 2f, y = reelY - diameter / 2f)
                    .size(diameter)
                    .graphicsLayer { rotationZ = takeupAngle.floatValue }
            )
        }
    }
}

@Composable
private fun MayakPhotoReel(
    photo: ImageBitmap, x: Float, y: Float, sourceDiameter: Float, modifier: Modifier
) {
    Canvas(modifier) {
        val w = photo.width
        val h = photo.height
        val cw = (w * sourceDiameter).roundToInt().coerceAtLeast(1)
        val ch = cw.coerceAtMost(h)
        val sx = (w * x - cw / 2f).roundToInt().coerceIn(0, (w-cw).coerceAtLeast(0))
        val sy = (h * y - ch / 2f).roundToInt().coerceIn(0, (h-ch).coerceAtLeast(0))
        val circle = Path().apply { addOval(Rect(Offset.Zero, size)) }
        clipPath(circle) {
            drawImage(
                image = photo,
                srcOffset = IntOffset(sx, sy),
                srcSize = IntSize(cw, ch),
                dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
                filterQuality = FilterQuality.High
            )
        }
    }
}

/** Conservation of tape-pack area: a full reel becomes empty as the other fills. */
internal fun mayakTapePackRadius(progress: Float, takeup: Boolean): Float {
    val p = progress.coerceIn(0f, 1f)
    val filled = if (takeup) p else 1f - p
    val hub = .145f
    val full = .386f
    return sqrt(hub * hub + (full * full - hub * hub) * filled)
}

/** Constant 19.05 cm/s tape travel, different angular speeds as reels change radius. */
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

