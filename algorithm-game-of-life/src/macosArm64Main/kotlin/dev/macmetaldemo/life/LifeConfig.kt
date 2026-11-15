package dev.macmetaldemo.life

internal data class LifeConfig(val width: Int, val height: Int, val initialLiveCells: Int, val generations: Int, val seed: UInt) {
    val cellCount: Int
    val workCount: Long

    init {
        require(width in 3..4096) { "Board width must be from 3 to 4096." }
        require(height in 3..4096) { "Board height must be from 3 to 4096." }
        require(generations in 1..1000000) { "Generation count must be from 1 to 1000000." }
        val capacity = checkedLifeProduct(width.toLong(), height.toLong())
        require(capacity <= Int.MAX_VALUE) { "The board exceeds the array size limit." }
        cellCount = capacity.toInt()
        require(initialLiveCells in 0..cellCount) { "The live cell count must be from 0 to $cellCount." }
        workCount = checkedLifeProduct(capacity, generations.toLong())
    }

    companion object {
        val WARM_UP = LifeConfig(16, 16, 50, 1, 42u)
    }
}

internal fun checkedLifeProduct(left: Long, right: Long): Long {
    require(left >= 0 && right >= 0) { "Numeric factors must not be negative." }
    require(right == 0L || left <= Long.MAX_VALUE / right) { "Numeric product exceeds the limit." }
    return left * right
}
