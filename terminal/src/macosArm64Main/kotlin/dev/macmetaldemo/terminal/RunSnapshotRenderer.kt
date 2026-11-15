package dev.macmetaldemo.terminal

import dev.macmetaldemo.domain.*

class RunSnapshotRenderer(private val io: TerminalIo) {
    fun render(snapshot: RunSnapshot) {
        when (snapshot.phase) {
            RunPhase.PREPARING -> io.write("Preparing.\n")
            RunPhase.WARMING_UP -> {
                snapshot.backend?.workerCount?.let { io.write("CPU workers: $it\n") }
                snapshot.backend?.deviceName?.let { io.write("Device: $it\n") }
                io.write("Warming up.\n")
            }
            else -> Unit
        }
    }
}
