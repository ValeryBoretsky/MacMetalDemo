package dev.macmetaldemo.life

import kotlin.test.Test
import kotlin.test.assertContentEquals

class LifeRuleTest {
    @Test
    fun isolatedCellDies() {
        assertNext("00000 00000 00100 00000 00000", "00000 00000 00000 00000 00000")
    }

    @Test
    fun blockRemainsStable() {
        assertNext("00000 01100 01100 00000 00000", "00000 01100 01100 00000 00000")
    }

    @Test
    fun blinkerReturnsAfterTwoGenerations() {
        val initial = board("00000 00000 01110 00000 00000")
        val next = FloatArray(25)
        val last = FloatArray(25)
        updateRange(initial, next, 5, 5, 0, 25)
        assertContentEquals(board("00000 00100 00100 00100 00000"), next)
        updateRange(next, last, 5, 5, 0, 25)
        assertContentEquals(initial, last)
    }

    @Test
    fun horizontalBlinkerCrossesColumnSeam() {
        assertNext("00000 00000 11001 00000 00000", "00000 10000 10000 10000 00000")
    }

    @Test
    fun verticalBlinkerCrossesRowSeam() {
        assertNext("00100 00100 00000 00000 00100", "01110 00000 00000 00000 00000")
    }

    @Test
    fun cornerBlockUsesDiagonalWrapping() {
        assertNext("10001 00000 00000 00000 10001", "10001 00000 00000 00000 10001")
    }

    @Test
    fun minimumBoardCountsEightDistinctNeighbors() {
        assertNext("111 111 111", "000 000 000")
    }

    @Test
    fun rangeWritesOnlyItsOwnCells() {
        val old = board("00000 00000 01110 00000 00000")
        val unchanged = old.copyOf()
        val next = FloatArray(25) { -1f }
        updateRange(old, next, 5, 5, 7, 18)
        assertContentEquals(unchanged, old)
        assertContentEquals(
            floatArrayOf(-1f, -1f, -1f, -1f, -1f, -1f, -1f, 1f, 0f, 0f, 0f, 0f, 1f,
                0f, 0f, 0f, 0f, 1f, -1f, -1f, -1f, -1f, -1f, -1f, -1f),
            next,
        )
    }

    @Test
    fun gliderMovesDownAndRight() {
        var current = board("00000 01000 00100 11100 00000")
        val expected = listOf(
            "00000 00000 10100 01100 01000",
            "00000 00000 00100 10100 01100",
            "00000 00000 01000 00110 01100",
            "00000 00000 00100 00010 01110",
        )
        for (rows in expected) {
            val next = FloatArray(25)
            updateRange(current, next, 5, 5, 0, 25)
            assertContentEquals(board(rows), next)
            current = next
        }
    }

    private fun assertNext(input: String, expected: String) {
        val width = input.indexOf(' ')
        val old = board(input)
        val next = FloatArray(old.size)
        updateRange(old, next, width, old.size / width, 0, old.size)
        assertContentEquals(board(expected), next)
    }
}

internal fun board(rows: String): FloatArray = rows.filter { it != ' ' }.map { if (it == '1') 1f else 0f }.toFloatArray()
