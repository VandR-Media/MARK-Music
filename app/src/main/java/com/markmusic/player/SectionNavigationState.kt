package com.markmusic.player

/**
 * Lightweight navigation history for MARK Audio's four main sections.
 * Re-selecting the current section doesn't add duplicate history entries.
 */
internal data class SectionNavigationState(
    val current: Int = 0,
    val history: List<Int> = emptyList()
) {
    fun open(destination: Int): SectionNavigationState {
        require(destination in 0..3) { "Unknown MARK Audio section: $destination" }
        return if (destination == current) this else copy(
            current = destination,
            history = history + current
        )
    }

    fun back(): SectionNavigationState {
        if (history.isEmpty()) return copy(current = 0)
        return copy(current = history.last(), history = history.dropLast(1))
    }
}
