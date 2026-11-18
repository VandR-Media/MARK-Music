package com.markmusic.player.visual

import android.graphics.Canvas as NativeCanvas
import android.graphics.Color as C
import android.graphics.Paint
import android.graphics.RectF
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import com.markmusic.player.audio.AudioMeterBus
import com.markmusic.player.audio.normalizeMeterRms
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Native Android Canvas rendering fallback for Huawei where embedded WebView
 * scenes remain black. Consumes ONLY ExoPlayer's measured PCM/FFT values.
 * This is a depth-styled native instrument, NOT a WebGL 3D renderer.
 */
@Composable
internal fun MarkNativeStereoInstrument(
    variant: Int,
    playing: Boolean,
    modifier: Modifier = Modifier
) {
    val meter by AudioMeterBus.levels.collectAsState()
    val spectrum by AudioMeterBus.frequencyBands.collectAsState()
    var style by remember { mutableIntStateOf(0) }
    val titles = arrayOf("ANALOG STUDIO", "SPECTRUM MATRIX", "NEON TOWERS",
        "HI-FI DECK", "PULSE RINGS", "OSCILLOSCOPE")
    val mode = if (variant == 2) style else variant.coerceIn(0, 1)
    val bands = if (playing && spectrum.ready) floatArrayOf(
        spectrum.left.low, spectrum.left.mid, spectrum.left.high,
        spectrum.right.low, spectrum.right.mid, spectrum.right.high
    ).map { normalizeMeterRms(it.toDouble()) }.toFloatArray() else FloatArray(6)
    val rms = if (playing) floatArrayOf(meter.left.coerceIn(0f, 1f), meter.right.coerceIn(0f, 1f))
        else floatArrayOf(0f, 0f)

    Box(modifier = modifier.then(if (variant == 2) Modifier.clickable { style = (style + 1) % 6 } else Modifier)) {
        Canvas(Modifier.fillMaxSize()) {
            drawIntoCanvas { target ->
                val canvas = target.nativeCanvas
                val w = size.width
                val h = size.height
                if (w <= 0f || h <= 0f) return@drawIntoCanvas
                canvas.save()
                canvas.scale(w / 800f, h / 800f)
                val g = InstrumentPainter(canvas)
                g.round(0f, 0f, 800f, 800f, 25f, C.rgb(3, 8, 20))
                g.round(22f, 38f, 778f, 754f, 48f, C.rgb(18, 31, 52))
                g.round(30f, 46f, 770f, 746f, 42f, C.rgb(77, 103, 131), false, 4f)
                g.round(43f, 58f, 757f, 730f, 34f, C.rgb(6, 13, 27))
                g.text(titles[mode], 400f, 120f, 32f, C.rgb(198, 232, 251))
                g.text("MARK  AUDIO   •   STEREO PCM", 400f, 155f, 17f, C.rgb(99, 204, 244))
                when (mode) {
                    0, 3 -> {
                        for (ch in 0..1) {
                            val cx = if (ch == 0) 220f else 580f
                            g.round(cx - 153f, 214f, cx + 153f, 519f, 24f, C.rgb(24, 28, 34))
                            g.round(cx - 138f, 227f, cx + 138f, 496f, 17f, C.rgb(196, 155, 94))
                            for (i in 0..8) {
                                val angle = Math.PI * (1.18 + .64 * i / 8)
                                val x = cx + (cos(angle) * 112).toFloat()
                                val y = 458f + (sin(angle) * 112).toFloat()
                                g.circle(x, y, 2f, C.rgb(72, 49, 32))
                            }
                            val angle = Math.PI * (1.18 + .64 * rms[ch])
                            g.line(cx, 466f, cx + (cos(angle) * 113).toFloat(),
                                466f + (sin(angle) * 113).toFloat(), C.rgb(120, 32, 34), 6f)
                            g.circle(cx, 466f, 13f, C.rgb(45, 52, 65))
                            g.text(if (ch == 0) "LEFT" else "RIGHT", cx, 288f, 22f, C.rgb(74, 48, 29))
                        }
                        if (mode == 3) g.text("HI-FI MASTER DECK", 400f, 555f, 21f, C.rgb(175, 211, 235))
                        for (i in 0..5) {
                            val x = 90f + i * 112f
                            g.text(arrayOf("L LOW","L MID","L HIGH","R LOW","R MID","R HIGH")[i],
                                x + 25f, 597f, 13f, C.rgb(168, 208, 239))
                            for (j in 0..9) g.round(x, 670f - j * 9f, x + 52f, 676f - j * 9f,
                                2f, if (bands[i] * 10f > j) g.led(j) else C.rgb(39, 57, 68))
                        }
                    }
                    1, 2 -> {
                        for (i in 0..5) {
                            val left = 88f + i * 110f
                            val top = if (mode == 2) 245f else 220f
                            g.round(left, top, left + 68f, 646f, 14f, C.rgb(15, 28, 46))
                            for (j in 0..21) {
                                val color = if (bands[i] * 22 > j) g.led(j / 2)
                                else C.rgb(32, 48, 62)
                                g.round(left + 8f, 624f - j * 16f,
                                    left + 60f, 634f - j * 16f, 3f, color)
                            }
                            g.text(arrayOf("LOW","MID","HIGH","LOW","MID","HIGH")[i],
                                left + 34f, 688f, 17f, C.rgb(181, 221, 247))
                        }
                        g.text("LEFT  /  RIGHT", 400f, 740f, 20f, C.rgb(101, 223, 246))
                    }
                    4 -> {
                        for (ch in 0..1) {
                            val cx = if (ch == 0) 230f else 570f
                            g.circle(cx, 405f, 151f, C.rgb(52, 73, 97))
                            g.circle(cx, 405f, 137f, C.rgb(4, 11, 27))
                            for (j in 0..2) {
                                val radius = 117f - j * 28f
                                g.arc(cx, 405f, radius, -90f, 360f * bands[ch * 3 + j],
                                    if (ch == 0) C.CYAN else C.MAGENTA, 9f)
                            }
                            g.text(if(ch == 0) "LEFT" else "RIGHT", cx, 620f, 22f, C.WHITE)
                        }
                    }
                    else -> {
                        for (ch in 0..1) {
                            val cx = if (ch == 0) 215f else 585f
                            g.round(cx - 150f, 250f, cx + 150f, 594f, 20f, C.rgb(4, 33, 39))
                            for (i in 0..8) g.line(cx - 136f, 270f + i * 39f,
                                cx + 136f, 270f + i * 39f, C.rgb(27, 65, 72), 1f)
                            // No sample-level PCM waveform is available; show honest signal levels.
                            g.line(cx - 130f, 422f - rms[ch] * 95f, cx + 130f,
                                422f - rms[ch] * 95f, if (ch == 0) C.CYAN else C.MAGENTA, 5f)
                            g.text(if(ch == 0) "LEFT LEVEL" else "RIGHT LEVEL", cx, 640f,
                                19f, C.rgb(167, 222, 242))
                        }
                    }
                }
                if (variant == 2) g.text("НАЖМИ, ЧТОБЫ СМЕНИТЬ ЭФФЕКТ  •  " +
                    (mode + 1) + "/6", 400f, 780f, 16f, C.rgb(132, 190, 216))
                canvas.restore()
            }
        }
    }
}

