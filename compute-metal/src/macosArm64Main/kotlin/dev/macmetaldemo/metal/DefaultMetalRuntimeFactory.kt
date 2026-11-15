package dev.macmetaldemo.metal

import dev.macmetaldemo.application.MetalProgram
import dev.macmetaldemo.application.MetalRuntimeFactory
import dev.macmetaldemo.domain.ExecutionMode
import dev.macmetaldemo.domain.ModeAvailability

class DefaultMetalRuntimeFactory internal constructor(private val supplied: MetalNativeCalls?) : MetalRuntimeFactory {
    constructor() : this(null)

    override fun availability(): ModeAvailability {
        val available = supplied != null || MetalSystemCalls.available()
        return ModeAvailability(ExecutionMode.METAL, available, if (available) null else "Metal device is unavailable")
    }

    override fun open(source: String, entryPoint: String): MetalProgram {
        val native = supplied ?: MetalSystemCalls.open()
        val queue = native.queue()
        try {
            val library = native.library(source)
            try {
                val pipeline = native.pipeline(library, entryPoint)
                return MetalProgramAdapter(native, queue, library, pipeline)
            } catch (failure: Throwable) {
                library.close()
                throw failure
            }
        } catch (failure: Throwable) {
            queue.close()
            throw failure
        }
    }
}
