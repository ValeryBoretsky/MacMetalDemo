package dev.macmetaldemo.rain

import dev.macmetaldemo.application.*
import dev.macmetaldemo.domain.*

interface RainPreparedRun : PreparedRun {
    fun finalStateCopy(): RainSnapshot
}

class RainSnapshot(val mass: FloatArray, val admitted: IntArray, val drained: IntArray)

internal class MeasuredRainRun(private val config: RainConfig, private val scene: RainScene,
                               private val engine: RainBackend) : RainPreparedRun {
    override val backend = engine.info
    private val rainCounts = FloatArray(config.width)
    private var state: RainState? = engine.open(config, scene)
    private var drawing: VisualizationPort = NoVisualization
    private var executed = false
    private var checked: RainSnapshot? = null

    override fun attachVisualization(port: VisualizationPort) { drawing = port }

    override fun warmUp() = guarded {
        checkNotNull(state)
        val warm = RainConfig.WARM_UP
        val warmScene = generateScene(warm)
        engine.open(warm, warmScene).use { sample ->
            val warmCounts = FloatArray(warm.width)
            for (tick in 0 until warm.ticks) {
                fillRainCounts(warm, tick, warmCounts)
                sample.advance(tick, warmCounts, GpuStatusSink { })
            }
        }
    }

    override fun execute(progress: ProgressSink, gpu: GpuStatusSink) = guarded {
        val measured = checkNotNull(state)
        check(!executed)
        for (tick in 0 until config.ticks) {
            fillRainCounts(config, tick, rainCounts)
            measured.advance(tick, rainCounts, gpu)
            val completed = tick + 1L
            capture(measured, completed)
            progress.publish(RunProgress(completed, config.ticks.toLong(), completed * config.cellCount))
        }
        executed = true
    }

    private fun capture(measured: RainState, completed: Long) {
        if (drawing === NoVisualization) return
        try {
            if (drawing.captureRequested()) drawing.publish(projectRain(config, scene.walls, measured.readMass(), completed))
        } catch (_: Throwable) {
            val failed = drawing
            drawing = NoVisualization
            try { failed.disable() } catch (_: Throwable) { }
        }
    }

    override fun checkAndSummarize(): CheckedSummary = guarded {
        check(executed)
        val measured = checkNotNull(state)
        val snapshot = RainSnapshot(measured.readMass(), measured.readAdmitted(), measured.readDrained())
        val summary = summarizeRain(config, scene.walls, snapshot.mass, snapshot.admitted, snapshot.drained)
        checked = snapshot
        summary
    }

    override fun finalStateCopy(): RainSnapshot {
        val final = checkNotNull(checked)
        return RainSnapshot(final.mass.copyOf(), final.admitted.copyOf(), final.drained.copyOf())
    }

    private inline fun <T> guarded(action: () -> T): T {
        try { return action() } catch (error: Throwable) {
            try { close() } catch (cleanup: Throwable) { error.addSuppressed(cleanup) }
            throw error
        }
    }

    override fun close() {
        val measured = state ?: return
        state = null
        checked = null
        drawing = NoVisualization
        engine.use { measured.close() }
    }
}
