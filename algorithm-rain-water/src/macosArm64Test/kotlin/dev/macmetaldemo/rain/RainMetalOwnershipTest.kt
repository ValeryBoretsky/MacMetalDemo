package dev.macmetaldemo.rain

import dev.macmetaldemo.application.GpuStatusSink
import dev.macmetaldemo.application.MetalBuffer
import dev.macmetaldemo.application.MetalDispatch
import dev.macmetaldemo.application.MetalProgram
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

class RainMetalOwnershipTest {
    private val config = RainConfig(32, 24, 4, 2, 42u, 4096)

    @Test
    fun bufferLimitRejectsBeforeAllocation() {
        val program = OwnershipProgram(limit = 3071)
        val backend = MetalRainBackend(program)
        assertFails { backend.open(config, generateScene(config)) }
        assertTrue(program.buffers.isEmpty())
        backend.close()
        assertTrue(program.closed)
    }

    @Test
    fun partialAcquisitionClosesEveryAcquiredBuffer() {
        for (allocation in 1..6) {
            val program = OwnershipProgram(failedAllocation = allocation)
            val backend = MetalRainBackend(program)
            assertFails { backend.open(config, generateScene(config)) }
            assertTrue(program.buffers.all { it.closed }, "Allocation $allocation")
            backend.close()
        }
    }

    @Test
    fun failedUploadClosesEveryAcquiredBuffer() {
        val program = OwnershipProgram(failedWrite = true)
        val backend = MetalRainBackend(program)
        assertFails { backend.open(config, generateScene(config)) }
        assertTrue(program.buffers.all { it.closed })
        backend.close()
    }

    @Test
    fun completedTickUsesFourPhasesWithoutReadback() {
        val program = OwnershipProgram()
        val backend = MetalRainBackend(program)
        val state = backend.open(config, generateScene(config))
        val counts = FloatArray(config.width)
        counts[1] = 3000f
        counts[21] = 1096f
        state.advance(3, counts, GpuStatusSink {})
        assertEquals(listOf(0, 1, 2, 3), program.phases)
        assertEquals(List(4) { config.cellCount }, program.counts)
        assertEquals(List(4) { 1 }, program.parities)
        assertEquals(1, program.batches)
        assertEquals(1, program.buffers[5].writes)
        assertContentEquals(counts, program.buffers[5].values)
        assertTrue(program.histograms.all { it === program.buffers[5] })
        assertEquals(0, program.reads)
        assertTrue(program.inputs.zipWithNext().all { (first, second) -> first !== second })
        assertTrue(state.readMass().all { it == 4f })
        assertEquals(1, program.reads)
        state.close()
        backend.close()
        assertTrue(program.closed && program.buffers.all { it.closed })
    }

    @Test
    fun failedHistogramUploadClosesTheStateBeforeSubmission() {
        val program = OwnershipProgram()
        val backend = MetalRainBackend(program)
        val state = backend.open(config, generateScene(config))
        program.failedWrite = true
        assertFails { state.advance(0, FloatArray(config.width), GpuStatusSink {}) }
        assertEquals(0, program.batches)
        assertTrue(program.buffers.all { it.closed })
        state.close()
        backend.close()
    }

    @Test
    fun failedPhaseStopsDispatchAndReleasesState() {
        val program = OwnershipProgram(failedPhase = 2)
        val backend = MetalRainBackend(program)
        val state = backend.open(config, generateScene(config))
        assertFails { state.advance(0, FloatArray(config.width) { 1f }, GpuStatusSink {}) }
        assertEquals(listOf(0, 1, 2), program.phases)
        assertEquals(0, program.reads)
        assertTrue(program.buffers.all { it.closed })
        state.close()
        backend.close()
    }

    @Test
    fun failedFrameReadDoesNotCloseCalculationState() {
        val program = OwnershipProgram(failedRead = true)
        val backend = MetalRainBackend(program)
        val state = backend.open(config, generateScene(config))
        assertFails { state.readMass() }
        state.advance(0, FloatArray(config.width) { 1f }, GpuStatusSink {})
        assertEquals(listOf(0, 1, 2, 3), program.phases)
        assertTrue(program.buffers.none { it.closed })
        state.close()
        backend.close()
    }

