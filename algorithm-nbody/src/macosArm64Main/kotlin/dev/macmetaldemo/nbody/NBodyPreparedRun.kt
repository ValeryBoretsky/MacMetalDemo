package dev.macmetaldemo.nbody

import dev.macmetaldemo.application.PreparedRun
import dev.macmetaldemo.nbody.domain.ParticleState

interface NBodyPreparedRun : PreparedRun {
    fun finalStateCopy(): ParticleState
}
