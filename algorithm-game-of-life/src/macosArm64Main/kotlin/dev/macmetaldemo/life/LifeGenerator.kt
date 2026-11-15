package dev.macmetaldemo.life

internal data class CellPosition(val x: Int, val y: Int)

internal fun generateBoard(config: LifeConfig): FloatArray {
    val board = FloatArray(config.cellCount)
    val random = LifeRandom(config.seed)
    var needed = config.initialLiveCells
    val origin = gliderOrigin(config)
    if (origin != null) {
        plant(board, config.width, origin, GLIDER)
        needed -= 5
    }

    val tileColumns = config.width / 8
    val tileRows = config.height / 8
    val tileCount = checkedLifeProduct(tileColumns.toLong(), tileRows.toLong()).toInt()
    if (needed >= 5 && tileCount > 0) {
        val centralTile = (tileRows / 2) * tileColumns + tileColumns / 2
        val tiles = IntArray(tileCount - 1)
        var entry = 0
        for (tile in 0 until tileCount) {
            if (tile == centralTile) continue
            tiles[entry++] = tile
        }
        var index = 0
        while (needed >= 5 && index < tiles.size) {
            val selected = index + random.draw(tiles.size - index)
            val tile = tiles[selected]
            tiles[selected] = tiles[index]
            tiles[index] = tile
            val position = CellPosition((tile % tileColumns) * 8 + 2, (tile / tileColumns) * 8 + 2)
            plant(board, config.width, position, PATTERN_CYCLE[index % PATTERN_CYCLE.size])
            needed -= 5
            index++
        }
    }

    // This sample selects the exact remaining population without repeated cell searches.
    var empty = config.cellCount - (config.initialLiveCells - needed)
    for (index in board.indices) {
        if (needed == 0) break
        if (board[index] == 1f) continue
        if (random.draw(empty) < needed) {
            board[index] = 1f
            needed--
        }
        empty--
    }
    return board
}

internal fun gliderOrigin(config: LifeConfig): CellPosition? {
    if (config.initialLiveCells < 5) return null
    val tileColumns = config.width / 8
    val tileRows = config.height / 8
    if (tileColumns > 0 && tileRows > 0) {
        return CellPosition((tileColumns / 2) * 8 + 2, (tileRows / 2) * 8 + 2)
    }
    if (config.width >= 5 && config.height >= 5) return CellPosition(0, 1)
    return null
}

private fun plant(board: FloatArray, width: Int, origin: CellPosition, pattern: IntArray) {
    for (index in pattern) {
        board[(origin.y + index / 3) * width + origin.x + index % 3] = 1f
    }
}

private class LifeRandom(private var state: UInt) {
    fun draw(bound: Int): Int {
        require(bound > 0) { "The random bound must be positive." }
        state = 1664525u * state + 1013904223u
        // The 64-bit product preserves all unsigned state bits before division by 2^32.
        return ((state.toULong() * bound.toULong()) shr 32).toInt()
    }
}

private val GLIDER = intArrayOf(1, 5, 6, 7, 8)
private val BOAT = intArrayOf(0, 1, 3, 5, 7)
private val R_PENTOMINO = intArrayOf(1, 2, 3, 4, 7)
private val PATTERN_CYCLE = arrayOf(BOAT, R_PENTOMINO, GLIDER)
