package com.markmusic.player

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * MARK Audio "Cosmic Neon + Premium Glass".
 * All effects are drawn by Compose: no network images and no replacements
 * for the two user-approved branding images.
 */
internal val NeonSubtle = Color(0xFFC0CFF1)
internal val NeonPink = Color(0xFFFF5DBD)
internal val NeonGold = Color(0xFFFFB65F)
internal val NeonBlue = Color(0xFF48E3FF)

@Composable
internal fun NeonGlass(
    palette: MarkPalette,
    modifier: Modifier = Modifier,
    radius: Dp = 25.dp,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val shape = RoundedCornerShape(radius)
    val tap = if (onClick == null) Modifier else Modifier.clickable(onClick = onClick)
    Box(
        modifier
            .shadow(10.dp, shape, ambientColor = palette.accent.copy(alpha = .20f),
                spotColor = palette.secondary.copy(alpha = .20f))
            .clip(shape)
            .background(Brush.verticalGradient(listOf(
                Color(0xE42C426E), palette.panel, Color(0xDE0C193B)
            )))
            .border(1.dp, Brush.linearGradient(listOf(
                Color.White.copy(alpha = .40f), palette.accent.copy(alpha = .50f),
                palette.secondary.copy(alpha = .23f)
            )), shape)
            .then(tap)
    ) {
        content()
        Box(
            Modifier.fillMaxWidth().height(1.dp)
                .background(Brush.horizontalGradient(listOf(
                    Color.Transparent, Color.White.copy(alpha = .28f), Color.Transparent)))
                .align(Alignment.TopCenter)
        )
    }
}

internal fun neonVector(name: String): ImageVector = when (name) {
    "search" -> Icons.Rounded.Search
    "refresh" -> Icons.Rounded.Refresh
    "settings" -> Icons.Rounded.Settings
    "music" -> Icons.Rounded.MusicNote
    "album" -> Icons.Rounded.Album
    "artist" -> Icons.Rounded.Person
    "folder" -> Icons.Rounded.Folder
    "heart" -> Icons.Rounded.Favorite
    "heart-outline" -> Icons.Rounded.FavoriteBorder
    "home" -> Icons.Rounded.Home
    "library" -> Icons.Rounded.LibraryMusic
    "play" -> Icons.Rounded.PlayArrow
    "pause" -> Icons.Rounded.Pause
    "next" -> Icons.Rounded.SkipNext
    "previous" -> Icons.Rounded.SkipPrevious
    "effects" -> Icons.Rounded.AutoAwesome
    "more" -> Icons.Rounded.MoreHoriz
    "back" -> Icons.Rounded.ArrowBack
    "time" -> Icons.Rounded.AccessTime
    "playlist" -> Icons.Rounded.QueueMusic
    "chevron" -> Icons.Rounded.ChevronRight
    "add" -> Icons.Rounded.Add
    "wave" -> Icons.Rounded.GraphicEq
    "grid" -> Icons.Rounded.GridView
    else -> Icons.Rounded.MusicNote
}

@Composable
internal fun NeonGlyph(
    name: String,
    tint: Color,
    modifier: Modifier = Modifier,
    size: Dp = 25.dp
) {
    Icon(
        imageVector = neonVector(name),
        contentDescription = null,
        tint = tint,
        modifier = modifier.size(size)
    )
}

@Composable
internal fun NeonIconTile(
    name: String,
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = 50.dp,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    val scale by animateFloatAsState(if (selected) 1.07f else 1f, label = "neon-icon-scale")
    val shape = RoundedCornerShape(17.dp)
    val tap = if (onClick == null) Modifier else Modifier.clickable(onClick = onClick)
    Box(
        modifier.size(size).scale(scale)
            .shadow(if (selected) 16.dp else 9.dp, shape,
                ambientColor = color.copy(alpha = .65f),
                spotColor = color.copy(alpha = .72f))
            .clip(shape)
            .background(Brush.linearGradient(listOf(
                Color.White.copy(alpha = .32f), color.copy(alpha = .85f),
                color.copy(alpha = .44f), Color(0xFF101D49)
            )))
            .border(1.dp, Brush.linearGradient(listOf(
                Color.White.copy(alpha = .80f), color.copy(alpha = .85f),
                color.copy(alpha = .12f)
            )), shape)
            .then(tap),
        contentAlignment = Alignment.Center
    ) {
        NeonGlyph(name, Color.White, size = size * .58f)
        Box(
            Modifier.fillMaxWidth(.70f).height(2.dp)
                .background(Color.White.copy(alpha = .40f))
                .align(Alignment.TopCenter)
        )
    }
}

@Composable
internal fun NeonRoundButton(
    name: String,
    color: Color,
    onClick: () -> Unit,
    size: Dp = 44.dp,
    primary: Boolean = false
) {
    val shape = CircleShape
    Box(
        Modifier.size(size)
            .shadow(if (primary) 18.dp else 8.dp, shape,
                ambientColor = color.copy(alpha = .60f), spotColor = color.copy(alpha = .50f))
            .clip(shape)
            .background(Brush.linearGradient(listOf(
                Color.White.copy(alpha = if (primary) .85f else .30f),
                color.copy(alpha = if (primary) 1f else .83f), Color(0xFF132D57)
            )))
            .border(1.5.dp, Color.White.copy(alpha = .48f), shape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        NeonGlyph(name, if (primary) Color(0xFF06213D) else Color.White, size = size * .55f)
    }
}

@Composable
internal fun NeonPill(
    label: String,
    palette: MarkPalette,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    icon: String? = null
) {
    val shape = RoundedCornerShape(24.dp)
    Row(
        modifier
            .shadow(12.dp, shape, ambientColor = palette.accent.copy(alpha = .40f),
                spotColor = palette.accent.copy(alpha = .40f))
            .clip(shape)
            .background(Brush.horizontalGradient(listOf(palette.accent, Color(0xFF64B2FF), palette.secondary)))
            .border(1.dp, Color.White.copy(alpha = .65f), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 21.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        if (icon != null) NeonGlyph(icon, Color(0xFF07142D), size = 22.dp)
        Text(label, color = Color(0xFF08152D), fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
    }
}

@Composable
internal fun NeonScript(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
    size: Int = 20,
    center: Boolean = false
) {
    Text(
        text,
        modifier = modifier,
        color = color,
        fontFamily = FontFamily.Cursive,
        fontStyle = FontStyle.Italic,
        fontWeight = FontWeight.SemiBold,
        fontSize = size.sp,
        lineHeight = (size * 1.35f).sp,
        textAlign = if (center) TextAlign.Center else TextAlign.Start,
        style = TextStyle(shadow = Shadow(
            color = color.copy(alpha = .55f),
            offset = Offset(0f, 2f), blurRadius = 18f
        ))
    )
}
