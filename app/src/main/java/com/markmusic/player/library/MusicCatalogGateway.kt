package com.markmusic.player.library

/**
 * Provider-neutral, read-only catalog boundary.
 * Authentication and actual remote access must be established independently.
 * A successful Yandex ID sign-in must never be interpreted as catalog access.
 */
internal data class CatalogTrack(
    val providerId: String,
    val title: String,
    val artists: List<String> = emptyList(),
    val albumId: String? = null,
    val coverUri: String? = null,
    val durationMs: Long? = null,
    val canPlay: Boolean = false
) {
    init {
        require(providerId.isNotBlank())
        require(durationMs == null || durationMs >= 0)
    }
}

internal data class CatalogPlaylist(
    val providerId: String,
    val title: String,
    val trackCount: Int? = null
)

internal data class CatalogPage<T>(
    val items: List<T>,
    val page: Int,
    val hasNext: Boolean
) {
    init { require(page >= 0) }
}

internal sealed interface CatalogResult<out T> {
    data class Success<T>(val data: T) : CatalogResult<T>
    data class Failure(val error: CatalogError) : CatalogResult<Nothing>
}

internal enum class CatalogError {
    NOT_CONFIGURED,
    AUTH_REQUIRED,
    FORBIDDEN,
    RATE_LIMITED,
    NO_NETWORK,
    INVALID_RESPONSE
}

internal object CatalogErrors {
    fun fromHttpStatus(status: Int): CatalogError = when (status) {
        401 -> CatalogError.AUTH_REQUIRED
        403 -> CatalogError.FORBIDDEN
        429 -> CatalogError.RATE_LIMITED
        else -> CatalogError.INVALID_RESPONSE
    }
}

internal interface MusicCatalogGateway {
    suspend fun search(query: String, page: Int = 0): CatalogResult<CatalogPage<CatalogTrack>>
    suspend fun likedTracks(page: Int = 0): CatalogResult<CatalogPage<CatalogTrack>>
    suspend fun playlists(): CatalogResult<List<CatalogPlaylist>>
    suspend fun tracks(ids: List<String>): CatalogResult<List<CatalogTrack>>
}

/**
 * Deliberately does not access Yandex endpoints: no approved catalog contract
 * for our OAuth client has been established. Never pretend a zero-item result
 * is a successfully loaded user's library.
 */
internal object UnconfiguredMusicCatalogGateway : MusicCatalogGateway {
    override suspend fun search(query: String, page: Int) =
        CatalogResult.Failure(CatalogError.NOT_CONFIGURED)

    override suspend fun likedTracks(page: Int) =
        CatalogResult.Failure(CatalogError.NOT_CONFIGURED)

    override suspend fun playlists() =
        CatalogResult.Failure(CatalogError.NOT_CONFIGURED)

    override suspend fun tracks(ids: List<String>) =
        CatalogResult.Failure(CatalogError.NOT_CONFIGURED)
}
