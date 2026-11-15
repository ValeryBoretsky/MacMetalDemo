package dev.macmetaldemo.terminal

import dev.macmetaldemo.domain.VisualizationFrame

fun renderVisualization(frame: VisualizationFrame, ascii: Boolean): List<String> {
    val lines = ArrayList<String>(PICTURE_ROWS)

    for (cellRow in 0 until PICTURE_ROWS) {
        val line = StringBuilder(PICTURE_COLUMNS)
        for (cellColumn in 0 until PICTURE_COLUMNS) {
            val pattern = braillePattern(frame, cellColumn, cellRow)
            if (ascii) {
                line.append(if (pattern == 0) ' ' else '*')
            } else if (pattern == 0) {
                line.append(' ')
            } else {
                line.append((0x2800 + pattern).toChar())
            }
        }
        lines.add(line.toString())
    }

    return lines
}

private fun braillePattern(frame: VisualizationFrame, cellColumn: Int, cellRow: Int): Int {
    val leftColumn = cellColumn * 2
    val rightColumn = leftColumn + 1
    val topRow = cellRow * 4
    var pattern = 0

    if (frame.isSet(leftColumn, topRow)) pattern = pattern or 0x01
    if (frame.isSet(leftColumn, topRow + 1)) pattern = pattern or 0x02
    if (frame.isSet(leftColumn, topRow + 2)) pattern = pattern or 0x04
    if (frame.isSet(rightColumn, topRow)) pattern = pattern or 0x08
    if (frame.isSet(rightColumn, topRow + 1)) pattern = pattern or 0x10
    if (frame.isSet(rightColumn, topRow + 2)) pattern = pattern or 0x20
    if (frame.isSet(leftColumn, topRow + 3)) pattern = pattern or 0x40
    if (frame.isSet(rightColumn, topRow + 3)) pattern = pattern or 0x80

    return pattern
}
