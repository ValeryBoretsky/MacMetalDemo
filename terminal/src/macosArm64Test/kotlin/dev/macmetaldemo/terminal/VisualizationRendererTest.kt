package dev.macmetaldemo.terminal

import dev.macmetaldemo.domain.VisualizationFrame
import kotlin.test.Test
import kotlin.test.assertEquals

class VisualizationRendererTest {
    @Test
    fun rendersEachBrailleDotBit() {
        val coordinates = listOf(
            0 to 0,
            0 to 1,
            0 to 2,
            1 to 0,
            1 to 1,
            1 to 2,
            0 to 3,
            1 to 3,
        )
        val codePoints = listOf(0x1, 0x2, 0x4, 0x8, 0x10, 0x20, 0x40, 0x80)

        for (index in coordinates.indices) {
            val (column, row) = coordinates[index]
            val frame = frameWithDots(column to row)

            assertEquals(
                0x2800 + codePoints[index],
                renderVisualization(frame, ascii = false).first().first().code,
            )
        }
    }

    @Test
    fun rendersAnEmptyFrameAsSpaces() {
        val lines = renderVisualization(emptyFrame(), ascii = false)
        val asciiLines = renderVisualization(emptyFrame(), ascii = true)

        assertEquals(15, lines.size)
        assertEquals(List(15) { " ".repeat(40) }, lines)
        assertEquals(List(15) { " ".repeat(40) }, asciiLines)
    }

    @Test
    fun mergesDotsThatShareOneBrailleCell() {
        val frame = frameWithDots(0 to 0, 1 to 1, 1 to 3)

        assertEquals(0x2800 + 0x91, renderVisualization(frame, ascii = false).first().first().code)
    }

    @Test
    fun rendersAnyOccupiedAsciiCellAsAnAsterisk() {
        val frame = frameWithDots(0 to 0, 1 to 1, 2 to 4)
        val lines = renderVisualization(frame, ascii = true)

        assertEquals("*" + " ".repeat(39), lines.first())
        assertEquals(" " + "*" + " ".repeat(38), lines[1])
        assertEquals(15, lines.size)
    }

    @Test
    fun keepsThePanelDimensionsAndCornerDots() {
        val frame = frameWithDots(0 to 0, 79 to 59)
        val lines = renderVisualization(frame, ascii = false)
        val asciiLines = renderVisualization(frame, ascii = true)

        assertEquals(15, lines.size)
        assertEquals(List(15) { 40 }, lines.map(String::length))
        assertEquals(0x2801, lines.first().first().code)
        assertEquals(0x2880, lines.last().last().code)
        assertEquals(List(15) { 40 }, asciiLines.map(String::length))
        assertEquals('*' + " ".repeat(39), asciiLines.first())
        assertEquals(" ".repeat(39) + '*', asciiLines.last())
    }

    private fun emptyFrame(): VisualizationFrame = VisualizationFrame(
        completedUnits = 0,
        dots = BooleanArray(80 * 60),
    )

    private fun frameWithDots(vararg coordinates: Pair<Int, Int>): VisualizationFrame {
        val dots = BooleanArray(80 * 60)
        for ((column, row) in coordinates) {
            dots[row * 80 + column] = true
        }
        return VisualizationFrame(completedUnits = 0, dots = dots)
    }
}
