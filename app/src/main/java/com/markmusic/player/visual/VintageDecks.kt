package com.markmusic.player.visual

import android.graphics.Paint
import android.graphics.Typeface
import android.text.TextPaint
import android.text.TextUtils
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.graphics.ImageBitmap
import com.markmusic.player.LocalVisualActive
import com.markmusic.player.R
import com.markmusic.player.audio.AudioMeterFrame
import kotlinx.coroutines.isActive
import kotlin.coroutines.coroutineContext
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.roundToInt

/**
 * The five original rendered photographic front panels selected by the user.
 * Each is a completely offline Android resource, packed at build time from
 * scripts/assets/five_vintage_decks.txt. The front panels never rotate.
 */
enum class VintageMachine { CD, MINIDISC, DAT, RADIO, JUKEBOX }

internal fun deckClock(positionMs: Long): String {
    val seconds = positionMs.coerceAtLeast(0L) / 1000L
    val minutes = seconds / 60L
    val remaining = seconds % 60L
    return if (minutes >= 60L) "%02d:%02d:%02d".format(
        minutes / 60L, minutes % 60L, remaining
    ) else "%02d:%02d".format(minutes, remaining)
}

internal fun vintageAngle(
    current: Float,
    elapsedNs: Long,
    speedRpm: Float
): Float {
    if (elapsedNs <= 0L || speedRpm <= 0f) return current
    val updated = ((current.toDouble() +
        elapsedNs.toDouble() * speedRpm / 60_000_000_000.0 * 360.0) % 360.0)
    return updated.toFloat()
}

internal fun machineDiscRpm(machine: VintageMachine, progress: Float, takeup: Boolean): Float {
    val p = progress.coerceIn(0f, 1f)
    return when (machine) {
        VintageMachine.CD -> 500f - 300f * p // approximate 1x CLV spindle speed
        VintageMachine.MINIDISC -> 450f - 170f * p
        VintageMachine.DAT -> if (takeup) 235f - 100f * p else 135f + 100f * p
        VintageMachine.RADIO -> 0f
        VintageMachine.JUKEBOX -> 33.333334f
    }
}

