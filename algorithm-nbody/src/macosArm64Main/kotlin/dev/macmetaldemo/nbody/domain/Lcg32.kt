package dev.macmetaldemo.nbody.domain

class Lcg32(seed: UInt = INITIAL_SEED) {
    private var state = seed
    fun nextUInt(): UInt { state = 1664525u * state + 1013904223u; return state }
    fun nextCoordinate(): Float = 2f * (nextUInt() shr 8).toFloat() / 16777216f - 1f
}
