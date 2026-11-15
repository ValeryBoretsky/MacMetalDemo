package dev.macmetaldemo.metal

import dev.macmetaldemo.application.MetalBuffer

internal class MetalBufferAdapter(
    override val byteCount: Long,
    private var native: NativeBuffer?,
    val owner: MetalProgramAdapter,
) : MetalBuffer {
    fun handle(): NativeBuffer = checkNotNull(native) { "Metal buffer is closed" }
    override fun write(values: FloatArray) {
        require(values.size.toLong() * 4 <= byteCount)
        handle().write(values)
    }
    override fun readInto(values: FloatArray) {
        require(values.size.toLong() * 4 <= byteCount)
        handle().readInto(values)
    }
    override fun readRecords(indices: IntArray, recordSize: Int, values: FloatArray) {
        require(recordSize > 0 && indices.size.toLong() * recordSize == values.size.toLong())
        require(indices.all { it >= 0 && (it.toLong() + 1) * recordSize <= byteCount / Float.SIZE_BYTES })
        handle().readRecords(indices, recordSize, values)
    }
    override fun close() {
        native?.close()
        native = null
    }
}
