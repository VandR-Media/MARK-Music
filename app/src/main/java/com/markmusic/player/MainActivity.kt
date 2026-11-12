package com.markmusic.player

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.Image
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.markmusic.player.audio.PlaybackService
import com.markmusic.player.library.LibraryGrouping
import com.markmusic.player.library.MusicLibrary
import com.markmusic.player.library.Track
import com.markmusic.player.visual.EffectPreview
import com.markmusic.player.visual.MusicVisualizer
import com.markmusic.player.visual.VisualStyle
import kotlinx.coroutines.delay
import kotlin.math.max

private val CYAN = Color(0xFF50DFFF)
private val VIOLET = Color(0xFFB65CFF)
private val BG = Color(0xFF0B1730)
private val PANEL = Color(0xCC14284B)
private val SUBTLE = Color(0xFFD2DCF5)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.rgb(11, 23, 48)
        window.navigationBarColor = android.graphics.Color.rgb(11, 23, 48)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(
                primary = CYAN, secondary = VIOLET,
                background = BG, surface = PANEL, onSurface = Color.White
            )) { MarkApp() }
        }
    }
}

@Composable
private fun MarkApp() {
    val ctx = LocalContext.current
    val prefs = remember { ctx.getSharedPreferences("mark_music", Context.MODE_PRIVATE) }
    val permission = remember {
        if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO
        else Manifest.permission.READ_EXTERNAL_STORAGE
    }
    var audioAllowed by remember { mutableStateOf(ctx.hasPermission(permission)) }
    val audioLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { audioAllowed = it }
    var showWelcome by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) { delay(2600); showWelcome = false }

    var songs by remember { mutableStateOf(emptyList<Track>()) }
    var loading by remember { mutableStateOf(false) }
    var refresh by remember { mutableIntStateOf(0) }
    var libraryError by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
    var tab by remember { mutableIntStateOf(0) }
    var navTab by remember { mutableIntStateOf(0) }
    var previousNavTab by remember { mutableIntStateOf(0) }
    var showEffects by remember { mutableStateOf(false) }
    var showThemes by remember { mutableStateOf(false) }
    var themeKey by remember { mutableStateOf(prefs.getString("theme", "COSMIC_BLUE") ?: "COSMIC_BLUE") }
    val palette = markPalette(themeKey)
    var favoriteIds by remember {
        mutableStateOf(prefs.getStringSet("favorites", emptySet())?.mapNotNull { it.toLongOrNull() }?.toSet().orEmpty())
    }
    var style by remember {
        mutableStateOf(runCatching { VisualStyle.valueOf(prefs.getString("effect", "PULSE") ?: "PULSE") }
            .getOrDefault(VisualStyle.PULSE))
    }

    LaunchedEffect(audioAllowed, refresh) {
        if (audioAllowed) {
            loading = true
            libraryError = null
            try { songs = MusicLibrary.scan(ctx) }
            catch (e: Exception) { libraryError = e.localizedMessage ?: "Не удалось прочитать медиатеку" }
            finally { loading = false }
        } else songs = emptyList()
    }

    var controller by remember { mutableStateOf<MediaController?>(null) }
    var playing by remember { mutableStateOf(false) }
    var mediaId by remember { mutableStateOf("") }
    var duration by remember { mutableLongStateOf(0L) }
    var position by remember { mutableLongStateOf(0L) }
    var playerError by remember { mutableStateOf<String?>(null) }
    DisposableEffect(ctx) {
        val future = MediaController.Builder(ctx, SessionToken(ctx, ComponentName(ctx, PlaybackService::class.java)))
            .buildAsync()
        var active = true
        var observed: MediaController? = null
        var listener: Player.Listener? = null
        future.addListener({
            if (active) {
                runCatching { future.get() }.onSuccess { player ->
                    val l = object : Player.Listener {
                        override fun onEvents(player: Player, events: Player.Events) {
                            playing = player.isPlaying
                            mediaId = player.currentMediaItem?.mediaId.orEmpty()
                            duration = player.duration.takeIf { it > 0 } ?: 0L
                            position = player.currentPosition.coerceAtLeast(0L)
                        }
                        override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                            playerError = "Не удалось воспроизвести файл: ${error.errorCodeName}"
                        }
                    }
                    observed = player
                    listener = l
                    player.addListener(l)
                    controller = player
                    playing = player.isPlaying
                    mediaId = player.currentMediaItem?.mediaId.orEmpty()
                }.onFailure { playerError = "Не удалось подключить проигрыватель" }
            }
        }, ctx.mainExecutor)
        onDispose {
            active = false
            listener?.let { observed?.removeListener(it) }
            MediaController.releaseFuture(future)
            controller = null
        }
    }
    LaunchedEffect(controller) {
        while (controller != null) {
            controller?.let {
                position = it.currentPosition.coerceAtLeast(0L)
                duration = it.duration.takeIf { d -> d > 0 } ?: 0L
            }
            delay(350)
        }
    }
    val current = songs.firstOrNull { it.id.toString() == mediaId }
    val filtered = remember(songs, query) {
        songs.filter { "${it.title} ${it.artist} ${it.album} ${it.folder}".contains(query, ignoreCase = true) }
    }
    fun playQueue(queue: List<Track>, selected: Track) {
        controller?.apply {
            setMediaItems(queue.map { it.toMediaItem() }, queue.indexOf(selected).coerceAtLeast(0), 0L)
            prepare()
            play()
        }
        playerError = null
        previousNavTab = navTab
        navTab = 2
    }

    Box(
        modifier = Modifier.fillMaxSize().drawBehind {
            drawRect(Brush.linearGradient(listOf(palette.start, palette.end)))
            drawCircle(palette.accent.copy(alpha = .07f), size.minDimension * .48f,
                androidx.compose.ui.geometry.Offset(size.width * .92f, size.height * .12f))
            drawCircle(palette.secondary.copy(alpha = .07f), size.minDimension * .36f,
                androidx.compose.ui.geometry.Offset(size.width * .02f, size.height * .78f))
            drawRect(Brush.verticalGradient(listOf(Color(0x22051020), Color(0xB8051024))))
        }.systemBarsPadding()
    ) {
        Column(Modifier.fillMaxSize().padding(horizontal = 18.dp)) {
            when (navTab) {
                0 -> MarkHomeScreen(
                    logo = BrandArtwork.logo(ctx), songs = songs,
                    favoritesCount = songs.count { favoriteIds.contains(it.id) },
                    current = current, playing = playing, palette = palette,
                    onSearch = { navTab = 1 },
                    onOpenThemes = { showThemes = true },
                    onOpenLibrary = { category -> tab = category; query = ""; navTab = 1 },
                    onOpenPlaylists = { navTab = 3 },
                    onPlay = { queue, selected -> playQueue(queue, selected) }
                )
                1 -> MarkLibraryScreen(
                    songs = filtered, query = query, onQueryChange = { query = it },
                    category = tab, onCategoryChange = { tab = it },
                    loading = loading, error = libraryError, palette = palette,
                    onPlay = { queue, selected -> playQueue(queue, selected) }
                )
                2 -> {
                    if (current != null) {
                        MarkPlayerScreen(
                            track = current, style = style, playing = playing,
                            position = position, duration = duration,
                            isFavorite = favoriteIds.contains(current.id),
                            playerError = playerError, palette = palette,
                            onBack = { navTab = previousNavTab },
                            onEffects = { showEffects = true },
                            onFavorite = {
                                val updated = favoriteIds.toMutableSet()
                                if (!updated.add(current.id)) updated.remove(current.id)
                                favoriteIds = updated
                                prefs.edit().putStringSet("favorites", updated.map { it.toString() }.toSet()).apply()
                            },
                            onSeek = { controller?.seekTo(it) },
                            onPrevious = { controller?.seekToPreviousMediaItem() },
                            onTogglePlayback = { controller?.let { if (it.isPlaying) it.pause() else it.play() } },
                            onNext = { controller?.seekToNextMediaItem() }
                        )
                    } else {
                        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center) {
                            Text("♫", color = palette.accent, fontSize = 54.sp)
                            Text("Выбери музыку", color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.Bold)
                            Text("Запусти любой трек из медиатеки", color = SUBTLE, fontSize = 13.sp)
                            Spacer(Modifier.height(14.dp))
                            Button(onClick = { navTab = 1 }) { Text("Открыть медиатеку") }
                        }
                    }
                }
                else -> MarkPlaylistScreen(
                    favorites = songs.filter { favoriteIds.contains(it.id) },
                    recent = songs.sortedByDescending { it.dateAddedSeconds }.take(12),
                    palette = palette,
                    onPlay = { queue, selected -> playQueue(queue, selected) }
                )
            }
            if (navTab != 2) {
                if (current != null) {
                    MarkMiniPlayer(
                        track = current, playing = playing, palette = palette,
                        onOpen = { previousNavTab = navTab; navTab = 2 },
                        onToggle = { controller?.let { if (it.isPlaying) it.pause() else it.play() } }
                    )
                }
                MarkBottomNavigation(navTab, palette) { selected ->
                    if (selected == 2 && current == null) navTab = 1 else {
                        if (selected == 2) previousNavTab = navTab
                        navTab = selected
                    }
                }
            }
        }
        if (showThemes) {
            MarkThemeDialog(
                selected = themeKey,
                onSelect = { key ->
                    themeKey = key
                    prefs.edit().putString("theme", key).apply()
                },
                onDismiss = { showThemes = false }
            )
        }
    }
    if (showEffects) {
        AlertDialog(
            onDismissRequest = { showEffects = false },
            title = { Text("Выбери свою вселенную") },
            text = {
                Box(Modifier.heightIn(max = 470.dp).clip(RoundedCornerShape(20.dp))) {
                    Image(BrandArtwork.dialogBackground, contentDescription = null,
                        modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop, alpha = 0.36f)
                    Box(Modifier.fillMaxSize().background(Color(0x99101A36)))
                    LazyVerticalGrid(GridCells.Fixed(2), horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(VisualStyle.entries.toList()) { item ->
                            Surface(onClick = {
                                style = item
                                prefs.edit().putString("effect", item.name).apply()
                                showEffects = false
                            }, shape = RoundedCornerShape(13.dp),
                                color = if (style == item) Color(0xFF324D67) else Color(0xFF20253F),
                                modifier = Modifier.height(112.dp)) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    EffectPreview(item, Modifier.fillMaxWidth().height(76.dp)
                                        .padding(6.dp).clip(RoundedCornerShape(9.dp)))
                                    Text(item.label, fontSize = 12.sp, textAlign = TextAlign.Center,
                                        color = if (style == item) CYAN else Color.White,
                                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showEffects = false }) { Text("Закрыть") } },
            containerColor = Color(0xFF14284B)
        )
    }
    if (showWelcome) MarkWelcomeScreen(BrandArtwork.logo(ctx), palette)
}

@Composable
private fun WelcomeScreen(context: Context) {
    Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Image(BrandArtwork.logo(context), contentDescription = "Официальный логотип MARK Audio",
                modifier = Modifier.fillMaxWidth().aspectRatio(1f), contentScale = ContentScale.Fit)
            Text("Feel the sound. See the music.", color = Color.White, fontSize = 13.sp,
                fontWeight = FontWeight.Medium, letterSpacing = 1.sp)
        }
    }
}

