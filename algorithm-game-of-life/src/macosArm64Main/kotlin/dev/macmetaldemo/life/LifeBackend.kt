package dev.macmetaldemo.life

import dev.macmetaldemo.application.*
import dev.macmetaldemo.domain.BackendInfo

internal interface LifeBackend : AutoCloseable {
    val info: BackendInfo
    fun openBoards(config: LifeConfig): GenerationBoards
}

internal interface GenerationBoards : AutoCloseable {
    fun advance(gpu: GpuStatusSink)
    fun completedBoard(): FloatArray
}

internal class CpuLifeBackend(private val runtime: CpuRuntime) : LifeBackend {
    override val info get() = BackendInfo(runtime.workerCount, null)

    override fun openBoards(config: LifeConfig): GenerationBoards = object : GenerationBoards {
        private var current = generateBoard(config)
        private var next = FloatArray(config.cellCount)

        override fun advance(gpu: GpuStatusSink) {
            val old = current
            val output = next
            runtime.forEachRange(config.cellCount) { start, end ->
                updateRange(old, output, config.width, config.height, start, end)
            }
            // The runtime waits for every range before the boards exchange roles.
            current = output
            next = old
        }

        override fun completedBoard() = current
        override fun close() = Unit
    }

    override fun close() = runtime.close()
}
