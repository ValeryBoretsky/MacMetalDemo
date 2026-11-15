package dev.macmetaldemo.life

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LifeGeneratorTest {
    @Test
    fun seedReproducesTheDocumentedSmallBoard() {
        val config = LifeConfig(5, 4, 7, 2, 42u)
        val first = generateBoard(config)
        assertContentEquals(board("11010 10100 00000 11000"), first)
        generateBoard(config.copy(seed = 43u))
        assertContentEquals(first, generateBoard(config))
        assertFalse(first.contentEquals(generateBoard(config.copy(seed = 43u))))
    }

    @Test
    fun smallBoardPlantsTheStartingGlider() {
        val config = LifeConfig(5, 5, 5, 4, 120509u)
        assertEquals(CellPosition(0, 1), gliderOrigin(config))
        assertContentEquals(board("00000 01000 00100 11100 00000"), generateBoard(config))
    }

    @Test
    fun defaultBoardHasTheSpecifiedPopulations() {
        val config = LifeConfig(512, 384, 1000, 4, 42u)
        assertEquals(CellPosition(258, 194), gliderOrigin(config))
        var current = generateBoard(config)
        assertEquals(1000, current.count { it == 1f })
        for (population in listOf(1066, 1132, 1264, 1198)) {
            val next = FloatArray(config.cellCount)
            updateRange(current, next, config.width, config.height, 0, config.cellCount)
            assertEquals(population, next.count { it == 1f })
            current = next
        }
    }

    @Test
    fun completeTilesUseAllThreePatterns() {
        val config = LifeConfig(16, 16, 20, 1, 42u)
        val initial = generateBoard(config)
        val patterns = mutableListOf<String>()
        for (tileY in 0..1) {
            for (tileX in 0..1) {
                val rows = (0..2).map { y ->
                    (0..2).map { x -> if (initial[(tileY * 8 + 2 + y) * 16 + tileX * 8 + 2 + x] == 1f) '1' else '0' }
                        .joinToString("")
                }
                patterns.add(rows.joinToString(" "))
            }
        }
        assertEquals(2, patterns.count { it == "010 001 111" })
        assertEquals(1, patterns.count { it == "110 101 010" })
        assertEquals(1, patterns.count { it == "011 110 010" })
        assertEquals(20, initial.count { it == 1f })
    }

    @Test
    fun generatorHonorsAllAcceptedPopulationSizes() {
        for ((width, height) in listOf(3 to 3, 5 to 4, 5 to 5, 8 to 8, 17 to 19)) {
            for (population in listOf(0, 1, 4, 5, 6, width * height)) {
                for (seed in listOf(0u, 42u, UInt.MAX_VALUE)) {
                    val config = LifeConfig(width, height, population, 1, seed)
                    val initial = generateBoard(config)
                    assertEquals(population, initial.count { it == 1f })
                    assertEquals(width * height, initial.size)
                    assertTrue(initial.all { it.toRawBits() == 0 || it.toRawBits() == 0x3f800000 })
                }
            }
        }
    }

    @Test
    fun originIsAbsentWhenNoGliderCanBePlanted() {
        assertNull(gliderOrigin(LifeConfig(8, 8, 4, 1, 42u)))
        assertNull(gliderOrigin(LifeConfig(5, 4, 5, 1, 42u)))
        assertNull(gliderOrigin(LifeConfig(3, 19, 5, 1, 42u)))
    }
}