@Composable
private fun TrackRow(track: Track, onClick: () -> Unit) {
    Surface(onClick = onClick, color = Color.Transparent, shape = RoundedCornerShape(15.dp)) {
        Row(Modifier.fillMaxWidth().padding(vertical = 7.dp, horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically) {
            CoverArtwork(track, Modifier.size(48.dp).clip(RoundedCornerShape(11.dp)))
            Spacer(Modifier.width(13.dp))
            Column(Modifier.weight(1f)) {
                Text(track.title, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.Medium)
                Text("${track.artist} · ${track.album}", color = SUBTLE, fontSize = 12.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(time(track.durationMs), color = SUBTLE, fontSize = 12.sp)
        }
    }
}

@Composable
private fun CoverArtwork(track: Track, modifier: Modifier) {
    val context = LocalContext.current
    val cover by produceState<ImageBitmap?>(initialValue = null, track.id) {
        value = MusicLibrary.embeddedCover(context, track.uri)
    }
    Box(modifier.background(Brush.linearGradient(listOf(VIOLET, Color(0xFF373E88), CYAN))),
        contentAlignment = Alignment.Center) {
        if (cover != null) Image(bitmap = cover!!, contentDescription = "Обложка альбома",
            contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        else Text("M", color = Color.White.copy(alpha = .84f), fontSize = 28.sp, fontWeight = FontWeight.Black)
    }
}

@Composable
private fun SeekAndControls(player: MediaController?, pos: Long, duration: Long, playing: Boolean) {
    var dragging by remember { mutableStateOf(false) }
    var sliderValue by remember { mutableFloatStateOf(0f) }
    Slider(
        value = if (dragging) sliderValue.coerceIn(0f, duration.coerceAtLeast(1).toFloat())
            else pos.coerceIn(0L, duration.coerceAtLeast(1L)).toFloat(),
        onValueChange = { sliderValue = it; dragging = true },
        onValueChangeFinished = { player?.seekTo(sliderValue.toLong()); dragging = false },
        valueRange = 0f..duration.coerceAtLeast(1L).toFloat(),
        modifier = Modifier.fillMaxWidth()
    )
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(time(pos), color = SUBTLE, fontSize = 12.sp)
        Text(time(duration), color = SUBTLE, fontSize = 12.sp)
    }
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically) {
        PlayerButton("⏮", 26, false) { player?.seekToPreviousMediaItem() }
        Spacer(Modifier.width(25.dp))
        PlayerButton(if (playing) "Ⅱ" else "▶", 29, true) {
            player?.let { if (it.isPlaying) it.pause() else it.play() }
        }
        Spacer(Modifier.width(25.dp))
        PlayerButton("⏭", 26, false) { player?.seekToNextMediaItem() }
    }
}

@Composable
private fun PlayerButton(symbol: String, font: Int, primary: Boolean, click: () -> Unit) {
    Surface(onClick = click, shape = CircleShape,
        color = if (primary) CYAN else PANEL,
        modifier = Modifier.size(if (primary) 78.dp else 56.dp)) {
        Box(contentAlignment = Alignment.Center) {
            Text(symbol, fontSize = font.sp,
                color = if (primary) BG else Color.White, fontWeight = FontWeight.Bold)
        }
    }
}

private fun Track.toMediaItem(): MediaItem = MediaItem.Builder()
    .setMediaId(id.toString())
    .setUri(uri)
    .setMediaMetadata(MediaMetadata.Builder().setTitle(title).setArtist(artist).setAlbumTitle(album).build())
    .build()

private fun Context.hasPermission(permission: String): Boolean =
    ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

private fun time(ms: Long): String {
    val seconds = max(0L, ms) / 1000
    return "%d:%02d".format(seconds / 60, seconds % 60)
}
