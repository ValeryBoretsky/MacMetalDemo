package dev.macmetaldemo.rain

internal data class RainConfig(
    val width: Int,
    val height: Int,
    val rainMass: Int,
    val ticks: Int,
    val seed: UInt,
    val dropsPerTick: Int,
) {
    val cellCount: Int
    val workCount: Long

    init {
        require(width in WIDTH_RANGE && height in HEIGHT_RANGE) { "Invalid water field dimensions." }
        require(rainMass in DROP_MASS_RANGE && ticks in TICK_RANGE) { "Invalid rain mass or tick count." }
        require(dropsPerTick in DROP_COUNT_RANGE) {
            "Enter ${DROP_COUNT_RANGE.first} to ${DROP_COUNT_RANGE.last} drops per tick."
        }
        val cells = width.toLong() * height
        require(cells <= Int.MAX_VALUE && cells <= Long.MAX_VALUE / ticks)
        cellCount = cells.toInt()
        workCount = cells * ticks
    }

    companion object {
        const val CELL_CAPACITY = 256
        val WIDTH_RANGE = 32L..2048L
        val HEIGHT_RANGE = 24L..2048L
        val DROP_COUNT_RANGE = 1L..16_384L
        val DROP_MASS_RANGE = 1L..16L
        val TICK_RANGE = 1L..1_000_000L

        val WARM_UP = RainConfig(width = 32, height = 24, rainMass = 1, ticks = 4, seed = 42u, dropsPerTick = 64)
    }
}
