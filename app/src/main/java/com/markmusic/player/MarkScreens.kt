package com.markmusic.player

import androidx.compose.foundation.*
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
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
        contentPadding = PaddingValues(top = 10.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(13.dp)
    ) {
        item {
            NeonGlass(palette, Modifier.fillMaxWidth(), radius = 28.dp) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 13.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Image(logo, null, Modifier.size(54.dp).clip(RoundedCornerShape(17.dp)), contentScale = ContentScale.Fit)
                    Spacer(Modifier.width(11.dp))
                    Column(Modifier.weight(1f)) {
                        Text("MARK", fontSize = 26.sp, fontWeight = FontWeight.Black,
                            color = Color.White, letterSpacing = 1.3.sp)
                        Text("A U D I O", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold,
                            color = palette.accent, letterSpacing = 2.sp)
                    }
                    HeaderAction("⌕", "Поиск", onSearch, palette)
                    Spacer(Modifier.width(7.dp))
                    HeaderAction("↻", "Обновить", onRefresh, palette)
                    Spacer(Modifier.width(7.dp))
                    HeaderAction("⚙", "Оформление", onOpenThemes, palette)
                }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                NeonScript("Feel the sound. See the music.", palette.accent, size = 22)
                Text("${songs.size} композиций в твоей вселенной", fontSize = 12.sp, color = NeonSubtle)
            }
        }
        if (!audioAllowed) {
            item {
                NeonGlass(palette, Modifier.fillMaxWidth(), radius = 27.dp) {
                    Column(Modifier.padding(19.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            NeonIconTile("wave", palette.secondary, size = 49.dp)
                            Text("Открой свою музыкальную вселенную", color = Color.White,
                                fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 21.sp)
                        }
                        Text("Разреши доступ к аудиофайлам, чтобы MARK Audio собрал треки, альбомы и папки на телефоне. Музыка остаётся на устройстве.",
                            color = NeonSubtle, fontSize = 12.sp, lineHeight = 18.sp)
                        NeonPill("Найти музыку", palette, onClick = onRequestPermission, icon = "search")
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
                            Box(Modifier.fillMaxWidth().shadow(9.dp, RoundedCornerShape(22.dp),
                                ambientColor = palette.secondary.copy(alpha = .30f), spotColor = palette.accent.copy(alpha = .25f))) {
                                MarkCover(track, Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(22.dp)))
                            }
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
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            NeonIconTile("library", palette.accent, size = 53.dp)
            Column {
                Text("Медиатека", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
                NeonScript("Твоя звуковая вселенная", palette.secondary, size = 15)
            }
        }
        Text("${songs.size} композиций на устройстве", color = NeonSubtle, fontSize = 12.sp,
            modifier = Modifier.padding(top = 5.dp))
        Spacer(Modifier.height(14.dp))
        OutlinedTextField(
            value = query, onValueChange = onQueryChange, singleLine = true,
            placeholder = { Text("Поиск музыки", fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            leadingIcon = { NeonGlyph("search", palette.accent, size = 28.dp) },
            shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()
                .shadow(10.dp, RoundedCornerShape(24.dp), ambientColor = palette.accent.copy(alpha = .2f))
                .background(Color(0xA5172B50), RoundedCornerShape(24.dp)),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = palette.accent, unfocusedBorderColor = palette.secondary.copy(alpha = .40f),
                focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                focusedPlaceholderColor = Color(0xFF9BAECD), unfocusedPlaceholderColor = Color(0xFF9BAECD)
            )
        )
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            labels.forEachIndexed { index, label ->
                val active = category == index
                val chipShape = RoundedCornerShape(18.dp)
                Box(
                    Modifier.weight(1f).height(41.dp)
                        .clip(chipShape)
                        .background(
                            if (active) palette.accent.copy(alpha = .24f)
                            else Color(0x991B2F54),
                            chipShape
                        )
                        .border(1.dp,
                            if (active) palette.accent else palette.secondary.copy(alpha = .35f),
                            chipShape
                        )
                        .clickable { onCategoryChange(index) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        if (index == 2) "Артисты" else label,
                        color = if (active) palette.accent else Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1
                    )
                }
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
                verticalArrangement = Arrangement.spacedBy(8.dp)
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
                                Text(name, Modifier.weight(1f), color = palette.accent, fontWeight = FontWeight.ExtraBold,
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
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            NeonIconTile("playlist", NeonPink, size = 53.dp)
            Column {
                Text("Плейлисты", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
                NeonScript("Музыка под настроение", palette.accent, size = 15)
            }
        }
        Spacer(Modifier.height(16.dp))
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(13.dp),
            contentPadding = PaddingValues(bottom = 15.dp)
        ) {
            item {
                PlaylistTile("♥", "Любимые треки", "${favorites.size} композиций", NeonPink) {
                    if (favorites.isNotEmpty()) onPlay(favorites, favorites.first())
                }
            }
            item {
                PlaylistTile("✦", "Недавно добавленные", "${recent.size} композиций", palette.accent) {
                    if (recent.isNotEmpty()) onPlay(recent, recent.first())
                }
            }
            item {
                NeonGlass(palette, Modifier.fillMaxWidth(), radius = 24.dp) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        NeonIconTile("wave", palette.secondary, size = 45.dp)
                        Text("Сохраняй понравившуюся музыку сердечком в плеере — она появится здесь.",
                            color = NeonSubtle, fontSize = 12.sp, lineHeight = 17.sp)
                    }
                }
            }
            item {
                Spacer(Modifier.height(4.dp))
                Text("Любимые треки", color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.ExtraBold)
            }
            if (favorites.isEmpty()) item { EmptyHint("Твои любимые композиции появятся здесь") }
            else items(favorites, key = { it.id }) { track ->
                LibraryTrackRow(track, palette) { onPlay(favorites, track) }
            }
            item {
                Spacer(Modifier.height(5.dp))
                Text("Недавно добавленные", color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.ExtraBold)
            }
            if (recent.isEmpty()) item { EmptyHint("Добавь музыку на телефон, чтобы увидеть её здесь") }
            else items(recent, key = { "recent-${it.id}" }) { track ->
                LibraryTrackRow(track, palette) { onPlay(recent, track) }
            }
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

    // The playback controls stay visible: the whole screen fits in the height provided
    // by systemBarsPadding(), including on compact Huawei display/font settings.
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val compact = maxHeight < 730.dp
        val gap = if (compact) 6.dp else 9.dp
        val artworkSize = minOf(maxWidth * .84f, maxHeight * (if (compact) .31f else .38f))
        val mainControl = if (compact) 61.dp else 69.dp
        val sideControl = if (compact) 45.dp else 51.dp

        Column(
            modifier = Modifier.fillMaxSize().padding(top = 5.dp, bottom = 9.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(gap)
        ) {
            Row(
                Modifier.fillMaxWidth().height(42.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                NeonRoundButton("back", palette.secondary, onBack, 40.dp)
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("СЕЙЧАС ИГРАЕТ", color = Color.White, fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold, letterSpacing = 1.5.sp)
                    Text("MARK AUDIO", color = palette.accent, fontSize = 9.sp, letterSpacing = 2.sp)
                }
                NeonRoundButton("effects", palette.accent, onEffects, 40.dp)
            }

            NeonGlass(palette, Modifier.size(artworkSize), radius = 27.dp) {
                MarkCover(track, Modifier.fillMaxSize().padding(5.dp)
                    .clip(RoundedCornerShape(23.dp)))
            }

            Row(
                Modifier.fillMaxWidth().heightIn(min = 52.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(track.title, color = Color.White,
                        fontSize = if (compact) 19.sp else 22.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(track.artist, color = NeonSubtle, fontSize = 12.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.width(7.dp))
                NeonRoundButton(if (isFavorite) "heart" else "heart-outline",
                    NeonPink, onFavorite, if (compact) 41.dp else 46.dp)
            }

            // Weighted visualizer gets the remaining room instead of pushing playback
            // buttons below the screen. Its body can compress without scrolling.
            NeonGlass(palette, Modifier.fillMaxWidth().weight(1f), radius = 22.dp) {
                Column(Modifier.fillMaxSize().padding(7.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    MusicVisualizer(style, playing,
                        Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(14.dp)))
                    Row(
                        Modifier.fillMaxWidth().height(27.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        NeonGlyph("wave", palette.secondary, size = 18.dp)
                        Spacer(Modifier.width(7.dp))
                        Text(style.label, Modifier.weight(1f), color = palette.accent,
                            fontWeight = FontWeight.ExtraBold, fontSize = 11.sp)
                        TextButton(
                            onClick = onEffects,
                            contentPadding = PaddingValues(horizontal = 5.dp, vertical = 0.dp),
                            modifier = Modifier.height(27.dp)
                        ) {
                            Text("20 эффектов", color = palette.accent,
                                fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        }
                    }
                }
            }

            Column(Modifier.fillMaxWidth()) {
                Slider(
                    value = if (dragging) sliderValue.coerceIn(0f, duration.coerceAtLeast(1).toFloat())
                        else position.coerceIn(0L, duration.coerceAtLeast(1L)).toFloat(),
                    onValueChange = { sliderValue = it; dragging = true },
                    onValueChangeFinished = { onSeek(sliderValue.toLong()); dragging = false },
                    valueRange = 0f..duration.coerceAtLeast(1L).toFloat(),
                    colors = SliderDefaults.colors(
                        thumbColor = Color.White, activeTrackColor = palette.accent,
                        inactiveTrackColor = palette.secondary.copy(alpha = .23f)
                    )
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(formatTrackTime(position), color = NeonSubtle, fontSize = 11.sp)
                    Text(formatTrackTime(duration), color = NeonSubtle, fontSize = 11.sp)
                }
            }

            if (playerError != null) Text(
                playerError, color = Color(0xFFFFA4A4), fontSize = 11.sp, maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Row(
                Modifier.fillMaxWidth().height(mainControl + 2.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                NeonRoundButton("previous", palette.secondary, onPrevious, sideControl)
                Spacer(Modifier.width(25.dp))
                NeonRoundButton(if (playing) "pause" else "play",
                    palette.accent, onTogglePlayback, mainControl, primary = true)
                Spacer(Modifier.width(25.dp))
                NeonRoundButton("next", palette.secondary, onNext, sideControl)
            }
        }
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
    NeonGlass(palette, Modifier.fillMaxWidth().padding(top = 5.dp, bottom = 8.dp),
        radius = 25.dp, onClick = onOpen) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            MarkCover(track, Modifier.size(52.dp).clip(RoundedCornerShape(17.dp)))
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(track.title, color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(track.artist, color = NeonSubtle, fontSize = 11.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            NeonRoundButton(if (playing) "pause" else "play", palette.accent,
                onToggle, size = 47.dp, primary = true)
        }
    }
}
@Composable
internal fun MarkBottomNavigation(selected: Int, palette: MarkPalette, onSelect: (Int) -> Unit) {
    val items = listOf(
        Triple("home", "Главная", palette.accent),
        Triple("library", "Медиатека", palette.secondary),
        Triple("music", "Плеер", NeonPink),
        Triple("heart", "Плейлисты", NeonGold)
    )
    NeonGlass(palette, Modifier.fillMaxWidth().padding(bottom = 3.dp), radius = 25.dp) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            items.forEachIndexed { index, item ->
                val interaction = remember { MutableInteractionSource() }
                val pressed by interaction.collectIsPressedAsState()
                Column(
                    Modifier.weight(1f)
                        .floatingGlassMotion(pressed, item.third)
                        .clip(RoundedCornerShape(18.dp))
                        .background(if (selected == index) item.third.copy(alpha = .12f)
                            else Color.Transparent)
                        .clickable(interactionSource = interaction, indication = LocalIndication.current) {
                            onSelect(index)
                        }
                        .padding(vertical = 3.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    NeonIconTile(item.first, item.third,
                        size = 35.dp, selected = selected == index)
                    Text(item.second,
                        color = if (selected == index) item.third else NeonSubtle,
                        fontSize = 10.sp,
                        maxLines = 1,
                        fontWeight = if (selected == index) FontWeight.ExtraBold else FontWeight.Medium)
                }
            }
        }
    }
}
@Composable
internal fun MarkThemeDialog(selected: String, onSelect: (String) -> Unit, onDismiss: () -> Unit) {
    val activePalette = markPalette(selected)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(13.dp)) {
                NeonIconTile("effects", activePalette.accent, size = 48.dp)
                Column {
                    Text("Оформление", color = Color.White, fontSize = 22.sp,
                        fontWeight = FontWeight.ExtraBold)
                    NeonScript("Моя музыкальная вселенная", activePalette.secondary, size = 13)
                }
            }
        },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(11.dp)
            ) {
                MARK_PALETTES.forEach { theme ->
                    NeonGlass(
                        theme,
                        Modifier.fillMaxWidth().height(71.dp),
                        radius = 22.dp,
                        onClick = { onSelect(theme.key) }
                    ) {
                        Row(
                            Modifier.fillMaxSize()
                                .background(Brush.horizontalGradient(listOf(
                                    theme.start, theme.end, theme.accent.copy(alpha = .33f)
                                )))
                                .padding(horizontal = 15.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            NeonIconTile("wave", theme.accent, size = 37.dp,
                                selected = theme.key == selected)
                            Spacer(Modifier.width(12.dp))
                            Text(theme.title, Modifier.weight(1f),
                                color = Color.White, fontSize = 14.sp,
                                fontWeight = FontWeight.ExtraBold)
                            if (selected == theme.key) {
                                NeonGlyph("heart", theme.accent, size = 25.dp)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            NeonPill("Готово", activePalette, onClick = onDismiss)
        },
        shape = RoundedCornerShape(31.dp),
        containerColor = Color(0xFF101D40)
    )
}
@Composable
internal fun MarkWelcomeScreen(welcomeArtwork: ImageBitmap, palette: MarkPalette) {
    Box(
        Modifier.fillMaxSize().background(
            Brush.linearGradient(listOf(Color(0xFF030714), palette.start, palette.end, Color(0xFF050816)))
        ),
        contentAlignment = Alignment.Center
    ) {
        Box(Modifier.fillMaxSize().drawBehind {
            repeat(54) { i ->
                val x = (i * 79 % size.width.toInt().coerceAtLeast(1)).toFloat()
                val y = (i * 131 % size.height.toInt().coerceAtLeast(1)).toFloat()
                drawCircle(Color.White.copy(alpha = .28f), if (i % 6 == 0) 1.8f else .9f, Offset(x, y))
            }
            drawCircle(palette.accent.copy(alpha = .13f), size.minDimension * .42f,
                Offset(size.width * .5f, size.height * .48f),
                style = androidx.compose.ui.graphics.drawscope.Stroke(size.minDimension * .018f))
        })
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(11.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp)
        ) {
            Image(
                welcomeArtwork, contentDescription = "Приветственный экран MARK Audio",
                modifier = Modifier.fillMaxWidth(.89f).aspectRatio(1f)
                    .clip(RoundedCornerShape(36.dp))
                    .border(1.5.dp, Brush.linearGradient(listOf(palette.accent, palette.secondary, palette.accent)), RoundedCornerShape(36.dp)),
                contentScale = ContentScale.Fit
            )
            NeonScript("Добро пожаловать!", Color.White, size = 29, center = true)
            NeonScript("Feel the sound. See the music.",
                palette.accent, size = 19, center = true)
        }
    }
}

@Composable
private fun HeaderAction(symbol: String, description: String, onClick: () -> Unit, palette: MarkPalette) {
    val icon = when (symbol) { "⌕" -> "search"; "↻" -> "refresh"; else -> "settings" }
    NeonRoundButton(icon, if (icon == "settings") palette.secondary else palette.accent, onClick, 42.dp)
}

@Composable
private fun CategoryTile(symbol: String, title: String, count: String, tint: Color, modifier: Modifier, onClick: () -> Unit) {
    val icon = when (symbol) { "♫" -> "music"; "◉" -> "album"; "♙" -> "artist"; else -> "folder" }
    NeonGlass(markPalette("COSMIC_BLUE"), modifier, radius = 24.dp, onClick = onClick) {
        Column(Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 12.dp, start = 2.dp, end = 2.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(7.dp)) {
            NeonIconTile(icon, tint, size = 51.dp)
            Text(title, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            Text(count, color = NeonSubtle, fontSize = 10.sp, fontWeight = FontWeight.Medium)
        }
    }
}
@Composable
private fun SectionHeading(title: String, action: String, palette: MarkPalette, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f), color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
        TextButton(onClick = onClick, contentPadding = PaddingValues(horizontal = 6.dp)) {
            Text(action, color = palette.accent, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
        }
    }
}

@Composable
private fun PlaylistTile(symbol: String, title: String, subtitle: String, tint: Color, onClick: () -> Unit) {
    NeonGlass(markPalette("COSMIC_BLUE"), Modifier.fillMaxWidth(), radius = 24.dp, onClick = onClick) {
        Row(Modifier.padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
            NeonIconTile(if (symbol == "♥") "heart" else if (symbol == "✦") "time" else "music",
                tint, size = 55.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Text(subtitle, color = NeonSubtle, fontSize = 11.sp)
            }
            NeonGlyph("chevron", tint, size = 28.dp)
        }
    }
}
@Composable
private fun EmptyHint(message: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 28.dp, horizontal = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(13.dp)) {
        NeonIconTile("wave", NeonBlue, size = 58.dp)
        Text(message, color = NeonSubtle, fontSize = 13.sp, lineHeight = 19.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}
@Composable
private fun LibraryTrackRow(track: Track, palette: MarkPalette, onClick: () -> Unit) {
    NeonGlass(palette, Modifier.fillMaxWidth(), radius = 20.dp, onClick = onClick) {
        Row(Modifier.fillMaxWidth().padding(9.dp), verticalAlignment = Alignment.CenterVertically) {
            MarkCover(track, Modifier.size(54.dp).clip(RoundedCornerShape(17.dp)))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(track.title, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${track.artist} · ${track.album}", color = NeonSubtle, fontSize = 11.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(formatTrackTime(track.durationMs), color = palette.accent, fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold)
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
        else NeonGlyph("music", Color.White, size = 32.dp)
    }
}

@Composable
private fun PlayerControl(symbol: String, fontSize: Int, primary: Boolean, palette: MarkPalette, onClick: () -> Unit) {
    val name = when (symbol) { "⏮" -> "previous"; "⏭" -> "next"; "▶" -> "play"; else -> "pause" }
    NeonRoundButton(name, if (primary) palette.accent else palette.secondary, onClick,
        if (primary) 76.dp else 55.dp, primary)
}
private fun formatTrackTime(ms: Long): String {
    val seconds = max(0L, ms) / 1000
    return "%d:%02d".format(seconds / 60, seconds % 60)
}
