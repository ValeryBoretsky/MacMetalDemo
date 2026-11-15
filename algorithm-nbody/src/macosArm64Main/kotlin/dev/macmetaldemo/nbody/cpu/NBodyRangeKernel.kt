package dev.macmetaldemo.nbody.cpu

import dev.macmetaldemo.nbody.domain.*
import kotlin.math.sqrt

fun calculateRange(config: NBodyConfig, old: ParticleState, next: ParticleState, start: Int, endExclusive: Int) {
    require(start in 0..endExclusive && endExclusive <= config.particleCount)
    require(old !== next && old.positionMass.size == config.particleCount * 4 && next.positionMass.size == old.positionMass.size)
    for (i in start until endExclusive) {
        val p = i * 4
        val x = old.positionMass[p]
        val y = old.positionMass[p + 1]
        val z = old.positionMass[p + 2]
        var ax = 0f
        var ay = 0f
        var az = 0f
        for (j in 0 until config.particleCount) {
            if (j == i) continue
            val q = j * 4
            val dx = old.positionMass[q] - x
            val dy = old.positionMass[q + 1] - y
            val dz = old.positionMass[q + 2] - z
            // Softening prevents a singular force when positions coincide.
            val distance = dx * dx + dy * dy + dz * dz + SOFTENING * SOFTENING
            val factor = GRAVITY * old.positionMass[q + 3] / (distance * sqrt(distance))
            ax += factor * dx
            ay += factor * dy
            az += factor * dz
        }
        // Semi-implicit Euler updates velocity before it updates position.
        val vx = old.velocity[p] + ax * TIME_STEP
        val vy = old.velocity[p + 1] + ay * TIME_STEP
        val vz = old.velocity[p + 2] + az * TIME_STEP
        next.velocity[p] = vx
        next.velocity[p + 1] = vy
        next.velocity[p + 2] = vz
        next.velocity[p + 3] = 0f
        next.positionMass[p] = x + vx * TIME_STEP
        next.positionMass[p + 1] = y + vy * TIME_STEP
        next.positionMass[p + 2] = z + vz * TIME_STEP
        next.positionMass[p + 3] = old.positionMass[p + 3]
    }
}
