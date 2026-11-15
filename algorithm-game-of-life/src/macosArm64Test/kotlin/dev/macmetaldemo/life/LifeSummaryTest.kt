package dev.macmetaldemo.life

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class LifeSummaryTest {
    @Test
    fun rowsAndHashesMatchTheDocumentedGenerations() {
        var current = generateBoard(LifeConfig(5, 4, 7, 2, 42u))
        val fixtures = listOf(
            Triple("11010 10100 00000 11000", "7", "8ee4e8f53e6fe0a6"),
            Triple("00000 10101 10000 11101", "8", "cf20293388231731"),
            Triple("00100 11001 00100 11001", "8", "bd6d954ad643a01d"),
        )
        for ((rows, population, hash) in fixtures) {
            kotlin.test.assertContentEquals(board(rows), current)
            val fields = summarizeBoard(current).fields.associate { it.id to it.value }
            assertEquals(mapOf("liveCells" to population, "boardHash" to hash), fields)
            val next = FloatArray(20)
            updateRange(current, next, 5, 4, 0, 20)
            current = next
        }
    }

    @Test
    fun hashIncludesLeadingZeros() {
        val fields = summarizeBoard(board("000000101100")).fields.associate { it.id to it.value }
        assertEquals("0f227daf806c5c42", fields["boardHash"])
    }

    @Test
    fun summaryRejectsEveryNonbinaryBitPattern() {
        val invalid = listOf(-0.0f, -1f, 0.5f, 2f, Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY)
        for (value in invalid) {
            assertFailsWith<IllegalStateException> { summarizeBoard(floatArrayOf(0f, value, 1f)) }
        }
    }
}
