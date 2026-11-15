package dev.macmetaldemo.life

import dev.macmetaldemo.application.*
import dev.macmetaldemo.domain.BackendInfo

internal class MetalLifeBackend(private val program: MetalProgram) : LifeBackend {
    override val info get() = BackendInfo(null, program.deviceName, program.deviceRegistryId)

    override fun openBoards(config: LifeConfig): GenerationBoards {
        val bytes = boardBytes(config)
        require(bytes <= program.maxBufferBytes && boardBytes(LifeConfig.WARM_UP) <= program.maxBufferBytes) {
            "A Life board exceeds the Metal buffer limit."
        }
        // Reuse the upload array for each requested frame and the final read.
        val transfer = generateBoard(config)
        val dimensions = dimensionBytes(config.width, config.height)
        val first = program.createBuffer(bytes)
        try {
            val second = program.createBuffer(bytes)
            try {
                first.write(transfer)
                return MetalGenerationBoards(program, first, second, dimensions, transfer)
            } catch (error: Throwable) {
                closeAfterFailure(error, second::close)
                throw error
            }
        } catch (error: Throwable) {
            closeAfterFailure(error, first::close)
            throw error
        }
    }

    override fun close() = program.close()
}

// Metal reads width and height as two consecutive little-endian uint values.
private fun dimensionBytes(width: Int, height: Int): ByteArray = ByteArray(8) { i ->
    val value = if (i < 4) width else height
    (value ushr ((i % 4) * 8)).toByte()
}

private class MetalGenerationBoards(private val program: MetalProgram, private var current: MetalBuffer,
                                    private var next: MetalBuffer, private val dimensions: ByteArray,
                                    private val transfer: FloatArray) : GenerationBoards {
    override fun advance(gpu: GpuStatusSink) {
        program.dispatch(listOf(current, next), dimensions, transfer.size, gpu)
        // The runtime waits for successful completion before returning from dispatch.
        val old = current
        current = next
        next = old
    }

    override fun completedBoard(): FloatArray {
        current.readInto(transfer)
        return transfer
    }

    override fun close() = closeBoth(current::close, next::close)
}
