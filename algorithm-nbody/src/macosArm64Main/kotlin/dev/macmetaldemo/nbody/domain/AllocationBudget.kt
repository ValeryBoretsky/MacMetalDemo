package dev.macmetaldemo.nbody.domain

class AllocationBudget(lengths: List<Long>) {
    val numericBytes: Long
    init {
        var total = 0L
        for (length in lengths) {
            val bytes = arrayBytes(length, 4)
            require(total <= Long.MAX_VALUE - bytes)
            total += bytes
        }
        numericBytes = total
    }
    companion object {
        fun arrayBytes(length: Long, elementBytes: Long): Long {
            require(length in 0..Int.MAX_VALUE.toLong()) { "Array length exceeds the limit." }
            return checkedProduct(length, elementBytes)
        }
        fun forRun(config: NBodyConfig): AllocationBudget {
            val size = checkedProduct(config.particleCount.toLong(), 4)
            val warm = 128L * 4
            return AllocationBudget(listOf(size, size, size, size, size, size, size, size, warm, warm, warm, warm, warm, warm))
        }
    }
}
