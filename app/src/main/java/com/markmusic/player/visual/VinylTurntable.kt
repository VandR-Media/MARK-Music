package com.markmusic.player.visual

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.ui.platform.LocalContext
import android.content.Context
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.markmusic.player.LocalVisualActive
import kotlinx.coroutines.isActive
import kotlin.coroutines.coroutineContext
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Single realistic-style vinyl turntable, replacing the complete effect catalog.
 * The disc and its central artwork rotate as one physical object at 33 1/3 RPM.
 * The tonearm stays separate and stationary above the disc.
 * All movement stops on pause or when the app leaves the foreground.
 */
@Composable
fun VinylTurntable(
    artwork: ImageBitmap,
    playing: Boolean,
    modifier: Modifier = Modifier
) {
    val foreground = LocalVisualActive.current.value
    val context = LocalContext.current
    val photo = remember(context) { loadTurntablePhoto(context) }
    var degrees by remember { mutableFloatStateOf(0f) }
    val armLowering by animateFloatAsState(
        targetValue = if (playing) 1f else 0f,
        animationSpec = tween(durationMillis = 650, easing = FastOutSlowInEasing),
        label = "tonearm"
    )

    LaunchedEffect(playing, foreground) {
        if (!playing || !foreground) return@LaunchedEffect
        var lastNs = 0L
        while (coroutineContext.isActive) {
            withFrameNanos { ns ->
                if (lastNs == 0L) {
                    lastNs = ns
                    return@withFrameNanos
                }
                if (ns - lastNs < 9_000_000L) return@withFrameNanos
                val delta = ((ns - lastNs) / 1_000_000_000f).coerceIn(0f, .08f)
                lastNs = ns
                degrees = (degrees + delta * 200f) % 360f
            }
        }
    }

    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val platterSize = maxWidth * .79f
        val platterX = maxWidth * .040f
        val platterY = maxHeight * .104f
        val labelSize = platterSize * .29f

        if (photo != null) {
            Image(bitmap = photo, contentDescription = "Корпус винилового проигрывателя",
                modifier = Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
        } else {
            Canvas(Modifier.fillMaxSize()) { drawTurntableDeck() }
        }
        Box(Modifier
            .offset(x = platterX - (maxWidth - platterSize) / 2f,
                y = platterY - (maxHeight - platterSize) / 2f)
            .size(platterSize)
            .graphicsLayer { rotationZ = degrees },
            contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                drawVinylDisc()
            }
            Image(
                bitmap = artwork,
                contentDescription = "Этикетка пластинки",
                modifier = Modifier.size(labelSize).clip(CircleShape),
                contentScale = ContentScale.Crop
            )
            Canvas(Modifier.size(labelSize)) {
                drawCircle(Color(0xFF030407).copy(alpha = .30f),
                    radius = size.minDimension * .06f)
                drawCircle(
                    Brush.radialGradient(listOf(Color(0xFFE7E4E2),
                        Color(0xFF6F737E), Color(0xFF252D3C))),
                    radius = size.minDimension * .042f
                )
                drawCircle(Color.White.copy(alpha = .8f),
                    radius = size.minDimension * .015f)
            }
        }
        Canvas(Modifier.fillMaxSize()) {
            drawTonearm(armLowering)
        }
    }
}

