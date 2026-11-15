package dev.macmetaldemo.app

import dev.macmetaldemo.application.*
import dev.macmetaldemo.domain.*
import platform.Foundation.NSCondition
import platform.Foundation.NSDate

class LockedRunMailbox(
    private val requireContext: Boolean = false,
    val visualization: NativeVisualizationExchange = NativeVisualizationExchange(false),
) : RunHandle, RunSnapshotSink {
    override fun setVisualizationVisible(visible: Boolean) = visualization.setVisible(visible)
    override fun requestVisualization() = visualization.request()
    override fun latestVisualization(): VisualizationFrame? = visualization.latest()
    private val condition = NSCondition()
    private var current = RunSnapshot(RunPhase.PREPARING, null, null, null, null, null, null, null)
    private val transitions = mutableListOf<RunSnapshot>()
    private var result: RunOutcome? = null
    private val seenPhases = mutableSetOf<RunPhase>()
    private var version = 0L
    private var contextAcknowledged = false
    private var contextCancelled = false

    override fun publish(snapshot: RunSnapshot) {
        condition.lock()
        try {
            if (seenPhases.add(snapshot.phase)) {
                transitions.add(snapshot)
                version++
                condition.broadcast()
            }
            current = snapshot
            if (requireContext && snapshot.phase == RunPhase.WARMING_UP && snapshot.backend != null) {
                while (!contextAcknowledged && !contextCancelled) condition.wait()
                if (contextCancelled) throw RunFailure("Terminal output failed.", true)
            }
        } finally { condition.unlock() }
    }
    fun finish(outcome: RunOutcome) {
        visualization.close()
        condition.lock()
        try { result = outcome; version++; condition.broadcast() } finally { condition.unlock() }
    }
    override fun latest(): RunSnapshot {
        condition.lock()
        try { return current } finally { condition.unlock() }
    }
    override fun outcome(): RunOutcome? {
        condition.lock()
        try { return result } finally { condition.unlock() }
    }
    override fun awaitCompletion(): RunOutcome {
        condition.lock()
        try {
            while (result == null) condition.wait()
            return result!!
        } finally { condition.unlock() }
    }
    override fun drainTransitions(): List<RunSnapshot> {
        condition.lock()
        try { return transitions.toList().also { transitions.clear() } } finally { condition.unlock() }
    }
    override fun acknowledgeContext() {
        condition.lock()
        try { contextAcknowledged = true; condition.broadcast() } finally { condition.unlock() }
    }
    override fun cancelContext() {
        condition.lock()
        try { contextCancelled = true; condition.broadcast() } finally { condition.unlock() }
    }
    override fun changeVersion(): Long {
        condition.lock()
        try { return version } finally { condition.unlock() }
    }
    override fun waitForChange(version: Long, durationNs: Long, fallback: WaitStrategy) {
        condition.lock()
        try {
            if (this.version == version && result == null) {
                val deadline = NSDate(timeIntervalSinceReferenceDate = NSDate().timeIntervalSinceReferenceDate + durationNs / 1_000_000_000.0)
                condition.waitUntilDate(deadline)
            }
        } finally { condition.unlock() }
    }

}
