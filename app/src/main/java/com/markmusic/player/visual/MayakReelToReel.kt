package com.markmusic.player.visual

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.markmusic.player.LocalVisualActive
import kotlinx.coroutines.isActive
import kotlin.coroutines.coroutineContext
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Original, high-resolution Mayak-203 inspired reel-to-reel player.
 * All materials are procedural: no remote pictures, GMS or audio sampling.
 * Only the two reel RenderNode transforms change every frame.
 */
@Composable
fun MayakReelToReel(playing: Boolean, progress: Float, modifier: Modifier = Modifier) {
    val foreground = LocalVisualActive.current.value
    val currentProgress = rememberUpdatedState(progress.coerceIn(0f, 1f))
    val tapeProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(350),
        label = "mayak-tape-winding"
    )
    val vu by animateFloatAsState(
        targetValue = if (playing && foreground) .67f else .08f,
        animationSpec = tween(700, easing = FastOutSlowInEasing),
        label = "mayak-level-needles"
    )
    val supplyAngle = remember { mutableFloatStateOf(0f) }
    val takeupAngle = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(playing, foreground) {
        if (!playing || !foreground) return@LaunchedEffect
        var previousFrameNs: Long? = null
        while (coroutineContext.isActive) {
            withFrameNanos { frameNs ->
                previousFrameNs?.let { previous ->
                    if (frameNs > previous) {
                        val elapsed = frameNs - previous
                        val t = currentProgress.value
                        supplyAngle.floatValue = advanceMayakReel(supplyAngle.floatValue, elapsed, t, false)
                        takeupAngle.floatValue = advanceMayakReel(takeupAngle.floatValue, elapsed, t, true)
                    }
                }
                previousFrameNs = frameNs
            }
        }
    }

    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val diameter = maxWidth * .414f
        val centerY = maxHeight * .291f
        val leftX = maxWidth * .275f
        val rightX = maxWidth * .725f
        Canvas(Modifier.fillMaxSize().drawWithCache {
            onDrawBehind { drawMayakHousing() }
        }) { }
        Box(Modifier.offset(leftX - maxWidth / 2f, centerY - maxHeight / 2f)
            .size(diameter).graphicsLayer { rotationZ = supplyAngle.floatValue }) {
            Canvas(Modifier.fillMaxSize()) { drawMayakReel(tapeProgress, false) }
        }
        Box(Modifier.offset(rightX - maxWidth / 2f, centerY - maxHeight / 2f)
            .size(diameter).graphicsLayer { rotationZ = takeupAngle.floatValue }) {
            Canvas(Modifier.fillMaxSize()) { drawMayakReel(tapeProgress, true) }
        }
        // Tape, head assembly and needles are in front of the rotating reels.
        Canvas(Modifier.fillMaxSize()) {
            drawMayakTapePath()
            drawMayakMeters(vu)
            drawMayakStatusLight(playing && foreground)
        }
        Text("МАЯК  203", Modifier.align(Alignment.Center).offset(y = maxHeight * .113f),
            color = Color(0xFF25201C), fontWeight = FontWeight.Black,
            fontSize = 17.sp, letterSpacing = 1.3.sp)
        Text("СТЕРЕО   ·   19,05", Modifier.align(Alignment.Center).offset(y = maxHeight * .176f),
            color = Color(0xFF5D554F), fontWeight = FontWeight.Medium,
            fontSize = 8.sp, letterSpacing = 1.sp)
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

