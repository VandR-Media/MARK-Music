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
 * Official Yandex Android LoginSDK delivers the OAuth token.
 * MARK Audio only verifies the login:info profile and encrypts token locally.
 * This does NOT enable Yandex Music catalog or licensed playback.
 */
internal class YandexIdAccountRepository(context: Context) {
    private val vault = YandexIdVault(context.applicationContext)

    fun current(): YandexAccount? = runCatching {
        val json = JSONObject(vault.read("account") ?: return null)
        val expiry = json.optLong("valid_until")
        if (System.currentTimeMillis() >= expiry) {
            vault.remove("account")
            return null
        }
        YandexAccount(
            login = json.getString("login"),
            userId = json.getString("user_id"),
            validUntilMillis = expiry
        )
    }.getOrNull()

    fun disconnect() {
        vault.remove("pending") // Discard abandoned legacy browser flow.
        vault.remove("account")
    }

    suspend fun completeSdkToken(
        oauthToken: String,
        expiresInSeconds: Long
    ): YandexLoginResult = withContext(Dispatchers.IO) {
        if (oauthToken.isBlank() || expiresInSeconds <= 0L) {
            return@withContext YandexLoginResult.Unavailable(
                "Яндекс ID не выдал действующий токен."
            )
        }

        try {
            val response = fetch(
                url = "https://login.yandex.ru/info?format=json",
                method = "GET",
                oauthToken = oauthToken
            )
            if (response.first != 200) {
                return@withContext YandexLoginResult.Unavailable(
                    "Яндекс ID не подтвердил профиль. Повтори вход."
                )
            }
            val profile = JSONObject(response.second)
            val sdkClientId = "cfaacee8ff6d4941a5966d00c53a61de"
            val returnedClient = profile.optString("client_id")
            if (returnedClient.isNotBlank() && returnedClient != sdkClientId) {
                return@withContext YandexLoginResult.Unavailable(
                    "Профиль относится к другому OAuth-приложению."
                )
            }
            val login = profile.optString("login")
            val userId = profile.optString("id")
            if (login.isBlank() || userId.isBlank()) {
                return@withContext YandexLoginResult.Unavailable(
                    "Не удалось проверить данные аккаунта Яндекс ID."
                )
            }
            val now = System.currentTimeMillis()
            val lifetimeSeconds = expiresInSeconds.coerceIn(1L, 365L * 24 * 60 * 60)
            val account = YandexAccount(login, userId, now + lifetimeSeconds * 1000L)
            // Do not put OAuth tokens into intents, state bundles or UI strings.
            vault.save("account", JSONObject()
                .put("access_token", oauthToken)
                .put("login", account.login)
                .put("user_id", account.userId)
                .put("valid_until", account.validUntilMillis)
                .toString())
            vault.remove("pending")
            YandexLoginResult.Connected(account)
        } catch (_: Exception) {
            YandexLoginResult.Unavailable(
                "Ошибка проверки аккаунта. Проверь интернет и повтори вход."
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
