package dev.macmetaldemo.rain

import dev.macmetaldemo.application.GpuStatusSink
import dev.macmetaldemo.domain.BackendInfo

internal interface RainBackend : AutoCloseable {
    val info: BackendInfo
    fun open(config: RainConfig, scene: RainScene): RainState
}

internal interface RainState : AutoCloseable {
    fun advance(tick: Int, counts: FloatArray, gpu: GpuStatusSink)
    fun readMass(): FloatArray
    fun readAdmitted(): IntArray
    fun readDrained(): IntArray
}
