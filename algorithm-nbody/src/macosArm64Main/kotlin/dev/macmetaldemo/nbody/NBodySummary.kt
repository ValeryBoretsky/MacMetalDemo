package dev.macmetaldemo.nbody

import dev.macmetaldemo.domain.*
import dev.macmetaldemo.nbody.domain.ParticleState

val summaryIds = listOf("x", "y", "z", "vx", "vy", "vz")
fun summarize(state: ParticleState): CheckedSummary {
    val sums = DoubleArray(6)
    for (i in state.positionMass.indices step 4) {
        check(state.positionMass[i + 3].isFinite() && state.velocity[i + 3].isFinite()) {
            "The result contains a nonfinite value."
        }
        for (axis in 0..2) {
            val position = state.positionMass[i + axis]
            val velocity = state.velocity[i + axis]
            check(position.isFinite() && velocity.isFinite()) { "The result contains a nonfinite value." }
            sums[axis] += position.toDouble()
            sums[axis + 3] += velocity.toDouble()
        }
    }
    return CheckedSummary(summaryIds.mapIndexed { index, id -> ResultFieldValue(id, sums[index].toString()) })
}
