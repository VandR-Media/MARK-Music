package com.markmusic.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.markmusic.player.library.CatalogError
import com.markmusic.player.library.CatalogPage
import com.markmusic.player.library.CatalogResult
import com.markmusic.player.library.CatalogTrack
import com.markmusic.player.library.NativeYandexOAuthClient
import com.markmusic.player.library.YandexNativeCatalogGateway
import kotlinx.coroutines.launch

/**
 * Native search only. No browser or fake catalog data.
 * User explicitly initiates each query; no automatic retry or background polling.
 */
@Composable
internal fun NativeYandexCatalogPanel(
    palette: MarkPalette,
    signedInYandexId: String?
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var client by remember { mutableStateOf(NativeYandexOAuthClient.USER) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var results by remember { mutableStateOf<CatalogPage<CatalogTrack>?>(null) }

    NeonGlass(palette, Modifier.fillMaxWidth(), radius = 24.dp) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Нативный каталог · поиск", color = palette.accent,
                fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text("Прямой запрос из MARK Audio с нашим OAuth-токеном. " +
                "Никакого браузера. Метод поиска известен из сторонней документации; " +
                "доступность для наших клиентов пока не подтверждена.",
                color = Color.White, fontSize = 12.sp)
            Text("OAuth-клиент для однократной проверки", color = Color.White, fontSize = 12.sp)
            NativeYandexOAuthClient.entries.forEach { option ->
                TextButton(
                    enabled = !busy,
                    onClick = {
                        client = option
                        message = null
                        results = null
                    }
                ) {
                    Text((if (client == option) "● " else "○ ") + option.title,
                        color = palette.accent, fontSize = 13.sp)
                }
            }
            OutlinedTextField(
                value = query,
                onValueChange = { query = it.take(100) },
                label = { Text("Исполнитель или название трека") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Button(
                modifier = Modifier.fillMaxWidth(),
                enabled = !busy && !signedInYandexId.isNullOrBlank() && query.isNotBlank(),
                onClick = {
                    val requestedQuery = query.trim()
                    val selectedClient = client
                    results = null
                    message = null
                    busy = true
                    scope.launch {
                        try {
                            val gateway = YandexNativeCatalogGateway(
                                context, selectedClient, signedInYandexId
                            )
                            when (val response = gateway.search(requestedQuery)) {
                                is CatalogResult.Success -> {
                                    results = response.data
                                    message = if (response.data.items.isEmpty())
                                        "Поиск выполнен, совпадений не найдено."
                                    else "Сервер вернул " + response.data.items.size +
                                        " треков. Аудиопотоки пока не подключены."
                                }
                                is CatalogResult.Failure -> {
                                    message = when (response.error) {
                                        CatalogError.AUTH_REQUIRED ->
                                            "Нет действительного токена этого клиента для " +
                                                "основного Яндекс ID. Проверьте подключение."
                                        CatalogError.FORBIDDEN ->
                                            "Music API: HTTP 403 — нашему клиенту " +
                                                "не разрешён этот запрос поиска."
                                        CatalogError.RATE_LIMITED ->
                                            "Music API: HTTP 429 — ограничение частоты. " +
                                                "Не повторяйте запрос."
                                        CatalogError.NO_NETWORK ->
                                            "Ошибка соединения или сетевого ответа."
                                        CatalogError.INVALID_RESPONSE ->
                                            "Ответ сервера не соответствует ожидаемой схеме."
                                        CatalogError.NOT_CONFIGURED ->
                                            "Метод каталога ещё не подключён."
                                    }
                                }
                            }
                        } finally { busy = false }
                    }
                }
            ) { Text(if (busy) "Поиск…" else "Проверить нативный поиск") }
            if (signedInYandexId.isNullOrBlank()) {
                Text("Для проверки сначала войдите в основной Яндекс ID.",
                    color = Color.White, fontSize = 12.sp)
            }
            message?.let { Text(it, color = Color.White, fontSize = 12.sp) }
            results?.items?.forEach { track ->
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(track.title, color = Color.White, fontWeight = FontWeight.Bold,
                        fontSize = 14.sp)
                    Text(track.artists.joinToString().ifBlank { "Исполнитель не указан" },
                        color = Color.LightGray, fontSize = 12.sp)
                }
            }
            Text("Результаты не сохраняются и не воспроизводятся. " +
                "Доступ к поиску не означает доступ к аудиопотокам.",
                color = Color.LightGray, fontSize = 11.sp)
        }
    }
}
