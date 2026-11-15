package dev.macmetaldemo.terminal

import dev.macmetaldemo.domain.*
import kotlin.test.*

class RunSnapshotRendererTest {
    @Test fun rendersPreparedBackendBeforeExecution() {
        var output = ""
        val io = object : TerminalIo {
            override fun readLine(): String? = null
            override fun write(text: String) { output += text }
        }
        val renderer = RunSnapshotRenderer(io)
        renderer.render(RunSnapshot(RunPhase.PREPARING, null, null, null, null, null, null, null))
        renderer.render(RunSnapshot(RunPhase.WARMING_UP, null, BackendInfo(4, null), null, null, null, null, null))
        assertEquals("Preparing.\nCPU workers: 4\nWarming up.\n", output)
        output = ""
        renderer.render(RunSnapshot(RunPhase.WARMING_UP, null, BackendInfo(null, "Test GPU"), null, null, null, null, null))
        assertEquals("Device: Test GPU\nWarming up.\n", output)
    }
}
