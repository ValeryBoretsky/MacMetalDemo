package dev.macmetaldemo.rain

import dev.macmetaldemo.domain.CheckedSummary
import dev.macmetaldemo.domain.ResultFieldDefinition
import dev.macmetaldemo.domain.ResultFieldValue

internal val rainResultFields = listOf(
    ResultFieldDefinition("injectedMass", "Injected mass", null),
    ResultFieldDefinition("drainedMass", "Drained mass", null),
    ResultFieldDefinition("storedMass", "Stored mass", null),
    ResultFieldDefinition("rejectedMass", "Rejected rain", null),
    ResultFieldDefinition("fieldHash", "Field hash", null),
)

internal fun summarizeRain(config: RainConfig, walls: FloatArray, mass: FloatArray,
                           admitted: IntArray, drained: IntArray): CheckedSummary {
    check(walls.size == config.cellCount && mass.size == config.cellCount)
    check(admitted.size == config.width && drained.size == config.width)
    var stored = 0L
    var hash = 0xcbf29ce484222325uL
    for (i in mass.indices) {
        val wall = exactCount(walls[i], 3)
        val water = exactCount(mass[i], RainConfig.CELL_CAPACITY)
        check(wall == 0 || water == 0) { "Water entered a wall." }
        stored += water
        hash = (hash xor wall.toULong()) * 0x100000001b3uL
        hash = (hash xor (water and 255).toULong()) * 0x100000001b3uL
        hash = (hash xor (water ushr 8).toULong()) * 0x100000001b3uL
    }
    val columnLimit = RainConfig.CELL_CAPACITY * config.ticks
    val injected = sumCounters(admitted, columnLimit)
    val removed = sumCounters(drained, columnLimit)
    val requested = config.dropsPerTick.toLong() * config.rainMass * config.ticks
    check(injected <= requested) { "Injected mass exceeds the requested rain." }
    check(injected == removed + stored) { "Injected mass differs from drained mass plus stored mass." }
    val values = listOf(injected.toString(), removed.toString(), stored.toString(),
        (requested - injected).toString(), hash.toString(16).padStart(16, '0'))
    return CheckedSummary(rainResultFields.zip(values) { field, value -> ResultFieldValue(field.id, value) })
}

private fun exactCount(value: Float, maximum: Int): Int {
    val units = value.toInt()
    check(units in 0..maximum && units.toFloat().toRawBits() == value.toRawBits()) {
        "A water field or counter contains an invalid value."
    }
    return units
}

private fun sumCounters(values: IntArray, columnLimit: Int): Long = values.sumOf { units ->
    check(units in 0..columnLimit) { "A water counter exceeds its column limit." }
    units.toLong()
}
