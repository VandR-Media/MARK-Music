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
        // Relative coordinates mirror the source photograph. The image stays
        // in its original square-ish frame; no vector chassis or fake labels.
        val reelDiameter = maxWidth * .382f
        val reelY = maxHeight * .290f
        val leftX = maxWidth * .278f
        val rightX = maxWidth * .720f

        Canvas(Modifier.fillMaxSize()) {
            drawImage(
                image = photo,
                dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
                filterQuality = FilterQuality.High
            )
            // Cover the stationary reel image under its rotating counterpart;
            // window cut-outs show an authentic shadowed tape well.
            for (x in listOf(.278f, .720f)) {
                drawCircle(Color(0xFF101315), radius = size.width * .190f,
                    center = Offset(size.width * x, size.height * .290f))
            }
        }
        MayakPhotoReel(
            photo, .278f, .290f, .382f,
            Modifier.align(Alignment.TopStart)
                .offset(x = leftX - reelDiameter / 2f, y = reelY - reelDiameter / 2f)
                .size(reelDiameter)
                .graphicsLayer { rotationZ = supplyAngle.floatValue }
        )
        MayakPhotoReel(
            photo, .720f, .290f, .382f,
            Modifier.align(Alignment.TopStart)
                .offset(x = rightX - reelDiameter / 2f, y = reelY - reelDiameter / 2f)
                .size(reelDiameter)
                .graphicsLayer { rotationZ = takeupAngle.floatValue }
        )
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
        val ch = (h * sourceDiameter).roundToInt().coerceAtLeast(1)
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

