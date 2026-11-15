package dev.macmetaldemo.nbody.domain

const val GRAVITY = 1.0f
const val TIME_STEP = 0.001f
const val SOFTENING = 0.01f
const val INITIAL_SEED = 42u

data class NBodyConfig(val particleCount: Int, val stepCount: Int, val seed: UInt = INITIAL_SEED) {
    init { require(particleCount in 2..262144 && stepCount in 1..1000000) }
    fun workCount(): Long = checkedProduct(checkedProduct(particleCount.toLong(), particleCount.toLong() - 1), stepCount.toLong())
}
fun checkedProduct(left: Long, right: Long): Long {
    require(left >= 0 && right >= 0)
    require(right == 0L || left <= Long.MAX_VALUE / right) { "Numeric product exceeds the limit." }
    return left * right
}
