package dev.macmetaldemo.domain

class VisualizationFrame(val completedUnits: Long, dots: BooleanArray) {
    private val storedDots = dots.copyOf()

    init {
        require(completedUnits >= 0)
        require(dots.size == WIDTH * HEIGHT)
    }

    fun isSet(column: Int, row: Int): Boolean {
        require(column in 0 until WIDTH && row in 0 until HEIGHT)
        return storedDots[row * WIDTH + column]
    }

    companion object {
        const val WIDTH = 80
        const val HEIGHT = 60
    }
}
