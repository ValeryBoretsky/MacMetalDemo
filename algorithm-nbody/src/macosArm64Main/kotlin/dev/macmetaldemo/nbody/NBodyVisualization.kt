package dev.macmetaldemo.nbody

import dev.macmetaldemo.application.VisualizationPort
import dev.macmetaldemo.domain.VisualizationFrame
import kotlin.math.floor
import kotlin.math.sqrt

internal fun sampleIndices(particleCount: Int): IntArray {
    val count = minOf(particleCount, 512)
    return IntArray(count) { (it.toLong() * particleCount / count).toInt() }
}

internal fun projectSamples(positions: FloatArray, step: Long): VisualizationFrame {
    val dots = BooleanArray(VisualizationFrame.WIDTH * VisualizationFrame.HEIGHT)
    for (offset in positions.indices step 4) {
        val x = positions[offset].toDouble()
        val y = positions[offset + 1].toDouble()
        val z = positions[offset + 2].toDouble()
        val column = floor(40.0 + 18.4 * (x - z) / sqrt(2.0))
        val row = floor(30.0 + 18.4 * (x + z - 2.0 * y) / sqrt(6.0))
        if (column >= 0 && column < 80 && row >= 0 && row < 60) {
            dots[row.toInt() * 80 + column.toInt()] = true
        }
    }
    return VisualizationFrame(step, dots)
}

internal inline fun captureFrame(port: VisualizationPort, capture: () -> VisualizationFrame): Boolean {
    try {
        if (port.captureRequested()) port.publish(capture())
        return true
    } catch (_: Throwable) {
        try { port.disable() } catch (_: Throwable) { }
        return false
    }
}
