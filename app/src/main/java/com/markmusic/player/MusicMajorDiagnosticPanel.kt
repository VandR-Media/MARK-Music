package com.markmusic.player

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.markmusic.player.library.YandexMusicOAuthRepository
import com.markmusic.player.library.YandexMusicOAuthRules
import com.markmusic.player.library.YandexMusicOauthResult
import com.markmusic.player.library.YandexMusicProbeStatus
import com.markmusic.player.library.YandexMusicTokenCheck
import kotlinx.coroutines.launch

/**
 * Fully opt-in, separate Music Major OAuth experiment.
 * No code, token, UID or Music response body is displayed or logged.
 * Existing Yandex ID, API-type and Android LoginSDK tokens remain untouched.
 */
@Composable
internal fun MusicMajorDiagnosticPanel(palette: MarkPalette, signedInYandexUserId: String?) {
    val context = LocalContext.current
    val repository = remember(context) {
        YandexMusicOAuthRepository(
            context = context,
            clientId = "017187c1377846eb80cedcd93cf3fd38",
            requestedScope = "music:api-public music:major-public-api",
            tokenSlot = "major_token",
            pendingSlot = "major_pending"
        )
    }
    val scope = rememberCoroutineScope()
    var connected by remember { mutableStateOf(repository.isConnected()) }
    var pending by remember { mutableStateOf(repository.isPending()) }
    var busy by remember { mutableStateOf(false) }
    var code by remember { mutableStateOf("") }
    var verified by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<String?>(null) }

    NeonGlass(palette, Modifier.fillMaxWidth(), radius = 24.dp) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Text("Music Major API · эксперимент", color = palette.accent,
                fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Text("Отдельный OAuth-клиент с двумя правами: music:api-public и " +
                "music:major-public-api. Не заменяет существующий Яндекс ID и Music OAuth. " +
                "Доступ к каталогу и потокам не подтверждён.",
                color = Color.White, fontSize = 13.sp)
            if (!connected) {
                Button(onClick = {
                    result = null
                    runCatching {
                        val url = repository.beginAuthorization()
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                        pending = true
                    }.onFailure { result = "Не удалось открыть авторизацию Яндекса." }
                }, enabled = !busy) {
                    Text(if (pending) "Получить новый код Music Major" else "Подключить Music Major")
                }
                if (pending) {
                    Text("Введите код только здесь. Не пересылайте его в переписку.",
                        color = Color.White, fontSize = 12.sp)
                    OutlinedTextField(
                        value = code,
                        onValueChange = { code = it.filter { c -> c.isLetterOrDigit() && c.code < 128 }.take(16) },
                        label = { Text("Код с сайта Яндекса") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(
                        enabled = !busy && YandexMusicOAuthRules.validCode(code),
                        onClick = {
                            busy = true
                            result = null
                            scope.launch {
                                try {
                                    when (val response = repository.exchangeCode(code)) {
                                        YandexMusicOauthResult.Connected -> {
                                            connected = true
                                            pending = false
                                            verified = false
                                            code = ""
                                            result = "Токен отдельного Music Major клиента получен. Проверьте Яндекс ID."
                                        }
                                        is YandexMusicOauthResult.Failure -> result = response.message
                                    }
                                } finally { busy = false }
                            }
                        }
                    ) { Text("Подтвердить код Music Major") }
                }
            } else {
                TextButton(enabled = !busy, onClick = {
                    busy = true
                    verified = false
                    scope.launch {
                        try {
                            result = when (repository.verifyStoredToken(signedInYandexUserId)) {
                                YandexMusicTokenCheck.VerifiedSameAccount -> {
                                    verified = true
                                    "Client ID подтверждён, аккаунт совпадает с основным Яндекс ID."
                                }
                                YandexMusicTokenCheck.VerifiedWithoutAccountComparison ->
                                    "Токен действителен, но для сравнения аккаунта подключите Яндекс ID."
                                YandexMusicTokenCheck.DifferentAccount ->
                                    "Аккаунты отличаются. Запрос к Music API заблокирован."
                                YandexMusicTokenCheck.WrongClient ->
                                    "Токен выдан не нашему Music Major OAuth-клиенту."
                                YandexMusicTokenCheck.Rejected -> "Яндекс отклонил токен."
                                YandexMusicTokenCheck.NotConnected -> "Токен отсутствует или истёк."
                                YandexMusicTokenCheck.TemporarilyUnavailable ->
                                    "Не удалось проверить токен. Проверьте соединение."
                            }
                        } finally { busy = false }
                    }
                }) { Text("Проверить Client ID и совпадение аккаунта") }
                TextButton(enabled = !busy && verified, onClick = {
                    busy = true
                    verified = false // Every manual probe requires a fresh identity verification.
                    scope.launch {
                        try {
                            result = when (repository.probeMusicServiceAccount()) {
                                YandexMusicProbeStatus.ACCOUNT_ENDPOINT_RESPONDED ->
                                    "Music API вернул данные аккаунта. Каталог и потоки не проверены."
                                YandexMusicProbeStatus.FORBIDDEN_403 ->
                                    "Music API: HTTP 403 — доступ к этому методу не разрешён."
                                YandexMusicProbeStatus.UNAUTHORIZED_401 ->
                                    "Music API: HTTP 401 — запрос не авторизован."
                                YandexMusicProbeStatus.RATE_LIMITED_429 ->
                                    "Music API: HTTP 429 — ограничение частоты. Не повторяйте запрос."
                                YandexMusicProbeStatus.INVALID_RESPONSE ->
                                    "Music API ответил, но формат ответа не распознан."
                                YandexMusicProbeStatus.NOT_AVAILABLE ->
                                    "Музыкальный сервис недоступен или произошла ошибка сети."
                                YandexMusicProbeStatus.NOT_CONNECTED -> "Music Major токен отсутствует."
                            }
                        } finally { busy = false }
                    }
                }) { Text("Один диагностический запрос /account/status") }
                TextButton(enabled = !busy, onClick = {
                    repository.disconnect()
                    connected = false
                    pending = false
                    verified = false
                    code = ""
                    result = "Только Music Major подключение удалено с устройства."
                }) { Text("Отключить Music Major") }
            }
            if (result != null) Text(result.orEmpty(), color = Color.White, fontSize = 12.sp)
            Text("Экспериментальный метод /account/status не является " +
                "подтверждённым официальным Music API. Токены хранятся в Android Keystore.",
                color = Color.White, fontSize = 11.sp)
        }
    }
}
