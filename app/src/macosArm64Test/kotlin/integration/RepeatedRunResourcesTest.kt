package integration

import dev.macmetaldemo.application.*
import dev.macmetaldemo.cpu.DefaultCpuRuntimeFactory
import dev.macmetaldemo.domain.*
import dev.macmetaldemo.metal.DefaultMetalRuntimeFactory
import dev.macmetaldemo.nbody.NBodyAlgorithm
import kotlin.test.*

class RepeatedRunResourcesTest {
    @Test fun repeatedRunsCloseOwnedRuntimesAndBuffers() {
        var active = 0
        val cpuDelegate = DefaultCpuRuntimeFactory()
        val cpu = object : CpuRuntimeFactory {
            override fun open(mode: ExecutionMode, elementCount: Int): CpuRuntime {
                val delegate = cpuDelegate.open(mode, elementCount)
                active++
                return object : CpuRuntime by delegate {
                    override fun close() { try { delegate.close() } finally { active-- } }
                }
            }
        }
        val metalDelegate = DefaultMetalRuntimeFactory()
        val metal = object : MetalRuntimeFactory {
            override fun availability() = metalDelegate.availability()
            override fun open(source: String, entryPoint: String): MetalProgram {
                val program = metalDelegate.open(source, entryPoint)
                active++
                val originals = mutableMapOf<MetalBuffer, MetalBuffer>()
                return object : MetalProgram by program {
                    override fun createBuffer(byteCount: Long): MetalBuffer {
                        val buffer = program.createBuffer(byteCount)
                        active++
                        val wrapper = object : MetalBuffer by buffer {
                            override fun close() { try { buffer.close() } finally { active-- } }
                        }
                        originals[wrapper] = buffer
                        return wrapper
                    }
                    override fun dispatch(buffers: List<MetalBuffer>, constants: ByteArray, elementCount: Int, gpu: GpuStatusSink): Long? =
                        program.dispatch(buffers.map { originals.getValue(it) }, constants, elementCount, gpu)
                    override fun close() { try { program.close() } finally { active-- } }
                }
            }
        }
        val algorithm = NBodyAlgorithm(cpu, metal)
        val registry = AlgorithmRegistry(listOf(algorithm))
        var now = 0L
        val controller = RunController(registry, MonotonicClock { now += 1_000_000; now })
        val values = ParameterValues(mapOf("N" to ParameterValue.Integer(17), "S" to ParameterValue.Integer(2)))
        for (mode in ExecutionMode.entries) repeat(3) {
            val outcome = controller.run(RunRequest("nbody", mode, values)) {}
            assertIs<RunOutcome.Success>(outcome, "$mode run $it")
            assertEquals(0, active, "$mode run $it")
        }
    }
}
