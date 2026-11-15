package dev.macmetaldemo.monitoring

import kotlin.test.*

class MachCpuCountersTest {
    @Test fun releasesAcquiredBuffersWithExactByteCountOnSuccessAndParseError() {
        val released = mutableListOf<Long>()
        val counters = MachCpuCounters(object : MachReader {
            var invalid = false
            override fun acquire(): MachBuffer = MachBuffer(1, 4, { index ->
                if (invalid && index == 2) error("Invalid counter.")
                index.toUInt()
            }, { bytes -> released.add(bytes) })
            override fun close() {}
        })
        assertEquals(listOf(CpuTicks(0u, 1u, 3u, 2u)), counters.read())
        assertEquals(listOf(16L), released)
        val broken = MachCpuCounters(object : MachReader {
            override fun acquire() = MachBuffer(1, 7, { error("Invalid counter.") }, { released.add(it) })
            override fun close() {}
        })
        assertNull(broken.read())
        assertEquals(listOf(16L, 28L), released)
    }

    @Test fun closeReleasesOwnedPort() {
        var closed = 0
        val counters = MachCpuCounters(object : MachReader {
            override fun acquire(): MachBuffer? = null
            override fun close() { closed++ }
        })
        counters.close()
        counters.close()
        assertEquals(1, closed)
    }
    @Test fun nativeReleaseFailureMakesSampleUnavailableAndResetsBaseline() {
        var releases = 0
        var nativeResult = 0
        var time = 0L
        val counters = MachCpuCounters(object : MachReader {
            override fun acquire() = MachBuffer(1, 4, { 10u }, { bytes ->
                assertEquals(16L, bytes)
                releases++
                checkMachRelease(nativeResult)
            })
            override fun close() {}
        })
        val sampler = MacosResourceSampler({ time }, object : CpuReaders {
            override fun readCores() = counters.read()
            override fun readProcessNs() = time
            override fun close() = counters.close()
        })
        sampler.sample()
        nativeResult = 5
        time = 10
        val failed = sampler.sample()
        assertTrue(failed.unavailableReason?.contains("release", ignoreCase = true) == true)
        assertEquals(100.0, failed.processPercent)
        nativeResult = 0
        time = 20
        val recovery = sampler.sample()
        assertNull(recovery.cores.single().percent)
        assertEquals(100.0, recovery.processPercent)
        assertEquals(3, releases)
        sampler.close()
    }

    @Test fun releaseCallbackExceptionDoesNotEscapeSample() {
        var releases = 0
        val counters = MachCpuCounters(object : MachReader {
            override fun acquire() = MachBuffer(1, 4, { 0u }, {
                releases++
                error("Release failed.")
            })
            override fun close() {}
        })
        val sampler = MacosResourceSampler({ 1L }, object : CpuReaders {
            override fun readCores() = counters.read()
            override fun readProcessNs() = 1L
            override fun close() = counters.close()
        })
        val snapshot = sampler.sample()
        assertTrue(snapshot.unavailableReason?.contains("release", ignoreCase = true) == true)
        assertNull(snapshot.processPercent)
        assertEquals(1, releases)
        sampler.close()
    }
}
