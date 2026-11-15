package dev.macmetaldemo.terminal

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class DurationFormatterTest {
    @Test
    fun formatsDurationsAcrossUnits() {
        val cases = listOf<Pair<Long?, String>>(
            0L to "0 ns",
            999_999L to "999999 ns",
            1_000_000L to "1 ms",
            999_999_999L to "999.999 ms",
            1_000_000_000L to "1 s",
            59_999_999_999L to "59.999 s",
            60_000_000_000L to "1 min 0 s",
            3_600_000_000_000L to "1 h 0 min 0 s",
            null to "N/A",
        )

        for ((durationNs, expected) in cases) {
            assertEquals(expected, formatDuration(durationNs), "durationNs=$durationNs")
        }
    }

    @Test
    fun trimsFractionalZerosAndFormatsMixedHours() {
        val cases = listOf<Pair<Long?, String>>(
            1_010_000L to "1.01 ms",
            1_001_000L to "1.001 ms",
            1_010_000_000L to "1.01 s",
            7_325_000_000_000L to "2 h 2 min 5 s",
            3_723_000_000_000L to "1 h 2 min 3 s",
            90_000_000_000_000L to "25 h 0 min 0 s",
        )

        for ((durationNs, expected) in cases) {
            assertEquals(expected, formatDuration(durationNs), "durationNs=$durationNs")
        }
    }

    @Test
    fun formatsLargestLongWithoutOverflow() {
        assertEquals("2562047 h 47 min 16 s", formatDuration(Long.MAX_VALUE))
    }

    @Test
    fun rejectsNegativeDurations() {
        assertFailsWith<IllegalArgumentException> { formatDuration(-1) }
    }
}
