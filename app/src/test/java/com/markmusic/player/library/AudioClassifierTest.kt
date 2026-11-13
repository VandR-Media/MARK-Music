package com.markmusic.player.library

import org.junit.Assert.*
import org.junit.Test

class AudioClassifierTest {
    @Test fun taggedMusicRemainsInLibrary() {
        assertTrue(AudioClassifier.isMusic("A Song", "song.mp3", "Music/Albums",
            true, "Artist", "Album", 240000))
    }

    @Test fun voiceMessageEvenIfTaggedAsMusicIsSeparated() {
        assertFalse(AudioClassifier.isMusic("Voice message", "PTT-abc.opus",
            "WhatsApp/Voice Notes", true, "<unknown>", "<unknown>", 14000))
    }

    @Test fun unknownRecorderGoesToOtherCollection() {
        assertFalse(AudioClassifier.isMusic("Audio", "audio001.m4a", "Downloads",
            false, "<unknown>", "<unknown>", 27000))
    }

    @Test fun missingMusicFlagButKnownArtistStaysInLibrary() {
        assertTrue(AudioClassifier.isMusic("Track", "track.flac", "Music",
            false, "Artist", "Album", 200000))
    }
}