@Composable
fun VintageDeck(
    machine: VintageMachine,
    playing: Boolean,
    positionMs: Long,
    durationMs: Long,
    trackTitle: String,
    audioMeter: AudioMeterFrame,
    modifier: Modifier = Modifier
) {
    val art = when (machine) {
        VintageMachine.CD -> ImageBitmap.imageResource(R.drawable.cd_deck)
        VintageMachine.MINIDISC -> ImageBitmap.imageResource(R.drawable.minidisc_deck)
        VintageMachine.DAT -> ImageBitmap.imageResource(R.drawable.dat_deck)
        VintageMachine.RADIO -> ImageBitmap.imageResource(R.drawable.tube_radio)
        VintageMachine.JUKEBOX -> ImageBitmap.imageResource(R.drawable.jukebox)
    }
    val active = LocalVisualActive.current.value
    val currentProgress = rememberUpdatedState(
        if (durationMs > 0L) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    )
    val discRotation = remember(machine) { mutableFloatStateOf(0f) }
    val secondRotation = remember(machine) { mutableFloatStateOf(0f) }

    LaunchedEffect(machine, playing, active) {
        if (!playing || !active || machine == VintageMachine.RADIO) return@LaunchedEffect
        var lastNs = 0L
        while (coroutineContext.isActive) {
            withFrameNanos { frameNs ->
                if (lastNs != 0L && frameNs > lastNs) {
                    val elapsed = frameNs - lastNs
                    val progress = currentProgress.value
                    discRotation.floatValue = vintageAngle(
                        discRotation.floatValue, elapsed,
                        machineDiscRpm(machine, progress, false)
                    )
                    if (machine == VintageMachine.DAT) {
                        secondRotation.floatValue = vintageAngle(
                            secondRotation.floatValue, elapsed,
                            machineDiscRpm(machine, progress, true)
                        )
                    }
                }
                lastNs = frameNs
            }
        }
    }

    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val side = minOf(maxWidth, maxHeight)
        // Every authored composition is a square; no stretch or perspective.
        Box(Modifier.size(side), contentAlignment = Alignment.Center) {
            Image(art, contentDescription = null, modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.FillBounds)

            when (machine) {
                VintageMachine.CD -> {
                    // Photographic laser carriage remains stationary. Only the
                    // metallic disc's surface/rainbow highlights rotate.
                    SpinMetalPart(
                        modifier = Modifier.align(Alignment.TopStart)
                            .offset(side * .122f, side * .145f).size(side * .692f)
                            .graphicsLayer { rotationZ = discRotation.floatValue },
                        mode = VintageMachine.CD
                    )
                    // Restore the fixed optical lens and metal guide rails from
                    // the source photo over the rotating disc layer.
                    Canvas(Modifier.fillMaxSize()) {
                        val fraction = size.width / 480f
                        drawImage(
                            image = art,
                            srcOffset = androidx.compose.ui.unit.IntOffset(274, 188),
                            srcSize = androidx.compose.ui.unit.IntSize(154, 98),
                            dstOffset = androidx.compose.ui.unit.IntOffset(
                                (274f * fraction).roundToInt(),
                                (188f * fraction).roundToInt()
                            ),
                            dstSize = androidx.compose.ui.unit.IntSize(
                                (154f * fraction).roundToInt(),
                                (98f * fraction).roundToInt()
                            )
                        )
                    }
                }
                VintageMachine.MINIDISC -> {
                    SpinMetalPart(
                        modifier = Modifier.align(Alignment.TopStart)
                            .offset(side * .337f, side * .483f).size(side * .264f)
                            .graphicsLayer { rotationZ = discRotation.floatValue },
                        mode = VintageMachine.MINIDISC
                    )
                    // Center axle is fixed relative to the cartridge, NOT the disc.
                    Canvas(Modifier.fillMaxSize()) {
                        val s = size.width / 480f
                        val c = Offset(224f * s, 293f * s)
                        drawCircle(Color(0xFF17191D), 8.7f * s, c)
                        drawCircle(Color(0xFF8E9394), 4.5f * s, c)
                        drawCircle(Color(0xFF22272B), 2f * s, c)
                    }
                }
                VintageMachine.DAT -> {
                    for ((x, angle) in listOf(
                        191f to discRotation, 265f to secondRotation
                    )) {
                        SpinMetalPart(
                            modifier = Modifier.align(Alignment.TopStart)
                                .offset(side * ((x - 20f) / 480f), side * .562f)
                                .size(side * (40f / 480f))
                                .graphicsLayer { rotationZ = angle.floatValue },
                            mode = VintageMachine.DAT
                        )
                    }
                }
                VintageMachine.RADIO -> Unit
                VintageMachine.JUKEBOX -> {
                    SpinMetalPart(
                        modifier = Modifier.align(Alignment.TopStart)
                            .offset(side * .518f, side * .286f)
                            .size(side * .108f)
                            .graphicsLayer { rotationZ = discRotation.floatValue },
                        mode = VintageMachine.JUKEBOX
                    )
                }
            }
            Canvas(Modifier.fillMaxSize().drawWithCache {
                onDrawBehind {
                    // Preserve the drawn hardware behind the animated overlay.
                }
            }) {
                drawRealDeckIndicators(machine, playing && active,
                    positionMs, durationMs, trackTitle, audioMeter)
            }
        }
    }
}

