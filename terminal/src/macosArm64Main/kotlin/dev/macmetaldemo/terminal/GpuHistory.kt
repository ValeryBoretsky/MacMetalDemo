package dev.macmetaldemo.terminal

import kotlin.math.ceil

internal class GpuHistory {
    private val samples = mutableListOf<Double?>()
    fun add(percent: Double?) {
        samples.add(percent?.takeIf { it.isFinite() && it in 0.0..100.0 })
        if (samples.size > 20) samples.removeAt(0)
    }
    fun rows(ascii: Boolean, width: Int): List<String> {
        val columns = samples.takeLast(width.coerceIn(0, 20))
        return (5 downTo 1).map { row ->
            columns.joinToString("") { value ->
                if (value == null) "?"
                else if (ceil(value / 20.0).toInt() >= row) if (ascii) "#" else "■"
                else if (ascii) "." else "□"
            }
        }
    }
}
