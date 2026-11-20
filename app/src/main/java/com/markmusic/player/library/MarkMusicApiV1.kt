package com.markmusic.player.library

/**
 * MARK Audio Music API v1.  Capability negotiation precedes OAuth and playback:
 * neither a paid subscription nor a Yandex ID login proves Music stream access.
 */
internal enum class MusicFeature {
    LOCAL_SCAN,
    IMPORTED_AUDIO,
    PERSONAL_CATALOG,
    SEARCH,
    PLAYLISTS,
    AUTHORIZATION,
    REMOTE_STREAM
}

internal data class MusicServiceCapabilities(
    val source: MusicSource,
    val available: Set<MusicFeature>,
    val blockedReason: String? = null
) {
    fun supports(feature: MusicFeature): Boolean = feature in available
}

/**
 * A gateway's result is always explicit; an empty list must never masquerade
 * as a successful account-library fetch when access is not available.
 */
internal sealed interface MusicApiResult<out T> {
    data class Success<T>(val value: T) : MusicApiResult<T>
    data class Unavailable(val reason: MusicApiError) : MusicApiResult<Nothing>
}

internal enum class MusicApiError {
    API_NOT_GRANTED,
    CLIENT_NOT_CONFIGURED,
    AUTH_REQUIRED,
    SERVICE_UNAVAILABLE
}

internal data class RemoteMusicItem(
    val providerId: String,
    val source: MusicSource,
    val title: String,
    val artist: String
)

internal data class AuthorizedStream(
    val mediaUrl: String,
    val providerId: String,
    val source: MusicSource
)

/**
 * Future Yandex/Suno connectors implement this boundary. Do not resolve a
 * playable stream unless the provider has explicitly granted that capability.
 * No unsafe implicit fallback to undocumented playback endpoints.
 */
internal interface RemoteMusicGateway {
    val source: MusicSource
    val capabilities: MusicServiceCapabilities

    suspend fun loadPersonalLibrary(): MusicApiResult<List<RemoteMusicItem>>

    suspend fun resolveAuthorizedStream(item: RemoteMusicItem): MusicApiResult<AuthorizedStream>
}

internal object MarkMusicApiV1 {
    fun capabilities(source: MusicSource): MusicServiceCapabilities = when (source) {
        MusicSource.DEVICE -> MusicServiceCapabilities(
            source,
            available = setOf(MusicFeature.LOCAL_SCAN, MusicFeature.SEARCH, MusicFeature.PLAYLISTS)
        )
        MusicSource.SUNO -> MusicServiceCapabilities(
            source,
            available = setOf(MusicFeature.IMPORTED_AUDIO),
            blockedReason = "Нет подтверждённого API для чтения и стриминга Suno-аккаунта"
        )
        MusicSource.YANDEX_MUSIC -> MusicServiceCapabilities(
            source,
            available = emptySet(),
            blockedReason = "Не предоставлен разрешённый API каталога и потоков Яндекс Музыки"
        )
    }

    /** Explicit unsupported response until a licensed adapter is configured. */
    fun remoteGateway(source: MusicSource): RemoteMusicGateway? =
        if (source == MusicSource.DEVICE) null else UnconfiguredGateway(source)

    private class UnconfiguredGateway(override val source: MusicSource) : RemoteMusicGateway {
        override val capabilities: MusicServiceCapabilities = capabilities(source)

        override suspend fun loadPersonalLibrary(): MusicApiResult<List<RemoteMusicItem>> =
            MusicApiResult.Unavailable(MusicApiError.API_NOT_GRANTED)

        override suspend fun resolveAuthorizedStream(
            item: RemoteMusicItem
        ): MusicApiResult<AuthorizedStream> =
            MusicApiResult.Unavailable(MusicApiError.API_NOT_GRANTED)
    }
}