/** Gradients, shadows, controls, engraved lines and metallic highlights. */
private fun DrawScope.drawTurntableDeck() {
    val s = size.minDimension
    val w = size.width
    val h = size.height
    fun pt(x: Float, y: Float) = Offset(x * w, y * h)
    drawRoundRect(
        brush = Brush.linearGradient(listOf(
            Color(0xFF66718A), Color(0xFF151C2B), Color(0xFF090F19),
            Color(0xFF31384D), Color(0xFF101727)),
            start = pt(0f, 0f), end = pt(1f, 1f)),
        topLeft = pt(.013f, .024f),
        size = Size(w * .974f, h * .945f),
        cornerRadius = CornerRadius(s * .050f)
    )
    drawRoundRect(
        brush = Brush.verticalGradient(listOf(
            Color(0xFF2C3545), Color(0xFF151C2A), Color(0xFF0D1221),
            Color(0xFF192238))),
        topLeft = pt(.025f, .033f),
        size = Size(w * .95f, h * .918f),
        cornerRadius = CornerRadius(s * .045f)
    )
    drawRoundRect(
        brush = Brush.linearGradient(listOf(
            Color(0xFF57D4EF).copy(alpha = .85f),
            Color(0xFF667589), Color(0xFFB465EF).copy(alpha = .74f)),
            start = pt(.01f,.03f), end = pt(.98f,.91f)),
        topLeft = pt(.025f, .032f), size = Size(w * .95f, h * .918f),
        cornerRadius = CornerRadius(s * .045f),
        style = Stroke(s * .003f)
    )
    repeat(4) { i ->
        val x = if (i % 2 == 0) .070f else .931f
        val y = if (i < 2) .076f else .905f
        val at = pt(x,y)
        drawCircle(Color(0xFF06090F), s * .025f, at)
        drawCircle(Brush.radialGradient(listOf(
            Color(0xFFE0E3EE), Color(0xFF555F70), Color(0xFF171C29)),
            center = at, radius = s * .021f), s * .021f, at)
        drawLine(Color(0xFF263246), Offset(at.x - s*.010f,at.y - s*.010f),
            Offset(at.x + s*.010f,at.y + s*.010f), s*.003f)
    }

    // Heavily shaded platter bearing behind the rotating record.
    val platter = pt(.435f, .50f)
    val r = s * .405f
    drawCircle(Color.Black.copy(alpha = .65f),r * 1.055f,
        platter + Offset(s*.006f,s*.014f))
    drawCircle(Brush.radialGradient(listOf(
        Color(0xFF9AA2B0), Color(0xFF1C2333), Color(0xFFBDC0C4),
        Color(0xFF161B28)), center=platter, radius = r*1.04f), r*1.037f, platter)
    repeat(85) { i ->
        val a = 2f * PI.toFloat() * i / 85f
        val inner = Offset(platter.x + cos(a)*r*1.005f,
            platter.y + sin(a)*r*1.005f)
        drawCircle(if (i%3==0) Color(0xFFF4CDA4) else Color(0xFF858C99),
            s*.0021f, inner)
    }

    // Tonearm bearing socket and an adjustable speed dial.
    val socket=pt(.882f,.190f)
    drawCircle(Color(0xFF080C16),s*.087f,socket + Offset(s*.003f,s*.008f))
    drawCircle(Brush.radialGradient(listOf(
        Color(0xFFCED5DF), Color(0xFF525A69), Color(0xFF151922)),
        center=socket, radius=s*.081f),s*.080f,socket)
    drawCircle(Color(0xFF080D17),s*.054f,socket)
    drawCircle(Brush.radialGradient(listOf(
        Color(0xFFF8D996), Color(0xFFA8752C), Color(0xFF201A18)),
        center=socket, radius=s*.035f),s*.036f,socket)

    val knob=pt(.874f,.830f)
    drawCircle(Color(0xFF050810),s*.068f,knob + Offset(s*.003f,s*.006f))
    drawCircle(Brush.linearGradient(listOf(
        Color(0xFFE0E4F3), Color(0xFF373E4F), Color(0xFFB9B4C1))),
        s*.054f,knob)
    drawCircle(Color(0xFF242B3D),s*.039f,knob)
    drawLine(Color(0xFF4CE0FF),knob,knob + Offset(0f,-s*.034f),s*.004f)
    val power=pt(.118f,.860f)
    drawCircle(Color(0xFF080617),s*.056f,power)
    drawCircle(Color(0xFFAA74F7),s*.043f,power,style=Stroke(s*.007f))
    drawCircle(Color(0xFFCB9AFF).copy(alpha=.17f),s*.052f,power)
}

