package io.github.paulneja.archdroid.ui.terminal

object TerminalTabState {
    fun afterRemoval(size: Int, activeIndex: Int, removedIndex: Int): Int {
        require(size > 0) { "size must be positive" }
        require(activeIndex in 0 until size) { "active index is outside the tab list" }
        require(removedIndex in 0 until size) { "removed index is outside the tab list" }
        if (size == 1) return -1
        return when {
            removedIndex < activeIndex -> activeIndex - 1
            removedIndex > activeIndex -> activeIndex
            else -> removedIndex.coerceAtMost(size - 2)
        }
    }
}
