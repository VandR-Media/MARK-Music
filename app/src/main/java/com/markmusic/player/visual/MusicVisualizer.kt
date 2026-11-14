package com.markmusic.player.visual

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.markmusic.player.LocalGlassTilt
import com.markmusic.player.LocalVisualActive
import com.markmusic.player.audio.AudioSpectrumBus
import kotlinx.coroutines.isActive
import kotlin.coroutines.coroutineContext
import kotlin.math.*

/**
 * Fully new dimensional renderer. Each frame is scheduled on Android's display
 * vsync (normally 60 fps), rather than a 66 ms delay (15 fps). PCM data is
 * intentionally analyzed at a lower rate, then smoothly interpolated here.
 * Runs only while no artwork is available and the player is in the foreground.
 */
enum class VisualStyle(val label: String) {
    PULSE("Пульсар"), BARS("Неоновые столбы"), SPIRAL("Спираль"), NEBULA("Туманность"),
    RADIAL("Звуковая корона"), RIBBON("Лента волн"), STARS("Созвездия"), LISSAJOUS("Лиссажу"),
    GRID("Киберсетка"), AURORA("Северное сияние"), DIAMOND("Кристалл"), ORBITS("Орбиты"),
    MOUNTAINS("Горы баса"), BLOBS("Жидкий свет"), RAIN("Звуковой дождь"), GALAXY("Галактика"),
    FIREWORKS("Фейерверк"), TUNNEL("Тоннель"), LIGHTNING("Молнии"), VINYL("Винил")
}

private val CYAN = Color(0xFF65EEFF)
private val VIOLET = Color(0xFFB78CFF)
private val PINK = Color(0xFFFF80CD)
private val GOLD = Color(0xFFFFD18B)
private val SHADES = arrayOf(CYAN, VIOLET, PINK, GOLD)

/** A compact immutable presentation state; only the visible Canvas redraws. */
private data class VisualFrame(val seconds: Float, val bands: List<Float>)

@Composable
fun MusicVisualizer(style: VisualStyle, playing: Boolean, modifier: Modifier = Modifier) {
    val active = LocalVisualActive.current.value && playing
    val source by AudioSpectrumBus.spectrum.collectAsState()
    val latestSource by rememberUpdatedState(source)
    var frame by remember { mutableStateOf(VisualFrame(0f, List(64) { 0f })) }
    val tilt = LocalGlassTilt.current.value

    DisposableEffect(active) {
        AudioSpectrumBus.enabled = active
        onDispose { AudioSpectrumBus.enabled = false }
    }
    LaunchedEffect(active) {
        if (!active) return@LaunchedEffect
        val filtered = FloatArray(64) { frame.bands.getOrElse(it) { 0f } }
        var elapsed = frame.seconds
        var lastFrameNs = 0L
        while (coroutineContext.isActive) {
            // withFrameNanos is tied to the display refresh. No artificial
            // 15-fps throttle and no busy spinning while the UI is hidden.
            withFrameNanos { now ->
                val dt = if (lastFrameNs == 0L) 1f / 60f
                    else ((now - lastFrameNs) / 1_000_000_000f).coerceIn(0f, .05f)
                lastFrameNs = now
                elapsed += dt
                val target = latestSource
                for (i in filtered.indices) {
                    val sample = target.getOrElse(i) { 0f }.coerceIn(0f, 1f)
                    // Rapid but controlled attack, gentle decay. 8-Hz audio
                    // sampling will not make the on-screen geometry jump.
                    val tau = if (sample > filtered[i]) .095f else .28f
                    val blend = (1f - exp(-dt / tau)).coerceIn(0f, 1f)
                    filtered[i] += (sample - filtered[i]) * blend
                }
                frame = VisualFrame(elapsed, filtered.toList())
            }
        }
    }
    Canvas(modifier) {
        renderNewScene(style, frame.bands, frame.seconds, tilt.x * .20f, tilt.y * .20f)
    }
}

