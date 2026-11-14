package com.markmusic.player.visual

import com.markmusic.player.audio.AudioSpectrumBus
import com.markmusic.player.LocalVisualActive
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import kotlinx.coroutines.delay
import kotlin.math.*

enum class VisualStyle(val label: String) {
    PULSE("Пульсар"), BARS("Неоновые столбы"), SPIRAL("Спираль"), NEBULA("Туманность"),
    RADIAL("Звуковая корона"), RIBBON("Лента волн"), STARS("Созвездия"), LISSAJOUS("Лиссажу"),
    GRID("Киберсетка"), AURORA("Северное сияние"), DIAMOND("Кристалл"), ORBITS("Орбиты"),
    MOUNTAINS("Горы баса"), BLOBS("Жидкий свет"), RAIN("Звуковой дождь"), GALAXY("Галактика"),
    FIREWORKS("Фейерверк"), TUNNEL("Тоннель"), LIGHTNING("Молнии"), VINYL("Винил")
}

private val CYAN = Color(0xFF58E9F5)
private val VIOLET = Color(0xFFBD83FF)
private val PINK = Color(0xFFFF76C5)
private val GOLD = Color(0xFFFFD38A)
private val BASE = Color(0xFF0A0D1F)

/** Audio-reactive visualizations from the player's decoded PCM, without microphone permission. */
@Composable
fun MusicVisualizer(
    style: VisualStyle,
    playing: Boolean,
    modifier: Modifier = Modifier
) {
    val active = LocalVisualActive.current.value && playing
    DisposableEffect(active) {
        AudioSpectrumBus.enabled = active
        onDispose { AudioSpectrumBus.enabled = false }
    }
    val latest by AudioSpectrumBus.spectrum.collectAsState()
    var phase by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(active) {
        while (active) {
            phase += 0.048f
            delay(66) // 15 FPS, enough for a decorative visualization
        }
    }
    Canvas(modifier) {
        drawRect(BASE.copy(alpha = .34f))
        render(style, if (active) latest else List(64) { 0f }, phase)
    }
}

@Composable
fun EffectPreview(style: VisualStyle, modifier: Modifier = Modifier) {
    val illustrativeSpectrum = remember {
        List(64) { i -> (.18f + .5f * abs(sin(i * .43f)) * abs(cos(i * .13f))).coerceIn(0f, 1f) }
    }
    Canvas(modifier) {
        drawRect(BASE.copy(alpha = .34f))
        render(style, illustrativeSpectrum, 0f)
    }
}

