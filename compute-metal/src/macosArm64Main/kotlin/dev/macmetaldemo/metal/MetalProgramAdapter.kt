package dev.macmetaldemo.metal

import dev.macmetaldemo.application.*
import dev.macmetaldemo.domain.GpuCommandState
import dev.macmetaldemo.domain.GpuCommandStatus

internal interface NativeHandle { fun close() }
internal interface NativeBuffer : NativeHandle {
    fun write(values: FloatArray)
    fun readInto(values: FloatArray)
    fun readRecords(indices: IntArray, recordSize: Int, values: FloatArray) { throw UnsupportedOperationException() }
}
internal interface NativeCommand : NativeHandle { fun execute(submitted: () -> Unit): NativeCompletion }
internal data class NativeCompletion(val completed: Boolean, val start: Double, val end: Double, val error: String?)
internal interface MetalNativeCalls {
    val deviceName: String
    val deviceRegistryId: ULong? get() = null
    val maxBufferBytes: Long
    fun queue(): NativeHandle
    fun library(source: String): NativeHandle
    fun pipeline(library: NativeHandle, entry: String): NativeHandle
    fun buffer(size: Long): NativeBuffer
    fun command(queue: NativeHandle, pipeline: NativeHandle, buffers: List<NativeBuffer>, constants: ByteArray, count: Int): NativeCommand
}

internal class MetalProgramAdapter(
    private var native: MetalNativeCalls?,
    private val queue: NativeHandle,
    private val library: NativeHandle,
    private val pipeline: NativeHandle,
) : MetalProgram {
    override val deviceName = checkNotNull(native).deviceName
    override val deviceRegistryId = checkNotNull(native).deviceRegistryId
    override val maxBufferBytes = checkNotNull(native).maxBufferBytes
    private val buffers = mutableListOf<MetalBufferAdapter>()
    private var closed = false

    override fun createBuffer(byteCount: Long): MetalBuffer {
        check(!closed)
        require(byteCount > 0 && byteCount <= maxBufferBytes && byteCount <= Int.MAX_VALUE.toLong())
        val buffer = MetalBufferAdapter(byteCount, checkNotNull(native).buffer(byteCount), this)
        buffers.add(buffer)
        return buffer
    }

    override fun dispatch(buffers: List<MetalBuffer>, constants: ByteArray, elementCount: Int, gpu: GpuStatusSink): Long? {
        check(!closed)
        require(elementCount > 0)
        val handles = buffers.map { buffer ->
            require(buffer is MetalBufferAdapter && buffer.owner === this)
            buffer.handle()
        }
        gpu.publish(GpuCommandStatus(GpuCommandState.PREPARING, null))
        val command = checkNotNull(native).command(queue, pipeline, handles, constants, elementCount)
        try {
            val result = command.execute { gpu.publish(GpuCommandStatus(GpuCommandState.SUBMITTED, null)) }
            check(result.completed && result.error == null) { result.error ?: "Metal command failed" }
            val duration = if (result.start.isFinite() && result.end.isFinite() && result.start > 0 && result.end > result.start && (result.end - result.start) * 1e9 <= Long.MAX_VALUE.toDouble()) {
                ((result.end - result.start) * 1e9).toLong()
            } else null
            gpu.publish(GpuCommandStatus(GpuCommandState.COMPLETED, duration))
            return duration
        } finally { command.close() }
    }

    override fun close() {
        if (closed) return
        closed = true
        buffers.forEach { it.close() }
        buffers.clear()
        pipeline.close()
        library.close()
        queue.close()
        native = null
    }
}
