package com.markmusic.player

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.content.Intent
import android.provider.MediaStore
import android.widget.Toast
import org.json.JSONArray
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.ActivityResultLauncher
import androidx.lifecycle.lifecycleScope
import com.yandex.authsdk.YandexAuthSdk
import com.yandex.authsdk.YandexAuthOptions
import com.yandex.authsdk.YandexAuthLoginOptions
import com.yandex.authsdk.YandexAuthResult
import com.markmusic.player.library.YandexLoginResult
import kotlinx.coroutines.launch
import com.markmusic.player.library.PlaylistStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
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
import com.markmusic.player.audio.MarkEqualizer
import com.markmusic.player.library.LibraryGrouping
import com.markmusic.player.library.MusicLibrary
import com.markmusic.player.library.Track
import com.markmusic.player.library.MusicSource
import com.markmusic.player.library.SunoImportedLibrary
import com.markmusic.player.library.YandexIdAccountRepository
import com.markmusic.player.library.YandexMusicOAuthRepository
import com.markmusic.player.library.YandexMusicOauthResult
import kotlinx.coroutines.delay
import kotlin.math.max

private val CYAN = Color(0xFF50DFFF)
private val VIOLET = Color(0xFFB65CFF)
private val BG = Color(0xFF0B1730)
private val PANEL = Color(0xCC14284B)
private val SUBTLE = Color(0xFFD2DCF5)

