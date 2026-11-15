package dev.macmetaldemo.application

import dev.macmetaldemo.domain.*

class RunFailure(message: String, val fatal: Boolean = false) : Exception(message)
sealed interface RunOutcome {
    data class Success(val result: RunResult) : RunOutcome
    data class Failure(val message: String, val fatal: Boolean) : RunOutcome
}

class RunController(private val registry: AlgorithmRegistry, private val clock: MonotonicClock) {
    fun run(request: RunRequest, sink: RunSnapshotSink): RunOutcome = run(request, NoVisualization, sink)

    fun run(request: RunRequest, visualization: VisualizationPort, sink: RunSnapshotSink): RunOutcome {
        var prepared: PreparedRun? = null
        var descriptor: RunDescriptor? = null
        var backend: BackendInfo? = null
        var progress: RunProgress? = null
        var gpu: GpuCommandStatus? = null
        var start: Long? = null
        var phase = RunPhase.PREPARING
        var result: RunResult? = null
        var completedProgress: RunProgress? = null
        var failure: RunOutcome.Failure? = null
        fun publish(elapsed: Long? = null) {
            val p = progress
            val eta = if (p != null && p.completedUnits > 0 && elapsed != null) {
                (elapsed.toDouble() * (p.totalUnits - p.completedUnits) / p.completedUnits).toLong()
            } else null
            sink.publish(RunSnapshot(phase, descriptor, backend, p, start, elapsed, eta, gpu))
        }
        try {
            val algorithm = registry.find(request.algorithmId) ?: throw RunFailure("Unknown algorithm")
            if (request.mode !in algorithm.supportedModes) throw RunFailure("Mode is not supported")
            val availability = algorithm.availability(request.mode)
            if (!availability.available || availability.mode != request.mode) throw RunFailure(availability.reason ?: "Mode is not available")
            val values = when (val validation = algorithm.validate(request.parameters)) {
                is ParameterValidation.Valid -> validation.values
                is ParameterValidation.Invalid -> throw RunFailure(validation.messages.values.joinToString(", "))
            }
            val validRequest = request.copy(parameters = values)
            descriptor = algorithm.describe(values)
            if (descriptor.key.algorithmId != algorithm.info.id ||
                descriptor.key.modelVersion != algorithm.info.modelVersion) {
                throw RunFailure("Algorithm identity does not match the run descriptor")
            }
            val totalStart = clock.nowNs()
            publish()
            val gpuSink = GpuStatusSink { status ->
                gpu = if (start != null && status.state != GpuCommandState.COMPLETED) {
                    status.copy(lastDurationNs = gpu?.lastDurationNs)
                } else status
                publish(start?.let { began -> clock.nowNs() - began })
            }
            prepared = algorithm.prepare(validRequest, gpuSink)
            if (request.visualizationEnabled) {
                try {
                    prepared.attachVisualization(visualization)
                } catch (error: Throwable) {
                    try { visualization.disable() } catch (_: Throwable) { }
                }
            }
            backend = prepared.backend
            phase = RunPhase.WARMING_UP
            publish()
            prepared.warmUp()
            gpu = null
            val computeStart = clock.nowNs()
            start = computeStart
            val preparationNs = computeStart - totalStart
            phase = RunPhase.RUNNING
            progress = RunProgress(0, descriptor.progressTotal, if (descriptor.exactWorkCount != null) 0 else null)
            publish(0)
            var last = RunProgress(0, descriptor.progressTotal, if (descriptor.exactWorkCount != null) 0 else null)
            var lastKnownWork: Long? = last.completedWork
            prepared.execute(ProgressSink { next ->
                val work = next.completedWork
                if (next.totalUnits != descriptor.progressTotal || next.completedUnits < last.completedUnits ||
                    next.completedUnits > next.totalUnits || next.completedUnits < 0 ||
                    (descriptor.exactWorkCount != null && work == null) ||
                    (work != null && (work < 0 ||
                        (lastKnownWork != null && work < lastKnownWork!!) ||
                        (descriptor.exactWorkCount != null && work > descriptor.exactWorkCount!!)))) {
                    throw RunFailure("Invalid progress")
                }
                if (work != null) lastKnownWork = work
                last = next
                if (next.completedUnits < next.totalUnits) { progress = next; publish(clock.nowNs() - computeStart) }
            }, gpuSink)
            if (last.completedUnits != descriptor.progressTotal ||
                (descriptor.exactWorkCount != null && last.completedWork != descriptor.exactWorkCount)) {
                throw RunFailure("Measured work is incomplete")
            }
            val computeNs = clock.nowNs() - computeStart
            phase = RunPhase.CHECKING
            publish(computeNs)
            val summary = prepared.checkAndSummarize()
            val totalNs = clock.nowNs() - totalStart
            result = RunResult(validRequest, descriptor, backend, summary, preparationNs, computeNs, totalNs)
            completedProgress = last
        } catch (error: Throwable) {
            failure = RunOutcome.Failure(error.message ?: "Run failed", (error as? RunFailure)?.fatal ?: false)
        } finally {
            try { prepared?.close() } catch (error: Throwable) {
                val cleanup = error.message ?: "Cleanup failed"
                failure = if (failure == null) RunOutcome.Failure(cleanup, (error as? RunFailure)?.fatal ?: true)
                    else RunOutcome.Failure("${failure.message}. Cleanup failed: $cleanup", failure.fatal || ((error as? RunFailure)?.fatal ?: true))
            }
        }
        return if (failure != null) {
            phase = RunPhase.FAILED
            publish(start?.let { clock.nowNs() - it })
            failure
        } else {
            phase = RunPhase.SUCCEEDED
            progress = completedProgress
            publish(result!!.computeNs)
            RunOutcome.Success(result)
        }
    }
}
