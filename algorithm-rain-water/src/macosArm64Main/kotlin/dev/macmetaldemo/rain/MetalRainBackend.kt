package dev.macmetaldemo.rain

import dev.macmetaldemo.application.GpuStatusSink
import dev.macmetaldemo.application.MetalBuffer
import dev.macmetaldemo.application.MetalDispatch
import dev.macmetaldemo.application.MetalProgram
import dev.macmetaldemo.domain.BackendInfo

internal class MetalRainBackend(private val program: MetalProgram) : RainBackend {
    private var closed = false
    override val info get() = BackendInfo(null, program.deviceName, program.deviceRegistryId)
    override fun open(config: RainConfig, scene: RainScene): RainState {
        check(!closed)
        val fieldBytes = config.cellCount.toLong() * Float.SIZE_BYTES
        val columnBytes = config.width.toLong() * Float.SIZE_BYTES
        val warmBytes = RainConfig.WARM_UP.cellCount.toLong() * Float.SIZE_BYTES
        require(maxOf(fieldBytes, columnBytes, warmBytes) <= minOf(program.maxBufferBytes, Int.MAX_VALUE.toLong())) {
            "A Rain Water buffer exceeds the Metal buffer limit."
        }
        require(scene.walls.size == config.cellCount) { "The wall mask does not match the field size." }
        val hostMass = FloatArray(config.cellCount)
        val counterWords = FloatArray(config.width)
        val acquired = ArrayList<MetalBuffer>(6)
        fun allocate(bytes: Long) = program.createBuffer(bytes).also { acquired.add(it) }
        try {
            val buffers = RainMetalBuffers(
                mass = allocate(fieldBytes),
                scratch = allocate(fieldBytes),
                walls = allocate(fieldBytes),
                admitted = allocate(columnBytes),
                drained = allocate(columnBytes),
                dropCounts = allocate(columnBytes),
            )
            buffers.mass.write(hostMass)
            buffers.walls.write(scene.walls)
            buffers.admitted.write(counterWords)
            buffers.drained.write(counterWords)
            return MetalRainState(program, config, buffers, hostMass, counterWords)
        } catch (error: Throwable) {
            closeRainBuffers(acquired, error)
            throw error
        }
    }

    override fun close() {
        if (closed) return
        closed = true
        program.close()
    }
}

private class RainMetalBuffers(
    val mass: MetalBuffer,
    scratch: MetalBuffer,
    val walls: MetalBuffer,
    val admitted: MetalBuffer,
    val drained: MetalBuffer,
    val dropCounts: MetalBuffer,
) : AutoCloseable {
    private val massToScratch = bindings(mass, scratch)
    private val scratchToMass = bindings(scratch, mass)

    fun bindingsForPass(pass: Int) = if (pass % 2 == 0) massToScratch else scratchToMass

    // The parameter order in rainStep defines the six buffer slots.
    private fun bindings(input: MetalBuffer, output: MetalBuffer) =
        listOf(input, output, walls, admitted, drained, dropCounts)

    override fun close() {
        closeRainBuffers(massToScratch, null)?.let { throw it }
    }
}

private class MetalRainState(
    private val program: MetalProgram,
    private val config: RainConfig,
    private val buffers: RainMetalBuffers,
    private val hostMass: FloatArray,
    private val counterWords: FloatArray,
) : RainState {
    private val admitted = IntArray(config.width)
    private val drained = IntArray(config.width)

    // The four passes alternate the mass buffers. The drain pass writes the mass for the next tick.
    private val batches = List(2) { parity ->
        RainPhase.entries.mapIndexed { pass, phase ->
            MetalDispatch(buffers.bindingsForPass(pass), rainConstants(config, phase, parity), config.cellCount)
        }
    }
    private var closed = false

    override fun advance(tick: Int, counts: FloatArray, gpu: GpuStatusSink) {
        check(!closed)
        require(counts.size == config.width) { "Rain Water needs one drop count per column." }
        try {
            buffers.dropCounts.write(counts)
            program.dispatchBatch(batches[tick and 1], gpu)
        } catch (error: Throwable) {
            try { close() } catch (cleanup: Throwable) { error.addSuppressed(cleanup) }
            throw error
        }
    }

    override fun readMass(): FloatArray {
        check(!closed)
        buffers.mass.readInto(hostMass)
        return hostMass
    }

    override fun readAdmitted() = readCounter(buffers.admitted, admitted)

    override fun readDrained() = readCounter(buffers.drained, drained)

    private fun readCounter(buffer: MetalBuffer, output: IntArray): IntArray {
        check(!closed)
        buffer.readInto(counterWords)
        // Metal stores uint counters. Preserve their bits when the shared buffer transfers them.
        for (i in output.indices) output[i] = counterWords[i].toRawBits()
        return output
    }

    override fun close() {
        if (closed) return
        closed = true
        buffers.close()
    }
}

// Metal reads five consecutive little-endian uint values at buffer index six.
private fun rainConstants(config: RainConfig, phase: RainPhase, parity: Int): ByteArray {
    val words = intArrayOf(config.width, config.height, config.rainMass, phase.shaderCode, parity)
    return ByteArray(words.size * 4) { index ->
        (words[index / 4] ushr ((index % 4) * 8)).toByte()
    }
}

private fun closeRainBuffers(buffers: List<MetalBuffer>, initial: Throwable?): Throwable? {
    var failure = initial
    for (buffer in buffers) {
        try { buffer.close() } catch (error: Throwable) {
            if (failure == null) failure = error else failure.addSuppressed(error)
        }
    }
    return failure
}