/** One static cached piece of high-resolution, metallic rotating hardware. */
@Composable
private fun SpinMetalPart(modifier: Modifier, mode: VintageMachine) {
    Canvas(modifier.drawWithCache {
        onDrawBehind {
            val c = center
            val r = size.minDimension * .495f
            when (mode) {
                VintageMachine.CD, VintageMachine.MINIDISC -> {
                    drawCircle(
                        Brush.sweepGradient(listOf(
                            Color(0xFFE5E2D5), Color(0xFF7AA9A9),
                            Color(0xFFEDC2C8), Color(0xFFB8B6C7),
                            Color(0xFF6FC9D4), Color(0xFFD7D3B9),
                            Color(0xFFA1A0AA), Color(0xFFE5E2D5)
                        ), center = c), radius = r, center = c
                    )
                    repeat(90) { n ->
                        val groove = r * (.39f + n * .0065f)
                        drawCircle(Color.White.copy(alpha = if (n % 9 == 0) .20f else .065f),
                            groove, c, style = Stroke(width = r * .0018f))
                    }
                    drawCircle(Color(0xFF151A20), r * .213f, c)
                    drawCircle(Color(0xFF838B90), r * .14f, c,
                        style = Stroke(r * .026f))
                    drawCircle(Color(0xFF111417), r * .055f, c)
                    drawLine(Color.White.copy(alpha = .38f),
                        c + Offset(-r * .095f, -r * .045f),
                        c + Offset(-r * .036f, -r * .066f),
                        r * .008f)
                }
                VintageMachine.DAT -> {
                    drawCircle(Color(0xFF080B0E), r, c)
                    drawCircle(Brush.sweepGradient(listOf(
                        Color(0xFFD4CFC4), Color(0xFF626363),
                        Color(0xFFD6D6D3), Color(0xFF666D70), Color(0xFFD4CFC4)
                    ), center = c), r * .86f, c)
                    for (i in 0..5) {
                        val a = 2 * PI * i / 6
                        val v = Offset(c.x + r * .48f * cos(a).toFloat(),
                            c.y + r * .48f * sin(a).toFloat())
                        drawCircle(Color(0xFF111417), r * .18f, v)
                    }
                    drawCircle(Color(0xFF22282D), r * .25f, c)
                    drawCircle(Color(0xFFDCE0DD), r * .115f, c)
                }
                VintageMachine.JUKEBOX -> {
                    drawCircle(Color(0xFF040509), r, c)
                    repeat(24) { n ->
                        drawCircle(Color(0xFF565E65).copy(alpha = .16f),
                            r * (.48f + .018f * n), c,
                            style = Stroke(r * .008f))
                    }
                    drawCircle(Color(0xFFAF3433), r * .36f, c)
                    drawCircle(Color(0xFFF8E7BA), r * .13f, c)
                    drawCircle(Color(0xFF171C20), r * .045f, c)
                    drawLine(Color(0xFFCDB990), c + Offset(0f,-r*.18f),
                        c + Offset(0f,-r*.32f),r*.023f)
                }
                VintageMachine.RADIO -> Unit
            }
        }
    }) { }
}

