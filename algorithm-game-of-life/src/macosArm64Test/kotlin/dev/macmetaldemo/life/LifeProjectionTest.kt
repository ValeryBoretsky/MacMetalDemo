package dev.macmetaldemo.life

import dev.macmetaldemo.domain.VisualizationFrame
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull

class LifeProjectionTest {
    @Test fun smallBoardCentersCellsWithOneBlankCharacterBetweenThem() {
        val config = LifeConfig(3, 4, 0, 1, 42u)
        val board = FloatArray(12)
        board[0] = 1f
        board[11] = 1f

        val frame = projectBoard(config, board, 7)

        assertEquals(7L, frame.completedUnits)
        assertCellDots(frame, 34, 20, true)
        assertCellDots(frame, 42, 32, true)
        assertEquals(16, countDots(frame))
        assertEquals("Full board", boardTitle(3, 4))
    }

    @Test fun largestSmallBoardUsesTheEntirePanel() {
        val config = LifeConfig(20, 15, 0, 1, 42u)
        val board = FloatArray(300) { 1f }

        val frame = projectBoard(config, board, 0)

        for (y in 0 until 60) {
            for (x in 0 until 80) assertEquals(x % 4 < 2, frame.isSet(x, y), "Dot $x,$y")
        }
        assertEquals("Full board", boardTitle(20, 15))
        assertEquals("Full board | Detail", boardTitle(21, 15))
        assertEquals("Full board | Detail", boardTitle(20, 16))
    }

    @Test fun unscaledOverviewPreservesIndividualCellsAndBlankPadding() {
        val config = LifeConfig(21, 16, 0, 1, 42u)
        val board = FloatArray(336)
        board[0] = 1f
        board[20] = 1f
        board[315] = 1f
        board[335] = 1f

        val frame = projectBoard(config, board, 1)

        assertOverview(frame, setOf(9 to 22, 29 to 22, 9 to 37, 29 to 37))
        assertBlankSeparator(frame)
    }

    @Test fun scaledOverviewIncludesEachCorner() {
        val config = LifeConfig(513, 385, 0, 1, 42u)
        val corners = listOf(0 to (0 to 15), 512 to (39 to 15), 196992 to (0 to 44), 197504 to (39 to 44))
        val board = FloatArray(513 * 385)

        for ((index, expected) in corners) {
            board[index] = 1f
            assertOverview(projectBoard(config, board, 1), setOf(expected))
            board[index] = 0f
        }
    }

    @Test fun overviewIntervalsCoverEveryColumnExactlyOnce() {
        val config = LifeConfig(513, 3, 0, 1, 42u)
        val board = FloatArray(513 * 3)

        for (x in 0 until 513) {
            board[513 + x] = 1f
            val expectedColumn = ((x + 1) * 40 - 1) / 513
            assertOverview(projectBoard(config, board, 1), setOf(expectedColumn to 29))
            board[513 + x] = 0f
        }
    }

    @Test fun overviewIntervalsCoverEveryRowExactlyOnce() {
        val config = LifeConfig(3, 385, 0, 1, 42u)
        val board = FloatArray(3 * 385)

        for (y in 0 until 385) {
            board[y * 3 + 1] = 1f
            val expectedRow = ((y + 1) * 60 - 1) / 385
            assertOverview(projectBoard(config, board, 1), setOf(19 to expectedRow))
            board[y * 3 + 1] = 0f
        }
    }

    @Test fun overviewDensityDistinguishesEmptySparseHalfAndFullRegions() {
        val config = LifeConfig(80, 120, 0, 1, 42u)
        val board = FloatArray(80 * 120)
        assertOverview(projectBoard(config, board, 1), emptySet())

        board[0] = 1f
        assertOverview(projectBoard(config, board, 1), setOf(0 to 0))

        for (y in 0 until 8) for (x in 0 until 4) board[y * 80 + x] = if (x % 2 == 0) 1f else 0f
        assertOverview(projectBoard(config, board, 1), setOf(0 to 0, 1 to 0, 0 to 1, 1 to 1))

        for (y in 0 until 8) for (x in 0 until 4) board[y * 80 + x] = 1f
        assertOverview(projectBoard(config, board, 1), (0 until 4).flatMap { y -> listOf(0 to y, 1 to y) }.toSet())
    }

    @Test fun overviewSelectsTheHighestFractionsBeforeRowOrderTies() {
        val config = LifeConfig(80, 120, 0, 1, 42u)
        val board = FloatArray(80 * 120)
        for ((x, y) in listOf(0 to 0, 2 to 0, 3 to 0, 0 to 2, 1 to 2, 0 to 3, 2 to 2, 3 to 2, 2 to 3, 3 to 3)) {
            board[y * 80 + x] = 1f
        }

        assertOverview(projectBoard(config, board, 1), setOf(1 to 0, 0 to 1, 1 to 1))
    }

