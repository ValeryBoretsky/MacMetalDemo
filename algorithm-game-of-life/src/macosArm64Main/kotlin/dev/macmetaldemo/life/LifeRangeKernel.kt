package dev.macmetaldemo.life

internal fun updateRange(old: FloatArray, next: FloatArray, width: Int, height: Int, start: Int, endExclusive: Int) {
    // Each task reads the old board and writes only its assigned output cells.
    for (index in start until endExclusive) {
        val x = index % width
        val y = index / width
        val left = if (x == 0) width - 1 else x - 1
        val right = if (x == width - 1) 0 else x + 1
        val above = (if (y == 0) height - 1 else y - 1) * width
        val row = y * width
        val below = (if (y == height - 1) 0 else y + 1) * width
        val neighbors = old[above + left].toInt() + old[above + x].toInt() + old[above + right].toInt() +
            old[row + left].toInt() + old[row + right].toInt() +
            old[below + left].toInt() + old[below + x].toInt() + old[below + right].toInt()
        next[index] = if (neighbors == 3 || (neighbors == 2 && old[index] == 1f)) 1f else 0f
    }
}
