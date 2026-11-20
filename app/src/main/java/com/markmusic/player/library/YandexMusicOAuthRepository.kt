package com.markmusic.player.library

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL
import javax.net.ssl.HttpsURLConnection
import java.security.SecureRandom

internal sealed interface YandexMusicOauthResult {
    data object Connected : YandexMusicOauthResult
    data class Failure(val message: String) : YandexMusicOauthResult
}

/**
 * Owner's second OAuth client: requested music:api-public is independent
 * of login:info from official Yandex LoginSDK.
 *
 * Only documented oauth.yandex.ru endpoints are called. No Music catalog,
 * stream URL resolution or hidden Yandex Music HTTP endpoints are used.
 */
internal class YandexMusicOAuthRepository(context: Context) {
    private val vault = YandexMusicVault(context.applicationContext)

    fun isConnected(): Boolean = runCatching {
        val raw = vault.read("token") ?: return false
        val stored = JSONObject(raw)
        val valid = stored.optString("client_id") == YandexMusicOAuthRules.CLIENT_ID &&
            stored.optString("access_token").isNotBlank() &&
            System.currentTimeMillis() < stored.optLong("expires_at")
        if (!valid) vault.remove("token")
        valid
    }.getOrDefault(false)

    fun isPending(): Boolean = pending() != null

    private fun pending(): JSONObject? = runCatching {
        val raw = vault.read("pending") ?: return null
        val data = JSONObject(raw)
        val expiresAt = data.optLong("expires_at")
        val valid = YandexMusicOAuthRules.pendingNotExpired(
            System.currentTimeMillis(), expiresAt
        ) && data.optString("client_id") == YandexMusicOAuthRules.CLIENT_ID &&
            data.optString("code_verifier").isNotBlank()
        if (!valid) {
            vault.remove("pending")
            return null
        }
        data
    }.getOrNull()

    /** Pending verifier survives browser roundtrips and process recreation, encrypted. */
    fun beginAuthorization(): String {
        val entropy = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val verifier = YandexMusicOAuthRules.verifier(entropy)
        val expiresAt = System.currentTimeMillis() + YandexMusicOAuthRules.PENDING_TTL_MILLIS
        vault.save("pending", JSONObject()
            .put("client_id", YandexMusicOAuthRules.CLIENT_ID)
            .put("code_verifier", verifier)
            .put("expires_at", expiresAt)
            .toString())
        return YandexMusicOAuthRules.authorizationUrl(verifier)
    }

    /**
     * User types the seven-digit code INSIDE MARK Audio.
     * Never transfer codes, access tokens or secrets through messages or logs.
     */
    suspend fun exchangeCode(verificationCode: String): YandexMusicOauthResult =
        withContext(Dispatchers.IO) {
            val code = verificationCode.trim()
            if (!YandexMusicOAuthRules.validCode(code)) {
                return@withContext YandexMusicOauthResult.Failure(
                    "Введите семизначный код подтверждения с сайта Яндекса."
                )
            }
            val pending = pending() ?: return@withContext YandexMusicOauthResult.Failure(
                "Код подтверждения устарел. Нажмите «Получить новый код»."
            )
            val verifier = pending.optString("code_verifier")
            try {
                val body = YandexMusicOAuthRules.tokenRequestBody(code, verifier)
                val connection = URL("https://oauth.yandex.ru/token").openConnection()
                    as HttpsURLConnection
                val (status, response) = try {
                    connection.connectTimeout = 12_000
                    connection.readTimeout = 12_000
                    connection.instanceFollowRedirects = false
                    connection.requestMethod = "POST"
                    connection.doOutput = true
                    connection.setRequestProperty("Accept", "application/json")
                    connection.setRequestProperty("Cache-Control", "no-store")
                    connection.setRequestProperty(
                        "Content-Type", "application/x-www-form-urlencoded; charset=UTF-8"
                    )
                    connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                    val status = connection.responseCode
                    val stream = if (status in 200..299) connection.inputStream
                        else connection.errorStream
                    val response = stream?.bufferedReader(Charsets.UTF_8)?.use {
                        it.readText().take(16_384)
                    }.orEmpty()
                    status to response
                } finally {
                    connection.disconnect()
                }
                if (status != 200) {
                    val errorCode = runCatching {
                        JSONObject(response).optString("error")
                    }.getOrDefault("")
                    return@withContext YandexMusicOauthResult.Failure(when (errorCode) {
                        "invalid_grant", "bad_verification_code" ->
                            "Неверный или просроченный код. Проверьте его или запросите новый."
                        "invalid_scope" ->
                            "Яндекс отклонил разрешение music:api-public. Проверьте настройки OAuth."
                        "invalid_client", "unauthorized_client" ->
                            "Яндекс не разрешил доступ второму OAuth-приложению."
                        else -> "Яндекс не выдал токен (HTTP $status). Повторите попытку."
                    })
                }
                val payload = JSONObject(response)
                val accessToken = payload.optString("access_token")
                val scope = if (payload.has("scope") && !payload.isNull("scope")) {
                    payload.optString("scope")
                } else null
                val expiresIn = payload.optLong("expires_in")
                val expiresAt = YandexSdkAuthRules.expiryTimeMillis(
                    System.currentTimeMillis(), expiresIn
                )
                if (accessToken.isBlank() || expiresAt == null ||
                    !YandexMusicOAuthRules.acceptsReturnedScope(scope)) {
                    return@withContext YandexMusicOauthResult.Failure(
                        "Яндекс не подтвердил токен с нужным разрешением Music API."
                    )
                }

                // Refresh token is intentionally not retained until renewal support is implemented.
                vault.save("token", JSONObject()
                    .put("client_id", YandexMusicOAuthRules.CLIENT_ID)
                    .put("access_token", accessToken)
                    .put("expires_at", expiresAt)
                    .toString())
                vault.remove("pending")
                YandexMusicOauthResult.Connected
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                YandexMusicOauthResult.Failure(
                    "Не удалось подтвердить код. Проверьте сеть и повторите попытку."
                )
            }
        }

    /** Local disconnect only. The user may revoke remote authorization in Yandex OAuth. */
    fun disconnect() {
        vault.remove("pending")
        vault.remove("token")
    }
}
