package dev.macmetaldemo.nbody

import dev.macmetaldemo.application.*
import dev.macmetaldemo.domain.*
import dev.macmetaldemo.nbody.domain.*
import dev.macmetaldemo.nbody.metal.*
import kotlin.test.*

class NBodyMetalRunTest {
    @Test fun warmUpUsesSeparateBuffersAndTransferFollowsExecution() {
        val program = FakeProgram()
        val run = NBodyMetalRun(NBodyConfig(17, 1), program)
        val initial = program.buffers[0].values.copyOf()
        run.warmUp()
        assertEquals(listOf(128), program.counts)
        assertEquals(List(4) { 17L * 16 } + List(4) { 128L * 16 }, program.buffers.map { it.byteCount })
        assertContentEquals(generateInitialState(NBodyConfig(128, 1)).positionMass, program.buffers[4].values)
        assertContentEquals(initial, program.buffers[0].values)
        assertTrue(program.buffers.take(4).none { it.closed })
        assertTrue(program.buffers.drop(4).all { it.closed })
        run.execute(ProgressSink { }, GpuStatusSink { })
        assertEquals(0, program.reads)
        run.checkAndSummarize()
        assertEquals(2, program.reads)
        run.close()
        assertTrue(program.closed && program.buffers.all { it.closed })
    }
    @Test fun failuresCloseResourcesAndDoNotPublishProgress() {
        for (stage in listOf("upload", "warmUpload", "warm", "dispatch", "transfer", "numerical")) {
            val program = FakeProgram(stage)
            val progress = mutableListOf<RunProgress>()
            assertFails {
                val run = NBodyMetalRun(NBodyConfig(2, 1), program)
                run.warmUp()
                run.execute(ProgressSink { progress.add(it) }, GpuStatusSink { })
                run.checkAndSummarize()
            }
            if (stage in listOf("upload", "warmUpload", "warm", "dispatch")) assertTrue(progress.isEmpty(), stage)
            assertTrue(program.closed, stage)
            assertTrue(program.buffers.all { it.closed }, stage)
        }
    }
    @Test fun nonfiniteMassAndPaddingRejectMetalResultAndCloseResources() {
        for (failure in listOf("mass", "padding")) {
            val program = FakeProgram(failure)
            val run = NBodyMetalRun(NBodyConfig(2, 1), program)
            run.warmUp()
            run.execute(ProgressSink { }, GpuStatusSink { })
            assertFailsWith<IllegalStateException>(failure) { run.checkAndSummarize() }
            assertFails { run.finalStateCopy() }
            assertTrue(program.closed && program.buffers.all { it.closed }, failure)
        }
    }
    @Test fun failedSecondDispatchKeepsOnlyCompletedProgress() {
        val program = FakeProgram("secondDispatch")
        val run = NBodyMetalRun(NBodyConfig(2, 2), program)
        val progress = mutableListOf<RunProgress>()
        run.warmUp()
        assertFails { run.execute(ProgressSink { progress.add(it) }, GpuStatusSink { }) }
        assertEquals(listOf(RunProgress(1, 2, 2)), progress)
        assertFails { run.checkAndSummarize() }
        assertFails { run.finalStateCopy() }
        assertTrue(program.closed && program.buffers.all { it.closed })
    }
    @Test fun deviceNameFailureClosesProgram() {
        val program = FakeProgram("device")
        val error = assertFailsWith<IllegalStateException> { NBodyMetalRun(NBodyConfig(2, 1), program) }
        assertEquals("device", error.message)
        assertTrue(program.closed)
    }
    @Test fun warmFailureKeepsPrimaryAndAttemptsEveryClose() {
        val program = FakeProgram("warmClose")
        val run = NBodyMetalRun(NBodyConfig(2, 1), program)
        val error = assertFailsWith<IllegalStateException> { run.warmUp() }
        assertEquals("dispatch", error.message)
        assertTrue(error.suppressedExceptions.isNotEmpty())
        assertTrue(program.closed && program.buffers.all { it.closed })
    }
    @Test fun closeAttemptsEveryBufferAndProgramAfterCloseFailure() {
        val program = FakeProgram("close")
        val run = NBodyMetalRun(NBodyConfig(2, 1), program)
        val error = assertFailsWith<IllegalStateException> { run.close() }
        assertEquals("buffer close", error.message)
        assertTrue(program.closed && program.buffers.all { it.closed })
        assertTrue(error.suppressedExceptions.isNotEmpty())
    }
    @Test fun uniformBytesUseNativeLayout() {
        val bytes = nbodyUniforms(17)
        assertEquals(16, bytes.size)
        fun word(offset: Int): UInt = (0..3).fold(0u) { value, index -> value or ((bytes[offset + index].toUInt() and 255u) shl (index * 8)) }
        assertEquals(17u, word(0))
        assertEquals(1f.toBits().toUInt(), word(4))
        assertEquals(0.001f.toBits().toUInt(), word(8))
        assertEquals((0.01f * 0.01f).toBits().toUInt(), word(12))
    }
}
private class FakeProgram(private val failure: String = "") : MetalProgram {
    override val deviceName: String get() { if (failure == "device") error("device"); return "fake" }
    override val maxBufferBytes = Long.MAX_VALUE
    val buffers = mutableListOf<FakeBuffer>()
    val counts = mutableListOf<Int>()
    var reads = 0
    var closed = false
    override fun createBuffer(byteCount: Long): MetalBuffer = FakeBuffer(byteCount, this).also { buffers.add(it) }
    override fun dispatch(buffers: List<MetalBuffer>, constants: ByteArray, elementCount: Int, gpu: GpuStatusSink): Long? {
        counts.add(elementCount)
        if ((failure in listOf("warm", "warmClose") && elementCount == 128) || (failure == "dispatch" && elementCount != 128) ||
            (failure == "secondDispatch" && counts.size == 3)) error("dispatch")
        val input = buffers[0] as FakeBuffer
        val output = buffers[2] as FakeBuffer
        input.values.copyInto(output.values)
        (buffers[1] as FakeBuffer).values.copyInto((buffers[3] as FakeBuffer).values)
        if (elementCount != 128) {
            if (failure == "numerical") output.values[0] = Float.NaN
            if (failure == "mass") output.values[3] = Float.POSITIVE_INFINITY
            if (failure == "padding") (buffers[3] as FakeBuffer).values[3] = Float.NaN
        }
        return null
    }
    override fun close() { closed = true; if (failure == "close") error("program close") }
    inner class FakeBuffer(override val byteCount: Long, private val owner: FakeProgram) : MetalBuffer {
        val values = FloatArray((byteCount / 4).toInt())
        var closed = false
        override fun write(values: FloatArray) {
            if (failure == "upload" || (failure == "warmUpload" && owner.buffers.indexOf(this) == 4)) error("upload")
            values.copyInto(this.values)
        }
        override fun readInto(values: FloatArray) {
            owner.reads++
            if (failure == "transfer") error("transfer")
            this.values.copyInto(values)
        }
        override fun close() {
            closed = true
            if ((failure == "close" && owner.buffers.indexOf(this) == 0) ||
                (failure == "warmClose" && owner.buffers.indexOf(this) == 4)) error("buffer close")
        }
    }
}
