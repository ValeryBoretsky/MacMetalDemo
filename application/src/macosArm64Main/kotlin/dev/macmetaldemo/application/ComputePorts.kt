package dev.macmetaldemo.application

import dev.macmetaldemo.domain.ExecutionMode
import dev.macmetaldemo.domain.ModeAvailability

interface CpuRuntimeFactory { fun open(mode: ExecutionMode, elementCount: Int): CpuRuntime }
interface CpuRuntime {
    val workerCount: Int
    fun forEachRange(elementCount: Int, action: (start: Int, endExclusive: Int) -> Unit)
    fun close()
}

interface MetalRuntimeFactory {
    fun availability(): ModeAvailability
    fun open(source: String, entryPoint: String): MetalProgram
}

class MetalDispatch(val buffers: List<MetalBuffer>, val constants: ByteArray, val elementCount: Int)

interface MetalProgram {
    val deviceName: String
    val deviceRegistryId: ULong? get() = null
    val maxBufferBytes: Long
    fun createBuffer(byteCount: Long): MetalBuffer
    fun dispatch(buffers: List<MetalBuffer>, constants: ByteArray, elementCount: Int, gpu: GpuStatusSink): Long?
    // Execute each dispatch in order. Return after the shared command buffer completes.
    fun dispatchBatch(steps: List<MetalDispatch>, gpu: GpuStatusSink): Long? {
        throw UnsupportedOperationException("This Metal program does not support ordered dispatches.")
    }
    fun close()
}

interface MetalBuffer {
    val byteCount: Long
    fun write(values: FloatArray)
    fun readInto(values: FloatArray)
    fun readRecords(indices: IntArray, recordSize: Int, values: FloatArray) { throw UnsupportedOperationException() }
    fun close()
}