private fun DrawScope.drawVinylDisc() {
    val r = size.minDimension * .498f
    val c = center
    drawCircle(Brush.radialGradient(listOf(
        Color(0xFF131720),Color(0xFF05070C),Color(0xFF1C1F28),
        Color(0xFF04060B),Color(0xFF282B33))),r,c)
    // Thin highlights follow microscopic physical grooves; avoid animated
    // spectrum sampling and expensive shaders.
    repeat(72) { i ->
        val fraction = .31f + i * .0091f
        val alpha = if(i%5==0) .22f else .08f
        drawCircle(if (i%3==0) Color(0xFFB5C2D1).copy(alpha=alpha)
            else Color(0xFF9199B6).copy(alpha=alpha*.65f),
            radius=r*fraction,center=c,style=Stroke(.9f))
    }
    drawCircle(
        brush=Brush.sweepGradient(listOf(
            Color.Transparent,Color.White.copy(alpha=.16f),
            Color.Transparent,Color(0xFF8EDCF0).copy(alpha=.11f),
            Color.Transparent,Color.White.copy(alpha=.11f),
            Color.Transparent),center=c),
        radius=r*.975f,center=c)
    drawCircle(Color(0xFF4B5260),r*.984f,c,style=Stroke(r*.010f))
    drawCircle(Color.Black.copy(alpha=.38f),r*.306f,c)
    drawCircle(Color(0xFF7E8492).copy(alpha=.60f),
        r*.294f,c,style=Stroke(1.5f))
}

/** Static anodized metal tonearm suspended above the spinning LP. */
private fun DrawScope.drawTonearm(lowering: Float) {
    val w=size.width
    val h=size.height
    val s=size.minDimension
    fun at(x:Float,y:Float)=Offset(x*w,y*h)
    val pivot=at(.882f,.190f)
    // When paused the cartridge lifts slightly away from the groove.
    val tip=at(.768f - .046f * lowering, .465f + .080f * lowering)
    val elbow=at(.818f - .023f * lowering, .357f + .028f * lowering)
    val armStart=at(.852f,.236f)
    drawLine(Color.Black.copy(alpha=.72f),
        armStart + Offset(s*.008f,s*.014f),
        elbow + Offset(s*.008f,s*.014f),s*.023f,StrokeCap.Round)
    drawLine(Color.Black.copy(alpha=.72f),
        elbow + Offset(s*.008f,s*.014f),
        tip + Offset(s*.008f,s*.014f),s*.023f,StrokeCap.Round)
    drawLine(Color(0xFF131924),armStart,elbow,s*.026f,StrokeCap.Round)
    drawLine(Brush.linearGradient(listOf(Color(0xFFE9EEF9),
        Color(0xFF6E7B90),Color(0xFF1A2230))),
        armStart,elbow,s*.018f,StrokeCap.Round)
    drawLine(Color(0xFF252F43),elbow,tip,s*.022f,StrokeCap.Round)
    drawLine(Color(0xFFD3DCEA),elbow,tip,s*.011f,StrokeCap.Round)
    drawCircle(Brush.radialGradient(listOf(
        Color(0xFFE4D6B5),Color(0xFF9F813C),Color(0xFF191F2B)),
        center=pivot,radius=s*.026f),s*.027f,pivot)
    drawCircle(Color(0xFF131923),s*.010f,pivot)
    drawLine(Color(0xFF18212C),tip,tip+Offset(-s*.019f,s*.020f),
        s*.030f,StrokeCap.Square)
    drawLine(Color(0xFFD0BD93),tip,tip+Offset(-s*.019f,s*.020f),
        s*.014f,StrokeCap.Square)
    drawCircle(Color(0xFFE2E4EF),s*.006f,
        tip+Offset(-s*.019f,s*.020f))
}

/** Offline cached still image. The animated LP and tonearm are rendered above it.
 * If an older APK or asset corruption is encountered, the vector deck remains
 * available as a complete non-crashing fallback. No network requests.
 */
private fun loadTurntablePhoto(context: Context): ImageBitmap? = runCatching {
    val encoded = context.assets.open("mark_turntable_base.b64").bufferedReader().use { it.readText() }
    val bytes = Base64.decode(encoded, Base64.DEFAULT)
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
}.getOrNull()
