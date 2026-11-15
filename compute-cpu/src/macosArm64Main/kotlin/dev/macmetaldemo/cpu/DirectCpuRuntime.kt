package dev.macmetaldemo.cpu

import dev.macmetaldemo.application.CpuRuntime

class DirectCpuRuntime : CpuRuntime {
    private var closed = false
    override val workerCount = 1
    override fun forEachRange(elementCount: Int, action: (Int, Int) -> Unit) {
        check(!closed)
        require(elementCount > 0)
        action(0, elementCount)
    }
    override fun close() { closed = true }
}
