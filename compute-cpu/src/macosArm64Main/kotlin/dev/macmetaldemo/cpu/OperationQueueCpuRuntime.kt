package dev.macmetaldemo.cpu

import dev.macmetaldemo.application.CpuRuntime
import platform.Foundation.NSLock
import platform.Foundation.NSOperationQueue

class OperationQueueCpuRuntime(override val workerCount: Int) : CpuRuntime {
    private val queue = NSOperationQueue()
    private val lifecycle = NSLock()
    private var closed = false

    init {
        require(workerCount > 0)
        queue.maxConcurrentOperationCount = workerCount.toLong()
    }

    override fun forEachRange(elementCount: Int, action: (Int, Int) -> Unit) {
        lifecycle.lock()
        try {
            check(!closed)
            val ranges = partitionRanges(elementCount, workerCount)
            val failureLock = NSLock()
            val failures = mutableListOf<Throwable>()
            for (range in ranges) {
                queue.addOperationWithBlock {
                    try {
                        action(range.start, range.endExclusive)
                    } catch (error: Throwable) {
                        failureLock.lock()
                        try { failures.add(error) } finally { failureLock.unlock() }
                    }
                }
            }
            // The coordinator retains the action and its arrays until every range finishes.
            queue.waitUntilAllOperationsAreFinished()
            if (failures.isNotEmpty()) {
                val first = failures.first()
                failures.drop(1).forEach { first.addSuppressed(it) }
                throw first
            }
        } finally { lifecycle.unlock() }
    }

    override fun close() {
        lifecycle.lock()
        try {
            if (!closed) {
                queue.waitUntilAllOperationsAreFinished()
                closed = true
            }
        } finally { lifecycle.unlock() }
    }
}
