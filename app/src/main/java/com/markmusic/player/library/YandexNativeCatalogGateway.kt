package com.markmusic.player.library

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.json.JSONArray
import java.net.URLEncoder
import java.net.URL
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.HttpsURLConnection

/**
 * Native, explicitly user-initiated read-only catalog test using only
 * MARK Audio's own OAuth clients and encrypted tokens. The search endpoint is
 * community-documented, NOT an official third-party catalog contract.
 *
 * No spoofed Yandex client headers, webview cookies, user agent overrides,
 * refresh loops, streaming URLs, background polling or token logging.
 */
internal enum class NativeYandexOAuthClient(
    val slot: String,
    val clientId: String,
    val title: String
) {
    USER("user_token", YandexMusicUserOAuthRules.CLIENT_ID, "Пользовательский OAuth"),
    MAJOR("major_token", "017187c1377846eb80cedcd93cf3fd38", "Music Major OAuth")
}

internal class YandexNativeCatalogGateway(
    context: Context,
    private val oauthClient: NativeYandexOAuthClient,
    private val signedInYandexId: String?
) : MusicCatalogGateway {
    private val vault = YandexMusicVault(context.applicationContext)

    override suspend fun search(
        query: String,
        page: Int
    ): CatalogResult<CatalogPage<CatalogTrack>> = withContext(Dispatchers.IO) {
        // Prevent malformed queries and runaway API use.
        if (query.isBlank() || query.length > 100 || page !in 0..10)
            return@withContext CatalogResult.Failure(CatalogError.INVALID_RESPONSE)
        if (signedInYandexId.isNullOrBlank())
            return@withContext CatalogResult.Failure(CatalogError.AUTH_REQUIRED)

        val raw = vault.read(oauthClient.slot)
            ?: return@withContext CatalogResult.Failure(CatalogError.AUTH_REQUIRED)
        val stored = runCatching { JSONObject(raw) }.getOrNull()
            ?: return@withContext CatalogResult.Failure(CatalogError.AUTH_REQUIRED)
        if (stored.optString("client_id") != oauthClient.clientId ||
            stored.optString("access_token").isBlank() ||
            stored.optLong("expires_at") <= System.currentTimeMillis()) {
            return@withContext CatalogResult.Failure(CatalogError.AUTH_REQUIRED)
        }
        val token = stored.optString("access_token")

        try {
            // Identity preflight checks that the current MARK ID matches the
            // encrypted token before any personal catalog operation.
            val idResponse = fetch(
                "https://login.yandex.ru/info?format=json",
                token,
                responseLimit = 16_384
            )
            if (idResponse.code != 200) {
                return@withContext CatalogResult.Failure(
                    if (idResponse.code in setOf(401, 403))
                        CatalogError.AUTH_REQUIRED
                    else CatalogErrors.fromHttpStatus(idResponse.code)
                )
            }
            val id = runCatching { JSONObject(idResponse.body) }.getOrNull()
                ?: return@withContext CatalogResult.Failure(CatalogError.INVALID_RESPONSE)
            if (YandexMusicTokenRules.classify(
                    clientId = id.optString("client_id"),
                    tokenUserId = id.optString("id"),
                    signedInYandexUserId = signedInYandexId,
                    expectedClientId = oauthClient.clientId
                ) != YandexMusicIdentity.SAME_ACCOUNT) {
                return@withContext CatalogResult.Failure(CatalogError.AUTH_REQUIRED)
            }

            val encoded = URLEncoder.encode(query.trim(), "UTF-8")
            val url = "https://api.music.yandex.net/search?text=$encoded&type=track&page=$page"
            val result = fetch(url, token, responseLimit = 512 * 1024)
            if (result.code != 200) {
                return@withContext CatalogResult.Failure(
                    CatalogErrors.fromHttpStatus(result.code)
                )
            }
            YandexNativeCatalogParser.parseSearch(result.body, page)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: UnknownHostException) {
            CatalogResult.Failure(CatalogError.NO_NETWORK)
        } catch (_: SocketTimeoutException) {
            CatalogResult.Failure(CatalogError.NO_NETWORK)
        } catch (_: Exception) {
            CatalogResult.Failure(CatalogError.NO_NETWORK)
        }
    }

    // Deliberately not claiming playlists or streaming without proof of search entitlement.
    override suspend fun likedTracks(page: Int): CatalogResult<CatalogPage<CatalogTrack>> =
        CatalogResult.Failure(CatalogError.NOT_CONFIGURED)

    override suspend fun playlists(): CatalogResult<List<CatalogPlaylist>> =
        CatalogResult.Failure(CatalogError.NOT_CONFIGURED)

    override suspend fun tracks(ids: List<String>): CatalogResult<List<CatalogTrack>> =
        CatalogResult.Failure(CatalogError.NOT_CONFIGURED)

    private data class Response(val code: Int, val body: String)

    private fun fetch(url: String, token: String, responseLimit: Int): Response {
        val connection = (URL(url).openConnection() as HttpsURLConnection)
        try {
            connection.requestMethod = "GET"
            connection.connectTimeout = 12_000
            connection.readTimeout = 12_000
            connection.instanceFollowRedirects = false
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("Authorization", "OAuth $token")
            connection.setRequestProperty("Cache-Control", "no-store")
            val status = connection.responseCode
            if (status != 200) return Response(status, "")
            val payload = connection.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
                val text = StringBuilder()
                val buffer = CharArray(8192)
                while (text.length <= responseLimit) {
                    val read = reader.read(buffer)
                    if (read < 0) break
                    text.append(buffer, 0, read)
                }
                if (text.length > responseLimit) "" else text.toString()
            }
            return Response(status, payload)
        } finally {
            connection.disconnect()
        }
    }
}

/** Parsing never grants streaming capability, even for an API track marked "available". */
internal object YandexNativeCatalogParser {
    fun parseSearch(json: String, page: Int): CatalogResult<CatalogPage<CatalogTrack>> =
        runCatching {
            val section = JSONObject(json).getJSONObject("result").getJSONObject("tracks")
            val items = section.getJSONArray("results")
            val tracks = ArrayList<CatalogTrack>(minOf(items.length(), 50))
            for (index in 0 until minOf(items.length(), 50)) {
                val track = items.optJSONObject(index) ?: continue
                val id = track.optString("id")
                if (id.isBlank()) continue
                val artists = track.optJSONArray("artists")
                val names = mutableListOf<String>()
                if (artists != null) for (j in 0 until minOf(artists.length(), 8)) {
                    artists.optJSONObject(j)?.optString("name")
                        ?.takeIf { it.isNotBlank() }?.let(names::add)
                }
                val duration = if (track.has("durationMs") && !track.isNull("durationMs"))
                    track.optLong("durationMs", -1).takeIf { it >= 0 } else null
                tracks.add(CatalogTrack(
                    providerId = id,
                    title = track.optString("title").ifBlank { "Без названия" },
                    artists = names,
                    albumId = track.optJSONArray("albums")?.optJSONObject(0)?.optString("id"),
                    coverUri = track.optString("coverUri").takeIf { it.isNotBlank() },
                    durationMs = duration,
                    canPlay = false
                ))
            }
            val total = section.optInt("total", -1)
            val hasNext = if (total >= 0) (page + 1L) * 50L < total
                else items.length() >= 50
            CatalogResult.Success(CatalogPage(tracks, page, hasNext))
        }.getOrElse { CatalogResult.Failure(CatalogError.INVALID_RESPONSE) }
}