private fun DrawScope.drawMayakHousing() {
    val w = size.width
    val h = size.height
    val s = size.minDimension
    fun at(x: Float, y: Float) = Offset(w * x, h * y)
    fun part(dx: Float, dy: Float) = Size(w * dx, h * dy)

    // A raised enclosure, dark wood cheeks, brushed aluminium and black transport.
    drawRoundRect(Color.Black.copy(alpha = .56f),
        at(.012f, .039f), part(.973f, .920f), CornerRadius(s * .033f))
    drawRoundRect(Brush.horizontalGradient(listOf(
        Color(0xFF4B2818), Color(0xFFB17B4C), Color(0xFF382014),
        Color(0xFF6F462C), Color(0xFF241710))),
        at(.016f, .025f), part(.969f, .922f), CornerRadius(s * .038f))
    repeat(31) { i ->
        val y = .044f + .0283f * i
        drawLine(Color(0xFFE1B685).copy(alpha = if (i % 4 == 0) .16f else .052f),
            at(.023f, y), at(.973f, y + .003f), s * .0013f)
    }
    drawRoundRect(Color(0xFF0B0D11),
        at(.057f, .042f), part(.887f, .888f), CornerRadius(s * .024f))
    drawRoundRect(Brush.linearGradient(listOf(
        Color(0xFF393C3F), Color(0xFF12151B), Color(0xFF0B0D12),
        Color(0xFF30343A)), start = at(.07f, .04f), end = at(.93f, .59f)),
        at(.069f, .054f), part(.862f, .55f), CornerRadius(s * .012f))
    drawRoundRect(Brush.verticalGradient(listOf(
        Color(0xFF878783), Color(0xFFE6E1D5), Color(0xFFC9C4BA),
        Color(0xFF6D6D6C))),
        at(.069f, .618f), part(.862f, .30f), CornerRadius(s * .008f))
    drawLine(Color(0xFFFBF4E8).copy(alpha = .72f),
        at(.07f, .619f), at(.93f, .619f), s * .0025f)
    drawLine(Color(0xFF2A2B30).copy(alpha = .75f),
        at(.07f, .913f), at(.93f, .913f), s * .004f)

    // Bearings behind reels.
    for (x in listOf(.275f, .725f)) {
        val c = at(x, .291f)
        drawCircle(Color.Black.copy(alpha = .70f), s * .215f,
            c + Offset(s * .006f, s * .010f))
        drawCircle(Brush.radialGradient(listOf(
            Color(0xFFCBD0D0), Color(0xFF515860), Color(0xFF090D12)),
            center = c, radius = s * .213f), s * .211f, c)
        drawCircle(Color(0xFF020408), s * .198f, c)
    }
    // Tape head block and stamped transport keys.
    drawRoundRect(Color.Black.copy(alpha = .79f),
        at(.377f, .500f), part(.246f, .098f), CornerRadius(s * .013f))
    drawRoundRect(Brush.linearGradient(listOf(
        Color(0xFF646C70), Color(0xFF12191F), Color(0xFF40484D))),
        at(.384f, .503f), part(.232f, .084f), CornerRadius(s * .011f))
    drawRoundRect(Color(0xFF10161A),
        at(.424f, .510f), part(.155f, .055f), CornerRadius(s * .007f))
    for (i in 0..4) {
        val x = .306f + i * .080f
        drawRoundRect(Color(0xFF34373B),
            at(x, .848f), part(.058f, .054f), CornerRadius(s * .007f))
        drawRoundRect(Brush.verticalGradient(listOf(
            if (i == 3) Color(0xFFAF4139) else Color(0xFFC2C4C0),
            if (i == 3) Color(0xFF66231F) else Color(0xFF6C7376))),
            at(x + .004f, .850f), part(.050f, .039f),
            CornerRadius(s * .004f))
    }

    for (x in listOf(.157f, .843f)) {
        val c = at(x, .850f)
        drawCircle(Color(0xFF292927).copy(alpha = .78f), s * .052f,
            c + Offset(s * .003f, s * .006f))
        drawCircle(Brush.linearGradient(listOf(
            Color(0xFFF8F5EE), Color(0xFF898B87), Color(0xFF373C3C),
            Color(0xFFC7C6BE))), s * .046f, c)
        drawCircle(Color(0xFF575957), s * .034f, c)
        drawCircle(Color(0xFFBBBAB1), s * .033f, c,
            style = Stroke(s * .0022f))
        drawLine(Color(0xFFF4E9D6), c,
            c + Offset(0f, -s * .030f), s * .0032f, StrokeCap.Round)
    }
    for (y in listOf(.078f, .892f)) for (x in listOf(.085f, .912f)) {
        val c = at(x, y)
        drawCircle(Color(0xFF27272A), s * .012f, c)
        drawCircle(Color(0xFFC6C7C3), s * .008f, c)
        drawLine(Color(0xFF565A5C),
            c + Offset(-s * .005f, s * .003f),
            c + Offset(s * .005f, -s * .003f), s * .0016f)
    }
}

