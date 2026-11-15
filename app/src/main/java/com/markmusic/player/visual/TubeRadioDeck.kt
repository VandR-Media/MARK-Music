package com.markmusic.player.visual

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.imageResource
import com.markmusic.player.LocalVisualActive
import com.markmusic.player.R
import com.markmusic.player.audio.AudioMeterBus

/** Retained valve radio: real PCM energy illuminates the glass valves. */
@Composable
fun TubeRadioDeck(playing: Boolean, modifier: Modifier = Modifier) {
    val art = ImageBitmap.imageResource(R.drawable.tube_radio)
    val foreground = LocalVisualActive.current.value
    val levels by AudioMeterBus.levels.collectAsState()
    val level = if (playing && foreground) {
        ((levels.left + levels.right) * .5f).coerceIn(0f, 1f)
    } else 0f
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val side = minOf(maxWidth, maxHeight)
        Box(Modifier.size(side)) {
            Image(art, contentDescription = null, modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.FillBounds)
            Canvas(Modifier.fillMaxSize()) {
                val u = size.width / 480f
                fun glow(x: Float, y: Float, radius: Float, strength: Float) {
                    if (strength <= 0f) return
                    val c = Offset(x * u, y * u)
                    drawCircle(
                        Brush.radialGradient(
                            listOf(
                                Color(0xFFFFB45A).copy(alpha = strength),
                                Color(0xFFFF8B2D).copy(alpha = strength * .30f),
                                Color.Transparent
                            ), center = c, radius = radius * u
                        ), radius = radius * u, center = c
                    )
                }
                // Lamp glass highlights breathe with actual decoded stereo PCM.
                val lamp = if (playing && foreground) .045f + level * .42f else .006f
                glow(89f, 188f, 22f, lamp * .75f)
                glow(135f, 179f, 33f, lamp)
                glow(176f, 189f, 22f, lamp * .70f)
                glow(264f, 269f, 31f, lamp * .65f)
            }
        }
    }
}
