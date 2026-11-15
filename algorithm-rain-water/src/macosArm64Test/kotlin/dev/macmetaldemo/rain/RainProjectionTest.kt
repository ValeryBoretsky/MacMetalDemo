package dev.macmetaldemo.rain

import dev.macmetaldemo.domain.VisualizationFrame
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RainProjectionTest {
    @Test fun aThinPlankKeepsAConnectedSlopeInThePicture() {
        val config = config(1024, 640)
        val walls = FloatArray(config.cellCount)
        for (x in 64..959) walls[(100 + x / 4) * config.width + x] = 1f

        val frame = projectRain(config, walls, FloatArray(config.cellCount), 19)

        assertEquals(19L, frame.completedUnits)
        var previous = -1
        for (x in 5..74) {
            val rows = (0 until 60).filter { frame.isSet(x, it) }
            assertTrue(rows.size in 1..2)
            if (previous >= 0) assertTrue(rows.first() - previous in 0..1)
            previous = rows.first()
        }
        assertTrue(previous > 27)
    }

    @Test fun heavierRainProducesMoreDotsAndLeavesOpenSpace() {
        val config = config(1024, 640)
        val walls = FloatArray(config.cellCount)
        fun dotsAt(mass: Float) = count(projectRain(config, walls, FloatArray(config.cellCount) { mass }, 1))
        val lightRain = dotsAt(2f)
        val heavyRain = dotsAt(8f)
        val runoff = dotsAt(64f)
        val full = dotsAt(RainConfig.CELL_CAPACITY.toFloat())

        assertTrue(lightRain in 100..750)
        assertTrue(heavyRain in (lightRain + 500) until runoff)
        assertTrue(runoff in 2000 until full)
        assertTrue(full in 2500..3200)
        assertEquals(0, dotsAt(0f))
    }

    @Test fun aThinWaterfallStaysVisibleAfterDownsampling() {
        val config = config(1024, 640)
        val walls = FloatArray(config.cellCount)
        val mass = FloatArray(config.cellCount)
        for (y in 0 until config.height) mass[y * config.width + 512] = RainConfig.CELL_CAPACITY.toFloat()
        val frame = projectRain(config, walls, mass, 21)
        val marked = count(frame)

        assertTrue(marked in 20..47)
        assertEquals(marked, (0 until 60).count { frame.isSet(40, it) })
    }

    @Test fun aThinLayerOfWaterStaysVisibleAfterDownsampling() {
        val config = config(1024, 640)
        val mass = FloatArray(config.cellCount)
        for (x in 0 until config.width) mass[192 * config.width + x] = RainConfig.CELL_CAPACITY.toFloat()
        val frame = projectRain(config, FloatArray(config.cellCount), mass, 21)
        val marked = count(frame)

        assertTrue(marked in 35..70)
        assertEquals(marked, (0 until 80).count { frame.isSet(it, 20) })
    }

    @Test fun oneDropPerTickStaysVisibleInTheFirstFortyTicks() {
        val config = RainConfig(1024, 640, 1, 40, 77u, 1)
        val walls = generateScene(config).walls
        var mass = FloatArray(config.cellCount)
        var next = FloatArray(config.cellCount)
        val counts = FloatArray(config.width)
        val admitted = IntArray(config.width)
        val drained = IntArray(config.width)
        val dryDots = count(projectRain(config, walls, mass, 0))
        for (tick in 0 until config.ticks) {
            fillRainCounts(config, tick, counts)
            // Gravity moves water at most one row per tick.
            val end = (tick + 2) * config.width
            for (phase in RainPhase.entries) {
                updateRainRange(config, walls, mass, next, admitted, drained, counts, tick, phase, 0, end)
                val before = mass
                mass = next
                next = before
            }
            val wetDots = count(projectRain(config, walls, mass, tick + 1L))
            assertTrue(wetDots > dryDots, "No rain dots at tick ${tick + 1}.")
        }
    }

    @Test fun theSameWaterStaysStillAcrossTicks() {
        val config = config(233, 119)
        val walls = FloatArray(config.cellCount)
        val mass = FloatArray(config.cellCount) { (it % 7).toFloat() }
        val first = projectRain(config, walls, mass, 1)
        val last = projectRain(config, walls, mass, 1000000)

        assertEquals(1000000L, last.completedUnits)
        for (y in 0 until 60) for (x in 0 until 80) assertEquals(first.isSet(x, y), last.isSet(x, y))
    }

    @Test fun wideAndTallFieldsKeepTheirEdgesAndCenterThePicture() {
        for ((w, h, extent) in listOf(Triple(1024, 640, 50), Triple(32, 2048, 12), Triple(2048, 24, 12))) {
            val config = config(w, h)
            val walls = FloatArray(config.cellCount) { 1f }
            val frame = projectRain(config, walls, FloatArray(config.cellCount), 1)
            val cols = if (w > h) 80 else extent
            val rows = if (w > h) extent else 60
            val left = (80 - cols) / 2
            val top = (60 - rows) / 2
            for (y in 0 until 60) for (x in 0 until 80) {
                assertEquals(x in left until left + cols && y in top until top + rows, frame.isSet(x, y))
            }
        }
    }

    @Test fun oddFieldShowsEveryBoundaryAndIncludesSmallCorners() {
        val config = config(107, 87)
        val walls = FloatArray(config.cellCount)
        for (y in listOf(0, 86)) for (x in listOf(0, 106)) walls[y * 107 + x] = 1f
        val frame = projectRain(config, walls, FloatArray(config.cellCount), 0)
        val displayedWidth = 107 * 60 / 87
        val left = (80 - displayedWidth) / 2

        assertEquals(4, count(frame))
        for (y in listOf(0, 59)) for (x in listOf(left, left + displayedWidth - 1)) assertTrue(frame.isSet(x, y))
    }

    private fun config(width: Int, height: Int) = RainConfig(width, height, 1, 1, 77u, 2048)

    private fun count(frame: VisualizationFrame) = (0 until 60).sumOf { y ->
        (0 until 80).count { x -> frame.isSet(x, y) }
    }
}
