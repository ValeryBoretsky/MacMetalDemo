package dev.macmetaldemo.life

import dev.macmetaldemo.domain.CheckedSummary
import dev.macmetaldemo.domain.ResultFieldValue

internal fun summarizeBoard(board: FloatArray): CheckedSummary {
    var liveCells = 0
    var hash = 0xcbf29ce484222325uL
    for (cell in board) {
        val value = when (cell.toRawBits()) {
            0 -> 0uL
            0x3f800000 -> {
                liveCells++
                1uL
            }
            else -> error("The result contains a cell outside positive 0.0f and 1.0f.")
        }
        hash = (hash xor value) * 0x100000001b3uL
    }
    return CheckedSummary(listOf(
        ResultFieldValue("liveCells", liveCells.toString()),
        ResultFieldValue("boardHash", hash.toString(16).padStart(16, '0')),
    ))
}
