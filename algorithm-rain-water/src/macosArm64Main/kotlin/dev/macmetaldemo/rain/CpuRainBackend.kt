package dev.macmetaldemo.rain

import dev.macmetaldemo.application.CpuRuntime
import dev.macmetaldemo.application.GpuStatusSink
import dev.macmetaldemo.domain.BackendInfo

internal class CpuRainBackend(private val runtime: CpuRuntime) : RainBackend {
    override val info = BackendInfo(runtime.workerCount, null)
    override fun open(config: RainConfig, scene: RainScene): RainState = object : RainState {
        private var current = FloatArray(config.cellCount)
        private var next = FloatArray(config.cellCount)
        private val admitted = IntArray(config.width)
        private val drained = IntArray(config.width)

        override fun advance(tick: Int, counts: FloatArray, gpu: GpuStatusSink) {
            for (phase in RainPhase.entries) {
                val input = current
                val output = next
                runtime.forEachRange(config.cellCount) { start, end ->
                    updateRainRange(config, scene.walls, input, output, admitted, drained, counts, tick, phase, start, end)
                }
                // Every range finishes before the next phase reads its output.
                current = output
                next = input
            }
        }

        override fun readMass() = current
        override fun readAdmitted() = admitted
        override fun readDrained() = drained
        override fun close() = Unit
    }

    override fun close() = runtime.close()
}
