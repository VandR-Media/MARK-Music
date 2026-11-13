package com.markmusic.player.library

/**
 * Heuristic only; Android's IS_MUSIC tag isn't always populated by importers.
 * We bias toward keeping tagged songs in the music library, and separate voice
 * messages / recorder files with recognizable names or non-music metadata.
 * No recording, fingerprinting, speech recognition or network call.
 */
object AudioClassifier {
    private val voiceMarkers = listOf(
        "voice message", "voice_", "voice-", "голосовое", "запись разговора",
        "recordings/", "recording_", "whatsapp audio", "telegram audio",
        "ptt-", "opus_audio", "диктофон", "audio message",
        "voice note", "голосовые", "voice notes"
    )

    fun isMusic(
        title: String, displayName: String, folder: String,
        isMusicTag: Boolean, artist: String, album: String,
        durationMs: Long
    ): Boolean {
        val text = "$displayName $title $folder".lowercase()
        if (voiceMarkers.any { text.contains(it) }) return false
        if (isMusicTag) return true
        val knownArtist = artist.isNotBlank() && !artist.contains("unknown", true) &&
            !artist.contains("неизвест", true) && artist != "<unknown>"
        val knownAlbum = album.isNotBlank() && !album.contains("unknown", true) &&
            !album.contains("без альбом", true)
        // Explicit music metadata is stronger evidence than a missing IS_MUSIC tag.
        if (knownArtist || knownAlbum) return true
        // Long ambiguous audio remains in "Voice and other" until categorized.
        return false
    }
}
