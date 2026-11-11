package com.markmusic.player.library

object LibraryGrouping {
    private val ordering = Comparator<String> { a, b ->
        val folded = a.compareTo(b, ignoreCase = true)
        if (folded != 0) folded else a.compareTo(b)
    }
    fun <T> groupByAlbum(items: List<T>, album: (T) -> String): Map<String, List<T>> =
        items.groupBy(album).toSortedMap(ordering)
    fun <T> groupByFolder(items: List<T>, folder: (T) -> String): Map<String, List<T>> =
        items.groupBy(folder).toSortedMap(ordering)
}