/** Static samples for the effect selector: no audio processing or animation. */
@Composable
fun EffectPreview(style: VisualStyle, modifier: Modifier = Modifier) {
    val preview = remember {
        List(64) { i -> (.20f + .20f * abs(sin(i * .31f))).coerceIn(0f, 1f) }
    }
    Canvas(modifier) { renderNewScene(style, preview, 1.5f, 0f, 0f) }
}

private fun DrawScope.renderNewScene(
    style: VisualStyle, bands: List<Float>, time: Float, pitch: Float, roll: Float
) {
    val side = size.minDimension
    val centerPoint = center
    val variant = style.ordinal / 5
    val family = style.ordinal % 5
    val shift = time * (if (family == 1) .36f else .18f)
    val bass = bands.take(8).average().toFloat().coerceIn(0f, 1f)
    val energy = bands.average().toFloat().coerceIn(0f, 1f)
    fun band(i: Int) = bands[(i % bands.size + bands.size) % bands.size].coerceIn(0f, 1f)
    fun shade(i: Int) = SHADES[(i + variant) % SHADES.size]
    val tau = (PI * 2.0).toFloat()

    // Ambient volume: gradients suggest depth before the geometry is drawn.
    drawRect(Brush.verticalGradient(listOf(
        Color(0xFF05091C), Color(0xFF131A42), Color(0xFF060B22))))
    drawCircle(Brush.radialGradient(
        listOf(shade(0).copy(alpha = .18f + energy * .16f),
            shade(1).copy(alpha = .08f), Color.Transparent),
        center = Offset(centerPoint.x + pitch * side, centerPoint.y + roll * side),
        radius = side * .80f), radius = side * .80f,
        center = Offset(centerPoint.x + pitch * side, centerPoint.y + roll * side))

    // Lightweight perspective projective transform. z = 0 is near, z > 0 far.
    fun project(x: Float, y: Float, z: Float): Offset {
        val rotatedX = x * cos(pitch) - z * sin(pitch)
        val rotatedZ = x * sin(pitch) + z * cos(pitch)
        val rotatedY = y * cos(roll) - rotatedZ * sin(roll)
        val depth = (1.0f + max(-.3f, rotatedZ) * .62f).coerceAtLeast(.36f)
        return Offset(centerPoint.x + rotatedX * side / depth,
            centerPoint.y + rotatedY * side / depth)
    }
    fun luminousLine(a: Offset, b: Offset, color: Color, width: Float, alpha: Float = 1f) {
        // Multiple transparent strokes simulate bloom without GPU blur shaders.
        drawLine(color.copy(alpha = alpha * .12f), a, b, width * 4.3f, StrokeCap.Round)
        drawLine(color.copy(alpha = alpha * .34f), a, b, width * 2.0f, StrokeCap.Round)
        drawLine(color.copy(alpha = alpha * .94f), a, b, width, StrokeCap.Round)
    }
    when (family) {
        0 -> { // Rotating toroidal sculpture / sound crown, curved in 3D.
            val rings = 5 + variant
            repeat(rings) { layer ->
                val radius = .16f + layer * .056f + bass * .035f
                val inclination = .15f + layer * .32f + shift * (if (layer % 2 == 0) 1 else -1)
                var previous: Offset? = null
                repeat(73) { n ->
                    val angle = n * tau / 72f
                    val ripple = band(n + layer * 7) * .028f
                    val localRadius = radius + ripple
                    val x = cos(angle) * localRadius
                    val y = sin(angle) * localRadius * cos(inclination)
                    val z = .15f + sin(angle) * localRadius * sin(inclination) + layer * .025f
                    val p = project(x, y, z)
                    previous?.let {
                        luminousLine(it, p, shade(layer), 1.0f + side * .0022f,
                            .60f + band(n + layer) * .35f)
                    }
                    previous = p
                }
            }
            drawCircle(Brush.radialGradient(listOf(
                shade(2).copy(alpha = .90f), shade(0).copy(alpha = .26f),
                Color.Transparent), centerPoint, side * .11f), radius = side * .11f)
        }
        1 -> { // Traveling perspective tunnel with moving depth planes.
            repeat(10) { plane ->
                val z = ((plane / 10f + time * (.085f + variant * .012f)) % 1f) * 3.0f
                val extent = .45f + bass * .055f
                val steps = 6 + variant * 2
                val corners = List(steps) { n ->
                    val a = n * tau / steps + time * .08f
                    project(cos(a) * extent, sin(a) * extent, z)
                }
                val color = shade(plane)
                repeat(steps) { n ->
                    luminousLine(corners[n], corners[(n+1)%steps],
                        color, 1.15f + side * .0012f,
                        (.80f - z / 4f).coerceIn(.10f, .90f))
                }
            }
            repeat(12) { spoke ->
                val a = spoke * tau / 12f
                val near = project(cos(a) * .46f, sin(a) * .46f, .03f)
                val far = project(cos(a) * .46f, sin(a) * .46f, 2.8f)
                drawLine(shade(spoke).copy(alpha = .10f + energy * .15f),
                    near, far, 1.2f)
            }
        }
        2 -> { // Volumetric layered ribbons, rippling out of screen.
            repeat(5 + variant) { layer ->
                val depth = layer * .20f
                val color = shade(layer)
                var before: Offset? = null
                repeat(81) { n ->
                    val u = n / 80f
                    val wave = sin(u * tau * (1.5f + variant * .28f) + time * .75f + layer * .62f)
                    val amplitude = .055f + band(n + layer * 7) * .15f
                    val x = (u - .5f) * .99f
                    val y = wave * amplitude + (layer - 3f) * .038f
                    val z = depth + sin(u * tau + time * .16f + layer) * .13f
                    val p = project(x, y, z)
                    before?.let { luminousLine(it, p, color, 1.6f, .85f - layer * .07f) }
                    before = p
                }
            }
        }
        3 -> { // 3D swirling galactic nebula with depth-sized particles.
            repeat(170) { i ->
                val seed = i * 2.399963f
                val r = sqrt((i + .5f) / 170f) * (.43f + band(i) * .05f)
                val angle = seed + time * (.12f + variant * .04f) + r * (4f + variant)
                val z = .10f + .55f * (1f + sin(seed * 1.43f + shift)) / 2f
                val pos = project(cos(angle) * r, sin(angle) * r, z)
                val pointRadius = (side * (.0025f + band(i) * .006f)) / (1f + z)
                drawCircle(shade(i / 15).copy(alpha = .12f + band(i) * .50f),
                    pointRadius * 2.5f, pos)
                drawCircle(shade(i / 15).copy(alpha = .48f + band(i) * .40f),
                    pointRadius.coerceAtLeast(.8f), pos)
            }
            drawCircle(Brush.radialGradient(listOf(
                Color.White.copy(alpha = .85f), shade(1).copy(alpha = .38f),
                Color.Transparent), centerPoint, side * .11f), radius = side * .11f)
        }
        else -> { // Extruded spectral pillars with parallax and illuminated caps.
            repeat(35) { i ->
                val x = (i - 17f) / 35f * .95f
                val z = .15f + (i % 5) * .14f
                val height = .07f + band(i * 2) * .35f + .02f * sin(time * .6f + i)
                val base = project(x, .31f, z)
                val top = project(x, .31f - height, z)
                val upperBack = project(x + .014f, .31f - height - .010f, z + .065f)
                val tint = shade(i / 7)
                luminousLine(base, top, tint, side * .007f, .85f)
                drawLine(Color.White.copy(alpha = .36f), top, upperBack, 1.6f)
                drawCircle(tint.copy(alpha = .5f), side * .004f, top)
            }
            repeat(5) { depth ->
                val y = .32f + depth * .015f
                val from = project(-.47f, y, depth * .22f)
                val to = project(.47f, y, depth * .22f)
                drawLine(shade(depth).copy(alpha = .14f), from, to, 1f)
            }
        }
    }
}
