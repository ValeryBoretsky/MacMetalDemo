@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)
@file:Suppress("UNCHECKED_CAST")

package dev.macmetaldemo.metal

import kotlinx.cinterop.*
import platform.Foundation.NSError
import platform.Metal.*
import platform.posix.memcpy

internal class MetalSystemCalls private constructor(private val device: MTLDeviceProtocol) : MetalNativeCalls {
    companion object {
        fun available(): Boolean = autoreleasepool { MTLCreateSystemDefaultDevice() != null }
        fun open(): MetalSystemCalls = MetalSystemCalls(MTLCreateSystemDefaultDevice() ?: error("Metal device is unavailable"))
    }
    override val deviceName = device.name
    override val deviceRegistryId: ULong = device.registryID
    override val maxBufferBytes = device.maxBufferLength.toLong()
    override fun queue(): NativeHandle = autoreleasepool {
        Holder(device.newCommandQueue() ?: error("Cannot create Metal queue"))
    }
    override fun library(source: String): NativeHandle = autoreleasepool {
        memScoped {
            val error = alloc<ObjCObjectVar<NSError?>>()
            val options = MTLCompileOptions().apply { fastMathEnabled = false }
            val library = device.newLibraryWithSource(source, options, error.ptr)
                ?: error(error.value?.localizedDescription ?: "Cannot compile Metal source")
            Holder(library)
        }
    }
    override fun pipeline(library: NativeHandle, entry: String): NativeHandle = autoreleasepool {
        memScoped {
            val function = (library as Holder<MTLLibraryProtocol>).value().newFunctionWithName(entry)
                ?: error("Metal function is unavailable: $entry")
            val error = alloc<ObjCObjectVar<NSError?>>()
            val pipeline = device.newComputePipelineStateWithFunction(function, error.ptr)
                ?: error(error.value?.localizedDescription ?: "Cannot create Metal pipeline")
            Holder(pipeline)
        }
    }
    override fun buffer(size: Long): NativeBuffer = autoreleasepool {
        val buffer = device.newBufferWithLength(size.toULong(), MTLResourceStorageModeShared)
            ?: error("Cannot allocate Metal buffer")
        SystemBuffer(buffer)
    }
    override fun command(queue: NativeHandle, pipeline: NativeHandle, buffers: List<NativeBuffer>, constants: ByteArray, count: Int): NativeCommand = autoreleasepool {
        val command = (queue as Holder<MTLCommandQueueProtocol>).value().commandBuffer()
            ?: error("Cannot create Metal command")
        val state = (pipeline as Holder<MTLComputePipelineStateProtocol>).value()
        SystemCommand(command, state, buffers.map { (it as SystemBuffer).value() }, constants, count)
    }
}

internal class Holder<T : Any>(private var reference: T?) : NativeHandle {
    fun value(): T = checkNotNull(reference)
    override fun close() { reference = null }
}

internal class SystemBuffer(private var reference: MTLBufferProtocol?) : NativeBuffer {
    fun value(): MTLBufferProtocol = checkNotNull(reference)
    override fun write(values: FloatArray) {
        if (values.isNotEmpty()) values.usePinned { memcpy(value().contents(), it.addressOf(0), (values.size.toLong() * 4).toULong()) }
    }
    override fun readInto(values: FloatArray) {
        if (values.isNotEmpty()) values.usePinned { memcpy(it.addressOf(0), value().contents(), (values.size.toLong() * 4).toULong()) }
    }
    override fun readRecords(indices: IntArray, recordSize: Int, values: FloatArray) {
        val source = value().contents()!!.reinterpret<FloatVar>()
        for (sample in indices.indices) {
            for (element in 0 until recordSize) {
                values[sample * recordSize + element] = source[indices[sample].toLong() * recordSize + element]
            }
        }
    }
    override fun close() { reference = null }
}

internal class SystemCommand(
    private var command: MTLCommandBufferProtocol?,
    private var state: MTLComputePipelineStateProtocol?,
    private var buffers: List<MTLBufferProtocol>,
    private val constants: ByteArray,
    private val count: Int,
) : NativeCommand {
    override fun execute(submitted: () -> Unit): NativeCompletion = autoreleasepool {
        val command = checkNotNull(command)
        val state = checkNotNull(state)
        executeMetalCommand(object : CommandOperations {
            override fun commit() {
                val encoder = command.computeCommandEncoder() ?: error("Cannot create Metal encoder")
                encoder.setComputePipelineState(state)
                buffers.forEachIndexed { index, buffer -> encoder.setBuffer(buffer, 0u, index.toULong()) }
                if (constants.isNotEmpty()) constants.usePinned { encoder.setBytes(it.addressOf(0), constants.size.toULong(), buffers.size.toULong()) }
                val width = minOf(state.threadExecutionWidth, state.maxTotalThreadsPerThreadgroup).coerceAtLeast(1u)
                val grid = cValue<MTLSize> { this.width = count.toULong(); height = 1u; depth = 1u }
                val group = cValue<MTLSize> { this.width = width; height = 1u; depth = 1u }
                encoder.dispatchThreads(grid, group)
                encoder.endEncoding()
                command.commit()
            }
            override fun waitForCompletion() = command.waitUntilCompleted()
            override fun completed() = command.status == MTLCommandBufferStatusCompleted
            override fun error() = command.error?.localizedDescription
            override fun start() = command.GPUStartTime
            override fun end() = command.GPUEndTime
        }, submitted)
    }
    override fun close() {
        command = null
        state = null
        buffers = emptyList()
    }
}

internal interface CommandOperations {
    fun commit()
    fun waitForCompletion()
    fun completed(): Boolean
    fun error(): String?
    fun start(): Double
    fun end(): Double
}

internal fun executeMetalCommand(operations: CommandOperations, submitted: () -> Unit): NativeCompletion {
    operations.commit()
    try {
        submitted()
    } catch (failure: Throwable) {
        try {
            operations.waitForCompletion()
        } catch (waitFailure: Throwable) {
            failure.addSuppressed(waitFailure)
        }
        throw failure
    }
    operations.waitForCompletion()
    val completed = operations.completed()
    val error = operations.error()
    check(completed && error == null) { error ?: "Metal command failed" }
    return NativeCompletion(completed, operations.start(), operations.end(), error)
}
