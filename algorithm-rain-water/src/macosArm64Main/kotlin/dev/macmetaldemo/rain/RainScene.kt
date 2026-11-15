package dev.macmetaldemo.rain

import kotlin.math.abs

internal class RainScene(val walls: FloatArray)

internal data class RainPlank(val highX: Int, val lowX: Int, val highY: Int, val drop: Int) {
    val direction = if (lowX > highX) 1 else -1
    val length = abs(lowX - highX)

    fun surfaceY(x: Int) = highY + abs(x - highX) * drop / length
}

internal fun mixRain(value: UInt): UInt {
    var mixed = (value xor (value shr 16)) * 0x7feb352du
    mixed = (mixed xor (mixed shr 15)) * 0x846ca68bu
    return mixed xor (mixed shr 16)
}

internal fun rainPlanks(config: RainConfig): List<RainPlank> {
    val width = config.width
    val height = config.height
    fun choice(stream: Int, count: Int) =
        (mixRain(config.seed xor (stream.toUInt() * 0x85ebca6bu)) % maxOf(1, count).toUInt()).toInt()

    return List(3) { tier ->
        val stream = 4 * tier + 1
        val start = 1 + choice(stream, width / 12)
        val end = 11 * width / 16 + choice(stream + 1, width / 16)
        val highY = (2 * tier + 1) * height / 8 + choice(stream + 2, height / 32)
        val drop = height / 8 + choice(stream + 3, height / 24)
        if (tier % 2 == 0) RainPlank(start, end, highY, drop)
        else RainPlank(width - 1 - start, width - 1 - end, highY, drop)
    }
}

internal fun generateScene(config: RainConfig): RainScene {
    val width = config.width
    val walls = FloatArray(config.cellCount)
    for (y in 0 until config.height) {
        walls[y * width] = 1f
        walls[y * width + width - 1] = 1f
    }
    val thickness = maxOf(1, config.height / 128)
    for (plank in rainPlanks(config)) {
        val surfaceCode = if (plank.direction > 0) 2f else 3f
        for (distance in 0..plank.length) {
            val x = plank.highX + distance * plank.direction
            val surface = plank.surfaceY(x)
            val nextX = if (x == plank.lowX) x else x + plank.direction
            // Join adjacent steps so water cannot pass through diagonal gaps.
            val bottom = plank.surfaceY(nextX) + thickness - 1
            for (y in surface..bottom) walls[y * width + x] = surfaceCode
        }
    }
    return RainScene(walls)
}

internal fun fillRainCounts(config: RainConfig, tick: Int, counts: FloatArray) {
    require(counts.size == config.width)
    counts.fill(0f)
    val inletWidth = (config.width - 2).toUInt()
    val weather = config.seed xor (tick.toUInt() * 0x9e3779b9u)
    for (drop in 1..config.dropsPerTick) {
        val sample = mixRain(weather xor (drop.toUInt() * 0x85ebca6bu))
        val x = 1 + (sample % inletWidth).toInt()
        counts[x] += 1f
    }
}