private fun DrawScope.drawMayakReel(progress: Float, takeup: Boolean) {
    val s = size.minDimension
    val c = center
    val r = s * .484f
    drawCircle(Color.Black.copy(alpha = .80f), r * 1.01f,
        c + Offset(s * .007f, s * .009f))
    drawCircle(Brush.sweepGradient(listOf(
        Color(0xFFEAECE9), Color(0xFF676E72), Color(0xFFA8B2B5),
        Color(0xFFF7F4EC), Color(0xFF424A50), Color(0xFFEAECE9)),
        center = c), r, c)
    repeat(32) { i ->
        drawCircle(Color(0xFFEBECE9).copy(alpha = if (i % 4 == 0) .12f else .036f),
            r * (.62f + .0115f * i), c, style = Stroke(s * .0016f))
    }
    // Real holes reveal the dark brown tape pack under metallic spokes.
    val pack = mayakTapePackRadius(progress, takeup) * s
    for (i in 0 until 3) {
        val a = (i * 120.0 - 90.0) * PI / 180.0
        fun pt(degrees: Double, radius: Float): Offset = Offset(
            c.x + (cos(a + degrees * PI / 180.0) * radius).toFloat(),
            c.y + (sin(a + degrees * PI / 180.0) * radius).toFloat()
        )
        val path = Path().apply {
            val p0 = pt(-31.0, r * .36f)
            val p1 = pt(-30.0, r * .84f)
            val p2 = pt(0.0, r * .88f)
            val p3 = pt(30.0, r * .84f)
            val p4 = pt(31.0, r * .36f)
            val p5 = pt(0.0, r * .30f)
            moveTo(p0.x, p0.y)
            lineTo(p1.x, p1.y)
            quadraticBezierTo(p2.x, p2.y, p3.x, p3.y)
            lineTo(p4.x, p4.y)
            quadraticBezierTo(p5.x, p5.y, p0.x, p0.y)
            close()
        }
        drawPath(path, Color(0xFF101114))
        clipPath(path) {
            drawCircle(Brush.radialGradient(listOf(
                Color(0xFF2B1B12), Color(0xFF483126), Color(0xFF17110F)),
                radius = pack, center = c), pack, c)
            repeat(16) { j ->
                val fraction = j / 16f
                val rr = s * .146f + fraction * (pack - s * .146f).coerceAtLeast(0f)
                drawCircle(Color(0xFFB28B62).copy(alpha = .07f), rr, c,
                    style = Stroke(s * .0013f))
            }
        }
        drawPath(path, Color(0xFFDDE1DE).copy(alpha = .36f),
            style = Stroke(s * .003f))
    }
    drawCircle(Color(0xFF343D41), r * .96f, c, style = Stroke(s * .009f))
    drawCircle(Color(0xFFFBFAF4).copy(alpha = .76f), r * .98f, c,
        style = Stroke(s * .003f))
    drawCircle(Brush.radialGradient(listOf(
        Color(0xFFFAF9F3), Color(0xFF7A8389), Color(0xFF1C272C)),
        center = c - Offset(s * .019f, s * .025f), radius = r * .43f),
        r * .304f, c)
    drawCircle(Color(0xFF333D43), r * .215f, c)
    drawCircle(Brush.linearGradient(listOf(
        Color(0xFFDBDDDA), Color(0xFF70797A), Color(0xFF1D2327))),
        r * .161f, c)
    drawCircle(Color(0xFF171D22), r * .062f, c)
    drawCircle(Color(0xFFE8E5DC), r * .026f, c)
}

