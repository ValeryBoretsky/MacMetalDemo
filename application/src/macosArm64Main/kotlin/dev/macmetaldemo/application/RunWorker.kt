package dev.macmetaldemo.application

import dev.macmetaldemo.domain.RunRequest
import dev.macmetaldemo.domain.RunSnapshot
import dev.macmetaldemo.domain.VisualizationFrame

interface RunHandle {
    fun setVisualizationVisible(visible: Boolean) = Unit
    fun requestVisualization() = Unit
    fun latestVisualization(): VisualizationFrame? = null
    fun latest(): RunSnapshot
    fun outcome(): RunOutcome?
    fun awaitCompletion(): RunOutcome
    fun drainTransitions(): List<RunSnapshot> = emptyList()
    fun acknowledgeContext() = Unit
    fun cancelContext() = Unit
    fun changeVersion(): Long = 0
    fun waitForChange(version: Long, durationNs: Long, fallback: WaitStrategy) = fallback.waitNs(durationNs)
}

fun interface RunWorker {
    fun start(request: RunRequest): RunHandle
    fun startObserved(request: RunRequest): RunHandle = start(request)
}
fun interface WaitStrategy { fun waitNs(durationNs: Long) }