    @Test
    fun readsReuseSeparateHostArrays() {
        val program = OwnershipProgram()
        val backend = MetalRainBackend(program)
        val state = backend.open(config, generateScene(config))
        program.buffers[3].values.fill(Float.fromBits(7))
        program.buffers[4].values.fill(Float.fromBits(11))
        assertSame(state.readMass(), state.readMass())
        assertSame(state.readAdmitted(), state.readAdmitted())
        assertSame(state.readDrained(), state.readDrained())
        val admitted = state.readAdmitted()
        val drained = state.readDrained()
        assertNotSame(admitted, drained)
        assertContentEquals(IntArray(config.width) { 7 }, admitted)
        assertContentEquals(IntArray(config.width) { 11 }, drained)
        state.close()
        backend.close()
    }

    @Test
    fun warmStateKeepsMeasuredMassAndLedgersDry() {
        val program = OwnershipProgram()
        val backend = MetalRainBackend(program)
        val scene = generateScene(config)
        val measured = backend.open(config, scene)
        val warm = backend.open(RainConfig.WARM_UP, generateScene(RainConfig.WARM_UP))
        warm.advance(0, FloatArray(RainConfig.WARM_UP.width) { 1f }, GpuStatusSink {})
        warm.close()
        assertContentEquals(scene.walls, program.buffers[2].values)
        assertContentEquals(FloatArray(config.cellCount), measured.readMass())
        assertContentEquals(IntArray(config.width), measured.readAdmitted())
        assertContentEquals(IntArray(config.width), measured.readDrained())
        assertTrue(program.buffers.take(6).none { it.closed })
        assertTrue(program.buffers.drop(6).all { it.closed })
        measured.close()
        backend.close()
    }

    @Test
    fun closeAttemptsEveryBufferAfterFailure() {
        val program = OwnershipProgram(failedClose = true)
        val backend = MetalRainBackend(program)
        val state = backend.open(config, generateScene(config))
        val error = assertFails { state.close() }
        assertTrue(error.suppressedExceptions.isNotEmpty())
        assertTrue(program.buffers.all { it.closed })
        state.close()
        backend.close()
        assertEquals(List(6) { 1 }, program.buffers.map { it.closeCount })
        assertTrue(program.closed)
    }
}

private class OwnershipProgram(
    private val limit: Long = Long.MAX_VALUE,
    private val failedAllocation: Int = 0,
    var failedWrite: Boolean = false,
    private val failedRead: Boolean = false,
    private val failedPhase: Int = -1,
    private val failedClose: Boolean = false,
) : MetalProgram {
    override val deviceName = "Test GPU"
    override val maxBufferBytes get() = limit
    val buffers = mutableListOf<OwnershipBuffer>()
    val phases = mutableListOf<Int>()
    val counts = mutableListOf<Int>()
    val parities = mutableListOf<Int>()
    val histograms = mutableListOf<MetalBuffer>()
    var batches = 0
    val inputs = mutableListOf<MetalBuffer>()
    var reads = 0
    var closed = false

    override fun createBuffer(byteCount: Long): MetalBuffer {
        if (buffers.size + 1 == failedAllocation) error("Allocation failed.")
        return OwnershipBuffer(byteCount).also { buffers.add(it) }
    }

    override fun dispatch(buffers: List<MetalBuffer>, constants: ByteArray, elementCount: Int, gpu: GpuStatusSink): Long? {
        error("Rain Water must submit one batch per tick.")
    }

    override fun dispatchBatch(steps: List<MetalDispatch>, gpu: GpuStatusSink): Long? {
        batches++
        for (step in steps) applyStep(step)
        return null
    }

    private fun applyStep(step: MetalDispatch) {
        val buffers = step.buffers
        val constants = step.constants
        fun word(offset: Int): Int {
            var value = 0
            for (index in 0..3) value = value or ((constants[offset + index].toInt() and 255) shl (index * 8))
            return value
        }
        val phase = word(12)
        phases.add(phase)
        counts.add(step.elementCount)
        parities.add(word(16))
        histograms.add(buffers[5])
        inputs.add(buffers[0])
        if (phase == failedPhase) error("Dispatch failed.")
        val output = buffers[1] as OwnershipBuffer
        val input = buffers[0] as OwnershipBuffer
        for (index in output.values.indices) output.values[index] = input.values[index] + 1f
    }

    override fun close() { closed = true }

    inner class OwnershipBuffer(override val byteCount: Long) : MetalBuffer {
        val values = FloatArray((byteCount / 4).toInt())
        var closed = false
        var closeCount = 0
        var writes = 0
        override fun write(values: FloatArray) {
            if (failedWrite) error("Upload failed.")
            writes++
            values.copyInto(this.values)
        }
        override fun readInto(values: FloatArray) {
            reads++
            if (failedRead) error("Read failed.")
            this.values.copyInto(values)
        }
        override fun close() {
            closed = true
            closeCount++
            if (failedClose) error("Buffer close failed.")
        }
    }
}