private fun DrawScope.drawMayakTapePath() {
    val w = size.width
    val h = size.height
    val s = size.minDimension
    fun at(x: Float, y: Float) = Offset(w * x, h * y)
    val path = Path().apply {
        moveTo(w * .279f, h * .481f)
        cubicTo(w * .30f, h * .518f, w * .334f, h * .545f, w * .385f, h * .544f)
        lineTo(w * .615f, h * .544f)
        cubicTo(w * .665f, h * .545f, w * .699f, h * .518f, w * .72f, h * .481f)
    }
    drawPath(path, Color.Black.copy(alpha = .74f),
        style = Stroke(s * .012f, cap = StrokeCap.Round))
    drawPath(path, Brush.linearGradient(listOf(
        Color(0xFF543C2B), Color(0xFFA57949), Color(0xFF60412A))),
        style = Stroke(s * .0055f, cap = StrokeCap.Round))
    for (x in listOf(.383f, .617f)) {
        val c = at(x, .545f)
        drawCircle(Color(0xFF090D11), s * .021f, c)
        drawCircle(Brush.radialGradient(listOf(
            Color(0xFFFDFAF0), Color(0xFF737D82), Color(0xFF252D32)),
            center = c, radius = s * .018f), s * .016f, c)
        drawCircle(Color(0xFF212A30), s * .006f, c)
    }
    drawCircle(Color(0xFF0D1116), s * .021f, at(.568f, .562f))
    drawCircle(Color(0xFFC5C4BA), s * .009f, at(.568f, .562f))
    // Highlights stay fixed relative to the ambient light, not reel spokes.
    drawLine(Color(0xFFDFE1DF).copy(alpha = .28f),
        at(.251f, .115f), at(.318f, .106f), s * .008f)
    drawLine(Color(0xFFDFE1DF).copy(alpha = .22f),
        at(.699f, .108f), at(.766f, .118f), s * .008f)
}

private fun DrawScope.drawMayakMeters(level: Float) {
    val w = size.width
    val h = size.height
    val s = size.minDimension
    fun at(x: Float, y: Float) = Offset(w * x, h * y)
    for (index in 0..1) {
        val x = if (index == 0) .246f else .752f
        val y = .762f
        val left = x - .129f
        drawRoundRect(Color(0xFF27282A), at(left - .006f, y - .064f),
            Size(w * .271f, h * .128f), CornerRadius(s * .011f))
        drawRoundRect(Brush.verticalGradient(listOf(
            Color(0xFFF9E9CB), Color(0xFFD7CBB3), Color(0xFFAC9C80))),
            at(left, y - .059f), Size(w * .259f, h * .107f),
            CornerRadius(s * .005f))
        val pivot = at(x, y + .037f)
        val theta = (216.0 + level * 106.0) * PI / 180.0
        val endpoint = Offset(
            pivot.x + (cos(theta) * s * .096f).toFloat(),
            pivot.y + (sin(theta) * s * .096f).toFloat()
        )
        drawLine(Color(0xFFB64034), pivot, endpoint,
            s * .0028f, StrokeCap.Round)
        drawCircle(Color(0xFF5B4D3B), s * .006f, pivot)
        for (tick in 0..8) {
            val angle = (216.0 + tick * 13.25) * PI / 180.0
            fun tickPoint(radius: Double) = Offset(
                pivot.x + cos(angle).toFloat() * s * radius.toFloat(),
                pivot.y + sin(angle).toFloat() * s * radius.toFloat()
            )
            drawLine(if (tick > 6) Color(0xFFAD3430) else Color(0xFF5D4B3B),
                tickPoint(.077), tickPoint(.084), s * .0015f)
        }
    }
}

private fun DrawScope.drawMayakStatusLight(active: Boolean) {
    val s = size.minDimension
    val c = Offset(size.width * .893f, size.height * .652f)
    drawCircle(Color(0xFF27221C), s * .015f, c)
    drawCircle(if (active) Color(0xFFE26E47) else Color(0xFF704638), s * .008f, c)
    if (active) drawCircle(Color(0xFFFFA574).copy(alpha = .22f), s * .024f, c)
}