/** Pixel geometry in the 480×480 original photographs, scaled to each device. */
private fun DrawScope.drawRealDeckIndicators(
    machine: VintageMachine,
    playing: Boolean,
    positionMs: Long,
    durationMs: Long,
    trackTitle: String,
    meter: AudioMeterFrame
) {
    val unit = size.width / 480f
    fun at(x: Float, y: Float) = Offset(x * unit, y * unit)
    fun rect(x: Float, y: Float, w: Float, h: Float,
             color: Color, corner: Float = 1.8f) {
        drawRoundRect(color, at(x, y), Size(w * unit,h * unit),
            CornerRadius(corner * unit))
    }
    fun glow(x: Float, y: Float, radius: Float, hue: Color, alpha: Float) {
        if(alpha <= 0f) return
        drawCircle(Brush.radialGradient(
            listOf(hue.copy(alpha=alpha), hue.copy(alpha=0f)),
            center=at(x,y),radius=radius*unit
        ),radius*unit,at(x,y))
    }
    fun text(x:Float,y:Float,value:String, fontSize:Float, color:Int,
             maxWidth:Float=250f, bold:Boolean=false, mono:Boolean=false) {
        drawIntoCanvas { native ->
            val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                this.color=color
                textSize=fontSize*unit
                typeface=if(mono) Typeface.create(Typeface.MONOSPACE,Typeface.BOLD)
                    else Typeface.create("sans-serif-condensed",
                        if(bold) Typeface.BOLD else Typeface.NORMAL)
            }
            val clipped = TextUtils.ellipsize(value,paint,maxWidth*unit,
                TextUtils.TruncateAt.END).toString()
            native.nativeCanvas.drawText(clipped, x*unit,y*unit,paint)
        }
    }
    fun bars(x: Float,y: Float,left:Float,right:Float,count:Int,w:Float,h:Float) {
        val gap=1.0f
        for(row in 0..1){
            val level=if(row==0) left else right
            for(i in 0 until count){
                val bar= w/count-gap
                val active=level>=((i+1).toFloat()/count)
                val col=when{
                    i>count*.83->Color(0xFFE7482F)
                    i>count*.63->Color(0xFFFFB441)
                    else->Color(0xFF72ED4B)
                }
                rect(x+i*w/count,y+row*(h+3f),bar,h,
                    col.copy(alpha=if(active) .93f else .13f),corner=.65f)
            }
        }
    }
    val title = trackTitle.trim().ifBlank { "MARK AUDIO" }
    when(machine){
        VintageMachine.CD -> {
            // LCD time (the still image's printed 0:00 is covered).
            rect(269f,389f,71f,24f,Color(0xFF989A8E).copy(alpha=.96f))
            text(273f,408f,deckClock(positionMs),14f,
                android.graphics.Color.rgb(27,31,31),64f,true,true)
            // The original Play/Pause button is at x180..230, light its symbols.
            glow(202f,444f,22f,if(playing) Color(0xFF68FF99)
                else Color(0xFFFFBD69),if(playing).38f else .26f)
            rect(178f,431f,50f,24f,
                (if(playing)Color(0xFF57F19A) else Color(0xFFFFC27B))
                    .copy(alpha=.10f))
        }
        VintageMachine.MINIDISC -> {
            // Digital readouts are dynamic, not the frozen mockup text.
            rect(151f,117f,177f,48f,Color(0xFF080405).copy(alpha=.98f),1.2f)
            text(158f,137f,title,17.2f,
                android.graphics.Color.rgb(255,158,37),163f,true,true)
            text(157f,157f,deckClock(positionMs),12.8f,
                android.graphics.Color.rgb(255,144,31),60f,true,true)
            text(265f,157f,deckClock(durationMs),12.1f,
                android.graphics.Color.rgb(245,151,42),62f,false,true)
            // Real stereo PCM is displayed as two rows, never fake spectrum.
            rect(330f,117f,100f,53f,Color(0xFF0D0808).copy(alpha=.97f))
            bars(335f,132f, meter.left,meter.right,21,90f,10f)
            glow(if(playing)239f else 97f,404f,27f,
                if(playing) Color(0xFF6EFF65) else Color(0xFFFFC26F),
                if(playing) .40f else .40f)
            rect(if(playing)214f else 82f,392f,50f,24f,
                (if(playing) Color(0xFF8AF46C) else Color(0xFFFFBC69))
                    .copy(alpha=.18f))
        }
        VintageMachine.DAT -> {
            rect(132f,100f,113f,30f,Color(0xFF09070A).copy(alpha=.96f))
            text(137f,125f,deckClock(positionMs),19f,
                android.graphics.Color.rgb(255,160,42),103f,true,true)
            rect(273f,100f,134f,39f,Color(0xFF080709).copy(alpha=.94f))
            bars(277f,107f,meter.left,meter.right,27,128f,12f)
            glow(if(playing)276f else 350f,387f,31f,
                if(playing) Color(0xFF69FD61) else Color(0xFFFFC36C),.41f)
            rect(if(playing)243f else 319f,371f,65f,32f,
                (if(playing) Color(0xFF78F767) else Color(0xFFFFB56B))
                    .copy(alpha=.15f))
        }
        VintageMachine.RADIO -> {
            // Real signal energy gently illuminates the three glass valves.
            val energy=if(playing)((meter.left+meter.right)/2f).coerceIn(0f,1f)
                else 0f
            glow(89f,188f,15f,Color(0xFFFFA53D),.07f+energy*.25f)
            glow(135f,179f,25f,Color(0xFFFFA63B),.08f+energy*.35f)
            glow(176f,189f,15f,Color(0xFFFFB44D),.08f+energy*.25f)
            // The radio tuning dial is a physical frequency scale, not a clock.
            // Do not fabricate an RF frequency for a local music file.
            glow(264f,269f,20f,Color(0xFFFFA33C),
                if(playing) .16f+energy*.15f else .04f)
        }
        VintageMachine.JUKEBOX -> {
            // Song selector on the original physical jukebox; no fake VU.
            val energy=if(playing)((meter.left+meter.right)/2f) else 0f
            glow(172f,284f,36f,Color(0xFFFFB36C),
                if(playing) .05f+energy*.21f else .03f)
            glow(329f,281f,36f,Color(0xFFFFA057),
                if(playing) .05f+energy*.21f else .03f)
            rect(156f,200f,171f,17f,Color(0xFF49301A).copy(alpha=.86f),2f)
            text(162f,213f,title,10.2f,
                android.graphics.Color.rgb(255,233,179),158f,true)
            if(playing) glow(304f,152f,25f,Color(0xFFFF8E42),.20f)
        }
    }
}