class MainActivity : ComponentActivity() {
    companion object {
        const val ACTION_OPEN_PLAYER = "com.markmusic.player.OPEN_PLAYER"
    }
    private val openPlayerRequests = mutableIntStateOf(0)
    private val yandexIdUpdates = mutableIntStateOf(0)
    private lateinit var yandexLoginLauncher: ActivityResultLauncher<YandexAuthLoginOptions>
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.action == ACTION_OPEN_PLAYER) openPlayerRequests.intValue++
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val sdk = YandexAuthSdk.create(YandexAuthOptions(this, loggingEnabled = false))
        yandexLoginLauncher = registerForActivityResult(sdk.contract) { result ->
            when (result) {
                is YandexAuthResult.Success -> lifecycleScope.launch {
                    val outcome = YandexIdAccountRepository(applicationContext)
                        .completeSdkToken(result.token.value, result.token.expiresIn)
                    when (outcome) {
                        is YandexLoginResult.Connected -> {
                            yandexIdUpdates.intValue++
                            Toast.makeText(
                                this@MainActivity,
                                "Яндекс ID подключён. Музыкальный каталог пока недоступен.",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                        is YandexLoginResult.Unavailable -> Toast.makeText(
                            this@MainActivity, outcome.reason, Toast.LENGTH_LONG
                        ).show()
                    }
                }
                is YandexAuthResult.Failure -> Toast.makeText(
                    this, "Не удалось войти через Яндекс ID. Проверь настройки приложения.",
                    Toast.LENGTH_LONG
                ).show()
                YandexAuthResult.Cancelled -> Unit
            }
        }
        window.statusBarColor = android.graphics.Color.rgb(11, 23, 48)
        window.navigationBarColor = android.graphics.Color.rgb(11, 23, 48)
        if (intent?.action == ACTION_OPEN_PLAYER) openPlayerRequests.intValue++
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(
                primary = CYAN, secondary = VIOLET,
                background = BG, surface = PANEL, onSurface = Color.White
            )) {
                GlassMotionHost {
                    MarkApp(
                        openPlayerRequests.intValue, yandexIdUpdates.intValue,
                        onYandexSignIn = {
                            runCatching { yandexLoginLauncher.launch(YandexAuthLoginOptions()) }
                                .onFailure {
                                    Toast.makeText(
                                        this@MainActivity,
                                        "Не удалось запустить Яндекс LoginSDK",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun MarkApp(
    openPlayerRequests: Int,
    yandexIdUpdates: Int,
    onYandexSignIn: () -> Unit
) {
    val ctx = LocalContext.current
    val prefs = remember { ctx.getSharedPreferences("mark_music", Context.MODE_PRIVATE) }
    val yandexRepo = remember { YandexIdAccountRepository(ctx) }
    var yandexAccount by remember { mutableStateOf(yandexRepo.current()) }
    LaunchedEffect(yandexIdUpdates) {
        yandexAccount = yandexRepo.current()
    }
    // Distinct, owner-registered Music API client: not the Yandex ID LoginSDK token.
    val musicApiRepo = remember { YandexMusicOAuthRepository(ctx) }
    var musicApiConnected by remember { mutableStateOf(musicApiRepo.isConnected()) }
    var musicApiPending by remember { mutableStateOf(musicApiRepo.isPending()) }
    var musicApiBusy by remember { mutableStateOf(false) }
    var musicApiError by remember { mutableStateOf<String?>(null) }
    val musicApiScope = rememberCoroutineScope()
    val permission = remember {
        if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO
        else Manifest.permission.READ_EXTERNAL_STORAGE
    }
    var audioAllowed by remember { mutableStateOf(ctx.hasPermission(permission)) }
    val audioLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { audioAllowed = it }
    var showWelcome by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) { delay(2600); showWelcome = false }

    var songs by remember { mutableStateOf(emptyList<Track>()) }
    var musicSource by remember {
        mutableStateOf(MusicSource.fromStored(prefs.getString("music_source", null)))
    }
    var sunoUris by remember { mutableStateOf(SunoImportedLibrary.saved(ctx)) }
    var sunoTracks by remember { mutableStateOf(emptyList<Track>()) }
    var sunoLoaded by remember { mutableStateOf(false) }
    val sunoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { selected ->
        val updated = sunoUris.toMutableSet()
        var failed = 0
        selected.forEach { uri ->
            val retained = runCatching {
                ctx.contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }.isSuccess
            if (retained) updated.add(uri.toString()) else failed++
        }
        if (updated != sunoUris) {
            sunoUris = updated.toSet()
            SunoImportedLibrary.save(ctx, sunoUris)
        }
        if (failed > 0) {
            Toast.makeText(ctx, "Не удалось сохранить доступ к $failed файлам",
                Toast.LENGTH_LONG).show()
        }
    }
    LaunchedEffect(sunoUris) {
        sunoLoaded = false
        sunoTracks = SunoImportedLibrary.scan(ctx, sunoUris)
        sunoLoaded = true
    }
    val allSongs = remember(songs, sunoTracks) { songs + sunoTracks }
    fun openOfficialMusicService(url: String) {
        runCatching {
            ctx.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url)))
        }.onFailure {
            Toast.makeText(ctx, "Не найден браузер для открытия сервиса",
                Toast.LENGTH_LONG).show()
        }
    }
    var loading by remember { mutableStateOf(false) }
    var refresh by remember { mutableIntStateOf(0) }
    var libraryError by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
    var tab by remember { mutableIntStateOf(0) }
    var navigation by remember { mutableStateOf(SectionNavigationState()) }
    val navTab = navigation.current
    fun navigate(destination: Int) { navigation = navigation.open(destination) }
    fun navigateBack() { navigation = navigation.back() }
    LaunchedEffect(yandexIdUpdates) {
        if (yandexIdUpdates > 0) {
            musicSource = MusicSource.YANDEX_MUSIC
            prefs.edit().putString("music_source", MusicSource.YANDEX_MUSIC.name).apply()
            showWelcome = false
            navigate(1)
        }
    }
    LaunchedEffect(openPlayerRequests) {
        if (openPlayerRequests > 0) {
            showWelcome = false
            navigate(2)
        }
    }
    var showThemes by remember { mutableStateOf(false) }
    var showEqualizer by remember { mutableStateOf(false) }
    var showAddToPlaylist by remember { mutableStateOf(false) }
    var showRepeatOptions by remember { mutableStateOf(false) }
    var showQueue by remember { mutableStateOf(false) }
    var queueIds by remember { mutableStateOf<List<Long>>(emptyList()) }
    var queueIndex by remember { mutableIntStateOf(0) }
    var newPlaylistName by remember { mutableStateOf("") }
    var pendingDelete by remember { mutableStateOf<Track?>(null) }
    var userPlaylists by remember { mutableStateOf(PlaylistStore.read(ctx)) }
    var glassDepth by remember {
        mutableFloatStateOf(prefs.getFloat("glass_depth", 1f).coerceIn(0f, 2f))
    }
    BackHandler(enabled = showThemes || showEqualizer || navTab != 0) {
        when {
            showThemes -> showThemes = false
            showEqualizer -> showEqualizer = false
            else -> navigateBack()
        }
    }
    var themeKey by remember { mutableStateOf(prefs.getString("theme", "COSMIC_BLUE") ?: "COSMIC_BLUE") }
    val palette = markPalette(themeKey)
    var tiltSensitivity by remember {
        mutableFloatStateOf(prefs.getFloat("tilt_sensitivity", 1f).coerceIn(0f, 2f))
    }
    var voyageSpeed by remember {
        mutableFloatStateOf(prefs.getFloat("voyage_speed", 1f).coerceIn(0f, 5f))
    }
    var favoriteIds by remember {
        mutableStateOf(prefs.getStringSet("favorites", mutableSetOf())?.mapNotNull { it.toLongOrNull() }?.toSet().orEmpty())
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
    var repeatMode by remember { mutableIntStateOf(Player.REPEAT_MODE_OFF) }
    var queueScope by remember { mutableStateOf("Очередь") }
    var restoredOnce by remember { mutableStateOf(false) }
    var deletingId by remember { mutableLongStateOf(-1L) }
    val deleteLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            if (deletingId >= 0L) {
                for (name in userPlaylists.keys) PlaylistStore.remove(ctx, name, deletingId)
                userPlaylists = PlaylistStore.read(ctx)
                val updated = favoriteIds - deletingId
                favoriteIds = updated
                prefs.edit().putStringSet("favorites",
                    updated.map { it.toString() }.toSet()).apply()
            }
            if (deletingId >= 0 && controller?.currentMediaItem?.mediaId == deletingId.toString()) {
                controller?.stop()
                controller?.clearMediaItems()
            }
            refresh++
        }
        deletingId = -1L
    }
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
                            repeatMode = player.repeatMode
                            queueIds = (0 until player.mediaItemCount).mapNotNull {
                                player.getMediaItemAt(it).mediaId.toLongOrNull()
                            }
                            queueIndex = player.currentMediaItemIndex.coerceAtLeast(0)
                        }
                        override fun onRepeatModeChanged(mode: Int) { repeatMode = mode }
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
                    repeatMode = player.repeatMode
                    queueIds = (0 until player.mediaItemCount).mapNotNull {
                        player.getMediaItemAt(it).mediaId.toLongOrNull()
                    }
                    queueIndex = player.currentMediaItemIndex.coerceAtLeast(0)
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
    // Restore the last queue and position once the device library and Media3
    // are ready. Never replace an already playing background media session.
    LaunchedEffect(controller, allSongs, sunoLoaded) {
        val p = controller
        if (!restoredOnce && p != null && allSongs.isNotEmpty() && sunoLoaded) {
            restoredOnce = true
            if (p.mediaItemCount == 0) {
                val ids = runCatching { JSONArray(prefs.getString("last_queue", "[]")) }
                    .getOrNull()
                val queue = if (ids == null) emptyList() else (0 until ids.length())
                    .mapNotNull { i -> ids.optString(i).toLongOrNull() }
                    .mapNotNull { id -> allSongs.firstOrNull { it.id == id } }
                if (queue.isNotEmpty()) {
                    val index = prefs.getInt("last_index", 0).coerceIn(0, queue.lastIndex)
                    val positionMs = prefs.getLong("last_position", 0L).coerceAtLeast(0L)
                    p.setMediaItems(queue.map { it.toMediaItem() }, index, positionMs)
                    p.repeatMode = prefs.getInt("last_repeat", Player.REPEAT_MODE_OFF)
                    queueScope = prefs.getString("last_scope", "Очередь") ?: "Очередь"
                    p.prepare()
                    if (prefs.getBoolean("resume_playing", false)) p.play()
                }
            }
        }
    }
    LaunchedEffect(controller) {
        while (true) {
            delay(5000)
            val p = controller ?: continue
            if (p.mediaItemCount > 0) {
                val ids = JSONArray()
                for (i in 0 until p.mediaItemCount) ids.put(p.getMediaItemAt(i).mediaId)
                prefs.edit().putString("last_queue", ids.toString())
                    .putInt("last_index", p.currentMediaItemIndex.coerceAtLeast(0))
                    .putLong("last_position", p.currentPosition.coerceAtLeast(0L))
                    .putBoolean("resume_playing", p.playWhenReady)
                    .putInt("last_repeat", p.repeatMode)
                    .putString("last_scope", queueScope).apply()
            }
        }
    }
    val musicSongs = remember(songs) { songs.filter { it.isMusic } }
    val otherAudio = remember(songs) { songs.filterNot { it.isMusic } }
    val current = allSongs.firstOrNull { it.id.toString() == mediaId }
    val filtered = remember(musicSongs, query) {
        musicSongs.filter { "${it.title} ${it.artist} ${it.album} ${it.folder}".contains(query, ignoreCase = true) }
    }
    fun playQueue(queue: List<Track>, selected: Track) {
        val ids = queue.map { it.id }.toSet()
        val matchingUserPlaylist = userPlaylists.entries.firstOrNull {
            it.value.isNotEmpty() && it.value == ids
        }?.key
        queueScope = when {
            matchingUserPlaylist != null -> matchingUserPlaylist
            queue.size > 1 && queue.all { it.albumId == selected.albumId } -> selected.album
            queue.size > 1 && queue.all { it.folder == selected.folder } ->
                selected.folder.trimEnd('/').substringAfterLast('/').ifBlank { "Папка" }
            else -> "Очередь"
        }
        controller?.apply {
            setMediaItems(queue.map { it.toMediaItem() }, queue.indexOf(selected).coerceAtLeast(0), 0L)
            prepare()
            play()
        }
        playerError = null
        navigate(2)
    }

    CompositionLocalProvider(
        LocalGlassSensitivity provides tiltSensitivity,
        LocalGlassDepth provides glassDepth
    ) {
    Box(modifier = Modifier.fillMaxSize().systemBarsPadding()) {
        CosmicVoyageBackground(
            palette = palette, speed = voyageSpeed, steering = tiltSensitivity,
            modifier = Modifier.matchParentSize()
        )
        Column(Modifier.fillMaxSize().padding(horizontal = 18.dp)) {
            when (navTab) {
                0 -> Box(Modifier.weight(1f)) {
                    MarkHomeScreen(
                        logo = BrandArtwork.logo(ctx), songs = musicSongs, audioAllowed = audioAllowed,
                        onRefresh = { refresh++ },
                        onRequestPermission = { audioLauncher.launch(permission) },
                        favoritesCount = songs.count { favoriteIds.contains(it.id) },
                        current = current, playing = playing, palette = palette,
                        onSearch = { navigate(1) },
                        onOpenThemes = { showThemes = true },
                        onOpenLibrary = { category ->
                             tab = category; query = ""
                             musicSource = MusicSource.DEVICE
                             prefs.edit().putString("music_source", MusicSource.DEVICE.name).apply()
                             navigate(1)
                         },
                        onOpenPlaylists = { navigate(3) },
                        onPlay = { queue, selected -> playQueue(queue, selected) }
                    )
                }
                1 -> Column(Modifier.weight(1f)) {
                    MarkMusicSourceTabs(musicSource, palette) { selected ->
                        musicSource = selected
                        prefs.edit().putString("music_source", selected.name).apply()
                    }
                    Box(Modifier.weight(1f)) {
                        when (musicSource) {
                            MusicSource.DEVICE -> MarkLibraryScreen(
                                songs = filtered, query = query, onQueryChange = { query = it },
                                onLongPress = { pendingDelete = it },
                                category = tab, onCategoryChange = { tab = it },
                                loading = loading, error = libraryError, palette = palette,
                                onPlay = { queue, selected -> playQueue(queue, selected) }
                            )
                            MusicSource.YANDEX_MUSIC -> MarkYandexMusicScreen(
                                palette = palette,
                                account = yandexAccount,
                                onBeginSignIn = onYandexSignIn,
                                onDisconnect = {
                                    yandexRepo.disconnect()
                                    yandexAccount = null
                                },
                                onOpenOfficialService = {
                                    openOfficialMusicService("https://music.yandex.ru/")
                                },
                                musicApiConnected = musicApiConnected,
                                musicApiPending = musicApiPending,
                                musicApiBusy = musicApiBusy,
                                musicApiError = musicApiError,
                                onBeginMusicApi = {
                                    runCatching {
                                        val url = musicApiRepo.beginAuthorization()
                                        musicApiPending = true
                                        musicApiError = null
                                        openOfficialMusicService(url)
                                    }.onFailure {
                                        musicApiError = "Не удалось начать авторизацию Music API."
                                    }
                                },
                                onCompleteMusicCode = { code ->
                                    if (!musicApiBusy) {
                                        musicApiBusy = true
                                        musicApiError = null
                                        musicApiScope.launch {
                                            try {
                                                when (val result = musicApiRepo.exchangeCode(code)) {
                                                    is YandexMusicOauthResult.Connected -> {
                                                        musicApiConnected = true
                                                        musicApiPending = false
                                                        Toast.makeText(
                                                            ctx, "Music API подключён. " +
                                                                "Воспроизведение пока не поддерживается.",
                                                            Toast.LENGTH_LONG
                                                        ).show()
                                                    }
                                                    is YandexMusicOauthResult.Failure -> {
                                                        musicApiError = result.message
                                                        musicApiPending = musicApiRepo.isPending()
                                                    }
                                                }
                                            } finally {
                                                musicApiBusy = false
                                            }
                                        }
                                    }
                                },
                                onDisconnectMusicApi = {
                                    musicApiRepo.disconnect()
                                    musicApiConnected = false
                                    musicApiPending = false
                                    musicApiError = null
                                }
                            )
                            MusicSource.SUNO -> MarkSunoListeningScreen(
                                tracks = sunoTracks,
                                palette = palette,
                                onPickAudio = { sunoPicker.launch(arrayOf("audio/*")) },
                                onOpenSuno = {
                                    openOfficialMusicService("https://suno.com/")
                                },
                                onPlay = { queue, selected -> playQueue(queue, selected) },
                                onForget = { imported ->
                                    if (queueIds.contains(imported.id)) {
                                        controller?.stop()
                                        controller?.clearMediaItems()
                                    }
                                    val updated = sunoUris - imported.uri.toString()
                                    sunoUris = updated
                                    SunoImportedLibrary.save(ctx, updated)
                                    if (favoriteIds.contains(imported.id)) {
                                        favoriteIds = favoriteIds - imported.id
                                        prefs.edit().putStringSet("favorites",
                                            favoriteIds.map { it.toString() }.toSet()).apply()
                                    }
                                    for (name in userPlaylists.keys) {
                                        PlaylistStore.remove(ctx, name, imported.id)
                                    }
                                    userPlaylists = PlaylistStore.read(ctx)
                                    runCatching {
                                        ctx.contentResolver.releasePersistableUriPermission(
                                            imported.uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                                        )
                                    }
                                }
                            )
                        }
                    }
                }
                2 -> Box(Modifier.weight(1f)) {
                    if (current != null) {
                        MarkPlayerScreen(
                            track = current, playing = playing,
                            position = position, duration = duration,
                            isFavorite = favoriteIds.contains(current.id),
                            playerError = playerError, palette = palette,
                            onBack = { navigateBack() },
                            onFavorite = {
                                val updated = favoriteIds.toMutableSet()
                                if (!updated.add(current.id)) updated.remove(current.id)
                                favoriteIds = updated
                                prefs.edit().putStringSet("favorites", updated.map { it.toString() }.toSet()).apply()
                            },
                            onSeek = { controller?.seekTo(it) },
                            onPrevious = { controller?.seekToPreviousMediaItem() },
                            onTogglePlayback = { controller?.let { if (it.isPlaying) it.pause() else it.play() } },
                            onNext = { controller?.seekToNextMediaItem() },
                            repeatLabel = when (repeatMode) {
                                Player.REPEAT_MODE_ONE -> "Повтор одного трека"
                                Player.REPEAT_MODE_ALL -> "Повтор: ${queueScope.lowercase()}"
                                else -> "Повтор выключен"
                            },
                            onRepeat = { showRepeatOptions = true },
                            onAddToPlaylist = { showAddToPlaylist = true },
                            onDelete = {
                                 if (current.id > 0L) pendingDelete = current
                                 else {
                                     musicSource = MusicSource.SUNO
                                     prefs.edit().putString("music_source", MusicSource.SUNO.name).apply()
                                     Toast.makeText(ctx, "Убери импорт во вкладке Suno",
                                         Toast.LENGTH_SHORT).show()
                                     navigate(1)
                                 }
                             },
                            queueLabel = queueScope,
                            onOpenQueue = { showQueue = true }
                        )
                    } else {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            NeonGlass(palette, Modifier.fillMaxWidth(), radius = 30.dp) {
                                Column(
                                    Modifier.fillMaxWidth().padding(26.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    NeonIconTile("music", palette.secondary, size = 86.dp, selected = true)
                                    NeonScript("Почувствуй музыку!", palette.accent, size = 25, center = true)
                                    Text("Запусти любой трек из медиатеки — и космос оживёт в такт музыке.",
                                        color = NeonSubtle, fontSize = 13.sp,
                                        textAlign = TextAlign.Center, lineHeight = 19.sp)
                                    NeonPill("Открыть медиатеку", palette,
                                        onClick = { navigate(1) }, icon = "library")
                                }
                            }
                        }
                    }
                }
                else -> Box(Modifier.weight(1f)) {
                    MarkPlaylistScreen(
                        favorites = allSongs.filter { it.isMusic && favoriteIds.contains(it.id) },
                        recent = musicSongs.sortedByDescending { it.dateAddedSeconds }.take(12),
                        otherAudio = otherAudio,
                        userPlaylists = userPlaylists,
                        allSongs = allSongs,
                        palette = palette,
                        onCreate = { name ->
                            val created = PlaylistStore.create(ctx, name)
                            if (created) userPlaylists = PlaylistStore.read(ctx)
                            created
                        },
                        onDeletePlaylist = { name ->
                            PlaylistStore.delete(ctx, name)
                            userPlaylists = PlaylistStore.read(ctx)
                        },
                        onRemoveTrack = { name, id ->
                            PlaylistStore.remove(ctx, name, id)
                            userPlaylists = PlaylistStore.read(ctx)
                        },
                        onPlay = { queue, selected -> playQueue(queue, selected) },
                        onLongPress = { track ->
                            if (track.id > 0L) pendingDelete = track
                            else Toast.makeText(ctx, "Убери импорт во вкладке Suno",
                                Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            }
            if (navTab != 2) {
                if (current != null) {
                    MarkMiniPlayer(
                        track = current, playing = playing, palette = palette,
                        onOpen = { navigate(2) },
                        onToggle = { controller?.let { if (it.isPlaying) it.pause() else it.play() } }
                    )
                }
                MarkBottomNavigation(navTab, palette) { selected ->
                    navigate(if (selected == 2 && current == null) 1 else selected)
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
                tiltSensitivity = tiltSensitivity,
                onTiltSensitivityChange = { value ->
                    tiltSensitivity = value
                    prefs.edit().putFloat("tilt_sensitivity", value).apply()
                },
                voyageSpeed = voyageSpeed,
                onVoyageSpeedChange = { value ->
                    voyageSpeed = value
                    prefs.edit().putFloat("voyage_speed", value).apply()
                },
                glassDepth = glassDepth,
                onGlassDepthChange = { value ->
                    glassDepth = value
                    prefs.edit().putFloat("glass_depth", value).apply()
                },
                onOpenEqualizer = {
                    showThemes = false
                    showEqualizer = true
                },
                onDismiss = { showThemes = false }
            )
        }
    }
    }
    if (showEqualizer) {
        MarkEqualizerDialog(palette) { showEqualizer = false }
    }
    if (showQueue) {
        AlertDialog(
            onDismissRequest = { showQueue = false },
            title = {
                Column {
                    Text("Сейчас в очереди", color = Color.White, fontWeight = FontWeight.Bold)
                    Text("$queueScope · ${queueIds.size} треков",
                        color = palette.accent, fontSize = 12.sp)
                }
            },
            text = {
                val queue = queueIds.mapNotNull { id -> allSongs.firstOrNull { it.id == id } }
                LazyColumn(modifier = Modifier.heightIn(max = 440.dp),
                    verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    items(queueIds.size) { index ->
                        val song = allSongs.firstOrNull { it.id == queueIds[index] }
                        if (song != null) {
                            NeonGlass(palette, Modifier.fillMaxWidth(), radius = 15.dp,
                                onClick = {
                                    controller?.seekToDefaultPosition(index)
                                    controller?.play()
                                    showQueue = false
                                }) {
                                Row(Modifier.padding(9.dp),
                                    verticalAlignment = Alignment.CenterVertically) {
                                    Text(if (index == queueIndex) "▶" else "${index + 1}",
                                        Modifier.width(27.dp), color = palette.accent,
                                        fontSize = 12.sp)
                                    Column(Modifier.weight(1f)) {
                                        Text(song.title, color = Color.White, maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            fontWeight = if (index == queueIndex) FontWeight.Bold
                                                else FontWeight.Normal)
                                        Text(song.artist, color = NeonSubtle, fontSize = 11.sp,
                                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                    Text(time(song.durationMs), color = NeonSubtle, fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showQueue = false }) {
                    Text("Закрыть", color = palette.accent)
                }
            },
            containerColor = Color(0xFF101D40)
        )
    }
    if (showRepeatOptions) {
        AlertDialog(
            onDismissRequest = { showRepeatOptions = false },
            title = { Text("Режим повтора", color = Color.White) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Выключен", "Текущий трек", "Альбом", "Папка").forEach { choice ->
                        NeonGlass(palette, Modifier.fillMaxWidth(), radius = 14.dp,
                            onClick = {
                                val p = controller
                                val track = current
                                if (p != null) {
                                    when (choice) {
                                        "Выключен" -> p.repeatMode = Player.REPEAT_MODE_OFF
                                        "Текущий трек" -> p.repeatMode = Player.REPEAT_MODE_ONE
                                        else -> if (track != null) {
                                            val group = if (choice == "Альбом") {
                                                allSongs.filter { it.albumId == track.albumId && it.isMusic }
                                            } else {
                                                allSongs.filter { it.folder == track.folder }
                                            }
                                            if (group.isNotEmpty()) {
                                                val wasPlaying = p.playWhenReady
                                                val seek = p.currentPosition.coerceAtLeast(0L)
                                                p.setMediaItems(group.map { it.toMediaItem() },
                                                    group.indexOfFirst { it.id == track.id }.coerceAtLeast(0),
                                                    seek)
                                                queueScope = choice
                                                p.repeatMode = Player.REPEAT_MODE_ALL
                                                p.prepare()
                                                if (wasPlaying) p.play()
                                            }
                                        }
                                    }
                                }
                                showRepeatOptions = false
                            }) {
                            Text(choice, Modifier.padding(13.dp), color = Color.White)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showRepeatOptions = false }) {
                Text("Закрыть", color = palette.accent)
            } },
            containerColor = Color(0xFF101D40)
        )
    }
    if (showAddToPlaylist) {
        AlertDialog(
            onDismissRequest = { showAddToPlaylist = false },
            title = { Text("Добавить в плейлист", color = Color.White) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    userPlaylists.keys.forEach { name ->
                        NeonGlass(palette, Modifier.fillMaxWidth(), radius = 14.dp,
                            onClick = {
                                current?.let { PlaylistStore.add(ctx, name, it.id) }
                                userPlaylists = PlaylistStore.read(ctx)
                                showAddToPlaylist = false
                            }) {
                            Text(name, Modifier.padding(11.dp), color = Color.White)
                        }
                    }
                    OutlinedTextField(value = newPlaylistName,
                        onValueChange = { newPlaylistName = it.take(45) },
                        label = { Text("Новый плейлист") }, singleLine = true)
                    NeonPill("Создать и добавить", palette, onClick = {
                        if (PlaylistStore.create(ctx, newPlaylistName)) {
                            current?.let { PlaylistStore.add(ctx, newPlaylistName.trim(), it.id) }
                            userPlaylists = PlaylistStore.read(ctx)
                            newPlaylistName = ""
                            showAddToPlaylist = false
                        } else Toast.makeText(ctx, "Введите новое название", Toast.LENGTH_SHORT).show()
                    })
                }
            },
            confirmButton = { TextButton(onClick = { showAddToPlaylist = false }) {
                Text("Закрыть", color = palette.accent)
            } }, containerColor = Color(0xFF101D40)
        )
    }
    pendingDelete?.let { track ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Удалить аудиофайл?", color = Color.White) },
            text = { Text("«${track.title}» будет удалён с телефона, а не только из MARK Audio. Android запросит подтверждение.",
                color = NeonSubtle) },
            confirmButton = {
                TextButton(onClick = {
                    pendingDelete = null
                    if (Build.VERSION.SDK_INT >= 30) {
                        runCatching {
                            val request = MediaStore.createDeleteRequest(
                                ctx.contentResolver, listOf(track.uri))
                            deletingId = track.id
                            deleteLauncher.launch(
                                IntentSenderRequest.Builder(request.intentSender).build())
                        }.onFailure {
                            Toast.makeText(ctx, "Не удалось запросить удаление", Toast.LENGTH_LONG).show()
                        }
                    } else {
                        Toast.makeText(ctx, "Для удаления файла требуется Android 11 или новее",
                            Toast.LENGTH_LONG).show()
                    }
                }) { Text("Удалить файл", color = NeonPink) }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) {
                Text("Отмена", color = palette.accent)
            } }, containerColor = Color(0xFF101D40)
        )
    }
    if (showWelcome) MarkWelcomeScreen(BrandArtwork.welcome(ctx), palette)
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
