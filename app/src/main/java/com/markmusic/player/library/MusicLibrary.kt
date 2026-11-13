package com.markmusic.player.library

import android.content.ContentUris
import android.content.Context
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class Track(
    val id: Long,
    val uri: Uri,
    val title: String,
    val artist: String,
    val album: String,
    val albumId: Long,
    val durationMs: Long,
    val folder: String,
    val dateAddedSeconds: Long = 0L,
    val isMusic: Boolean = true
)

object MusicLibrary {
    suspend fun scan(context: Context): List<Track> = withContext(Dispatchers.IO) {
        val songs = mutableListOf<Track>()
        val projection = mutableListOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.IS_MUSIC,
            MediaStore.Audio.Media.DISPLAY_NAME
        )
        if (Build.VERSION.SDK_INT >= 29) projection += MediaStore.Audio.Media.RELATIVE_PATH
        else projection += MediaStore.Audio.Media.DATA
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        context.contentResolver.query(
            collection, projection.toTypedArray(),
            null, null,
            "${MediaStore.Audio.Media.DATE_ADDED} DESC, ${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC"
        )?.use { cursor ->
            fun col(key: String) = cursor.getColumnIndexOrThrow(key)
            val idIndex = col(MediaStore.Audio.Media._ID)
            val titleIndex = col(MediaStore.Audio.Media.TITLE)
            val artistIndex = col(MediaStore.Audio.Media.ARTIST)
            val albumIndex = col(MediaStore.Audio.Media.ALBUM)
            val albumIdIndex = col(MediaStore.Audio.Media.ALBUM_ID)
            val durationIndex = col(MediaStore.Audio.Media.DURATION)
            val dateAddedIndex = col(MediaStore.Audio.Media.DATE_ADDED)
            val musicIndex = col(MediaStore.Audio.Media.IS_MUSIC)
            val nameIndex = col(MediaStore.Audio.Media.DISPLAY_NAME)
            val pathIndex = if (Build.VERSION.SDK_INT < 29) col(MediaStore.Audio.Media.DATA) else -1
            val relIndex = if (Build.VERSION.SDK_INT >= 29) col(MediaStore.Audio.Media.RELATIVE_PATH) else -1
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idIndex)
                val path = if (pathIndex >= 0) cursor.getString(pathIndex).orEmpty() else ""
                val folder = if (relIndex >= 0) cursor.getString(relIndex).orEmpty().trimEnd('/')
                    else path.substringBeforeLast('/', "")
                val title = cursor.getString(titleIndex).orEmpty().ifBlank { "Без названия" }
                val displayName = cursor.getString(nameIndex).orEmpty()
                val isMusic = AudioClassifier.isMusic(
                    title = title, displayName = displayName, folder = folder,
                    isMusicTag = cursor.getInt(musicIndex) != 0,
                    artist = cursor.getString(artistIndex).orEmpty(),
                    album = cursor.getString(albumIndex).orEmpty(),
                    durationMs = cursor.getLong(durationIndex)
                )
                songs.add(Track(
                    id = id,
                    uri = ContentUris.withAppendedId(collection, id),
                    title = title,
                    artist = cursor.getString(artistIndex).orEmpty().ifBlank { "Неизвестный исполнитель" },
                    album = cursor.getString(albumIndex).orEmpty().ifBlank { "Без альбома" },
                    albumId = cursor.getLong(albumIdIndex),
                    durationMs = cursor.getLong(durationIndex),
                    folder = folder.ifBlank { "Другие папки" },
                    dateAddedSeconds = cursor.getLong(dateAddedIndex),
                    isMusic = isMusic
                ))
            }
        }
        songs
    }
    suspend fun folderImage(context: Context, folder: String): ImageBitmap? = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT < 29) return@withContext null
        runCatching {
            val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            val normalized = folder.trimEnd('/') + "/"
            context.contentResolver.query(
                collection,
                arrayOf(MediaStore.Images.Media._ID),
                "${MediaStore.Images.Media.RELATIVE_PATH} = ?",
                arrayOf(normalized),
                "${MediaStore.Images.Media.DATE_ADDED} DESC"
            )?.use { cursor ->
                if (!cursor.moveToFirst()) return@use null
                val uri = ContentUris.withAppendedId(collection, cursor.getLong(0))
                context.contentResolver.openInputStream(uri)?.use { input ->
                    BitmapFactory.decodeStream(input)?.asImageBitmap()
                }
            }
        }.getOrNull()
    }

    suspend fun embeddedCover(context: Context, uri: Uri): ImageBitmap? = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            retriever.embeddedPicture?.let { bytes ->
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
            }
        } catch (_: Exception) {
            null
        } finally {
            try { retriever.release() } catch (_: Exception) { }
        }
    }
}
