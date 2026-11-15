package dev.macmetaldemo.rain

internal fun updateRainRange(
    config: RainConfig, walls: FloatArray, old: FloatArray, next: FloatArray,
    admitted: IntArray, drained: IntArray, counts: FloatArray, tick: Int, phase: RainPhase,
    start: Int, end: Int,
) {
    when (phase) {
        RainPhase.ADMIT_RAIN -> admitRain(config, walls, old, next, counts, admitted, start, end)
        RainPhase.GRAVITY -> applyGravity(config, walls, old, next, start, end)
        RainPhase.RUNOFF -> applyRunoff(config, walls, old, next, tick, start, end)
        RainPhase.DRAIN -> drainBottom(config.cellCount - config.width, old, next, drained, start, end)
    }
}

private fun admitRain(
    config: RainConfig, walls: FloatArray, old: FloatArray, next: FloatArray,
    counts: FloatArray, admitted: IntArray, start: Int, end: Int,
) {
    old.copyInto(next, start, start, end)
    for (column in start until minOf(end, config.width)) {
        if (walls[column] != 0f) continue
        val mass = old[column].toInt()
        val requested = counts[column].toInt() * config.rainMass
        val accepted = minOf(requested, RainConfig.CELL_CAPACITY - mass)
        next[column] = (mass + accepted).toFloat()
        admitted[column] += accepted
    }
}

private fun applyGravity(config: RainConfig, walls: FloatArray, old: FloatArray, next: FloatArray, start: Int, end: Int) {
    val width = config.width
    val bottom = config.cellCount - width
    for (i in start until end) {
        if (walls[i] != 0f) {
            next[i] = 0f
            continue
        }
        val mass = old[i].toInt()
        // Both cells read the same old masses to calculate the transfer across their shared face.
        val incoming = if (i >= width && walls[i - width] == 0f) {
            minOf(RainConfig.CELL_CAPACITY - mass, old[i - width].toInt())
        } else 0
        val outgoing = if (i < bottom && walls[i + width] == 0f) {
            minOf(mass, RainConfig.CELL_CAPACITY - old[i + width].toInt())
        } else 0
        next[i] = (mass + incoming - outgoing).toFloat()
    }
}

private fun applyRunoff(
    config: RainConfig, walls: FloatArray, old: FloatArray, next: FloatArray, tick: Int, start: Int, end: Int,
) {
    val width = config.width
    val bottom = config.cellCount - width
    for (i in start until end) {
        val x = i % width
        val offset = if ((x and 1) == (tick and 1)) 1 else -1
        val other = i + offset
        next[i] = old[i]
        if (i >= bottom || x + offset !in 0 until width || walls[i] != 0f || walls[other] != 0f) continue
        val left = minOf(i, other)
        val transfer = horizontalTransfer(old, walls, left, left + 1, width)
        val mass = old[i].toInt()
        next[i] = (if (i == left) mass - transfer else mass + transfer).toFloat()
    }
}

private fun drainBottom(bottom: Int, old: FloatArray, next: FloatArray, drained: IntArray, start: Int, end: Int) {
    old.copyInto(next, start, start, end)
    for (i in maxOf(start, bottom) until end) {
        drained[i - bottom] += old[i].toInt()
        next[i] = 0f
    }
}

// A positive transfer moves water from the left cell to the right cell.
private fun horizontalTransfer(old: FloatArray, walls: FloatArray, left: Int, right: Int, width: Int): Int {
    val a = old[left].toInt()
    val b = old[right].toInt()
    val leftSupport = walls[left + width]
    val rightSupport = walls[right + width]
    if (leftSupport >= 2f || rightSupport >= 2f) {
        val toRight = leftSupport == 2f
        val toLeft = rightSupport == 3f
        if (toRight == toLeft) return 0
        return if (toRight) minOf(a, RainConfig.CELL_CAPACITY - b) else -minOf(b, RainConfig.CELL_CAPACITY - a)
    }
    val donorBase = (if (a > b) left else right) + width
    if (walls[donorBase] == 0f && old[donorBase] != RainConfig.CELL_CAPACITY.toFloat()) return 0
    return (a - b) / 2
}
