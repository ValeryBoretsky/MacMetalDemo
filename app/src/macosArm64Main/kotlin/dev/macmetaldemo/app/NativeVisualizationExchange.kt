package dev.macmetaldemo.app

import dev.macmetaldemo.application.VisualizationPort
import dev.macmetaldemo.domain.VisualizationFrame
import platform.Foundation.NSLock

class NativeVisualizationExchange(private var enabled: Boolean) : VisualizationPort {
    private val lock = NSLock()
    private var visible = false
    private var pending = false
    private var captured = false
    private var newest: VisualizationFrame? = null

    fun setVisible(value: Boolean) = locked {
        visible = value
        if (!value) {
            pending = false
            captured = false
            newest = null
        }
    }

    fun request() = locked {
        if (enabled && visible) pending = true
    }

    override fun captureRequested(): Boolean = locked {
        if (!enabled || !visible || !pending) return@locked false
        pending = false
        captured = true
        true
    }

    override fun publish(frame: VisualizationFrame) = locked {
        if (enabled && visible && captured && frame.completedUnits > (newest?.completedUnits ?: -1)) {
            newest = frame
        }
        captured = false
    }

    fun latest(): VisualizationFrame? = locked { newest }

    override fun disable() = locked {
        enabled = false
        visible = false
        pending = false
        captured = false
        newest = null
    }

    fun close() = disable()

    private inline fun <T> locked(action: () -> T): T {
        lock.lock()
        try { return action() } finally { lock.unlock() }
    }
}
