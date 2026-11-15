package dev.macmetaldemo.life

import dev.macmetaldemo.application.*
import dev.macmetaldemo.domain.*

interface LifePreparedRun : PreparedRun {
    fun finalBoardCopy(): FloatArray
}

// Each CPU board pair has two arrays. Metal also keeps one transfer array per pair.
internal fun boardBytes(config: LifeConfig) = checkedLifeProduct(config.cellCount.toLong(), Float.SIZE_BYTES.toLong())

internal fun storageBudget(config: LifeConfig, mode: ExecutionMode): Long {
    val cellsInBothRuns = config.cellCount.toLong() + LifeConfig.WARM_UP.cellCount
    val boardCopies = if (mode == ExecutionMode.METAL) 3L else 2L
    return checkedLifeProduct(checkedLifeProduct(cellsInBothRuns, boardCopies), Float.SIZE_BYTES.toLong())
}

internal class MeasuredLifeRun(private val config: LifeConfig, private val engine: LifeBackend) : LifePreparedRun {
    override val backend = engine.info
    private var boards: GenerationBoards? = engine.openBoards(config)
    private var drawing: VisualizationPort = NoVisualization
    private var executed = false
    private var checked: FloatArray? = null

    override fun attachVisualization(port: VisualizationPort) { drawing = port }

    private inline fun <T> guarded(action: () -> T): T {
        try {
            return action()
        } catch (error: Throwable) {
            closeAfterFailure(error, ::close)
            throw error
        }
    }

    override fun warmUp() = guarded {
        checkNotNull(boards)
        engine.openBoards(LifeConfig.WARM_UP).use { it.advance(GpuStatusSink { }) }
    }

    override fun execute(progress: ProgressSink, gpu: GpuStatusSink) = guarded {
        val measured = checkNotNull(boards)
        check(!executed)
        for (generation in 1..config.generations) {
            measured.advance(gpu)
            capture(measured, generation.toLong())
            progress.publish(RunProgress(generation.toLong(), config.generations.toLong(), config.cellCount.toLong() * generation))
        }
        executed = true
    }

    private fun capture(measured: GenerationBoards, generation: Long) {
        if (drawing === NoVisualization) return
        try {
            if (drawing.captureRequested()) drawing.publish(projectBoard(config, measured.completedBoard(), generation))
        } catch (_: Throwable) {
            val failed = drawing
            drawing = NoVisualization
            try { failed.disable() } catch (_: Throwable) { }
        }
    }

    override fun checkAndSummarize(): CheckedSummary = guarded {
        check(executed)
        val final = checkNotNull(boards).completedBoard()
        val summary = summarizeBoard(final)
        checked = final
        summary
    }

    override fun finalBoardCopy() = checkNotNull(checked).copyOf()

    override fun close() {
        val measured = boards ?: return
        boards = null
        checked = null
        drawing = NoVisualization
        closeBoth(measured::close, engine::close)
    }
}

internal inline fun closeAfterFailure(error: Throwable, close: () -> Unit) {
    try { close() } catch (cleanup: Throwable) { error.addSuppressed(cleanup) }
}

internal inline fun closeBoth(first: () -> Unit, second: () -> Unit) {
    try {
        first()
    } catch (error: Throwable) {
        closeAfterFailure(error, second)
        throw error
    }
    second()
}
