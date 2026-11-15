package dev.macmetaldemo.life

import dev.macmetaldemo.domain.VisualizationFrame
import kotlin.math.floor

internal fun projectBoard(config: LifeConfig, board: FloatArray, generation: Long): VisualizationFrame {
    val dots = BooleanArray(VisualizationFrame.WIDTH * VisualizationFrame.HEIGHT)
    if (fitsFullBoard(config.width, config.height)) {
        projectFullBoard(config, board, dots)
    } else {
        projectOverview(config, board, dots)
        projectDetail(config, board, generation, dots)
    }
    return VisualizationFrame(generation, dots)
}

internal fun boardTitle(width: Int, height: Int): String =
    if (fitsFullBoard(width, height)) "Full board" else "Full board | Detail"

private fun fitsFullBoard(width: Int, height: Int): Boolean = width <= 20 && height <= 15

private fun projectFullBoard(config: LifeConfig, board: FloatArray, dots: BooleanArray) {
    val left = (40 - config.width * 2) / 2 * 2
    val top = (15 - config.height) / 2 * 4
    for (y in 0 until config.height) {
        for (x in 0 until config.width) {
            if (board[y * config.width + x] == 1f) fillCell(dots, left + x * 4, top + y * 4)
        }
    }
}

private fun projectDetail(config: LifeConfig, board: FloatArray, generation: Long, dots: BooleanArray) {
    val glider = gliderOrigin(config)
    val shift = if (glider == null) 0L else 2L * (generation / 8L)
    val originX = (glider?.x ?: (config.width / 2)).toLong() - 2L + shift
    val originY = (glider?.y ?: (config.height / 2)).toLong() - 2L + shift
    val columns = minOf(9, config.width)
    val rows = minOf(9, config.height)
    val left = 44 + (9 - columns) / 2 * 4
    val top = 12 + (9 - rows) / 2 * 4
    for (row in 0 until rows) {
        val boardY = wrap(originY + row, config.height)
        for (column in 0 until columns) {
            val boardX = wrap(originX + column, config.width)
            if (board[boardY * config.width + boardX] == 1f) fillCell(dots, left + column * 4, top + row * 4)
        }
    }
}

private fun wrap(coordinate: Long, length: Int): Int = ((coordinate % length + length) % length).toInt()

private fun fillCell(dots: BooleanArray, left: Int, top: Int) {
    for (row in top until top + 4) {
        dots[row * VisualizationFrame.WIDTH + left] = true
        dots[row * VisualizationFrame.WIDTH + left + 1] = true
    }
}

private fun projectOverview(config: LifeConfig, board: FloatArray, dots: BooleanArray) {
    val scaled = config.width > 40 || config.height > 60
    // Integer ratios keep the limiting axis exact when floating-point rounding would remove one dot.
    val (columns, rows) = when {
        !scaled -> config.width to config.height
        config.width * 60 >= config.height * 40 -> 40 to maxOf(1, config.height * 40 / config.width)
        else -> maxOf(1, config.width * 60 / config.height) to 60
    }
    val left = (40 - columns) / 2
    val top = (60 - rows) / 2
    val liveCounts = IntArray(40 * 60)
    val cellCounts = IntArray(40 * 60)
    for (row in 0 until rows) {
        val startY = row * config.height / rows
        val endY = (row + 1) * config.height / rows
        for (column in 0 until columns) {
            val startX = column * config.width / columns
            val endX = (column + 1) * config.width / columns
            val regionIndex = (top + row) * 40 + left + column
            var liveCells = 0
            for (y in startY until endY) {
                for (x in startX until endX) if (board[y * config.width + x] == 1f) liveCells++
            }
            liveCounts[regionIndex] = liveCells
            cellCounts[regionIndex] = (endX - startX) * (endY - startY)
            if (!scaled && liveCells != 0) {
                dots[(top + row) * VisualizationFrame.WIDTH + left + column] = true
            }
        }
    }
    if (scaled) projectDensity(liveCounts, cellCounts, dots)
}

private fun projectDensity(liveCounts: IntArray, cellCounts: IntArray, dots: BooleanArray) {
    for (top in 0 until 60 step 4) {
        for (left in 0 until 40 step 2) {
            val occupiedRegions = mutableListOf<Int>()
            var regions = 0
            var liveCells = 0
            var totalCells = 0
            for (row in top until top + 4) {
                for (column in left until left + 2) {
                    val index = row * 40 + column
                    if (cellCounts[index] == 0) continue
                    regions++
                    liveCells += liveCounts[index]
                    totalCells += cellCounts[index]
                    if (liveCounts[index] != 0) occupiedRegions.add(index)
                }
            }
            if (liveCells == 0) continue
            val dotCount = maxOf(1, floor(0.5 + regions.toDouble() * liveCells / totalCells).toInt())
            // Cross products compare exact fractions. The index resolves ties in row order.
            occupiedRegions.sortWith(Comparator { first, second ->
                val firstFraction = liveCounts[first].toLong() * cellCounts[second]
                val secondFraction = liveCounts[second].toLong() * cellCounts[first]
                val densityOrder = secondFraction.compareTo(firstFraction)
                if (densityOrder == 0) first.compareTo(second) else densityOrder
            })
            for (region in occupiedRegions.take(dotCount)) {
                dots[(region / 40) * VisualizationFrame.WIDTH + region % 40] = true
            }
        }
    }
}
