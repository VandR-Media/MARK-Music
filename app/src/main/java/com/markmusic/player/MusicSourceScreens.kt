package com.markmusic.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.markmusic.player.library.MusicSource
import com.markmusic.player.library.Track

/**
 * Source selector belongs to the library, not the player. Changing sources does
 * not interrupt the current Media3 session or affect local library permissions.
 */
@Composable
internal fun MarkMusicSourceTabs(
    selected: MusicSource,
    palette: MarkPalette,
    onSelect: (MusicSource) -> Unit
) {
    // Huawei Pura 80 Ultra: 1276 physical pixels != 1276 dp.
    // Three equally weighted targets must remain visible after density/font scaling.
    Row(
        Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        MusicSource.entries.forEach { source ->
            FilterChip(
                selected = selected == source,
                onClick = { onSelect(source) },
                label = {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text(
                            source.compactLabel,
                            fontSize = 13.sp,
                            lineHeight = 15.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            maxLines = 2
                        )
                    }
                },
                modifier = Modifier.weight(1f).height(60.dp),
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = Color(0xAA172846),
                    labelColor = Color.White,
                    selectedContainerColor = palette.accent.copy(alpha = .25f),
                    selectedLabelColor = Color.White
                )
            )
        }
    }
}

@Composable
internal fun MarkYandexMusicScreen(
    palette: MarkPalette,
    onOpenOfficialService: () -> Unit
) {
    var showDetails by androidx.compose.runtime.remember {
        androidx.compose.runtime.mutableStateOf(false)
    }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(top = 12.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(11.dp)
    ) {
        Text("Яндекс Музыка", color = Color.White, fontSize = 23.sp,
            fontWeight = FontWeight.ExtraBold)
        NeonGlass(palette, Modifier.fillMaxWidth(), radius = 24.dp) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
                Text("Не подключено", color = palette.accent,
                    fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text("Подписка активна в сервисе Яндекса, но для воспроизведения в MARK Audio " +
                    "нужен разрешённый доступ к каталогу и потокам.",
                    color = Color.White, fontSize = 13.sp, lineHeight = 19.sp)
                NeonPill("Открыть Яндекс Музыку", palette,
                    onClick = onOpenOfficialService, icon = "music")
                androidx.compose.material3.TextButton(onClick = { showDetails = !showDetails }) {
                    Text(if (showDetails) "Скрыть подробности" else "Почему нет входа?",
                        color = palette.accent, fontSize = 13.sp)
                }
                if (showDetails) {
                    Text("Яндекс ID поддерживает OAuth с PKCE. Но разрешение на вход " +
                        "не равно разрешению получать музыкальные потоки. " +
                        "Пока этот доступ не согласован, MARK Audio не запрашивает токены.",
                        color = NeonSubtle, fontSize = 12.sp, lineHeight = 18.sp)
                }
            }
        }
    }
}

@Composable
internal fun MarkSunoListeningScreen(
    tracks: List<Track>,
    palette: MarkPalette,
    onPickAudio: () -> Unit,
    onOpenSuno: () -> Unit,
    onPlay: (List<Track>, Track) -> Unit,
    onForget: (Track) -> Unit
) {
    Column(Modifier.fillMaxSize().padding(top = 14.dp)) {
        Text("Suno · прослушивание", color = Color.White,
            fontWeight = FontWeight.ExtraBold, fontSize = 24.sp)
        Text("Без генерации, ремиксов и редактирования", color = NeonSubtle,
            fontSize = 12.sp, modifier = Modifier.padding(top = 3.dp, bottom = 10.dp))
        NeonGlass(palette, Modifier.fillMaxWidth(), radius = 22.dp) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Импортируй экспортированные MP3/WAV из Suno. " +
                    "Они играют в MARK Audio со всеми визуализациями.",
                    color = Color.White, fontSize = 13.sp, lineHeight = 19.sp)
                NeonPill("Добавить аудиофайлы", palette,
                    onClick = onPickAudio, icon = "music")
                Text("Это локальные файлы, не синхронизация аккаунта.",
                    color = NeonSubtle, fontSize = 12.sp, lineHeight = 17.sp)
                androidx.compose.material3.TextButton(onClick = onOpenSuno) {
                    Text("Открыть Suno", color = palette.accent, fontSize = 13.sp)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Text("Добавлено: ${tracks.size}", color = palette.accent, fontSize = 13.sp,
            fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(7.dp))
        if (tracks.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Здесь появятся выбранные аудиофайлы Suno",
                    color = NeonSubtle, fontSize = 14.sp)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(tracks, key = { it.uri.toString() }) { track ->
                    NeonGlass(palette, Modifier.fillMaxWidth(), radius = 18.dp,
                        onClick = { onPlay(tracks, track) }) {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 13.dp, vertical = 11.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text("▶", color = palette.accent, fontSize = 18.sp)
                            Column(Modifier.weight(1f)) {
                                Text(track.title, color = Color.White, fontWeight = FontWeight.Bold,
                                    maxLines = 1, fontSize = 14.sp)
                                Text(track.artist, color = NeonSubtle, maxLines = 1, fontSize = 12.sp)
                            }
                            androidx.compose.material3.TextButton(onClick = { onForget(track) }) {
                                Text("Убрать", color = palette.secondary, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}
