package com.markmusic.player

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.compose.ui.platform.LocalContext
import com.markmusic.player.library.LibraryGrouping
import com.markmusic.player.library.MusicLibrary
import com.markmusic.player.library.Track
import com.markmusic.player.visual.MusicVisualizer
import com.markmusic.player.visual.VisualStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max

internal data class MarkPalette(
    val key: String,
    val title: String,
    val start: Color,
    val end: Color,
    val panel: Color,
    val accent: Color,
    val secondary: Color
)

internal val MARK_PALETTES = listOf(
    MarkPalette("COSMIC_BLUE", "Cosmic Blue", Color(0xFF06152F), Color(0xFF24105A), Color(0xD9162A50), Color(0xFF43DFFF), Color(0xFFB45CFF)),
    MarkPalette("AURORA", "Aurora", Color(0xFF041D2B), Color(0xFF073D49), Color(0xD9103945), Color(0xFF4DE7C5), Color(0xFF48B9FF)),
    MarkPalette("ELECTRIC_POP", "Electric Pop", Color(0xFF18082E), Color(0xFF3C0B53), Color(0xD9271746), Color(0xFFFF55D6), Color(0xFF9D63FF)),
    MarkPalette("CRYSTAL_LIGHT", "Crystal Light", Color(0xFF071D39), Color(0xFF153E68), Color(0xD91B3A5C), Color(0xFF8BE8FF), Color(0xFFB6C9FF)),
    MarkPalette("SOLAR_ENERGY", "Solar Energy", Color(0xFF251126), Color(0xFF55230F), Color(0xD9382536), Color(0xFFFFB05E), Color(0xFFFF6E9D))
)

internal fun markPalette(key: String): MarkPalette = MARK_PALETTES.firstOrNull { it.key == key } ?: MARK_PALETTES.first()

