package dev.macmetaldemo.nbody.domain

// Each positionMass entry stores x, y, z, and mass.
// Each velocity entry stores x, y, z, and zero padding.
class ParticleState(val positionMass: FloatArray, val velocity: FloatArray) {
    init { require(positionMass.size == velocity.size && positionMass.size % 4 == 0) }
    fun copy(): ParticleState = ParticleState(positionMass.copyOf(), velocity.copyOf())
}
fun generateInitialState(config: NBodyConfig): ParticleState {
    val length = checkedProduct(config.particleCount.toLong(), 4)
    AllocationBudget.arrayBytes(length, 4)
    val state = ParticleState(FloatArray(length.toInt()), FloatArray(length.toInt()))
    val generator = Lcg32(config.seed)
    for (i in 0 until config.particleCount) {
        val offset = i * 4
        for (axis in 0..2) state.positionMass[offset + axis] = generator.nextCoordinate()
        state.positionMass[offset + 3] = 1f / config.particleCount
    }
    return state
}
