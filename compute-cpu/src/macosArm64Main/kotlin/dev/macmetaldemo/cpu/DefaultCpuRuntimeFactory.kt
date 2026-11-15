package dev.macmetaldemo.cpu

import dev.macmetaldemo.application.*
import dev.macmetaldemo.domain.ExecutionMode
import platform.Foundation.NSProcessInfo

class DefaultCpuRuntimeFactory : CpuRuntimeFactory {
    override fun open(mode: ExecutionMode, elementCount: Int): CpuRuntime {
        require(elementCount > 0)
        return when (mode) {
            ExecutionMode.CPU_SINGLE -> DirectCpuRuntime()
            ExecutionMode.CPU_PARALLEL -> OperationQueueCpuRuntime(minOf(elementCount, NSProcessInfo.processInfo.activeProcessorCount.toInt()))
            ExecutionMode.METAL -> throw IllegalArgumentException("CPU mode is unavailable.")
        }
    }
}
