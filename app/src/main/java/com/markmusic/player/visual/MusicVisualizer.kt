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
                // On 120-Hz panels render every other vsync (~60 FPS); on
                // 60/90-Hz panels keep each vsync. Avoid 120-FPS thermal load.
                if (lastFrameNs != 0L && now - lastFrameNs < 9_000_000L) {
                    return@withFrameNanos
                }
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
    style: VisualStyle, bands: List<Float>, t: Float, pitch: Float, roll: Float
) {
    val s = size.minDimension
    val w = size.width
    val h = size.height
    val c = center
    val tau = (PI * 2).toFloat()
    fun b(i: Int): Float = bands[(i % bands.size + bands.size) % bands.size].coerceIn(0f, 1f)
    val bass = bands.take(9).average().toFloat().coerceIn(0f, 1f)
    val mid = bands.drop(12).take(26).average().toFloat().coerceIn(0f, 1f)
    val treble = bands.drop(38).average().toFloat().coerceIn(0f, 1f)
    fun ink(i: Int) = SHADES[(i % SHADES.size + SHADES.size) % SHADES.size]
    fun p(x: Float, y: Float) = Offset(c.x + x * s, c.y + y * s)
    fun polar(a: Float, r: Float) = p(cos(a) * r, sin(a) * r)
    fun projected(x: Float, y: Float, z: Float): Offset {
        val xx = x * cos(pitch) - z * sin(pitch)
        val zz = x * sin(pitch) + z * cos(pitch)
        val yy = y * cos(roll) - zz * sin(roll)
        val d = (1.1f + zz * .65f).coerceAtLeast(.4f)
        return p(xx / d, yy / d)
    }
    fun beam(a: Offset, z: Offset, color: Color, thick: Float = 1.7f, opacity: Float = .85f) {
        drawLine(color.copy(alpha = opacity * .13f), a, z, thick * 5f, StrokeCap.Round)
        drawLine(color.copy(alpha = opacity * .36f), a, z, thick * 2.3f, StrokeCap.Round)
        drawLine(color.copy(alpha = opacity), a, z, thick, StrokeCap.Round)
    }
    fun dot(at: Offset, r: Float, color: Color, a: Float = .9f) {
        drawCircle(color.copy(alpha = a * .16f), r * 2.9f, at)
        drawCircle(color.copy(alpha = a), r, at)
    }
    fun traced(count: Int, color: Color, width: Float = 1.8f, calc: (Int) -> Offset) {
        var last = calc(0)
        for (i in 1..count) {
            val next = calc(i)
            beam(last, next, color, width)
            last = next
        }
    }
    // Each named mode below is a separate composition, not a variation of
    // five reused templates. Every preview depicts its actual live geometry.
    drawRect(Brush.verticalGradient(listOf(
        Color(0xFF040818), Color(0xFF101D3D), Color(0xFF070B21))))
    val halo = p(sin(t * .11f) * .12f, cos(t * .09f) * .09f)
    drawCircle(Brush.radialGradient(listOf(
        ink(style.ordinal).copy(alpha = .12f + bass * .18f),
        Color.Transparent), halo, s * .68f), s * .68f, halo)

    when (style) {
        VisualStyle.PULSE -> {
            // Breathing plasma core, expanding shockwaves and spark corona.
            drawCircle(Brush.radialGradient(listOf(
                Color.White.copy(alpha = .9f), CYAN.copy(alpha = .8f),
                VIOLET.copy(alpha = .25f), Color.Transparent), c, s * (.12f + bass * .10f)),
                s * (.12f + bass * .10f))
            repeat(4) { ring ->
                val r = s * ((ring / 4f + t * .12f) % 1f) * .48f
                drawCircle(CYAN.copy(alpha = (1f - r / (s * .5f)) * .65f),
                    r, c, style = Stroke(1.2f + bass * 3f))
            }
            repeat(56) { i ->
                val a = i * tau / 56f
                val outer = .17f + b(i) * .14f + sin(t * 1.1f + i) * .011f
                beam(polar(a, .14f), polar(a, outer), ink(i / 14), 1.5f)
            }
        }
        VisualStyle.BARS -> {
            // Floating skyline with 3D extruded building faces.
            repeat(32) { i ->
                val x = (i - 15.5f) / 32f * .93f
                val z = .10f + (i % 6) * .075f
                val height = .07f + b(i * 2) * .40f
                val foot = projected(x, .38f, z)
                val roof = projected(x, .38f - height, z)
                val side = projected(x + .016f, .38f - height - .012f, z + .06f)
                beam(foot, roof, ink(i / 8), 2f + s * .003f)
                beam(roof, side, Color.White, 1.3f, .55f)
            }
            traced(40, CYAN.copy(alpha = .34f), 1.1f) { i ->
                projected((i / 40f - .5f) * .96f, .40f, .10f)
            }
        }
        VisualStyle.SPIRAL -> {
            // Double DNA helix: two crossing strands connected by rungs.
            repeat(2) { strand ->
                traced(180, if (strand == 0) CYAN else PINK, 2.7f) { i ->
                    val u = i / 180f
                    val a = u * tau * 4.0f + t * .64f + strand * PI.toFloat()
                    projected(cos(a) * .19f, (u - .5f) * .85f, .35f + sin(a) * .19f)
                }
            }
            repeat(36) { i ->
                val u = i / 35f
                val a = u * tau * 4f + t * .64f
                beam(projected(cos(a) * .19f, (u-.5f)*.85f, .35f+sin(a)*.19f),
                    projected(-cos(a)*.19f, (u-.5f)*.85f, .35f-sin(a)*.19f),
                    GOLD, 1.1f, .5f + b(i)*.35f)
            }
        }
        VisualStyle.NEBULA -> {
            // Soft colored gas clouds with slowly drifting luminous filaments.
            repeat(11) { layer ->
                val a = layer * 2.39996f + t * .09f
                val centerCloud = polar(a, .13f + layer * .019f)
                val radius = s * (.10f + (layer % 4) * .028f + bass * .03f)
                drawCircle(Brush.radialGradient(listOf(
                    ink(layer).copy(alpha = .21f), Color.Transparent),
                    centerCloud, radius), radius, centerCloud)
            }
            repeat(95) { i ->
                val angle = i * 2.39996f + t * .045f
                val r = sqrt((i + 1) / 95f) * .43f
                dot(polar(angle, r), 1f + b(i) * 2f, ink(i / 24), .35f + b(i)*.4f)
            }
        }
        VisualStyle.RADIAL -> {
            // Turbine / sound crown: a true 360-degree spiked spectrum disc.
            drawCircle(VIOLET.copy(alpha = .16f), s * .20f, c)
            repeat(96) { i ->
                val a = i * tau / 96f - PI.toFloat() / 2f
                val length = .035f + b(i) * .23f
                beam(polar(a, .18f), polar(a, .18f + length), ink(i / 24),
                    1.7f + b(i) * 2.1f)
            }
            drawCircle(CYAN, s * .177f, c, style = Stroke(2.8f))
            dot(c, s * .025f, Color.White)
        }
        VisualStyle.RIBBON -> {
            // Wide streaming silk ribbons, flowing across canvas like fabric.
            repeat(5) { layer ->
                traced(96, ink(layer), 2.0f + (4 - layer) * .6f) { i ->
                    val u = i / 96f
                    val wave = sin(u * tau * 2.5f + t * .8f + layer * .67f)
                    val envelope = .06f + b(i + layer*9) * .12f
                    projected((u-.5f)*.96f, wave * envelope + (layer-2f)*.036f,
                        .15f + layer*.19f + cos(u*tau+t*.25f)*.08f)
                }
            }
        }
        VisualStyle.STARS -> {
            // Constellation atlas: points connected by deliberate polygon chains.
            val nodes = List(38) { i ->
                val a = i * 2.39996f + t * .015f
                polar(a, sqrt((i+1)/38f)*.43f)
            }
            nodes.forEachIndexed { i, loc ->
                if (i > 0 && i % 4 != 0) beam(nodes[i-1], loc, VIOLET, 1.2f, .27f)
                if (i >= 6 && i % 5 == 0) beam(nodes[i-6], loc, CYAN, .9f, .25f)
                dot(loc, 1.3f + b(i)*3.5f, ink(i / 10))
            }
        }
        VisualStyle.LISSAJOUS -> {
            // One smooth continuous orbital knot, not a stack of waves.
            repeat(3) { layer ->
                traced(340, ink(layer), 1.6f + layer*.55f) { i ->
                    val a = i / 340f * tau
                    projected(sin(3*a + t*.30f + layer*.25f)*.32f,
                        sin(4*a + t*.22f + .65f)*.30f,
                        .29f + cos(5*a + layer*.50f)*.18f)
                }
            }
        }
        VisualStyle.GRID -> {
            // Moving synthwave landscape receding to a horizon.
            val horizonY = -.18f
            repeat(17) { i ->
                val depth = i / 16f
                val y = horizonY + depth*depth*.74f
                beam(p(-.50f, y), p(.50f, y), CYAN, 1.1f, .10f + depth*.55f)
            }
            repeat(15) { col ->
                val x = (col-7f)/7f
                beam(p(x*.09f, horizonY), p(x*.50f, .56f), PINK, 1.1f, .42f)
            }
            val sun = p(0f, -.23f)
            drawCircle(Brush.radialGradient(listOf(PINK.copy(alpha=.7f),
                GOLD.copy(alpha=.20f),Color.Transparent), sun,s*.20f),s*.20f,sun)
            traced(100, GOLD, 1.6f) { i ->
                val x = i / 100f - .5f
                p(x, -.15f - .04f*sin(x*8f + t*.35f))
            }
        }
        VisualStyle.AURORA -> {
            // Curtains of translucent aurora stretching vertically.
            repeat(7) { layer ->
                val color = ink(layer)
                repeat(22) { strand ->
                    val x = (strand/21f - .5f)*.90f
                    traced(38, color, 1.25f+layer*.3f) { j ->
                        val v = j/38f
                        p(x + sin(v*6f+t*.33f+layer*.50f)*(.018f+layer*.006f),
                            v*.84f-.43f)
                    }
                }
            }
        }
        VisualStyle.DIAMOND -> {
            // A spinning faceted crystal, with distinct front/back faces.
            val theta = t*.34f
            fun crystalPoint(i: Int, back: Boolean): Offset {
                val a = i*tau/6f + theta
                val radius = if (i%2==0) .32f else .24f
                return projected(cos(a)*radius, sin(a)*radius,
                    if (back) .64f else .05f)
            }
            repeat(6) { i ->
                beam(crystalPoint(i, false), crystalPoint((i+1)%6, false), CYAN, 2.3f)
                beam(crystalPoint(i, true), crystalPoint((i+1)%6, true), VIOLET, 1.7f, .47f)
                beam(crystalPoint(i, false), crystalPoint(i, true), PINK, 1.2f)
                beam(crystalPoint(i, false), c, GOLD, .9f, .35f)
            }
            dot(c, s*.014f, Color.White)
        }
        VisualStyle.ORBITS -> {
            // Miniature solar system with eccentric orbital planes and planets.
            dot(c, s*(.045f+bass*.025f), GOLD)
            repeat(6) { orbit ->
                val r = .10f+orbit*.063f
                traced(115, ink(orbit), 1.1f) { i ->
                    val a = i*tau/115f
                    projected(cos(a)*r, sin(a)*r*.61f,
                        .20f + sin(a)*r*.39f)
                }
                val angle = t*(.17f+orbit*.045f) + orbit*1.25f
                val loc = projected(cos(angle)*r,sin(angle)*r*.61f,
                    .20f+sin(angle)*r*.39f)
                dot(loc, s*(.006f + orbit*.0018f + b(orbit)*.008f), ink(orbit))
            }
        }
        VisualStyle.MOUNTAINS -> {
            // Layered alpine horizon with oscillating jagged peaks.
            repeat(7) { depth ->
                val base = .42f - depth*.10f
                traced(80, ink(depth), 1.35f) { i ->
                    val x = i/80f-.5f
                    val peaks = abs(sin(x*13f + depth*1.4f + t*.11f))
                    val finer = abs(sin(x*29f + depth*.7f))
                    p(x,base - (peaks*.19f + finer*.055f)*(1f+b(i+depth)*.7f))
                }
            }
        }
        VisualStyle.BLOBS -> {
            // Metaball-like soft bubbles, not a particle galaxy.
            repeat(12) { i ->
                val a = i*tau/12f + t*(if(i%2==0) .21f else -.14f)
                val r = .09f + .18f * abs(sin(i*1.43f+t*.12f))
                val loc = polar(a,r)
                val radius = s*(.035f + b(i)*.045f + (i%4)*.008f)
                drawCircle(Brush.radialGradient(listOf(
                    Color.White.copy(alpha=.58f),ink(i).copy(alpha=.35f),
                    ink(i).copy(alpha=.06f),Color.Transparent),loc,radius*1.75f),
                    radius*1.75f,loc)
            }
        }
        VisualStyle.RAIN -> {
            // Falling droplets with distinct gravitational trails.
            repeat(75) { i ->
                val x = (i+.5f)/75f-.5f
                val travel = (t*(.19f+i%7*.025f)+i*.073f)%1.25f
                val y = travel-.63f
                val len = .012f+b(i)*.10f
                beam(p(x,y-len),p(x,y),ink(i/19),1.0f+b(i)*1.8f,.45f+b(i)*.50f)
            }
        }
        VisualStyle.GALAXY -> {
            // Four spiral arms in a rotating, inclined galactic disc.
            repeat(200) { i ->
                val arm = i%4
                val u = (i/4+.5f)/50f
                val a = arm*tau/4f + u*4.8f+t*.13f
                val r = sqrt(u)*.43f
                val loc = projected(cos(a)*r,sin(a)*r*.48f,
                    .35f+sin(a)*r*.13f)
                dot(loc, .7f+b(i)*2.9f, ink(arm),.38f+b(i)*.55f)
            }
            dot(c,s*(.025f+bass*.02f),Color.White)
        }
        VisualStyle.FIREWORKS -> {
            // Timed bursts of expanding radial fireworks at independent locations.
            repeat(5) { burst ->
                val origin = p((burst-2f)*.17f, sin(burst*2f)*.21f)
                val age = ((t*.27f + burst*.20f)%1f)
                repeat(23) { ray ->
                    val angle = ray*tau/23f+burst
                    val r = .025f + age*.18f + b(ray+burst)*.10f
                    val pt = Offset(origin.x+cos(angle)*r*s,
                        origin.y+sin(angle)*r*s)
                    dot(pt,1.0f+(1f-age)*3.2f,ink(burst),1f-age*.82f)
                }
            }
        }
        VisualStyle.TUNNEL -> {
            // Forward-moving luminous hexagonal corridor in perspective.
            repeat(12) { ring ->
                val z = ((ring/12f+t*.13f)%1f)*3.6f
                val n = 6
                repeat(n) { side ->
                    val a = side*tau/n + t*.075f
                    val next = (side+1)*tau/n + t*.075f
                    beam(projected(cos(a)*.40f,sin(a)*.40f,z),
                        projected(cos(next)*.40f,sin(next)*.40f,z),
                        ink(ring),1.35f,(.86f-z*.18f).coerceAtLeast(.12f))
                }
            }
        }
        VisualStyle.LIGHTNING -> {
            // Branching electric discharge, angular rather than smooth.
            repeat(5) { bolt ->
                var prev = p((bolt-2f)*.19f,-.44f)
                repeat(18) { k ->
                    val y = -.44f + (k+1)*.048f
                    val jag = sin(k*13.71f+bolt*17.3f+t*1.8f)*
                        (.024f+b(k+bolt)*.047f)
                    val next = p((bolt-2f)*.19f+jag,y)
                    beam(prev,next,if(bolt%2==0) CYAN else VIOLET,1.7f)
                    if(k%5==0) {
                        val spur = p((bolt-2f)*.19f+jag+.07f,y+.10f)
                        beam(next,spur,PINK,1.0f,.65f)
                    }
                    prev=next
                }
            }
        }
        VisualStyle.VINYL -> {
            // Actual turntable silhouette with grooves, label and tonearm.
            drawCircle(Color(0xFF111526),s*.42f,c)
            repeat(18) { groove ->
                drawCircle(ink(groove).copy(alpha=.10f+b(groove)*.15f),
                    s*(.15f+groove*.014f),c,style=Stroke(1.05f))
            }
            drawCircle(PINK,s*.135f,c)
            drawCircle(GOLD.copy(alpha=.7f),s*.088f,c,style=Stroke(2.5f))
            drawCircle(Color(0xFF111526),s*.016f,c)
            val pivot=p(.36f,-.37f)
            val needle=p(.09f+sin(t*.15f)*.025f,.25f)
            beam(pivot,needle,Color.White,3.5f,.7f)
            dot(pivot,s*.018f,GOLD)
        }
    }
}
