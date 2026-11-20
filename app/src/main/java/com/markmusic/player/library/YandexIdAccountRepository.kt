package com.markmusic.player.library

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL
import javax.net.ssl.HttpsURLConnection

internal data class YandexAccount(
    val login: String,
    val userId: String,
    val validUntilMillis: Long
)

internal sealed interface YandexLoginResult {
    data class Connected(val account: YandexAccount) : YandexLoginResult
    data class Unavailable(val reason: String) : YandexLoginResult
}

/**
 * Yandex ID account connection, not a Yandex Music stream integration.
 * Network requests are sent only to the official HTTPS OAuth and ID hosts.
 * No credentials are logged, sent through URL parameters, or exported.
 */
internal class YandexIdAccountRepository(context: Context) {
    private val ctx = context.applicationContext
    private val vault = YandexIdVault(ctx)
    private val publicPrefs = ctx.getSharedPreferences("mark_yandex_id_config", Context.MODE_PRIVATE)

    fun begin(clientId: String): String {
        require(YandexIdTokenProtocol.clientIdIsValid(clientId.trim())) {
            "Неверный идентификатор OAuth-приложения"
        }
        val id = clientId.trim()
        val request = YandexIdPkce.prepare(
            clientId = id,
            redirectUri = YandexIdTokenProtocol.APP_REDIRECT,
            scopes = listOf(YandexIdTokenProtocol.ACCOUNT_SCOPE)
        )
        val serialized = JSONObject()
            .put("client_id", request.clientId)
            .put("redirect_uri", request.redirectUri)
            .put("state", request.state)
            .put("verifier", request.codeVerifier)
            .put("challenge", request.codeChallenge)
            .put("created", request.createdAtMillis)
            .toString()
        vault.save("pending", serialized)
        publicPrefs.edit().putString("client_id", id).apply()
        return request.authorizationUrl()
    }

    fun rememberedClientId(): String = publicPrefs.getString("client_id", "").orEmpty()

    fun current(): YandexAccount? = runCatching {
        val json = JSONObject(vault.read("account") ?: return null)
        val validUntil = json.optLong("valid_until")
        if (System.currentTimeMillis() >= validUntil) {
            vault.remove("account")
            return null
        }
        YandexAccount(
            login = json.getString("login"),
            userId = json.getString("user_id"),
            validUntilMillis = validUntil
        )
    }.getOrNull()

    fun disconnect() {
        vault.remove("pending")
        vault.remove("account")
    }

    suspend fun completeRedirect(uri: String): YandexLoginResult = withContext(Dispatchers.IO) {
        val request = try {
            val pending = vault.read("pending")
                ?: return@withContext YandexLoginResult.Unavailable(
                    "Запрос входа не найден. Начни авторизацию заново."
                )
            val json = JSONObject(pending)
            YandexIdPkce.Request(
                clientId = json.getString("client_id"),
                redirectUri = json.getString("redirect_uri"),
                scopes = listOf(YandexIdTokenProtocol.ACCOUNT_SCOPE),
                state = json.getString("state"),
                codeVerifier = json.getString("verifier"),
                codeChallenge = json.getString("challenge"),
                createdAtMillis = json.getLong("created")
            )
        } catch (_: Exception) {
            return@withContext YandexLoginResult.Unavailable("Повреждено состояние авторизации.")
        } finally {
            // One-time state: reject replay and duplicated callbacks.
            vault.remove("pending")
        }

        val result = request.validateRedirect(uri, System.currentTimeMillis())
        if (result !is YandexIdPkce.CallbackResult.Code) {
            return@withContext YandexLoginResult.Unavailable(
                "Ответ Яндекс ID отклонён. Попробуй войти снова."
            )
        }

        try {
            val body = YandexIdTokenProtocol.exchangeBody(request, result.value)
            val response = fetch(
                url = YandexIdTokenProtocol.TOKEN_ENDPOINT, method = "POST",
                formBody = body
            )
            if (response.first != 200) {
                return@withContext YandexLoginResult.Unavailable(
                    "Яндекс ID отказал в выдаче токена. Проверь Client ID и настройки доступа."
                )
            }
            val tokenData = JSONObject(response.second)
            val accessToken = tokenData.getString("access_token")
            require(accessToken.isNotBlank())
            val seconds = tokenData.optLong("expires_in", 3600)
                .coerceIn(1L, 315_360_000L)
            // Use OAuth header rather than unsafe token-in-URL.
            val info = fetch(
                url = YandexIdTokenProtocol.USERINFO_ENDPOINT,
                method = "GET",
                oauthToken = accessToken
            )
            if (info.first != 200) {
                return@withContext YandexLoginResult.Unavailable(
                    "Токен получен, но профиль Яндекс ID не подтвердился."
                )
            }
            val profile = JSONObject(info.second)
            val returnedClient = profile.optString("client_id")
            if (returnedClient.isNotBlank() && returnedClient != request.clientId) {
                return@withContext YandexLoginResult.Unavailable(
                    "Яндекс ID вернул данные другого OAuth-клиента."
                )
            }
            val login = profile.optString("login")
            val userId = profile.optString("id")
            if (login.isBlank() || userId.isBlank()) {
                return@withContext YandexLoginResult.Unavailable(
                    "Яндекс ID не подтвердил личность пользователя."
                )
            }
            val account = YandexAccount(
                login = login,
                userId = userId,
                validUntilMillis = System.currentTimeMillis() +
                    seconds.coerceAtMost((Long.MAX_VALUE - System.currentTimeMillis()) / 1000L) * 1000L
            )
            // Encrypt tokens; never expose token through Composables or app logs.
            vault.save("account", JSONObject()
                .put("access_token", accessToken)
                .put("login", account.login)
                .put("user_id", account.userId)
                .put("valid_until", account.validUntilMillis)
                .toString())
            YandexLoginResult.Connected(account)
        } catch (_: Exception) {
            YandexLoginResult.Unavailable(
                "Не удалось завершить вход. Проверь интернет и настройки OAuth."
            )
        }
    }

    private fun fetch(
        url: String,
        method: String,
        formBody: String? = null,
        oauthToken: String? = null
    ): Pair<Int, String> {
        val endpoint = URL(url)
        require(endpoint.protocol == "https")
        require(endpoint.host in setOf("oauth.yandex.ru", "login.yandex.ru"))
        val connection = endpoint.openConnection() as HttpsURLConnection
        return try {
            connection.connectTimeout = 12_000
            connection.readTimeout = 12_000
            connection.instanceFollowRedirects = false
            connection.requestMethod = method
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("Cache-Control", "no-store")
            if (oauthToken != null) {
                connection.setRequestProperty("Authorization", "OAuth " + oauthToken)
            }
            if (formBody != null) {
                connection.doOutput = true
                connection.setRequestProperty(
                    "Content-Type", "application/x-www-form-urlencoded; charset=UTF-8"
                )
                connection.outputStream.use { it.write(formBody.toByteArray(Charsets.UTF_8)) }
            }
            val status = connection.responseCode
            val source = if (status in 200..299) connection.inputStream
                else connection.errorStream
            val text = source?.bufferedReader(Charsets.UTF_8)?.use { reader ->
                val buffer = CharArray(8192)
                val content = StringBuilder()
                while (content.length < 32_768) {
                    val n = reader.read(buffer, 0, minOf(buffer.size, 32_768 - content.length))
                    if (n < 0) break
                    content.append(buffer, 0, n)
                }
                content.toString()
            }.orEmpty()
            status to text
        } finally {
            connection.disconnect()
        }
    }
}
