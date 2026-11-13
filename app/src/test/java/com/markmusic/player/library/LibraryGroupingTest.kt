package com.markmusic.player.library

import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryGroupingTest {
    @Test fun foldersWithSameLastSegmentRemainDistinct() {
        val paths = listOf("Music/Rock", "Downloads/Rock", "Music/Rock")
        val groups = LibraryGrouping.groupByFolder(paths) { it }
        assertEquals(2, groups.size)
        assertEquals(2, groups["Music/Rock"]?.size)
    }
    @Test fun albumsAreAlphabetical() {
        val albums = listOf("Z", "a", "A")
        val keys = LibraryGrouping.groupByAlbum(albums) { it }.keys.toList()
        assertEquals(listOf("A", "a", "Z"), keys)
        assertEquals(3, LibraryGrouping.groupByAlbum(albums) { it }.values.sumOf { it.size })
    }
}
