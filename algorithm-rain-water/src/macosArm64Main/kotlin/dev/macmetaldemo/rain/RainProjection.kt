package dev.macmetaldemo.rain

import dev.macmetaldemo.domain.VisualizationFrame
import dev.macmetaldemo.domain.VisualizationFrame.Companion.HEIGHT
import dev.macmetaldemo.domain.VisualizationFrame.Companion.WIDTH

internal fun projectRain(config: RainConfig, walls: FloatArray, mass: FloatArray, completed: Long): VisualizationFrame {
    // Keep enough dots on the short axis to separate the three planks on a long, narrow field.
    val columns = (config.width * HEIGHT / config.height).coerceIn(12, WIDTH)
    val rows = (config.height * WIDTH / config.width).coerceIn(12, HEIGHT)
    val left = (WIDTH - columns) / 2
    val top = (HEIGHT - rows) / 2
    val dots = BooleanArray(WIDTH * HEIGHT)
    val wet = BooleanArray(dots.size)
    val columnMass = IntArray((config.width + columns - 1) / columns)
    for (row in 0 until rows) {
        val firstY = row * config.height / rows
        val endY = maxOf(firstY + 1, (row + 1) * config.height / rows)
        for (column in 0 until columns) {
            val firstX = column * config.width / columns
            val endX = maxOf(firstX + 1, (column + 1) * config.width / columns)
            val index = (top + row) * WIDTH + left + column
            dots[index] = projectDot(config.width, walls, mass, firstX, endX, firstY, endY, columnMass, wet, index)
        }
    }
    // Keep isolated drops visible even when one dot covers hundreds of field cells.
    for (index in wet.indices) {
        if (wet[index] && !dots[index] && isIsolated(wet, index % WIDTH, index / WIDTH)) dots[index] = true
    }
    return VisualizationFrame(completed, dots)
}

private fun isIsolated(wet: BooleanArray, x: Int, y: Int): Boolean {
    var nearby = 0
    for (row in maxOf(0, y - 1)..minOf(HEIGHT - 1, y + 1)) {
        for (column in maxOf(0, x - 1)..minOf(WIDTH - 1, x + 1)) {
            if (wet[row * WIDTH + column] && ++nearby > 2) return false
        }
    }
    return true
}

private fun projectDot(
    width: Int, walls: FloatArray, mass: FloatArray, firstX: Int, endX: Int, firstY: Int, endY: Int,
    columnMass: IntArray, wet: BooleanArray, dotIndex: Int,
): Boolean {
    var total = 0
    var heaviestRow = 0
    columnMass.fill(0)
    for (y in firstY until endY) {
        var rowMass = 0
        for (x in firstX until endX) {
            val index = y * width + x
            // Even one wall cell must remain visible after the field shrinks to terminal size.
            if (walls[index] != 0f) return true
            val water = mass[index].toInt()
            rowMass += water
            columnMass[x - firstX] += water
        }
        total += rowMass
        heaviestRow = maxOf(heaviestRow, rowMass)
    }
    if (total == 0) return false
    wet[dotIndex] = true

    val rows = endY - firstY
    val columns = endX - firstX
    // Combine the region mean with one fixed sample to show local changes in falling rain.
    val sample = mass[(firstY + rows / 2) * width + firstX + columns / 2]
    val rain = (total.toFloat() / (columns * rows) + sample) / 2
    // A thin stream can fill one row or column while the rest of the region stays dry.
    val runoff = maxOf(heaviestRow.toFloat() / columns, columnMass.max().toFloat() / rows) / 2
    // Keep gaps in deep water so that a solid plank remains distinct.
    val density = maxOf(rain, runoff)
    val shade = when {
        density <= 8f -> density
        density <= 16f -> 8f + (density - 8f) * 0.75f
        else -> 14f + (density - 16f) / (RainConfig.CELL_CAPACITY - 16) * 5f
    }
    val threshold = (mixRain(dotIndex.toUInt()) % (24u * 256u)).toFloat() / 256
    return shade > threshold
}