private class InstrumentPainter(private val c: NativeCanvas) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    fun round(l: Float, t: Float, r: Float, b: Float, rad: Float, color: Int,
              fill: Boolean = true, width: Float = 1f) {
        p.color = color
        p.style = if (fill) Paint.Style.FILL else Paint.Style.STROKE
        p.strokeWidth = width
        c.drawRoundRect(RectF(l, t, r, b), rad, rad, p)
        p.style = Paint.Style.FILL
    }
    fun circle(x: Float, y: Float, rad: Float, color: Int) {
        p.color = color
        p.style = Paint.Style.FILL
        c.drawCircle(x, y, rad, p)
    }
    fun line(x: Float, y: Float, x2: Float, y2: Float, color: Int, width: Float) {
        p.color = color
        p.strokeWidth = width
        p.strokeCap = Paint.Cap.ROUND
        c.drawLine(x, y, x2, y2, p)
    }
    fun arc(cx: Float, cy: Float, rad: Float, start: Float, sweep: Float, color: Int, width: Float) {
        p.color = color
        p.strokeWidth = width
        p.style = Paint.Style.STROKE
        c.drawArc(RectF(cx-rad,cy-rad,cx+rad,cy+rad),start,sweep,false,p)
        p.style = Paint.Style.FILL
    }
    fun text(s: String, x: Float, y: Float, size: Float, color: Int) {
        p.color = color
        p.textSize = size
        p.typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD)
        p.textAlign = Paint.Align.CENTER
        p.style = Paint.Style.FILL
        c.drawText(s, x, y, p)
    }
    fun led(j: Int): Int = when {
        j >= 8 -> C.rgb(255, 91, 95)
        j >= 5 -> C.rgb(250, 208, 76)
        else -> C.rgb(44, 232, 170)
    }
}
