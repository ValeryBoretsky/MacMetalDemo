package dev.macmetaldemo.nbody.metal

import dev.macmetaldemo.application.*
import dev.macmetaldemo.domain.*
import dev.macmetaldemo.nbody.*
import dev.macmetaldemo.nbody.domain.*

class NBodyMetalRun(private val config: NBodyConfig, program: MetalProgram) : NBodyPreparedRun {
    private var program: MetalProgram? = program
    private val buffers = mutableListOf<MetalBuffer>()
    private var current = 0
    private var visualization: VisualizationPort = NoVisualization
    private val sampleIndices by lazy { sampleIndices(config.particleCount) }
    override fun attachVisualization(port: VisualizationPort) { visualization = port }
    private var executed = false
    private var checkedState: ParticleState? = null
    override val backend: BackendInfo
    init {
        try {
            backend = BackendInfo(null, program.deviceName, program.deviceRegistryId)
            AllocationBudget.forRun(config)
            val initial = generateInitialState(config)
            repeat(4) { buffers.add(program.createBuffer(config.particleCount.toLong() * 16)) }
            buffers[0].write(initial.positionMass)
            buffers[1].write(initial.velocity)
        } catch (error: Throwable) { fail(error) }
    }
    private fun fail(error: Throwable): Nothing {
        try { close() } catch (cleanup: Throwable) { error.addSuppressed(cleanup) }
        throw error
    }
    private inline fun <T> guarded(action: () -> T): T = try { action() } catch (error: Throwable) { fail(error) }
    override fun warmUp() = guarded {
        val active = checkNotNull(program)
        val warm = mutableListOf<MetalBuffer>()
        var failure: Throwable? = null
        try {
            repeat(4) { warm.add(active.createBuffer(128L * 16)) }
            val initial = generateInitialState(NBodyConfig(128, 1))
            warm[0].write(initial.positionMass)
            warm[1].write(initial.velocity)
            active.dispatch(warm, nbodyUniforms(128), 128, GpuStatusSink { })
        } catch (error: Throwable) {
            failure = error
        }
        failure = closeBuffers(warm, failure)
        failure?.let { throw it }
        Unit
    }
    override fun execute(progress: ProgressSink, gpu: GpuStatusSink) = guarded {
        val active = checkNotNull(program)
        check(!executed)
        for (step in 1..config.stepCount) {
            val next = 2 - current
            active.dispatch(listOf(buffers[current], buffers[current + 1], buffers[next], buffers[next + 1]), nbodyUniforms(config.particleCount), config.particleCount, gpu)
            current = next
            val captureSucceeded = captureFrame(visualization) {
                val positions = FloatArray(sampleIndices.size * 4)
                buffers[current].readRecords(sampleIndices, 4, positions)
                projectSamples(positions, step.toLong())
            }
            if (!captureSucceeded) visualization = NoVisualization
            progress.publish(RunProgress(step.toLong(), config.stepCount.toLong(), config.particleCount.toLong() * (config.particleCount - 1) * step))
        }
        executed = true
    }
    override fun checkAndSummarize(): CheckedSummary = guarded {
        check(executed && program != null)
        val state = ParticleState(FloatArray(config.particleCount * 4), FloatArray(config.particleCount * 4))
        buffers[current].readInto(state.positionMass)
        buffers[current + 1].readInto(state.velocity)
        val result = summarize(state)
        checkedState = state
        result
    }
    override fun finalStateCopy(): ParticleState = guarded { checkNotNull(checkedState).copy() }
    override fun close() {
        visualization = NoVisualization
        val active = program ?: return
        program = null
        checkedState = null
        var failure = closeBuffers(buffers, null)
        buffers.clear()
        try { active.close() } catch (error: Throwable) {
            if (failure == null) failure = error else failure.addSuppressed(error)
        }
        failure?.let { throw it }
    }
    private fun closeBuffers(owned: List<MetalBuffer>, initial: Throwable?): Throwable? {
        var failure = initial
        for (buffer in owned) {
            try { buffer.close() } catch (error: Throwable) {
                if (failure == null) failure = error else failure.addSuppressed(error)
            }
        }
        return failure
    }
}
