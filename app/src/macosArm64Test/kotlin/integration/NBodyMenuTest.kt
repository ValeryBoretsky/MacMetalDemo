package integration

import dev.macmetaldemo.application.*
import dev.macmetaldemo.cpu.DefaultCpuRuntimeFactory
import dev.macmetaldemo.nbody.NBodyAlgorithm
import dev.macmetaldemo.terminal.*
import kotlin.test.*

class NBodyMenuTest {
    @Test fun showsSpecifiedMenuFromRealAlgorithm() {
        val registry = AlgorithmRegistry(listOf(NBodyAlgorithm(DefaultCpuRuntimeFactory(), dev.macmetaldemo.metal.DefaultMetalRuntimeFactory())))
        var output = ""
        val io = object : TerminalIo {
            override fun readLine(): String = "0"
            override fun write(text: String) { output += text }
        }
        assertEquals(0, TerminalSession(registry, RunDriver { fail("Unexpected run") }, RunHistory(registry), io).run())
        assertEquals("Select an algorithm:\n1  N-body - particles under mutual gravity\n0  Exit\n", output)
    }
}
