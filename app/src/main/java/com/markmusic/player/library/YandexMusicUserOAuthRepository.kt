package com.markmusic.player.library

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL
import javax.net.ssl.HttpsURLConnection

internal sealed interface YandexUserMusicAuthResult {
    data object Connected : YandexUserMusicAuthResult
    data class Failure(val reason: String) : YandexUserMusicAuthResult
}

/**
 * User-authorization OAuth client for music:api-public.
 * Always uses the official Android LoginSDK, not verification codes.
 * Its encrypted token is stored separately from both the original Yandex ID
 * login token and the API-type Yandex Music verification-code token.
 */
internal class YandexMusicUserOAuthRepository(context: Context) {
    private val vault = YandexMusicVault(context.applicationContext)

    fun isConnected(): Boolean = runCatching {
        val raw = vault.read("user_token") ?: return@runCatching false
        val token = JSONObject(raw)
        val valid = token.optString("client_id") == YandexMusicUserOAuthRules.CLIENT_ID &&
            token.optString("access_token").isNotBlank() &&
            System.currentTimeMillis() < token.optLong("expires_at")
        if (!valid) vault.remove("user_token")
        valid
    }.getOrDefault(false)

    /**
     * Verifies the SDK-issued token against the documented Yandex ID profile
     * endpoint. Do not store it if the selected Yandex account differs from
     * the already connected LoginSDK identity.
     */
    suspend fun acceptSdkToken(
        value: String,
        expiresInSeconds: Long,
        signedInYandexUserId: String?
    ): YandexUserMusicAuthResult = withContext(Dispatchers.IO) {
        if (value.isBlank() || signedInYandexUserId.isNullOrBlank()) {
            return@withContext YandexUserMusicAuthResult.Failure(
                "Сначала подключи основной Яндекс ID к MARK Audio."
            )
        }
        val expiresAt = YandexSdkAuthRules.expiryTimeMillis(
            System.currentTimeMillis(), expiresInSeconds
        ) ?: return@withContext YandexUserMusicAuthResult.Failure(
            "Яндекс не подтвердил срок действия нового OAuth-токена."
        )

        try {
            val connection = URL("https://login.yandex.ru/info?format=json")
                .openConnection() as HttpsURLConnection
            val (status, profile) = try {
                connection.requestMethod = "GET"
                connection.connectTimeout = 12_000
                connection.readTimeout = 12_000
                connection.instanceFollowRedirects = false
                connection.setRequestProperty("Accept", "application/json")
                connection.setRequestProperty("Authorization", "OAuth " + value)
                connection.setRequestProperty("Cache-Control", "no-store")
                val code = connection.responseCode
                val body = if (code == 200) {
                    connection.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
                        val result = StringBuilder()
                        val buffer = CharArray(4096)
                        while (result.length < 16_384) {
                            val n = reader.read(buffer, 0, minOf(buffer.size, 16_384 - result.length))
                            if (n < 0) break
                            result.append(buffer, 0, n)
                        }
                        result.toString()
                    }
                } else ""
                code to body
            } finally {
                connection.disconnect()
            }
            if (status != 200) {
                return@withContext YandexUserMusicAuthResult.Failure(
                    "Яндекс ID не подтвердил новый OAuth-токен (HTTP $status)."
                )
            }
            val json = JSONObject(profile)
            if (!YandexMusicUserOAuthRules.matchesAccount(
                    returnedClientId = json.optString("client_id"),
                    returnedUserId = json.optString("id"),
                    signedInYandexUserId = signedInYandexUserId
                )) {
                return@withContext YandexUserMusicAuthResult.Failure(
                    "Новый Music OAuth-клиент не подтвердил тот же аккаунт Яндекс ID. " +
                        "Проверь аккаунт, выбранный в окне авторизации."
                )
            }
            vault.save("user_token", JSONObject()
                .put("client_id", YandexMusicUserOAuthRules.CLIENT_ID)
                .put("access_token", value)
                .put("expires_at", expiresAt)
                .toString())
            YandexUserMusicAuthResult.Connected
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            YandexUserMusicAuthResult.Failure(
                "Проверка нового OAuth-клиента недоступна. Проверь интернет."
            )
        }
    }

    /** Read-only diagnostic, user-initiated; no playlists or streams accessed. */
    suspend fun probeMusicAccount(): YandexMusicProbeStatus = withContext(Dispatchers.IO) {
        val token = runCatching {
            JSONObject(vault.read("user_token") ?: return@withContext
                YandexMusicProbeStatus.NOT_CONNECTED)
        }.getOrNull() ?: return@withContext YandexMusicProbeStatus.NOT_CONNECTED
        if (token.optString("client_id") != YandexMusicUserOAuthRules.CLIENT_ID ||
            token.optString("access_token").isBlank() ||
            System.currentTimeMillis() >= token.optLong("expires_at")) {
            return@withContext YandexMusicProbeStatus.NOT_CONNECTED
        }
        try {
            val connection = URL(
                "https://" + YandexMusicProbeRules.HOST + YandexMusicProbeRules.PATH
            ).openConnection() as HttpsURLConnection
            val (status, accountPresent) = try {
                connection.requestMethod = "GET"
                connection.connectTimeout = 12_000
                connection.readTimeout = 12_000
                connection.instanceFollowRedirects = false
                connection.setRequestProperty("Accept", "application/json")
                connection.setRequestProperty("Cache-Control", "no-store")
                connection.setRequestProperty(
                    "Authorization", "OAuth " + token.optString("access_token")
                )
                val code = connection.responseCode
                if (code != 200) code to false
                else {
                    val body = connection.inputStream.bufferedReader(Charsets.UTF_8).use {
                        val buffer = CharArray(8192)
                        val result = StringBuilder()
                        while (result.length <= 131_072) {
                            val n = it.read(buffer, 0, buffer.size)
                            if (n < 0) break
                            result.append(buffer, 0, n)
                        }
                        if (result.length > 131_072) "" else result.toString()
                    }
                    val account = runCatching {
                        JSONObject(body).optJSONObject("result")?.optJSONObject("account")
                    }.getOrNull()
                    code to ((account?.optLong("uid", 0L) ?: 0L) > 0L)
                }
            } finally {
                connection.disconnect()
            }
            YandexMusicProbeRules.classify(status, accountPresent)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            YandexMusicProbeStatus.NOT_AVAILABLE
        }
    }

    fun disconnect() = vault.remove("user_token")
}