private fun DrawScope.render(style: VisualStyle, fft: List<Float>, t: Float) {
    val c = center
    val w = size.width
    val h = size.height
    val unit = size.minDimension
    fun b(i: Int): Float = fft[(i % fft.size + fft.size) % fft.size]
    fun point(angle: Float, radius: Float, x: Offset = c): Offset = Offset(
        x.x + cos(angle) * radius, x.y + sin(angle) * radius
    )
    fun tint(i: Int): Color = when (i % 4) { 0 -> CYAN; 1 -> VIOLET; 2 -> PINK; else -> GOLD }
    val beat = fft.take(10).average().toFloat().coerceIn(0f, 1f)
    val bass = beat * unit * .08f
    val tau = (PI * 2).toFloat()
    when (style) {
        VisualStyle.PULSE -> {
            for (ring in 0..5) {
                val r = unit * (.13f + ring * .058f) + bass * (1.2f - ring * .11f)
                drawCircle(tint(ring).copy(alpha = .65f), r, c, style = Stroke(2f + beat * 7f))
            }
            drawCircle(CYAN.copy(alpha = .22f), unit * (.10f + beat * .09f))
            drawCircle(Color.White, unit * (.012f + beat * .02f))
        }
        VisualStyle.BARS -> {
            repeat(48) { i ->
                val x = (i + .5f) / 48f * w
                val value = .05f + b(i) * .78f
                drawLine(tint(i).copy(alpha = .85f), Offset(x, h * (.5f - value * .42f)),
                    Offset(x, h * (.5f + value * .42f)), 2f + w / 115f, StrokeCap.Round)
            }
        }
        VisualStyle.SPIRAL -> {
            for (arm in 0..2) {
                var prev: Offset? = null
                repeat(145) { i ->
                    val f = i / 144f
                    val a = f * tau * 3.5f + arm * tau / 3 + t * .45f
                    val radius = unit * .035f + unit * .38f * f + b(i) * unit * .035f
                    val now = point(a, radius)
                    prev?.let { drawLine(tint(arm), it, now, 2f + beat * 3f) }
                    prev = now
                }
            }
        }
        VisualStyle.NEBULA -> {
            repeat(140) { i ->
                val angle = (i * 2.39996f + t * (.1f + i % 5 * .02f))
                val radius = sqrt((i + 1) / 140f) * unit * (.39f + b(i) * .05f)
                drawCircle(tint(i).copy(alpha = .18f + b(i) * .7f),
                    1.5f + b(i) * unit * .017f + i % 3, point(angle, radius))
            }
        }
        VisualStyle.RADIAL -> {
            repeat(80) { i ->
                val a = i * tau / 80f - PI.toFloat() / 2
                val r = unit * .17f
                drawLine(tint(i / 12), point(a, r), point(a, r + unit * (.035f + b(i) * .18f)),
                    2.5f, StrokeCap.Round)
            }
            drawCircle(CYAN.copy(alpha = .4f), unit * .16f, style = Stroke(3f))
        }
        VisualStyle.RIBBON -> {
            for (layer in 0..4) {
                var prev: Offset? = null
                repeat(125) { i ->
                    val x = i / 124f * w
                    val f = i / 124f
                    val y = h / 2 + sin(f * tau * (1.5f + layer * .25f) + t * .8f + layer) *
                        unit * (.03f + b(i + layer) * .17f)
                    val now = Offset(x, y)
                    prev?.let { drawLine(tint(layer).copy(alpha = .75f), it, now, 1.8f + beat * 4f) }
                    prev = now
                }
            }
        }
        VisualStyle.STARS -> {
            val points = List(52) { i ->
                val a = (i * 2.4f + t * .06f)
                point(a, sqrt((i + 1) / 52f) * unit * (.40f + b(i) * .05f))
            }
            points.forEachIndexed { i, p ->
                if (i > 0 && i % 3 == 0) drawLine(CYAN.copy(alpha = .24f + b(i) * .5f), points[i - 1], p, 1.3f)
                drawCircle(tint(i).copy(alpha = .5f + b(i) * .5f), 2.5f + b(i) * 5f, p)
            }
        }
        VisualStyle.LISSAJOUS -> {
            var prev: Offset? = null
            repeat(360) { i ->
                val a = i / 359f * tau
                val p = Offset(c.x + sin(3 * a + t * .35f) * unit * (.28f + bass / unit),
                    c.y + sin(4 * a + t * .23f + .7f) * unit * (.28f + bass / unit))
                prev?.let { drawLine(tint(i / 90), it, p, 2f) }
                prev = p
            }
        }
        VisualStyle.GRID -> {
            repeat(16) { i ->
                val shift = sin(t * .3f) * 8f
                val y = h * i / 15f
                drawLine(CYAN.copy(alpha = .13f + b(i) * .6f), Offset(0f, y), Offset(w, y + shift), 1.4f)
            }
            repeat(14) { i ->
                val x = w * i / 13f
                drawLine(VIOLET.copy(alpha = .13f + b(i + 16) * .6f), Offset(x, 0f), Offset(x, h), 1.4f)
            }
            drawCircle(PINK.copy(alpha = .65f), unit * (.08f + beat * .06f), style = Stroke(3f))
        }
        VisualStyle.AURORA -> {
            repeat(7) { layer ->
                var prev: Offset? = null
                repeat(110) { i ->
                    val f = i / 109f
                    val x = f * w
                    val y = h * (.24f + layer * .075f) +
                        sin(f * tau * 2f + t * .30f + layer * .4f) * unit * (.025f + b(i + layer) * .12f)
                    val p = Offset(x, y)
                    prev?.let { drawLine(tint(layer).copy(alpha = .22f + (7 - layer) * .06f), it, p, 3f + beat * 5f) }
                    prev = p
                }
            }
        }
        VisualStyle.DIAMOND -> {
            for (layer in 0..6) {
                val r = unit * (.07f + layer * .047f) + bass
                val corners = listOf(Offset(c.x, c.y-r), Offset(c.x+r, c.y),
                    Offset(c.x, c.y+r), Offset(c.x-r, c.y))
                repeat(4) { i -> drawLine(tint(layer).copy(alpha = .9f), corners[i], corners[(i+1)%4], 2f) }
            }
        }
        VisualStyle.ORBITS -> {
            for (orbit in 0..7) {
                val r = unit * (.08f + orbit * .045f)
                drawCircle(tint(orbit).copy(alpha = .23f), r, style = Stroke(2f))
                val angle = t * (.15f + orbit * .08f) + orbit * .83f
                drawCircle(tint(orbit), 3f + b(orbit) * 8f, point(angle, r))
            }
        }
        VisualStyle.MOUNTAINS -> {
            repeat(5) { layer ->
                val path = Path().apply { moveTo(0f, h * (.80f - layer * .075f)) }
                repeat(65) { i ->
                    val x = i / 64f * w
                    val y = h * (.80f - layer * .075f) - b(i + layer) * unit * .29f -
                        abs(sin(i * .22f + layer + t * .12f)) * unit * .035f
                    path.lineTo(x, y)
                }
                drawPath(path, tint(layer).copy(alpha = .65f), style = Stroke(2f + beat * 3f))
            }
        }
        VisualStyle.BLOBS -> {
            repeat(16) { i ->
                val a = i * tau / 16 + t * .18f * (if (i % 2 == 0) 1 else -1)
                val p = point(a, unit * (.10f + b(i) * .13f))
                drawCircle(tint(i).copy(alpha = .20f + b(i) * .6f),
                    unit * (.035f + b(i) * .045f), p)
            }
        }
        VisualStyle.RAIN -> {
            repeat(75) { i ->
                val x = (i + .5f) / 75f * w
                val scroll = (t * (18 + i % 8) + i * 19f) % (h + 20f)
                val drop = 6f + b(i) * 75f
                drawLine(tint(i).copy(alpha = .4f + b(i) * .6f), Offset(x, scroll),
                    Offset(x, (scroll + drop).coerceAtMost(h)), 1.5f + b(i) * 3f, StrokeCap.Round)
            }
        }
        VisualStyle.GALAXY -> {
            repeat(180) { i ->
                val arm = i % 4
                val r = sqrt((i + 1) / 180f) * unit * (.38f + b(i) * .06f)
                val a = arm * tau / 4 + r / unit * 7f + t * .13f
                drawCircle(tint(arm).copy(alpha = .25f + b(i) * .7f),
                    1f + b(i) * 4f, point(a, r))
            }
            drawCircle(Color.White.copy(alpha = .8f), unit * (.018f + beat * .028f))
        }
        VisualStyle.FIREWORKS -> {
            repeat(5) { fire ->
                val origin = Offset(w * (.15f + (fire * .17f)), h * (.25f + (fire % 3) * .23f))
                repeat(18) { i ->
                    val a = i * tau / 18 + fire
                    val r = unit * (.03f + b(i + fire) * .14f)
                    drawLine(tint(fire), point(a, r * .5f, origin), point(a, r, origin), 2.2f, StrokeCap.Round)
                }
            }
        }
        VisualStyle.TUNNEL -> {
            repeat(11) { i ->
                val v = ((i / 11f + t * .08f) % 1f)
                val r = unit * (.04f + v * .46f) + beat * 12f
                val corners = listOf(Offset(c.x-r, c.y-r), Offset(c.x+r, c.y-r),
                    Offset(c.x+r, c.y+r), Offset(c.x-r, c.y+r))
                repeat(4) { k -> drawLine(tint(i).copy(alpha = .25f + v * .6f),
                    corners[k], corners[(k+1)%4], 1.5f + v * 2f) }
            }
        }
        VisualStyle.LIGHTNING -> {
            repeat(9) { bolt ->
                var from = Offset(w * (bolt + .5f) / 9f, h * .10f)
                repeat(13) { segment ->
                    val x = w * (bolt + .5f) / 9f + sin(segment * 9f + bolt + t) *
                        unit * (.018f + b(segment + bolt) * .1f)
                    val to = Offset(x, h * (.1f + segment * .065f))
                    drawLine(tint(bolt).copy(alpha = .5f + b(segment + bolt) * .5f), from, to, 1.8f + beat * 4f)
                    from = to
                }
            }
        }
        VisualStyle.VINYL -> {
            drawCircle(Color(0xFF171A31), unit * .36f)
            repeat(11) { i ->
                drawCircle(tint(i).copy(alpha = .14f + b(i) * .25f),
                    unit * (.12f + i * .02f), style = Stroke(1.5f))
            }
            drawCircle(PINK, unit * (.105f + beat * .03f))
            drawCircle(BASE, unit * .014f)
            rotate(t * 17f) {
                drawLine(Color.White.copy(alpha = .6f), c, point(0f, unit * .34f), 3f)
            }
        }
    }
}
