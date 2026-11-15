package dev.macmetaldemo.terminal

import dev.macmetaldemo.terminal.nativeScreen.screen_close
import dev.macmetaldemo.terminal.nativeScreen.screen_open
import kotlinx.cinterop.ExperimentalForeignApi

interface TerminalScreen : AutoCloseable {
    val active: Boolean
    fun clear()
    override fun close()
}

object PlainTerminalScreen : TerminalScreen {
    override val active = false
    override fun clear() = Unit
    override fun close() = Unit
}

@OptIn(ExperimentalForeignApi::class)
fun openTerminalScreen(io: TerminalIo, capabilities: TerminalCapabilities): TerminalScreen {
    return openTerminalScreen(io, capabilities, { screen_open() != 0 }, { screen_close() })
}

internal fun openTerminalScreen(
    io: TerminalIo,
    capabilities: TerminalCapabilities,
    open: () -> Boolean,
    closeNative: () -> Unit,
): TerminalScreen {
    if (!capabilities.supportsScreen() || !open()) return PlainTerminalScreen
    return object : TerminalScreen {
        override var active = true
            private set
        override fun clear() { if (active) io.write("\u001b[2J\u001b[H") }
        override fun close() {
            if (active) {
                active = false
                closeNative()
            }
        }
    }
}
