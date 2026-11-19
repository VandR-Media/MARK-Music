package com.markmusic.player.library

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.zip.CRC32

/**
 * The source is an explicit user choice. A Yandex ID session is NOT an entitlement
 * to third-party Yandex Music streams, and Suno does not expose a supported
 * self-service library/listening API. Do not claim connectivity without it.
 */
internal enum class MusicSource(val displayName: String) {
    DEVICE("На устройстве"),
    YANDEX_MUSIC("Яндекс Музыка"),
    SUNO("Suno");

    companion object {
        fun fromStored(value: String?): MusicSource =
            entries.firstOrNull { it.name == value } ?: DEVICE
    }
}

/** MediaStore IDs are positive. Imported SAF documents use deterministic negative IDs. */
internal fun sunoDocumentId(uri: String): Long {
    val crc = CRC32().apply { update(uri.toByteArray(Charsets.UTF_8)) }
    return -(crc.value + 1L)
}

/**
 * User-selected Suno audio exports (MP3/WAV etc), not a scraped Suno catalog.
 * Android's document picker grants the only access used by this component.
 * No account cookies, OAuth tokens or passwords are collected.
 */
internal object SunoImportedLibrary {
    private const val STORE = "mark_music"
    private const val KEY = "suno_imported_audio_uris"

    fun saved(context: Context): Set<String> =
        context.getSharedPreferences(STORE, Context.MODE_PRIVATE)
            .getStringSet(KEY, emptySet())?.toSet().orEmpty()

    fun save(context: Context, items: Set<String>) {
        context.getSharedPreferences(STORE, Context.MODE_PRIVATE)
            .edit().putStringSet(KEY, items.toSet()).apply()
    }

    suspend fun scan(context: Context, uriStrings: Set<String>): List<Track> =
        withContext(Dispatchers.IO) {
            uriStrings.sorted().mapNotNull { text ->
                runCatching { readOne(context, Uri.parse(text)) }.getOrNull()
            }
        }

    private fun readOne(context: Context, uri: Uri): Track {
        // Make sure the persisted URI can still be read.
        context.contentResolver.openFileDescriptor(uri, "r")?.use { }
            ?: error("Cannot open selected audio document")
        val displayName = context.contentResolver.query(
            uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null
        )?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }.orEmpty()

        val retriever = MediaMetadataRetriever()
        var title: String? = null
        var artist: String? = null
        var duration = 0L
        try {
            retriever.setDataSource(context, uri)
            title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
            artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
            duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull()?.coerceAtLeast(0L) ?: 0L
        } catch (_: Exception) {
            // A valid audio document may have no metadata; ExoPlayer decides playability.
        } finally {
            runCatching { retriever.release() }
        }
        return Track(
            id = sunoDocumentId(uri.toString()),
            uri = uri,
            title = title?.takeIf { it.isNotBlank() }
                ?: displayName.substringBeforeLast('.', displayName).ifBlank { "Аудиофайл Suno" },
            artist = artist?.takeIf { it.isNotBlank() } ?: "Suno",
            album = "Импорт из Suno",
            albumId = -90001L,
            durationMs = duration,
            folder = "Suno / импорт",
            isMusic = true
        )
    }
}