@Composable
internal fun MarkHomeScreen(
    logo: ImageBitmap,
    songs: List<Track>,
    audioAllowed: Boolean,
    favoritesCount: Int,
    current: Track?,
    playing: Boolean,
    palette: MarkPalette,
    onSearch: () -> Unit,
    onRefresh: () -> Unit,
    onRequestPermission: () -> Unit,
    onOpenThemes: () -> Unit,
    onOpenLibrary: (Int) -> Unit,
    onOpenPlaylists: () -> Unit,
    onPlay: (List<Track>, Track) -> Unit
) {
    val recent = remember(songs) { songs.sortedByDescending { it.dateAddedSeconds }.take(12) }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 14.dp, bottom = 14.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Image(logo, null, Modifier.size(54.dp).clip(RoundedCornerShape(17.dp)), contentScale = ContentScale.Crop)
                Spacer(Modifier.width(11.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Text("MARK", fontSize = 25.sp, fontWeight = FontWeight.Black, color = Color.White, letterSpacing = 1.2.sp)
                    Text("AUDIO PLAYER", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = palette.accent, letterSpacing = 2.1.sp)
                }
                HeaderAction("⌕", "Поиск", onSearch, palette)
                Spacer(Modifier.width(6.dp))
                HeaderAction("↻", "Обновить", onRefresh, palette)
                Spacer(Modifier.width(6.dp))
                HeaderAction("⚙", "Оформление", onOpenThemes, palette)
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Твоя музыка. Твои правила.", fontSize = 23.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Text("${songs.size} композиций уже ждут тебя", fontSize = 13.sp, color = Color(0xFFB9C9E8))
            }
        }
        if (!audioAllowed) {
            item {
                Surface(color = Color(0xCC17294A), shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                        Text("Открой свою музыкальную вселенную", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                        Text("Разреши доступ к аудиофайлам, чтобы MARK Audio собрал треки, альбомы и папки на телефоне. Музыка остаётся на устройстве.",
                            color = Color(0xFFB9C9E8), fontSize = 12.sp)
                        Button(onClick = onRequestPermission, colors = ButtonDefaults.buttonColors(containerColor = palette.accent)) {
                            Text("Найти музыку", color = Color(0xFF07152D), fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp), modifier = Modifier.fillMaxWidth()) {
                CategoryTile("♫", "Треки", songs.size.toString(), palette.accent, Modifier.weight(1f)) { onOpenLibrary(0) }
                CategoryTile("◉", "Альбомы", songs.map { it.albumId }.distinct().size.toString(), palette.secondary, Modifier.weight(1f)) { onOpenLibrary(1) }
                CategoryTile("♙", "Артисты", songs.map { it.artist }.distinct().size.toString(), Color(0xFF70B8FF), Modifier.weight(1f)) { onOpenLibrary(2) }
                CategoryTile("▣", "Папки", songs.map { it.folder }.distinct().size.toString(), Color(0xFFFFB46D), Modifier.weight(1f)) { onOpenLibrary(3) }
            }
        }
        item {
            SectionHeading("Недавно добавленные", "ВСЕ", palette) { onOpenLibrary(0) }
            if (recent.isEmpty()) {
                EmptyHint("Здесь появится музыка, которую ты добавишь на устройство")
            } else {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(11.dp)) {
                    recent.forEach { track ->
                        Column(Modifier.width(116.dp).clickable { onPlay(recent, track) }, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            MarkCover(track, Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(17.dp)))
                            Text(track.title, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(track.artist, color = Color(0xFFB9C9E8), fontSize = 10.sp,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
        item {
            SectionHeading("Мои плейлисты", "ОТКРЫТЬ", palette, onOpenPlaylists)
            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                PlaylistTile("♥", "Любимые треки", "$favoritesCount треков", palette.secondary, onOpenPlaylists)
                PlaylistTile("♫", "Вся медиатека", "${songs.size} треков", palette.accent) { onOpenLibrary(0) }
            }
        }
        if (current != null) {
            item {
                Text("Сейчас играет", color = palette.accent, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    MarkCover(current, Modifier.size(54.dp).clip(RoundedCornerShape(14.dp)))
                    Spacer(Modifier.width(11.dp))
                    Column(Modifier.weight(1f)) {
                        Text(current.title, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(current.artist, color = Color(0xFFB9C9E8), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Text(if (playing) "Ⅱ" else "▶", color = palette.accent, fontSize = 25.sp)
                }
            }
        }
    }
}

@Composable
internal fun MarkLibraryScreen(
    songs: List<Track>,
    query: String,
    onQueryChange: (String) -> Unit,
    category: Int,
    onCategoryChange: (Int) -> Unit,
    loading: Boolean,
    error: String?,
    palette: MarkPalette,
    onPlay: (List<Track>, Track) -> Unit
) {
    val labels = listOf("Треки", "Альбомы", "Исполнители", "Папки")
    Column(Modifier.fillMaxSize().padding(top = 12.dp)) {
        Text("Медиатека", color = Color.White, fontSize = 27.sp, fontWeight = FontWeight.Bold)
        Text("${songs.size} композиций на устройстве", color = Color(0xFFB9C9E8), fontSize = 12.sp)
        Spacer(Modifier.height(14.dp))
        OutlinedTextField(
            value = query, onValueChange = onQueryChange, singleLine = true,
            placeholder = { Text("Поиск музыки, альбома, папки") },
            leadingIcon = { Text("⌕", fontSize = 25.sp, color = palette.accent) },
            shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = palette.accent, unfocusedBorderColor = Color(0xFF425577),
                focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                focusedPlaceholderColor = Color(0xFF9BAECD), unfocusedPlaceholderColor = Color(0xFF9BAECD)
            )
        )
        Spacer(Modifier.height(10.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            labels.forEachIndexed { index, label ->
                FilterChip(
                    selected = category == index,
                    onClick = { onCategoryChange(index) },
                    label = { Text(label) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = palette.accent.copy(alpha = .2f),
                        selectedLabelColor = palette.accent,
                        labelColor = Color.White
                    )
                )
            }
        }
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth(), color = palette.accent)
        if (error != null) Text(error, color = Color(0xFFFFA4A4), fontSize = 12.sp)
        Spacer(Modifier.height(5.dp))
        if (songs.isEmpty() && !loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                EmptyHint("Музыка пока не найдена. Добавь аудиофайлы или обнови медиатеку.")
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (category == 0) {
                    items(songs, key = { it.id }) { track -> LibraryTrackRow(track, palette) { onPlay(songs, track) } }
                } else {
                    val groups = when (category) {
                        1 -> songs.groupBy { it.albumId }.values.map { it.first().album to it }
                            .sortedBy { it.first.lowercase() }
                        2 -> LibraryGrouping.groupByAlbum(songs) { it.artist }.toList()
                        else -> LibraryGrouping.groupByFolder(songs) { it.folder }.toList()
                    }
                    groups.forEach { (name, tracks) ->
                        item(key = "group-$category-$name") {
                            Row(Modifier.fillMaxWidth().padding(top = 13.dp, bottom = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(name, Modifier.weight(1f), color = palette.accent, fontWeight = FontWeight.Bold,
                                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Text("${tracks.size}", color = Color(0xFFB9C9E8), fontSize = 12.sp)
                            }
                        }
                        items(tracks, key = { "$category-$name-${it.id}" }) { track ->
                            LibraryTrackRow(track, palette) { onPlay(tracks, track) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun MarkPlaylistScreen(
    favorites: List<Track>,
    recent: List<Track>,
    palette: MarkPalette,
    onPlay: (List<Track>, Track) -> Unit
) {
    Column(Modifier.fillMaxSize().padding(top = 14.dp)) {
        Text("Плейлисты", color = Color.White, fontSize = 27.sp, fontWeight = FontWeight.Bold)
        Text("Музыка, собранная по твоему настроению", color = Color(0xFFB9C9E8), fontSize = 12.sp)
        Spacer(Modifier.height(18.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(9.dp), contentPadding = PaddingValues(bottom = 12.dp)) {
            item { PlaylistTile("♥", "Любимые треки", "${favorites.size} треков", palette.secondary) {
                if (favorites.isNotEmpty()) onPlay(favorites, favorites.first())
            } }
            item { PlaylistTile("✦", "Недавно добавленные", "${recent.size} треков", palette.accent) {
                if (recent.isNotEmpty()) onPlay(recent, recent.first())
            } }
            item { Spacer(Modifier.height(4.dp)); Text("Любимые треки", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold) }
            if (favorites.isEmpty()) {
                item { EmptyHint("Нажимай на сердечко в плеере, чтобы собирать здесь любимую музыку") }
            } else {
                items(favorites, key = { it.id }) { track -> LibraryTrackRow(track, palette) { onPlay(favorites, track) } }
            }
            item { Spacer(Modifier.height(8.dp)); Text("Недавно добавленные", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold) }
            items(recent, key = { "recent-${it.id}" }) { track -> LibraryTrackRow(track, palette) { onPlay(recent, track) } }
        }
    }
}

@Composable
internal fun MarkPlayerScreen(
    track: Track,
    style: VisualStyle,
    playing: Boolean,
    position: Long,
    duration: Long,
    isFavorite: Boolean,
    playerError: String?,
    palette: MarkPalette,
    onBack: () -> Unit,
    onEffects: () -> Unit,
    onFavorite: () -> Unit,
    onSeek: (Long) -> Unit,
    onPrevious: () -> Unit,
    onTogglePlayback: () -> Unit,
    onNext: () -> Unit
) {
    var dragging by remember { mutableStateOf(false) }
    var sliderValue by remember { mutableFloatStateOf(0f) }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = 7.dp, bottom = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("‹  Назад", color = palette.accent, fontSize = 15.sp) }
            Spacer(Modifier.weight(1f))
            Text("СЕЙЧАС ИГРАЕТ", color = Color(0xFFB9C9E8), fontSize = 10.sp, letterSpacing = 1.8.sp)
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onEffects) { Text("✧", color = palette.accent, fontSize = 23.sp) }
        }
        Spacer(Modifier.height(8.dp))
        MarkCover(track, Modifier.fillMaxWidth(.78f).aspectRatio(1f).clip(RoundedCornerShape(28.dp)))
        Spacer(Modifier.height(18.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(track.title, color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.Bold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(track.artist, color = Color(0xFFB9C9E8), fontSize = 14.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Surface(onClick = onFavorite, shape = CircleShape, color = palette.panel, modifier = Modifier.size(48.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Text(if (isFavorite) "♥" else "♡", color = if (isFavorite) Color(0xFFFF59B9) else palette.accent, fontSize = 27.sp)
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        MusicVisualizer(style, playing, Modifier.fillMaxWidth().height(142.dp).clip(RoundedCornerShape(24.dp)))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(style.label, color = palette.accent, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Text("Визуализация звука", color = Color(0xFFB9C9E8), fontSize = 10.sp)
            }
            TextButton(onClick = onEffects) { Text("20 эффектов  ›", color = palette.accent) }
        }
        Spacer(Modifier.height(9.dp))
        Slider(
            value = if (dragging) sliderValue.coerceIn(0f, duration.coerceAtLeast(1).toFloat())
                else position.coerceIn(0L, duration.coerceAtLeast(1L)).toFloat(),
            onValueChange = { sliderValue = it; dragging = true },
            onValueChangeFinished = { onSeek(sliderValue.toLong()); dragging = false },
            valueRange = 0f..duration.coerceAtLeast(1L).toFloat(),
            colors = SliderDefaults.colors(thumbColor = palette.accent, activeTrackColor = palette.accent)
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatTrackTime(position), color = Color(0xFFB9C9E8), fontSize = 11.sp)
            Text(formatTrackTime(duration), color = Color(0xFFB9C9E8), fontSize = 11.sp)
        }
        if (playerError != null) Text(playerError, color = Color(0xFFFFA4A4), fontSize = 12.sp)
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            PlayerControl("⏮", 25, false, palette, onPrevious)
            Spacer(Modifier.width(20.dp))
            PlayerControl(if (playing) "Ⅱ" else "▶", 30, true, palette, onTogglePlayback)
            Spacer(Modifier.width(20.dp))
            PlayerControl("⏭", 25, false, palette, onNext)
        }
        Spacer(Modifier.height(8.dp))
        Text("MARK AUDIO", color = Color(0xFF8196BC), fontSize = 9.sp, letterSpacing = 2.5.sp)
    }
}

@Composable
internal fun MarkMiniPlayer(
    track: Track,
    playing: Boolean,
    palette: MarkPalette,
    onOpen: () -> Unit,
    onToggle: () -> Unit
) {
    Surface(
        onClick = onOpen, color = palette.panel, shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth().padding(top = 7.dp, bottom = 8.dp)
    ) {
        Row(Modifier.padding(9.dp), verticalAlignment = Alignment.CenterVertically) {
            MarkCover(track, Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(track.title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(track.artist, color = Color(0xFFB9C9E8), fontSize = 11.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            TextButton(onClick = onToggle) { Text(if (playing) "Ⅱ" else "▶", color = palette.accent, fontSize = 22.sp) }
        }
    }
}

@Composable
internal fun MarkBottomNavigation(selected: Int, palette: MarkPalette, onSelect: (Int) -> Unit) {
    val items = listOf("⌂" to "Главная", "▦" to "Медиатека", "♫" to "Плеер", "♡" to "Плейлисты")
    Surface(color = palette.panel, shape = RoundedCornerShape(23.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 5.dp)) {
        Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), horizontalArrangement = Arrangement.SpaceAround) {
            items.forEachIndexed { index, pair ->
                Column(
                    Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).clickable { onSelect(index) }.padding(vertical = 5.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(pair.first, color = if (selected == index) palette.accent else Color(0xFF9BAECD), fontSize = 22.sp)
                    Text(pair.second, color = if (selected == index) palette.accent else Color(0xFF9BAECD),
                        fontSize = 9.sp, fontWeight = if (selected == index) FontWeight.Bold else FontWeight.Normal)
                }
            }
        }
    }
}

@Composable
internal fun MarkThemeDialog(selected: String, onSelect: (String) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Оформление", color = Color.White) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(9.dp), modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text("Темы интерфейса", color = Color(0xFFB9C9E8), fontSize = 12.sp)
                MARK_PALETTES.forEach { palette ->
                    Surface(
                        onClick = { onSelect(palette.key) },
                        shape = RoundedCornerShape(16.dp),
                        color = palette.start,
                        border = androidx.compose.foundation.BorderStroke(
                            if (selected == palette.key) 2.dp else 1.dp,
                            if (selected == palette.key) palette.accent else Color(0xFF526589)
                        ),
                        modifier = Modifier.fillMaxWidth().height(62.dp)
                    ) {
                        Box(
                            Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(palette.start, palette.end, palette.accent.copy(alpha = .3f)))).padding(horizontal = 16.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(palette.title, Modifier.weight(1f), color = Color.White, fontWeight = FontWeight.SemiBold)
                                if (selected == palette.key) Text("✓", color = palette.accent, fontSize = 23.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Готово") } },
        containerColor = Color(0xFF14213B)
    )
}

@Composable
internal fun MarkWelcomeScreen(logo: ImageBitmap, palette: MarkPalette) {
    Box(
        Modifier.fillMaxSize().background(Brush.linearGradient(listOf(palette.start, palette.end))),
        contentAlignment = Alignment.Center
    ) {
        Box(Modifier.fillMaxSize().drawBehind {
            val max = size.minDimension
            drawCircle(palette.accent.copy(alpha = .12f), max * .42f, center)
            drawCircle(palette.secondary.copy(alpha = .10f), max * .32f, center, style = androidx.compose.ui.graphics.drawscope.Stroke(max * .025f))
            repeat(42) { i ->
                val x = (i * 79 % size.width.toInt().coerceAtLeast(1)).toFloat()
                val y = (i * 131 % size.height.toInt().coerceAtLeast(1)).toFloat()
                drawCircle(Color.White.copy(alpha = .32f), if (i % 5 == 0) 2f else 1f, Offset(x, y))
            }
        })
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Image(logo, null, Modifier.fillMaxWidth(.68f).aspectRatio(1f), contentScale = ContentScale.Fit)
            Spacer(Modifier.height(18.dp))
            Text("MARK AUDIO", color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.Black, letterSpacing = 3.sp)
            Text("FEEL THE SOUND. SEE THE MUSIC.", color = palette.accent, fontSize = 9.sp, letterSpacing = 1.8.sp)
        }
    }
}

@Composable
private fun HeaderAction(symbol: String, description: String, onClick: () -> Unit, palette: MarkPalette) {
    Surface(onClick = onClick, color = palette.panel, shape = CircleShape, modifier = Modifier.size(42.dp)) {
        Box(contentAlignment = Alignment.Center) { Text(symbol, color = palette.accent, fontSize = 23.sp) }
    }
}

@Composable
private fun CategoryTile(symbol: String, title: String, count: String, tint: Color, modifier: Modifier, onClick: () -> Unit) {
    Surface(onClick = onClick, color = Color(0xAA17294A), shape = RoundedCornerShape(17.dp), modifier = modifier) {
        Column(Modifier.padding(vertical = 12.dp, horizontal = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(symbol, color = tint, fontSize = 23.sp, fontWeight = FontWeight.Bold)
            Text(title, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Medium, maxLines = 1)
            Text(count, color = Color(0xFF9BAECD), fontSize = 9.sp)
        }
    }
}

@Composable
private fun SectionHeading(title: String, action: String, palette: MarkPalette, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f), color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        TextButton(onClick = onClick, contentPadding = PaddingValues(horizontal = 6.dp)) {
            Text(action, color = palette.accent, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun PlaylistTile(symbol: String, title: String, subtitle: String, tint: Color, onClick: () -> Unit) {
    Surface(onClick = onClick, color = Color(0xAA17294A), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(43.dp).clip(RoundedCornerShape(12.dp)).background(Brush.linearGradient(listOf(tint, tint.copy(alpha = .35f)))),
                contentAlignment = Alignment.Center) {
                Text(symbol, color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                Text(subtitle, color = Color(0xFF9BAECD), fontSize = 10.sp)
            }
            Text("›", color = tint, fontSize = 25.sp)
        }
    }
}

@Composable
private fun EmptyHint(message: String) {
    Text(message, color = Color(0xFF9BAECD), fontSize = 12.sp, modifier = Modifier.fillMaxWidth().padding(20.dp),
        textAlign = androidx.compose.ui.text.style.TextAlign.Center)
}

@Composable
private fun LibraryTrackRow(track: Track, palette: MarkPalette, onClick: () -> Unit) {
    Surface(onClick = onClick, color = Color.Transparent, shape = RoundedCornerShape(14.dp)) {
        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp, horizontal = 3.dp), verticalAlignment = Alignment.CenterVertically) {
            MarkCover(track, Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)))
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f)) {
                Text(track.title, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${track.artist} · ${track.album}", color = Color(0xFF9BAECD), fontSize = 10.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(formatTrackTime(track.durationMs), color = palette.accent, fontSize = 10.sp)
        }
    }
}

@Composable
private fun MarkCover(track: Track, modifier: Modifier) {
    val context = LocalContext.current
    val cover by produceState<ImageBitmap?>(initialValue = null, track.id) {
        value = MusicLibrary.embeddedCover(context, track.uri)
    }
    Box(modifier.background(Brush.linearGradient(listOf(Color(0xFF713BBA), Color(0xFF174C8C), Color(0xFF14AFC6)))),
        contentAlignment = Alignment.Center) {
        if (cover != null) Image(cover!!, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else Text("M", color = Color.White.copy(alpha = .9f), fontSize = 25.sp, fontWeight = FontWeight.Black)
    }
}

@Composable
private fun PlayerControl(symbol: String, fontSize: Int, primary: Boolean, palette: MarkPalette, onClick: () -> Unit) {
    Surface(
        onClick = onClick, shape = CircleShape,
        color = if (primary) palette.accent else palette.panel,
        modifier = Modifier.size(if (primary) 76.dp else 54.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(symbol, color = if (primary) Color(0xFF07152D) else Color.White, fontSize = fontSize.sp, fontWeight = FontWeight.Bold)
        }
    }
}

private fun formatTrackTime(ms: Long): String {
    val seconds = max(0L, ms) / 1000
    return "%d:%02d".format(seconds / 60, seconds % 60)
}
