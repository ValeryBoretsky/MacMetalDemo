package dev.macmetaldemo.nbody.cpu

import dev.macmetaldemo.application.*
import dev.macmetaldemo.domain.*
import dev.macmetaldemo.nbody.*
import dev.macmetaldemo.nbody.domain.*

class NBodyCpuRun internal constructor(
    private val config: NBodyConfig, runtime: CpuRuntime,
    private val copyState: (ParticleState) -> ParticleState,
) : NBodyPreparedRun {
    constructor(config: NBodyConfig, runtime: CpuRuntime) : this(config, runtime, { it.copy() })

    private var runtime: CpuRuntime? = runtime
    private var current: ParticleState? = null
    private var next: ParticleState? = null
    private var visualization: VisualizationPort = NoVisualization
    private val sampleIndices by lazy { sampleIndices(config.particleCount) }
    override fun attachVisualization(port: VisualizationPort) { visualization = port }
    private var executed = false
    private var checked = false
    override val backend: BackendInfo
    init {
        try {
            backend = BackendInfo(runtime.workerCount, null)
            AllocationBudget.forRun(config)
            current = generateInitialState(config)
            next = ParticleState(FloatArray(config.particleCount * 4), FloatArray(config.particleCount * 4))
        } catch (error: Throwable) {
            try { close() } catch (cleanup: Throwable) { error.addSuppressed(cleanup) }
            throw error
        }
    }
    private inline fun <T> guarded(action: () -> T): T {
        try { return action() } catch (error: Throwable) {
            try { close() } catch (cleanup: Throwable) { error.addSuppressed(cleanup) }
            throw error
        }
    }
    override fun warmUp() = guarded {
        val active = checkNotNull(runtime)
        val warmConfig = NBodyConfig(128, 1)
        val old = generateInitialState(warmConfig)
        val result = ParticleState(FloatArray(512), FloatArray(512))
        active.forEachRange(128) { start, end -> calculateRange(warmConfig, old, result, start, end) }
    }
    override fun execute(progress: ProgressSink, gpu: GpuStatusSink) = guarded {
        val active = checkNotNull(runtime)
        check(!executed)
        for (step in 1..config.stepCount) {
            val old = checkNotNull(current)
            val output = checkNotNull(next)
            active.forEachRange(config.particleCount) { start, end -> calculateRange(config, old, output, start, end) }
            current = output
            next = old
            val captureSucceeded = captureFrame(visualization) {
                val positions = FloatArray(sampleIndices.size * 4)
                for (sample in sampleIndices.indices) {
                    output.positionMass.copyInto(positions, sample * 4, sampleIndices[sample] * 4, sampleIndices[sample] * 4 + 4)
                }
                projectSamples(positions, step.toLong())
            }
            if (!captureSucceeded) visualization = NoVisualization
            progress.publish(RunProgress(step.toLong(), config.stepCount.toLong(), checkedProduct(checkedProduct(config.particleCount.toLong(), config.particleCount.toLong() - 1), step.toLong())))
        }
        executed = true
    }
    override fun checkAndSummarize(): CheckedSummary = guarded {
        check(executed && runtime != null)
        val result = summarize(checkNotNull(current))
        checked = true
        result
    }
    override fun finalStateCopy(): ParticleState {
        check(checked && runtime != null)
        return guarded { copyState(checkNotNull(current)) }
    }
    override fun close() {
        visualization = NoVisualization
        val active = runtime
        runtime = null
        current = null
        next = null
        active?.close()
    }
}
