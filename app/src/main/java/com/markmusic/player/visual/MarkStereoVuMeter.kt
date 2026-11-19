package com.markmusic.player.visual

import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.platform.LocalContext
import com.markmusic.player.R
import com.markmusic.player.audio.AudioMeterBus
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.PI

/**
 * Real MARK Audio branded photographic VU with two separate mechanical needles.
 *
 * The complete approved vintage instrument artwork is stored offline as a WebP
 * Android drawable. It is never reconstructed by vector primitives. Only the
 * two removed pointers are animated, independently, using ExoPlayer PCM levels.
 */
internal fun vuNeedleAngle(level: Float): Float =
    -132f + (if (level.isFinite()) level.coerceIn(0f, 1f) else 0f) * 90f

@Composable
internal fun MarkStereoVuMeter(
    playing: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val artwork = remember(context) {
        BitmapFactory.decodeResource(context.resources, R.drawable.mark_stereo_vu_photo)
    }
    val meter by AudioMeterBus.levels.collectAsState()
    val left by animateFloatAsState(
        targetValue = if (playing) meter.left else 0f,
        animationSpec = tween(durationMillis = 170),
        label = "stereo-vu-left"
    )
    val right by animateFloatAsState(
        targetValue = if (playing) meter.right else 0f,
        animationSpec = tween(durationMillis = 170),
        label = "stereo-vu-right"
    )

    Canvas(modifier.fillMaxSize()) {
        drawIntoCanvas { target ->
            val canvas = target.nativeCanvas
            val scale = minOf(size.width / 1774f, size.height / 887f)
            if (scale <= 0f) return@drawIntoCanvas
            canvas.save()
            canvas.translate((size.width - 1774f * scale) / 2f,
                (size.height - 887f * scale) / 2f)
            canvas.scale(scale, scale)
            val photoPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            canvas.drawBitmap(artwork, null, RectF(0f, 0f, 1774f, 887f), photoPaint)
            drawNeedle(canvas, 446f, 555f, vuNeedleAngle(left))
            drawNeedle(canvas, 1282f, 555f, vuNeedleAngle(right))
            canvas.restore()
        }
    }
}

private fun drawNeedle(
    canvas: android.graphics.Canvas,
    cx: Float, cy: Float, degrees: Float
) {
    val angle = degrees * (PI / 180.0)
    val ux = cos(angle).toFloat()
    val uy = sin(angle).toFloat()
    val nx = -uy
    val ny = ux
    val length = 348f
    val tipX = cx + ux * length
    val tipY = cy + uy * length

    fun shape(offsetX: Float, offsetY: Float, base: Float): Path = Path().apply {
        moveTo(cx + nx * base + offsetX, cy + ny * base + offsetY)
        lineTo(tipX + offsetX, tipY + offsetY)
        lineTo(cx - nx * base + offsetX, cy - ny * base + offsetY)
        close()
    }
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.argb(110, 19, 12, 8)
    }
    canvas.drawPath(shape(3f, 4f, 6f), paint)
    paint.color = Color.rgb(13, 15, 17)
    canvas.drawPath(shape(0f, 0f, 5f), paint)
    val gleam = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(95, 195, 186, 169)
        strokeWidth = 1.4f
        strokeCap = Paint.Cap.ROUND
    }
    canvas.drawLine(cx + nx * 2f, cy + ny * 2f,
        cx + ux * (length - 35f) + nx * 0.6f,
        cy + uy * (length - 35f) + ny * 0.6f, gleam)
    paint.color = Color.rgb(49, 41, 30)
    canvas.drawCircle(cx, cy, 10f, paint)
    paint.color = Color.rgb(205, 155, 72)
    canvas.drawCircle(cx - 1.5f, cy - 2f, 4.4f, paint)
}
