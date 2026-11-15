package dev.macmetaldemo.monitoring

import dev.macmetaldemo.application.MonotonicClock
import kotlin.test.*

class MacosResourceSamplerTest {
    private class Clock : MonotonicClock { var time = 0L; override fun nowNs() = time }
    private class Readers : CpuReaders {
        var cores: List<CpuTicks>? = listOf(CpuTicks(0u, 0u, 0u, 0u))
        var process: Long? = 0
        var closed = false
        override fun readCores() = cores
        override fun readProcessNs() = process
        override fun close() { closed = true }
    }

    @Test fun gpuAndCpuFailuresRemainIndependent() {
        val clock = Clock()
        val reader = Readers()
        var failGpu = true
        val sampler = MacosResourceSampler(clock, reader, GpuReader {
            if (failGpu) error("GPU error")
            listOf(dev.macmetaldemo.domain.GpuLoad(7u, 16, 30.0))
        })
        sampler.sample()
        clock.time = 10
        reader.cores = listOf(CpuTicks(10u, 0u, 0u, 10u))
        reader.process = 10
        val gpuFailed = sampler.sample()
        assertTrue(gpuFailed.gpus.isEmpty())
        assertEquals(50.0, gpuFailed.cores.single().percent)
        assertEquals(100.0, gpuFailed.processPercent)
        failGpu = false
        reader.cores = null
        assertEquals(30.0, sampler.sample().gpus.single().percent)
        assertNotNull(sampler.sample().unavailableReason)
    }
    @Test fun failureAndCoreChangesResetBaselines() {
        val clock = Clock()
        val reader = Readers()
        val sampler = MacosResourceSampler(clock, reader)
        assertNull(sampler.sample().cores.single().percent)
        clock.time = 100
        reader.cores = listOf(CpuTicks(20u, 10u, 10u, 60u))
        reader.process = 800
        assertEquals(40.0, sampler.sample().cores.single().percent)
        reader.cores = null
        clock.time = 200
        assertNotNull(sampler.sample().processPercent)
        reader.cores = listOf(CpuTicks(30u, 10u, 10u, 60u))
        clock.time = 300
        assertNull(sampler.sample().cores.single().percent)
        clock.time = 400
        reader.cores = listOf(CpuTicks(40u, 10u, 10u, 60u), CpuTicks(0u, 0u, 0u, 0u))
        assertTrue(sampler.sample().cores.all { it.percent == null })
        sampler.close()
        assertTrue(reader.closed)
    }
    @Test fun invalidIntervalAndProcessFailureRequireFreshBaseline() {
        val clock = Clock()
        val reader = Readers()
        val sampler = MacosResourceSampler(clock, reader)
        sampler.sample()
        reader.cores = listOf(CpuTicks(10u, 0u, 0u, 10u))
        reader.process = 10
        assertNull(sampler.sample().cores.single().percent)
        assertNull(sampler.sample().processPercent)
        reader.process = null
        reader.cores = listOf(CpuTicks(20u, 0u, 0u, 20u))
        clock.time = 10
        assertNotNull(sampler.sample().unavailableReason)
        reader.process = 20
        reader.cores = listOf(CpuTicks(30u, 0u, 0u, 30u))
        clock.time = 20
        val recovery = sampler.sample()
        assertNotNull(recovery.cores.single().percent)
        assertNull(recovery.processPercent)
        clock.time = 30
        reader.process = 100
        reader.cores = listOf(CpuTicks(40u, 0u, 0u, 40u))
        val valid = sampler.sample()
        assertEquals(50.0, valid.cores.single().percent)
        assertEquals(800.0, valid.processPercent)
        sampler.close()
    }
    @Test fun independentFailuresKeepOtherSourceAndRecover() {
        val clock = Clock(); val reader = Readers(); val sampler = MacosResourceSampler(clock, reader)
        sampler.sample()
        clock.time = 10; reader.cores = listOf(CpuTicks(10u, 0u, 0u, 10u)); reader.process = null
        val processFailed = sampler.sample()
        assertEquals(50.0, processFailed.cores.single().percent); assertNull(processFailed.processPercent)
        clock.time = 20; reader.cores = null; reader.process = 20
        val coreFailed = sampler.sample()
        assertEquals(0, coreFailed.cores.single().index); assertNull(coreFailed.cores.single().percent)
        assertNull(coreFailed.processPercent)
        clock.time = 30; reader.cores = listOf(CpuTicks(30u, 0u, 0u, 30u)); reader.process = 30
        val recovery = sampler.sample()
        assertNull(recovery.cores.single().percent); assertEquals(100.0, recovery.processPercent)
        clock.time = 40; reader.cores = listOf(CpuTicks(40u, 0u, 0u, 40u)); reader.process = 40
        val valid = sampler.sample()
        assertEquals(50.0, valid.cores.single().percent); assertEquals(100.0, valid.processPercent)
    }

}
