package com.markmusic.player.visual

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
    progress: Float,
    modifier: Modifier = Modifier
) {
    val foreground = LocalVisualActive.current.value
    // The original bundled deck was only 128px and became visibly blurry
    // on high-density Huawei displays. Draw at native screen resolution instead.
    val trackProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 240, easing = LinearEasing),
        label = "stylus-groove-progress"
    )
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

        // Static precision geometry: no bitmap enlargement and no downloaded assets.
        Canvas(Modifier.fillMaxSize()) { drawTurntableDeck() }
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
            // Reflection belongs below the tonearm, not above its metal surface.
            drawRecordReflections()
            drawTonearm(armLowering, trackProgress)
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


/**
 * High-density record surface modeled after real black vinyl: restrained
 * near-black material, densely packed grooves, broad silver-gray reflections
 * and thinner iridescent highlights. All paths are drawn at output resolution.
 */
private fun DrawScope.drawVinylDisc() {
    val r = size.minDimension * .497f
    val c = center
    drawCircle(Color(0xFF050609), radius = r, center = c)
    drawCircle(
        brush = Brush.radialGradient(listOf(
            Color(0xFF0A0B0E), Color(0xFF171A20), Color(0xFF090A0D),
            Color(0xFF161920), Color(0xFF030408))),
        radius = r, center = c
    )
    // The groove band begins outside the label and ends just before the runout.
    // Hundreds of fine native-resolution lines avoid the old flat 72-ring look.
    val count = 256
    repeat(count) { i ->
        val t = i.toFloat() / (count - 1)
        val radius = r * (.303f + t * .672f)
        val light = when {
            i % 19 == 0 -> Color(0xFF7A8490).copy(alpha = .29f)
            i % 7 == 0 -> Color(0xFF727B88).copy(alpha = .16f)
            i % 2 == 0 -> Color(0xFF4C5665).copy(alpha = .16f)
            else -> Color(0xFF010204).copy(alpha = .39f)
        }
        drawCircle(light, radius, c, style = Stroke(width = .60f))
    }
    // Separate mastering bands, lead-in, and the quieter inner run-out region.
    for (fraction in listOf(.332f,.405f,.535f,.675f,.795f,.880f,.963f)) {
        drawCircle(Color(0xFF8D98A9).copy(alpha = .22f),
            radius = r * fraction, center = c, style = Stroke(.9f))
        drawCircle(Color.Black.copy(alpha = .44f),
            radius = r * (fraction + .003f), center = c, style = Stroke(1.1f))
    }
    drawCircle(Brush.sweepGradient(listOf(
        Color(0xFF8C929D).copy(alpha = .04f),
        Color.White.copy(alpha = .15f),
        Color.Transparent,
        Color(0xFFB7BDCB).copy(alpha = .04f),
        Color.White.copy(alpha = .10f),
        Color.Transparent,
        Color(0xFF8C929D).copy(alpha = .04f)
    ), center = c), radius = r * .975f)
    drawCircle(Color(0xFF525967), radius = r * .989f, center = c,
        style = Stroke(width = r * .009f))
    drawCircle(Color.Black.copy(alpha = .35f), radius = r * .302f, center = c)
    drawCircle(Color(0xFF8F97A2).copy(alpha = .39f),
        radius = r * .296f, center = c, style = Stroke(width = 1f))
}

/**
 * Directional ambient reflection falls on the *same* sector as the record
 * rotates beneath it. This is distinct from the spinning groove texture.
 */
private fun DrawScope.drawRecordReflections() {
    val s = size.minDimension
    val platter = Offset(size.width * .435f, size.height * .50f)
    val radius = s * .394f
    drawCircle(brush = Brush.sweepGradient(listOf(
        Color.Transparent,
        Color(0xFFB8C9D8).copy(alpha = .12f),
        Color(0xFFD2DAE9).copy(alpha = .19f),
        Color.Transparent,
        Color.Transparent,
        Color(0xFF94A8C3).copy(alpha = .08f),
        Color.Transparent,
        Color.Transparent
    ), center = platter), radius = radius, center = platter)
}

/**
 * The stylus follows a circular arc around its real tonearm pivot, not a
 * straight line toward the spindle. Groove radius travels from about 0.36
 * to 0.16 of the deck size as the current track progresses.
 *
 * Angle range was derived from intersecting the stylus-pivot circle with
 * the record's outer and inner playback radii. Pausing lifts the cartridge
 * without losing its groove position.
 */
internal fun stylusOnRecord(progress: Float): Pair<Float, Float> {
    val t = progress.coerceIn(0f, 1f)
    val angle = 1.814f + (2.304f - 1.814f) * t
    return Pair(.882f + .43f * cos(angle), .190f + .43f * sin(angle))
}

private fun DrawScope.drawTonearm(lowering: Float, progress: Float) {
    val w = size.width
    val h = size.height
    val s = size.minDimension
    fun at(x: Float, y: Float) = Offset(x * w, y * h)
    val pivot = at(.882f, .190f)
    val pos = stylusOnRecord(progress)
    // Cartridge raises a little when playback is paused.
    val lift = 1f - lowering
    val tip = at(pos.first + .012f * lift, pos.second - .032f * lift)
    val elbow = Offset(
        pivot.x + (tip.x - pivot.x) * .60f - s * .021f,
        pivot.y + (tip.y - pivot.y) * .60f - s * .008f
    )
    val armStart = at(.862f, .237f)

    // Soft cast shadow, dark underlay and multiple metallic facets.
    val shadow = Offset(s * .009f, s * .014f)
    drawLine(Color.Black.copy(alpha = .63f), armStart + shadow,
        elbow + shadow, s * .023f, StrokeCap.Round)
    drawLine(Color.Black.copy(alpha = .63f), elbow + shadow,
        tip + shadow, s * .022f, StrokeCap.Round)
    drawLine(Color(0xFF141922), armStart, elbow, s * .025f, StrokeCap.Round)
    drawLine(Brush.linearGradient(listOf(
        Color(0xFFEEF2F7), Color(0xFF717E91), Color(0xFF1A2330))),
        armStart, elbow, s * .018f, StrokeCap.Round)
    drawLine(Color(0xFF1A2030), elbow, tip, s * .022f, StrokeCap.Round)
    drawLine(Color(0xFFCAD5E4), elbow, tip, s * .013f, StrokeCap.Round)
    drawLine(Color.White.copy(alpha = .52f), elbow + Offset(-s*.002f, 0f),
        tip + Offset(-s*.002f, 0f), s * .003f, StrokeCap.Round)

    // Pivot/counterweight stays fixed while the cartridge traces the record.
    drawCircle(Brush.radialGradient(listOf(
        Color(0xFFF1DFBB), Color(0xFFA98C54), Color(0xFF202739)),
        center = pivot, radius = s * .026f), s * .027f, pivot)
    drawCircle(Color(0xFF161B25), s * .011f, pivot)
    val cartridge = tip + Offset(-s*.015f, s*.013f)
    drawLine(Color(0xFF0F141C), tip, cartridge, s * .028f, StrokeCap.Square)
    drawLine(Color(0xFFD7C8A8), tip, cartridge, s * .014f, StrokeCap.Square)
    drawCircle(Color(0xFFE5E8EF), s * .006f, cartridge)
}
