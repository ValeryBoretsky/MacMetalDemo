package dev.macmetaldemo.metal

import dev.macmetaldemo.application.GpuStatusSink
import dev.macmetaldemo.domain.GpuCommandState
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith

class MetalRuntimeTest {
    @Test
    fun doublesSeventeenElementsWithoutChangingSentinels() {
        val factory = DefaultMetalRuntimeFactory()
        assertTrue(factory.availability().available, "This test requires an Apple Silicon Metal device")
        val source = """
            #include <metal_stdlib>
            using namespace metal;
            kernel void twice(device float *values [[buffer(0)]],
                              constant uint &count [[buffer(1)]],
                              uint i [[thread_position_in_grid]]) {
                if (i < count) values[i] *= 2.0f;
            }
        """.trimIndent()
        val program = factory.open(source, "twice")
        try {
            println("Metal device: ${program.deviceName}")
            val buffer = program.createBuffer(80)
            val input = FloatArray(20) { if (it < 17) it.toFloat() else -99f }
            buffer.write(input)
            val states = mutableListOf<GpuCommandState>()
            program.dispatch(listOf(buffer), byteArrayOf(17, 0, 0, 0), 17, GpuStatusSink { states.add(it.state) })
            val output = FloatArray(20)
            buffer.readInto(output)
            println("Metal output: ${output.joinToString()}")
            assertContentEquals(FloatArray(20) { if (it < 17) it.toFloat() * 2f else -99f }, output)
            assertEquals(GpuCommandState.COMPLETED, states.last())
        } finally {
            program.close()
        }
    }

    @Test
    fun readsOnlySelectedRecordsAndChecksBounds() {
        val source = """
            #include <metal_stdlib>
            using namespace metal;
            kernel void noop(uint i [[thread_position_in_grid]]) { }
        """.trimIndent()
        val program = DefaultMetalRuntimeFactory().open(source, "noop")
        try {
            val buffer = program.createBuffer(64)
            buffer.write(FloatArray(16) { it.toFloat() })
            val sample = FloatArray(8)
            buffer.readRecords(intArrayOf(3, 1), 4, sample)
            assertContentEquals(floatArrayOf(12f, 13f, 14f, 15f, 4f, 5f, 6f, 7f), sample)
            assertFailsWith<IllegalArgumentException> { buffer.readRecords(intArrayOf(4), 4, FloatArray(4)) }
            assertFailsWith<IllegalArgumentException> { buffer.readRecords(intArrayOf(-1), 4, FloatArray(4)) }
            assertFailsWith<IllegalArgumentException> { buffer.readRecords(intArrayOf(0), 4, FloatArray(3)) }
            buffer.close()
        } finally { program.close() }
    }

    @Test
    fun compileFailureIncludesMetalDiagnostic() {
        val failure = assertFailsWith<IllegalStateException> {
            DefaultMetalRuntimeFactory().open("not valid Metal source", "twice")
        }
        assertTrue(failure.message.orEmpty().contains("error", ignoreCase = true))
    }
}
