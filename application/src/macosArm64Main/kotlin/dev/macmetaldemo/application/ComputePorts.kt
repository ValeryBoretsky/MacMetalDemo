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

interface MetalProgram {
    val deviceName: String
    val deviceRegistryId: ULong? get() = null
    val maxBufferBytes: Long
    fun createBuffer(byteCount: Long): MetalBuffer
    fun dispatch(buffers: List<MetalBuffer>, constants: ByteArray, elementCount: Int, gpu: GpuStatusSink): Long?
    fun close()
}

interface MetalBuffer {
    val byteCount: Long
    fun write(values: FloatArray)
    fun readInto(values: FloatArray)
    fun readRecords(indices: IntArray, recordSize: Int, values: FloatArray) { throw UnsupportedOperationException() }
    fun close()
}