    @Test fun fullOverviewExcludesPaddingFromDensity() {
        val config = LifeConfig(83, 123, 0, 1, 42u)
        val frame = projectBoard(config, FloatArray(83 * 123) { 1f }, 1)

        for (y in 0 until 60) for (x in 0 until 40) assertEquals(y < 59, frame.isSet(x, y), "Dot $x,$y")
    }

    @Test fun overviewKeepsTheLimitingAxisExact() {
        val horizontal = LifeConfig(77, 30, 0, 1, 42u)
        val horizontalFrame = projectBoard(horizontal, FloatArray(77 * 30) { 1f }, 1)
        for (y in 0 until 60) for (x in 0 until 40) {
            assertEquals(y in 22 until 37, horizontalFrame.isSet(x, y), "Horizontal dot $x,$y")
        }

        val vertical = LifeConfig(30, 88, 0, 1, 42u)
        val verticalFrame = projectBoard(vertical, FloatArray(30 * 88) { 1f }, 1)
        for (y in 0 until 60) for (x in 0 until 40) {
            assertEquals(x in 10 until 30, verticalFrame.isSet(x, y), "Vertical dot $x,$y")
        }
    }

    @Test fun overviewComparesFractionsWithUnequalRegionAreas() {
        val config = LifeConfig(83, 123, 0, 1, 42u)
        val board = FloatArray(83 * 123)
        board[26] = 1f
        board[2 * 83 + 24] = 1f

        assertOverview(projectBoard(config, board, 1), setOf(12 to 1))
    }

    @Test fun equalFractionsUseRowOrderWithUnequalLiveCounts() {
        val config = LifeConfig(83, 123, 0, 1, 42u)
        val board = FloatArray(83 * 123)
        for (x in 24 until 29) board[x] = 1f

        assertOverview(projectBoard(config, board, 1), setOf(12 to 0))
    }

    @Test fun densityUsesCellTotalsAcrossUnequalRegionAreas() {
        val config = LifeConfig(83, 123, 0, 1, 42u)
        val board = FloatArray(83 * 123)
        for (y in 0 until 8) for (x in 24 until 26) board[y * 83 + x] = 1f

        assertOverview(projectBoard(config, board, 1), setOf(12 to 0, 12 to 1, 12 to 2))
    }

    @Test fun narrowDetailShowsEachColumnOnceWithBlankPadding() {
        val config = LifeConfig(3, 40, 0, 1, 42u)
        val frame = projectBoard(config, FloatArray(120) { 1f }, 1)

        assertDetail(frame, List(9) { "000111000" })
        assertBlankSeparator(frame)
    }

    @Test fun shortDetailShowsEachRowOnceWithBlankPadding() {
        val config = LifeConfig(40, 4, 0, 1, 42u)
        val frame = projectBoard(config, FloatArray(160) { 1f }, 1)

        assertDetail(frame, listOf("000000000", "000000000", "111111111", "111111111", "111111111", "111111111", "000000000", "000000000", "000000000"))
    }

    @Test fun detailWithoutStartingGliderUsesCenterWithoutGenerationShift() {
        val config = LifeConfig(40, 20, 0, 1, 42u)
        val board = FloatArray(800)
        board[10 * 40 + 20] = 1f
        val expected = listOf("000000000", "000000000", "001000000", "000000000", "000000000", "000000000", "000000000", "000000000", "000000000")

        assertDetail(projectBoard(config, board, 1), expected)
        assertDetail(projectBoard(config, board, 1000), expected)
    }

    @Test fun smallGliderShowsEveryCellForItsFirstFourGenerations() {
        val config = LifeConfig(5, 5, 5, 4, 120509u)
        var board = generateBoard(config)
        val rows = gliderRows()

        for (generation in 0..4) {
            val frame = projectBoard(config, board, generation.toLong())
            assertEquals(generation.toLong(), frame.completedUnits)
            val expected = mutableSetOf<Pair<Int, Int>>()
            for (y in 0 until 5) for (x in 0 until 5) if (rows[generation][y][x] == '1') {
                addCellDots(expected, 30 + x * 4, 20 + y * 4)
            }
            assertFrame(frame, expected)
            board = nextBoard(board, config)
        }
    }

