package com.markmusic.player.visual

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.ui.draw.drawWithCache
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
    // State is read only by the graphics layer. Each frame updates the layer
    // transform, NOT the entire turntable composition or its 256 groove paths.
    val rotation = remember { mutableFloatStateOf(0f) }
    val armLowering by animateFloatAsState(
        targetValue = if (playing) 1f else 0f,
        animationSpec = tween(durationMillis = 650, easing = FastOutSlowInEasing),
        label = "tonearm"
    )

    LaunchedEffect(playing, foreground) {
        if (!playing || !foreground) return@LaunchedEffect
        var previousFrameNs: Long? = null
        while (coroutineContext.isActive) {
            withFrameNanos { frameNs ->
                // No frame throttling: 120 Hz displays have 8.33 ms frames.
                // Skipping frames below 9 ms caused alternating angle updates.
                previousFrameNs?.let { previous ->
                    if (frameNs > previous) {
                        rotation.floatValue = advanceVinylRotation(
                            rotation.floatValue, frameNs - previous
                        )
                    }
                }
                previousFrameNs = frameNs
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
            .graphicsLayer { rotationZ = rotation.floatValue },
            contentAlignment = Alignment.Center) {
            // The static artwork is recorded in the rotating RenderNode layer.
            // Rotation changes a GPU transform instead of redrawing every groove.
            Canvas(Modifier.fillMaxSize().drawWithCache {
                onDrawBehind { drawVinylDisc() }
            }) { }
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

/**
 * Speed of a 33 1/3 RPM long-playing record. Pure rotation math is unit tested
 * at 60, 90, and 120 Hz so no display refresh rate is artificially throttled.
 */
internal fun advanceVinylRotation(currentDegrees: Float, elapsedNanos: Long): Float {
    if (elapsedNanos <= 0L) return currentDegrees
    return ((currentDegrees.toDouble() +
        elapsedNanos.toDouble() * (200.0 / 1_000_000_000.0)) % 360.0).toFloat()
}

/** A satin graphite hi-fi plinth with brushed-aluminium machined components. */
private fun DrawScope.drawTurntableDeck() {
    val s = size.minDimension
    val w = size.width
    val h = size.height
    fun pt(x: Float, y: Float) = Offset(x * w, y * h)

    // Raised chassis, chamfered black anodized body, directional softbox lighting.
    drawRoundRect(Color.Black.copy(alpha = .47f),
        topLeft = pt(.018f, .035f), size = Size(w * .969f, h * .947f),
        cornerRadius = CornerRadius(s * .046f))
    drawRoundRect(
        brush = Brush.linearGradient(listOf(
            Color(0xFF767A7D), Color(0xFF34383E), Color(0xFF161A1F),
            Color(0xFF2B3037), Color(0xFF62686E)),
            start = pt(0f, 0f), end = pt(.98f, .98f)),
        topLeft = pt(.012f, .015f), size = Size(w * .974f, h * .939f),
        cornerRadius = CornerRadius(s * .048f)
    )
    drawRoundRect(
        brush = Brush.linearGradient(listOf(
            Color(0xFF464A4D), Color(0xFF23282D), Color(0xFF12171D),
            Color(0xFF20252C), Color(0xFF30353A)),
            start = pt(.05f, 0f), end = pt(.95f, 1f)),
        topLeft = pt(.024f, .028f), size = Size(w * .950f, h * .907f),
        cornerRadius = CornerRadius(s * .041f)
    )
    // Hairline aluminium chamfer and restrained studio reflections.
    drawRoundRect(
        brush = Brush.linearGradient(listOf(
            Color(0xFFBEC2C4).copy(alpha = .62f),
            Color(0xFF8C9399).copy(alpha = .25f),
            Color(0xFF07090C).copy(alpha = .50f),
            Color(0xFF969A9C).copy(alpha = .29f))),
        topLeft = pt(.024f, .028f), size = Size(w * .950f, h * .907f),
        cornerRadius = CornerRadius(s * .041f), style = Stroke(s * .0022f)
    )
    // Fine horizontal machining marks. These are stationary, not animated.
    repeat(52) { i ->
        val y = .049f + i * .0168f
        val alpha = if (i % 4 == 0) .037f else .012f
        drawLine(Color(0xFFCBD0D3).copy(alpha = alpha),
            pt(.044f, y), pt(.955f, y), s * .0009f)
    }
    drawRoundRect(
        brush = Brush.linearGradient(listOf(
            Color.White.copy(alpha = .052f),
            Color.Transparent, Color.White.copy(alpha = .012f))),
        topLeft = pt(.033f, .040f), size = Size(w * .931f, h * .884f),
        cornerRadius = CornerRadius(s * .035f)
    )

    // Four recessed metal fasteners with machined grooves, not neon dots.
    for (i in 0..3) {
        val x = if (i % 2 == 0) .074f else .925f
        val y = if (i < 2) .078f else .884f
        val at = pt(x, y)
        drawCircle(Color(0xFF080C0F), s * .023f, at)
        drawCircle(Brush.radialGradient(listOf(
            Color(0xFFDFE1E2), Color(0xFF8D9499), Color(0xFF3B4249),
            Color(0xFF171B21)), center = at, radius = s * .021f),
            s * .019f, at)
        drawLine(Color(0xFF33383F),
            at + Offset(-s * .009f, s * .003f),
            at + Offset(s * .009f, -s * .003f), s * .0025f)
    }

    val platter = pt(.435f, .50f)
    val radius = s * .405f
    drawCircle(Color.Black.copy(alpha = .80f),
        radius * 1.065f, platter + Offset(s * .008f, s * .014f))
    drawCircle(Brush.radialGradient(listOf(
        Color(0xFF0A0D12), Color(0xFF91989E), Color(0xFF282F35),
        Color(0xFFB8BCBE), Color(0xFF272B31)),
        center = platter, radius = radius * 1.055f),
        radius * 1.046f, platter)
    drawCircle(Color(0xFF090B0E), radius * 1.013f, platter)
    drawCircle(Color(0xFFB6BCC0).copy(alpha = .62f),
        radius * 1.038f, platter, style = Stroke(s * .0026f))
    // Stroboscopic ring etched into the circumference of the heavy platter.
    repeat(120) { i ->
        val a = 2.0 * PI * i / 120.0
        val inner = Offset(platter.x + cos(a).toFloat() * radius * 1.021f,
            platter.y + sin(a).toFloat() * radius * 1.021f)
        drawCircle(
            if (i % 5 == 0) Color(0xFFD4D7D8) else Color(0xFF777F86),
            s * .0017f, inner
        )
    }

    // Precision pivot bearing with concentric chrome and dark steel rings.
    val socket = pt(.882f, .190f)
    drawCircle(Color(0xFF070A0F), s * .091f,
        socket + Offset(s * .004f, s * .006f))
    drawCircle(Brush.radialGradient(listOf(
        Color(0xFFFAFBFA), Color(0xFF8B9297), Color(0xFF272D32),
        Color(0xFF717B85), Color(0xFF11161C)),
        center = socket - Offset(s * .012f, s * .015f), radius = s * .123f),
        s * .083f, socket)
    drawCircle(Color(0xFF1D2227), s * .058f, socket)
    drawCircle(Color(0xFFB4BBC0).copy(alpha = .71f),
        s * .058f, socket, style = Stroke(s * .003f))
    drawCircle(Brush.radialGradient(listOf(
        Color(0xFFD6D7D4), Color(0xFF7C8183), Color(0xFF282E34)),
        center = socket, radius = s * .040f), s * .039f, socket)
    drawCircle(Color(0xFF31373B), s * .012f, socket)

    // Sturdy machined speed knob and a low-intensity operating indicator.
    val knob = pt(.874f, .818f)
    drawCircle(Color.Black.copy(alpha = .60f), s * .070f,
        knob + Offset(s * .004f, s * .007f))
    drawCircle(Brush.linearGradient(listOf(
        Color(0xFFEBEDEF), Color(0xFF747D83), Color(0xFF242B32),
        Color(0xFFAFB4B7))), s * .057f, knob)
    drawCircle(Brush.radialGradient(listOf(
        Color(0xFF434B54), Color(0xFF151B22))),
        s * .045f, knob)
    drawCircle(Color(0xFF939BA2), s * .045f, knob,
        style = Stroke(s * .0019f))
    drawLine(Color(0xFFE0D4B8), knob,
        knob + Offset(0f, -s * .033f), s * .004f, StrokeCap.Round)

    val power = pt(.120f, .852f)
    drawCircle(Color(0xFF090E12), s * .047f, power)
    drawCircle(Color(0xFF4C565B), s * .040f, power,
        style = Stroke(s * .005f))
    drawCircle(Color(0xFFE2AF79), s * .008f, power)
    drawCircle(Color(0xFFF1C28C).copy(alpha = .11f), s * .024f, power)
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
            Color(0xFF0E1114), Color(0xFF1D2124), Color(0xFF080A0D),
            Color(0xFF171A1C), Color(0xFF020305))),
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
    drawCircle(Color(0xFF8D9091).copy(alpha = .54f), radius = r * .989f, center = c,
        style = Stroke(width = r * .006f))
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
        Color(0xFFC2CBD0).copy(alpha = .07f),
        Color(0xFFE3E5E3).copy(alpha = .13f),
        Color.Transparent,
        Color.Transparent,
        Color(0xFFB3BFC5).copy(alpha = .06f),
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
    val lift = 1f - lowering
    val tip = at(pos.first + .012f * lift, pos.second - .032f * lift)
    val elbow = Offset(
        pivot.x + (tip.x - pivot.x) * .60f - s * .021f,
        pivot.y + (tip.y - pivot.y) * .60f - s * .008f
    )
    val armStart = at(.862f, .237f)
    val shadow = Offset(s * .009f, s * .014f)

    // Realistic stepped rear counterweight behind the gimbal bearing.
    val rear = at(.936f, .099f)
    drawLine(Color.Black.copy(alpha = .68f), pivot + shadow,
        rear + shadow, s * .027f, StrokeCap.Round)
    drawLine(Brush.linearGradient(listOf(
        Color(0xFFE1E5E5), Color(0xFF7B8388), Color(0xFF22272C))),
        pivot, rear, s * .016f, StrokeCap.Round)
    drawCircle(Color.Black.copy(alpha = .7f),
        s * .047f, rear + Offset(s * .004f, s * .006f))
    drawCircle(Brush.linearGradient(listOf(
        Color(0xFFF2F1EC), Color(0xFF7C8589), Color(0xFF252C32),
        Color(0xFF9FA4A5))),
        s * .043f, rear)
    drawCircle(Color(0xFF353B40), s * .029f, rear)
    drawCircle(Color(0xFFBFC3C3).copy(alpha = .78f),
        s * .029f, rear, style = Stroke(s * .0027f))
    drawCircle(Color(0xFF1A1F24), s * .013f, rear)

    // Cast shadows below a two-section metal tube.
    drawLine(Color.Black.copy(alpha = .64f), armStart + shadow,
        elbow + shadow, s * .029f, StrokeCap.Round)
    drawLine(Color.Black.copy(alpha = .66f), elbow + shadow,
        tip + shadow, s * .024f, StrokeCap.Round)
    drawLine(Color(0xFF1A2026), armStart, elbow,
        s * .028f, StrokeCap.Round)
    drawLine(Brush.linearGradient(listOf(
        Color(0xFFFCFDFB), Color(0xFFB7BEC1), Color(0xFF5C686F),
        Color(0xFF1E2A30))),
        armStart, elbow, s * .020f, StrokeCap.Round)
    drawLine(Color.White.copy(alpha = .44f),
        armStart + Offset(-s * .003f, -s * .001f),
        elbow + Offset(-s * .003f, -s * .001f),
        s * .003f, StrokeCap.Round)
    drawCircle(Brush.radialGradient(listOf(
        Color(0xFFD9DBD8), Color(0xFF676F75), Color(0xFF11191F)),
        center = elbow, radius = s * .024f), s * .023f, elbow)

    drawLine(Color(0xFF1C2329), elbow, tip,
        s * .024f, StrokeCap.Round)
    drawLine(Brush.linearGradient(listOf(
        Color(0xFFE4E8E7), Color(0xFF9FAAB0), Color(0xFF3D4850))),
        elbow, tip, s * .016f, StrokeCap.Round)
    drawLine(Color.White.copy(alpha = .36f),
        elbow + Offset(-s * .002f, 0f),
        tip + Offset(-s * .002f, 0f),
        s * .0028f, StrokeCap.Round)

    // Headshell and diamond stylus. The cartridge lifts when paused.
    val cartridge = tip + Offset(-s * .017f, s * .013f)
    drawLine(Color.Black.copy(alpha = .56f),
        tip + shadow * .55f, cartridge + shadow * .55f,
        s * .031f, StrokeCap.Round)
    drawLine(Color(0xFF0A0F14), tip, cartridge,
        s * .027f, StrokeCap.Round)
    drawLine(Brush.linearGradient(listOf(
        Color(0xFFDFDCD2), Color(0xFF646B6D), Color(0xFF20272D))),
        tip, cartridge, s * .017f, StrokeCap.Round)
    drawCircle(Color(0xFFCBD0CD), s * .006f, cartridge)
    drawCircle(Color(0xFFF5F2EA), s * .0027f,
        cartridge + Offset(-s * .004f, s * .007f))

    // A restrained gimbal cap is drawn last, so the arm feels properly mounted.
    drawCircle(Color(0xFF10161C), s * .035f,
        pivot + Offset(s * .003f, s * .003f))
    drawCircle(Brush.radialGradient(listOf(
        Color(0xFFF7F6F0), Color(0xFFA3ACB0), Color(0xFF434B51),
        Color(0xFF161B22)), center = pivot, radius = s * .034f),
        s * .029f, pivot)
    drawCircle(Color(0xFF252C32), s * .010f, pivot)
    drawCircle(Color.White.copy(alpha = .58f), s * .0035f,
        pivot - Offset(s * .007f, s * .007f))
}
