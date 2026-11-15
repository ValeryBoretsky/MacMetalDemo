package dev.macmetaldemo.rain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class RainResultTest {
    private val config = RainConfig(32, 24, 4, 1, 42u, 6)
    private val scene get() = generateScene(config)
    private fun counts(value: Int) = IntArray(config.width).also { it[1] = value }

    @Test fun checksRainAgainstIndependentDrainAndStoredMass() {
        val mass = FloatArray(config.cellCount)
        mass[config.width + 1] = 5f
        val fields = summarizeRain(config, scene.walls, mass, counts(8), counts(3)).fields
        assertEquals(listOf("8", "3", "5", "16"), fields.take(4).map { it.value })
        assertFailsWith<IllegalStateException> { summarizeRain(config, scene.walls, mass, counts(7), counts(3)) }
    }

    @Test fun sumsLargeColumnCountersAndHashesTheHighByteOfCellMass() {
        val heavy = config.copy(ticks = 1000000, dropsPerTick = 4096)
        val mass = FloatArray(heavy.cellCount)
        val walls = scene.walls
        val drained = (1 shl 24) + 1
        fun fields() = summarizeRain(heavy, walls, mass, counts(drained + mass[1].toInt()), counts(drained)).fields
        val dryHash = fields().last().value
        mass[1] = 256f
        val full = fields()
        assertEquals((drained + 256).toString(), full.first().value)
        assertEquals(drained.toString(), full[1].value)
        kotlin.test.assertNotEquals(dryHash, full.last().value)
    }

    @Test fun rejectsWetWallsAndInvalidEncodings() {
        for (bad in listOf(1.25f, -0f, Float.NaN, -1f, 257f, Float.POSITIVE_INFINITY)) {
            val mass = FloatArray(config.cellCount)
            mass[1] = bad
            assertFailsWith<IllegalStateException> { summarizeRain(config, scene.walls, mass, counts(0), counts(0)) }
        }
        val mass = FloatArray(config.cellCount).also { it[0] = 2f }
        assertFailsWith<IllegalStateException> { summarizeRain(config, scene.walls, mass, counts(2), counts(0)) }
        assertFailsWith<IllegalStateException> {
            summarizeRain(config, scene.walls, FloatArray(config.cellCount), counts(257), counts(257))
        }
    }
}
