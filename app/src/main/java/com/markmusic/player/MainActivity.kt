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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.Image
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.markmusic.player.audio.AudioSessionBus
import com.markmusic.player.audio.PlaybackService
import com.markmusic.player.library.LibraryGrouping
import com.markmusic.player.library.MusicLibrary
import com.markmusic.player.library.Track
import com.markmusic.player.visual.EffectPreview
import com.markmusic.player.visual.MusicVisualizer
import com.markmusic.player.visual.VisualStyle
import kotlinx.coroutines.delay
import kotlin.math.max

private val CYAN = Color(0xFF61EFF4)
private val VIOLET = Color(0xFFB68DFF)
private val BG = Color(0xFF080B1B)
private val PANEL = Color(0xFF171B35)
private val SUBTLE = Color(0xFFB7BDD5)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.rgb(8, 11, 27)
        window.navigationBarColor = android.graphics.Color.rgb(8, 11, 27)
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
    var captureAllowed by remember { mutableStateOf(ctx.hasPermission(Manifest.permission.RECORD_AUDIO)) }
    val audioLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { audioAllowed = it }
    val captureLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { captureAllowed = it }
    var songs by remember { mutableStateOf(emptyList<Track>()) }
    var loading by remember { mutableStateOf(false) }
    var refresh by remember { mutableIntStateOf(0) }
    var libraryError by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
    var tab by remember { mutableIntStateOf(0) }
    var showPlayer by remember { mutableStateOf(false) }
    var showEffects by remember { mutableStateOf(false) }
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
    val sessionId by AudioSessionBus.sessionId.collectAsState()
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
        showPlayer = true
    }

    Column(modifier = Modifier.fillMaxSize().background(
        Brush.verticalGradient(listOf(Color(0xFF14152E), BG, BG))
    ).systemBarsPadding().padding(horizontal = 18.dp)) {
        if (showPlayer && current != null) {
            Row(Modifier.fillMaxWidth().padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { showPlayer = false }) { Text("‹  Медиатека", color = CYAN) }
                Spacer(Modifier.weight(1f))
                Text("MARK", color = Color.White, fontWeight = FontWeight.Black, letterSpacing = 3.sp)
            }
            Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(Modifier.height(6.dp))
                CoverArtwork(current, Modifier.size(126.dp).clip(RoundedCornerShape(26.dp)))
                Spacer(Modifier.height(12.dp))
                Text(current.title, color = Color.White, fontWeight = FontWeight.Bold,
                    fontSize = 22.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(current.artist, color = SUBTLE, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(16.dp))
                MusicVisualizer(style, sessionId, captureAllowed, playing,
                    Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(28.dp)))
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(style.label, color = CYAN, fontWeight = FontWeight.Bold)
                        Text("Визуализация звука", color = SUBTLE, fontSize = 12.sp)
                    }
                    OutlinedButton(onClick = { showEffects = true }) { Text("20 эффектов") }
                }
                if (!captureAllowed) {
                    TextButton(onClick = { captureLauncher.launch(Manifest.permission.RECORD_AUDIO) }) {
                        Text("Разрешить реакцию эффектов на музыку", color = CYAN, fontSize = 12.sp)
                    }
                }
                if (playerError != null) Text(playerError.orEmpty(), color = Color(0xFFFFA4A4), fontSize = 12.sp)
                SeekAndControls(controller, position, duration, playing)
                Spacer(Modifier.height(18.dp))
            }
        } else {
            Spacer(Modifier.height(20.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("MARK", fontWeight = FontWeight.Black, fontSize = 37.sp,
                        letterSpacing = 3.sp, color = Color.White)
                    Text("M U S I C", fontSize = 12.sp, color = CYAN, letterSpacing = 3.sp)
                }
                Surface(shape = CircleShape, color = PANEL, onClick = { refresh++ }) {
                    Text("↻", Modifier.padding(horizontal = 17.dp, vertical = 11.dp), fontSize = 26.sp, color = CYAN)
                }
            }
            Spacer(Modifier.height(22.dp))
            Text("Твоя музыка. Твой ритм.", fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
            Text("${songs.size} композиций на устройстве", color = SUBTLE, fontSize = 13.sp)
            Spacer(Modifier.height(19.dp))
            if (!audioAllowed) {
                ElevatedCard(colors = CardDefaults.elevatedCardColors(containerColor = PANEL)) {
                    Column(Modifier.padding(22.dp)) {
                        Text("Найдём твою музыку", fontSize = 21.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        Text("Разреши доступ к аудиофайлам. MARK Music не отправляет их в интернет.", color = SUBTLE)
                        Spacer(Modifier.height(14.dp))
                        Button(onClick = { audioLauncher.launch(permission) }) { Text("Открыть медиатеку") }
                    }
                }
            } else {
                OutlinedTextField(
                    value = query, onValueChange = { query = it }, singleLine = true,
                    placeholder = { Text("Поиск музыки, альбома, папки") },
                    leadingIcon = { Text("⌕", fontSize = 25.sp) },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    shape = RoundedCornerShape(19.dp), modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(13.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    listOf("Песни", "Альбомы", "Папки").forEachIndexed { index, label ->
                        FilterChip(selected = tab == index, onClick = { tab = index }, label = { Text(label) })
                    }
                }
                if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                if (libraryError != null) Text(libraryError.orEmpty(), color = Color(0xFFFFA4A4))
                Spacer(Modifier.height(7.dp))
                Box(Modifier.weight(1f)) {
                    if (!loading && filtered.isEmpty()) {
                        Text("Музыка пока не найдена. Проверь файлы или обнови медиатеку.",
                            color = SUBTLE, modifier = Modifier.align(Alignment.Center), textAlign = TextAlign.Center)
                    } else {
                        LazyColumn(contentPadding = PaddingValues(bottom = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            if (tab == 0) {
                                items(filtered, key = { it.id }) { track ->
                                    TrackRow(track) { playQueue(filtered, track) }
                                }
                            } else {
                                val groups = if (tab == 1) filtered.groupBy { it.albumId }.values
                                    .map { it.first().album to it }.sortedBy { it.first.lowercase() }
                                    else LibraryGrouping.groupByFolder(filtered) { it.folder }.toList()
                                groups.forEach { (name, tracks) ->
                                    item(key = "head-$tab-$name") {
                                        Row(Modifier.fillMaxWidth().padding(top = 13.dp, bottom = 5.dp)) {
                                            Text(name, Modifier.weight(1f), fontWeight = FontWeight.Bold, color = CYAN,
                                                maxLines = 2, overflow = TextOverflow.Ellipsis)
                                            Text("${tracks.size}", color = SUBTLE)
                                        }
                                    }
                                    items(tracks, key = { "$tab-$name-${it.id}" }) { track ->
                                        TrackRow(track) { playQueue(tracks, track) }
                                    }
                                }
                            }
                        }
                    }
                }
                if (current != null) {
                    Surface(onClick = { showPlayer = true }, color = PANEL,
                        shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
                        Row(Modifier.padding(11.dp), verticalAlignment = Alignment.CenterVertically) {
                            CoverArtwork(current, Modifier.size(52.dp).clip(RoundedCornerShape(13.dp)))
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(current.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Bold)
                                Text(current.artist, color = SUBTLE, fontSize = 12.sp, maxLines = 1)
                            }
                            TextButton(onClick = { controller?.let { if (it.isPlaying) it.pause() else it.play() } }) {
                                Text(if (playing) "Ⅱ" else "▶", color = CYAN, fontSize = 25.sp)
                            }
                        }
                    }
                }
            }
        }
    }
    if (showEffects) {
        AlertDialog(
            onDismissRequest = { showEffects = false },
            title = { Text("Выбери свою вселенную") },
            text = {
                Box(Modifier.heightIn(max = 470.dp)) {
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
            containerColor = Color(0xFF171B35)
        )
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
