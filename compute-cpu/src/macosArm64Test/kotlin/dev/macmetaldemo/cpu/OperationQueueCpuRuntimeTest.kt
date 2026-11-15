package dev.macmetaldemo.cpu

import platform.Foundation.NSCondition
import platform.Foundation.NSDate
import platform.Foundation.NSOperationQueue
import platform.Foundation.NSProcessInfo
import dev.macmetaldemo.domain.ExecutionMode
import kotlin.test.*

class OperationQueueCpuRuntimeTest {
    @Test fun factoryReportsAvailableWorkers() {
        val runtime = DefaultCpuRuntimeFactory().open(ExecutionMode.CPU_PARALLEL, 2)
        assertEquals(minOf(2, NSProcessInfo.processInfo.activeProcessorCount.toInt()), runtime.workerCount)
        runtime.close()
    }
    @Test fun coordinatorWaitsForHeldRange() = checkWait(false)
    @Test fun coordinatorWaitsForAllRangesBeforeReportingError() = checkWait(true)

    private fun checkWait(fail: Boolean) {
        val runtime = OperationQueueCpuRuntime(2)
        val condition = NSCondition()
        var held = false
        var release = false
        var finished = false
        var errorStarted = false
        var caught: Throwable? = null
        val state = IntArray(2)
        var completed = false
        var closeStarted = false
        var closeFinished = false
        val coordinator = NSOperationQueue()
        coordinator.addOperationWithBlock {
            try {
                runtime.forEachRange(2) { start, _ ->
                    if (start == 0) {
                        condition.lock()
                        held = true
                        condition.broadcast()
                        while (!release) condition.wait()
                        state[start] = 42
                        completed = true
                        condition.unlock()
                    } else if (fail) {
                        condition.lock()
                        errorStarted = true
                        condition.broadcast()
                        condition.unlock()
                        error("Task failure")
                    }
                }
            } catch (error: Throwable) { caught = error }
            condition.lock()
            finished = true
            condition.broadcast()
            condition.unlock()
        }
        condition.lock()
        val deadline = NSDate(timeIntervalSinceReferenceDate = NSDate().timeIntervalSinceReferenceDate + 5.0)
        while ((!held || (fail && !errorStarted)) && condition.waitUntilDate(deadline)) {}
        val closer = NSOperationQueue()
        closer.addOperationWithBlock {
            condition.lock()
            closeStarted = true
            condition.broadcast()
            condition.unlock()
            runtime.close()
            condition.lock()
            closeFinished = true
            condition.broadcast()
            condition.unlock()
        }
        while (!closeStarted && condition.waitUntilDate(deadline)) {}
        val closedEarly = closeFinished
        val observedHeld = held
        val observedError = errorStarted
        val returnedEarly = finished
        release = true
        condition.broadcast()
        val finishDeadline = NSDate(timeIntervalSinceReferenceDate = NSDate().timeIntervalSinceReferenceDate + 5.0)
        while (!finished && condition.waitUntilDate(finishDeadline)) {}
        assertTrue(finished)
        assertTrue(completed)
        condition.unlock()
        coordinator.waitUntilAllOperationsAreFinished()
        closer.waitUntilAllOperationsAreFinished()
        assertFalse(closedEarly)
        assertTrue(closeFinished)
        assertEquals(42, state[0])
        assertTrue(observedHeld)
        if (fail) assertTrue(observedError)
        assertFalse(returnedEarly)
        if (fail) assertEquals("Task failure", caught?.message) else assertNull(caught)
        runtime.close()
        runtime.close()
        assertFailsWith<IllegalStateException> { runtime.forEachRange(1) { _, _ -> fail("Closed runtime executed work") } }
    }
}
