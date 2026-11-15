package dev.macmetaldemo.application

import dev.macmetaldemo.domain.VisualizationFrame

interface VisualizationPort {
    // Consume requests only after one complete calculation unit.
    fun captureRequested(): Boolean
    fun publish(frame: VisualizationFrame)
    fun disable()
}

object NoVisualization : VisualizationPort {
    override fun captureRequested() = false
    override fun publish(frame: VisualizationFrame) = Unit
    override fun disable() = Unit
}
