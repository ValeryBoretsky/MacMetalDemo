package dev.macmetaldemo.app

import dev.macmetaldemo.app.di.createApplication
import dev.macmetaldemo.terminal.*
import kotlin.system.exitProcess

fun main() {
    val io = object : TerminalIo {
        override fun readLine(): String? = kotlin.io.readlnOrNull()
        override fun write(text: String) { print(text) }
    }
    val screen = openTerminalScreen(io, terminalCapabilities())
    val status = try { createApplication(io, screen).run() } finally { screen.close() }
    exitProcess(status)
}