    @Test fun defaultGliderShowsEveryCellForItsFirstFourGenerations() {
        val config = LifeConfig(512, 384, 1000, 4, 42u)
        var board = generateBoard(config)
        val patterns = listOf(
            listOf("000000000", "000000000", "000100000", "000010000", "001110000", "000000000", "000000000", "000000000", "000000000"),
            listOf("000000000", "000000000", "000000000", "001010000", "000110000", "000100000", "000000000", "000000000", "000000000"),
            listOf("000000000", "000000000", "000000000", "000010000", "001010000", "000110000", "000000000", "000000000", "000000000"),
            listOf("000000000", "000000000", "000000000", "000100000", "000011000", "000110000", "000000000", "000000000", "000000000"),
            listOf("000000000", "000000000", "000000000", "000010000", "000001000", "000111000", "000000000", "000000000", "000000000")
        )

        for (generation in 0..4) {
            val frame = projectBoard(config, board, generation.toLong())
            assertEquals(generation.toLong(), frame.completedUnits)
            assertDetail(frame, patterns[generation])
            board = nextBoard(board, config)
        }
    }

    @Test fun detailShiftsByTwoCellsAfterEachEightGenerations() {
        val config = LifeConfig(24, 24, 5, 100, 42u)
        val board = FloatArray(24 * 24)
        val origin = assertNotNull(gliderOrigin(config))
        board[origin.y * 24 + origin.x + 1] = 1f

        val initial = List(9) { y -> if (y == 2) "000100000" else "000000000" }
        val shifted = List(9) { y -> if (y == 0) "010000000" else "000000000" }
        assertDetail(projectBoard(config, board, 0), initial)
        assertDetail(projectBoard(config, board, 7), initial)
        assertDetail(projectBoard(config, board, 8), shifted)
        assertDetail(projectBoard(config, board, 15), shifted)
        assertDetail(projectBoard(config, board, 16), List(9) { "000000000" })
        assertDetail(projectBoard(config, board, 96), initial)
    }

    @Test fun detailWrapsAtBothEdgesAndReadsTheCurrentBoard() {
        val config = LifeConfig(5, 20, 5, 1, 42u)
        val board = FloatArray(100)
        board[19 * 5 + 4] = 1f
        val frame = projectBoard(config, board, 0)

        assertDetail(frame, listOf("000100000", "000000000", "000000000", "000000000", "000000000", "000000000", "000000000", "000000000", "000000000"))
    }

    private fun gliderRows(): List<List<String>> = listOf(
        listOf("00000", "01000", "00100", "11100", "00000"),
        listOf("00000", "00000", "10100", "01100", "01000"),
        listOf("00000", "00000", "00100", "10100", "01100"),
        listOf("00000", "00000", "01000", "00110", "01100"),
        listOf("00000", "00000", "00100", "00010", "01110")
    )

    private fun nextBoard(board: FloatArray, config: LifeConfig): FloatArray = FloatArray(board.size).also {
        updateRange(board, it, config.width, config.height, 0, board.size)
    }

    private fun assertCellDots(frame: VisualizationFrame, left: Int, top: Int, alive: Boolean) {
        for (dy in 0 until 4) for (dx in 0 until 4) {
            assertEquals(alive && dx < 2, frame.isSet(left + dx, top + dy), "Cell dot ${left + dx},${top + dy}")
        }
    }

    private fun countDots(frame: VisualizationFrame): Int = (0 until 60).sumOf { y ->
        (0 until 80).count { x -> frame.isSet(x, y) }
    }

    private fun assertOverview(frame: VisualizationFrame, expected: Set<Pair<Int, Int>>) {
        for (y in 0 until 60) for (x in 0 until 40) {
            assertEquals((x to y) in expected, frame.isSet(x, y), "Overview dot $x,$y")
        }
    }

    private fun assertDetail(frame: VisualizationFrame, rows: List<String>) {
        for (y in 0 until 9) for (x in 0 until 9) {
            assertCellDots(frame, 44 + x * 4, 12 + y * 4, rows[y][x] == '1')
        }
        for (y in 0 until 12) for (x in 44 until 80) assertFalse(frame.isSet(x, y), "Top padding $x,$y")
        for (y in 48 until 60) for (x in 44 until 80) assertFalse(frame.isSet(x, y), "Bottom padding $x,$y")
    }

    private fun assertBlankSeparator(frame: VisualizationFrame) {
        for (y in 0 until 60) for (x in 40 until 44) assertFalse(frame.isSet(x, y))
    }

    private fun addCellDots(expected: MutableSet<Pair<Int, Int>>, left: Int, top: Int) {
        for (dy in 0 until 4) for (dx in 0 until 2) expected += (left + dx) to (top + dy)
    }

    private fun assertFrame(frame: VisualizationFrame, expected: Set<Pair<Int, Int>>) {
        for (y in 0 until 60) for (x in 0 until 80) {
            assertEquals((x to y) in expected, frame.isSet(x, y), "Frame dot $x,$y")
        }
    }
}
