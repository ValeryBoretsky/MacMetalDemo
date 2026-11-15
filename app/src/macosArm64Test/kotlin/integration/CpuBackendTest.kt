package integration

import dev.macmetaldemo.cpu.DefaultCpuRuntimeFactory
import dev.macmetaldemo.domain.*
import dev.macmetaldemo.nbody.NBodyAlgorithm
import integration.fixtures.executeNBodyForTest
import kotlin.test.*

class CpuBackendTest {
    @Test fun parallelMatchesSingleAndStartsFresh() {
        val algorithm = NBodyAlgorithm(DefaultCpuRuntimeFactory(), dev.macmetaldemo.metal.DefaultMetalRuntimeFactory())
        for (n in listOf(2, 17, 128)) for (steps in listOf(1, 8)) {
            fun run(mode: ExecutionMode) = executeNBodyForTest(algorithm, RunRequest("nbody", mode,
                ParameterValues(mapOf("N" to ParameterValue.Integer(n.toLong()), "S" to ParameterValue.Integer(steps.toLong())))))
            val single = run(ExecutionMode.CPU_SINGLE)
            val parallel = run(ExecutionMode.CPU_PARALLEL)
            val repeated = run(ExecutionMode.CPU_PARALLEL)
            assertContentEquals(single.state.positionMass.map { it.toBits() }, parallel.state.positionMass.map { it.toBits() })
            assertContentEquals(single.state.velocity.map { it.toBits() }, parallel.state.velocity.map { it.toBits() })
            assertContentEquals(parallel.state.positionMass.map { it.toBits() }, repeated.state.positionMass.map { it.toBits() })
            assertContentEquals(parallel.state.velocity.map { it.toBits() }, repeated.state.velocity.map { it.toBits() })
            assertEquals(single.descriptor.exactWorkCount, parallel.descriptor.exactWorkCount)
            assertEquals((1..steps).map { RunProgress(it.toLong(), steps.toLong(), n.toLong() * (n - 1) * it) }, parallel.progress)
            assertEquals(single.progress, parallel.progress)
        }
    }
}

class CpuProgressBarrierTest {
    @Test fun progressWaitsForEveryRange() {
        val condition = platform.Foundation.NSCondition()
        var held = false
        var release = false
        var finished = false
        val records = mutableListOf<RunProgress>()
        val factory = object : dev.macmetaldemo.application.CpuRuntimeFactory {
            override fun open(mode: ExecutionMode, elementCount: Int): dev.macmetaldemo.application.CpuRuntime {
                val real = dev.macmetaldemo.cpu.OperationQueueCpuRuntime(2)
                return object : dev.macmetaldemo.application.CpuRuntime {
                    override val workerCount = real.workerCount
                    override fun forEachRange(elementCount: Int, action: (Int, Int) -> Unit) {
                        real.forEachRange(elementCount) { start, end ->
                            if (start == 0) {
                                condition.lock()
                                held = true
                                condition.broadcast()
                                while (!release) condition.wait()
                                condition.unlock()
                            }
                            action(start, end)
                        }
                    }
                    override fun close() = real.close()
                }
            }
        }
        val algorithm = NBodyAlgorithm(factory, dev.macmetaldemo.metal.DefaultMetalRuntimeFactory())
        val request = RunRequest("nbody", ExecutionMode.CPU_PARALLEL,
            ParameterValues(mapOf("N" to ParameterValue.Integer(2), "S" to ParameterValue.Integer(1))))
        val gpu = dev.macmetaldemo.application.GpuStatusSink { }
        val run = algorithm.prepare(request, gpu)
        val coordinator = platform.Foundation.NSOperationQueue()
        coordinator.addOperationWithBlock {
            run.execute(dev.macmetaldemo.application.ProgressSink {
                condition.lock()
                records.add(it)
                condition.unlock()
            }, gpu)
            condition.lock()
            finished = true
            condition.broadcast()
            condition.unlock()
        }
        condition.lock()
        val deadline = platform.Foundation.NSDate(timeIntervalSinceReferenceDate = platform.Foundation.NSDate().timeIntervalSinceReferenceDate + 5.0)
        while (!held && condition.waitUntilDate(deadline)) {}
        val observedHeld = held
        val earlyProgress = records.toList()
        release = true
        condition.broadcast()
        val finishDeadline = platform.Foundation.NSDate(timeIntervalSinceReferenceDate = platform.Foundation.NSDate().timeIntervalSinceReferenceDate + 5.0)
        while (!finished && condition.waitUntilDate(finishDeadline)) {}
        val completed = finished
        val finalProgress = records.toList()
        condition.unlock()
        coordinator.waitUntilAllOperationsAreFinished()
        run.close()
        assertTrue(observedHeld)
        assertTrue(earlyProgress.isEmpty())
        assertTrue(completed)
        assertEquals(listOf(RunProgress(1, 1, 2)), finalProgress)
    }
}
