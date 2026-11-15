package dev.macmetaldemo.application

import dev.macmetaldemo.domain.*
import kotlin.test.*

class RunControllerTest {
    private class Clock : MonotonicClock { var time = 0L; override fun nowNs() = time }
    private class Algorithm(val clock: Clock) : DemoAlgorithm {
        override val info = AlgorithmInfo("test", "1", "Test", "Test algorithm")
        override val parameters = listOf(
            ParameterDefinition.Integer("items", "Items", 2, 1, 10, "Invalid items"),
            ParameterDefinition.Decimal("gain", "Gain", 1.0, 0.0, 2.0, "Invalid gain"),
        )
        override var supportedModes = setOf(ExecutionMode.CPU_SINGLE, ExecutionMode.METAL)
        var exactWork: Long? = 12
        var invalid = false
        var failure = ""
        var closed = 0
        var released = false
        var closeFailure = false
        var progressValues = listOf(0L, 1L, 2L)
        var progressUpdates: List<RunProgress>? = null
        var gpuSequence = false
        var keyAlgorithm = "test"
        var keyVersion = "1"
        override fun availability(mode: ExecutionMode) = ModeAvailability(mode, mode in supportedModes, null)
        override fun validate(values: ParameterValues): ParameterValidation =
            if (invalid) ParameterValidation.Invalid(mapOf("items" to "Invalid items")) else ParameterValidation.Valid(values)
        override fun describe(values: ParameterValues) = RunDescriptor(
            ComparisonKey(keyAlgorithm, keyVersion, "items=2", "fp32", "samples"), values, emptyMap(),
            WorkDefinition("samples", "samples", 1.0, "samples/s"), exactWork, 2, "batches", emptyList(),
        )
        override fun prepare(request: RunRequest, gpu: GpuStatusSink): PreparedRun {
            clock.time += 10
            if (failure == "prepare") { released = true; throw RunFailure("prepare") }
            return object : PreparedRun {
                override val backend = BackendInfo(1, null)
                override fun warmUp() { if (gpuSequence) gpu.publish(GpuCommandStatus(GpuCommandState.COMPLETED, 900)); clock.time += 20; if (failure == "warm") throw RunFailure("warm") }
                override fun execute(progress: ProgressSink, gpu: GpuStatusSink) {
                    if (gpuSequence) {
                        gpu.publish(GpuCommandStatus(GpuCommandState.PREPARING, null))
                        gpu.publish(GpuCommandStatus(GpuCommandState.SUBMITTED, null))
                        gpu.publish(GpuCommandStatus(GpuCommandState.COMPLETED, 42))
                        gpu.publish(GpuCommandStatus(GpuCommandState.PREPARING, null))
                        gpu.publish(GpuCommandStatus(GpuCommandState.SUBMITTED, null))
                        gpu.publish(GpuCommandStatus(GpuCommandState.COMPLETED, null))
                    }
                    val updates = progressUpdates ?: progressValues.map { value ->
                        RunProgress(value, if (failure == "total") 3 else 2, exactWork?.let { value * 6 })
                    }
                    updates.forEachIndexed { index, update ->
                        if (index == 1) clock.time += 20
                        progress.publish(update)
                    }
                    clock.time += 15
                    if (failure == "execute") throw RunFailure("execute")
                }
                override fun checkAndSummarize(): CheckedSummary {
                    clock.time += 7
                    if (failure == "summary") throw RunFailure("summary")
                    return CheckedSummary(emptyList())
                }
                override fun close() { closed++; if (failure == "close" || closeFailure) throw RunFailure("close", true) }
            }
        }
    }
    private val values = ParameterValues(mapOf("items" to ParameterValue.Integer(2), "gain" to ParameterValue.Decimal(1.0)))
    private fun request() = RunRequest("test", ExecutionMode.CPU_SINGLE, values)

    @Test fun timingAndEtaIncludeProgressPublication() {
        val clock = Clock(); val algorithm = Algorithm(clock); val snapshots = mutableListOf<RunSnapshot>()
        val outcome = RunController(AlgorithmRegistry(listOf(algorithm)), clock).run(request()) {
            snapshots.add(it)
            if (it.phase == RunPhase.RUNNING && it.progress?.completedUnits == 1L) clock.time += 5
        }
        val result = assertIs<RunOutcome.Success>(outcome).result
        assertEquals(30L, result.preparationNs); assertEquals(40L, result.computeNs); assertEquals(77L, result.totalNs)
        assertNull(snapshots.first { it.phase == RunPhase.RUNNING && it.progress?.completedUnits == 0L }.etaNs)
        assertEquals(20L, snapshots.first { it.progress?.completedUnits == 1L }.etaNs)
        assertEquals(1, algorithm.closed)
    }
    @Test fun failuresCloseAndNeverSucceed() {
        for (phase in listOf("prepare", "warm", "execute", "summary", "close")) {
            val clock = Clock(); val algorithm = Algorithm(clock); algorithm.failure = phase
            val snapshots = mutableListOf<RunSnapshot>()
            val outcome = RunController(AlgorithmRegistry(listOf(algorithm)), clock).run(request()) { snapshots.add(it) }
            assertIs<RunOutcome.Failure>(outcome, phase)
            assertEquals(if (phase == "prepare") 0 else 1, algorithm.closed, phase)
            if (phase == "prepare") assertTrue(algorithm.released)
            assertTrue(snapshots.none { it.phase == RunPhase.SUCCEEDED }, phase)
            assertTrue(snapshots.none { it.progress?.completedUnits == 2L }, phase)
        }
    }
    @Test fun invalidProgressFails() {
        for (sequence in listOf(listOf(1L, 0L), listOf(3L), listOf(1L))) {
            val clock = Clock(); val algorithm = Algorithm(clock); algorithm.progressValues = sequence
            assertIs<RunOutcome.Failure>(RunController(AlgorithmRegistry(listOf(algorithm)), clock).run(request()) {})
        }
        val clock = Clock(); val algorithm = Algorithm(clock); algorithm.failure = "total"
        assertIs<RunOutcome.Failure>(RunController(AlgorithmRegistry(listOf(algorithm)), clock).run(request()) {})
    }
    @Test fun primaryErrorSurvivesCleanupFailure() {
        val clock = Clock(); val algorithm = Algorithm(clock)
        algorithm.failure = "execute"; algorithm.closeFailure = true
        val outcome = RunController(AlgorithmRegistry(listOf(algorithm)), clock).run(request()) {}
        val failure = assertIs<RunOutcome.Failure>(outcome)
        assertTrue(failure.message.contains("execute")); assertTrue(failure.message.contains("close"))
        assertTrue(failure.fatal); assertEquals(1, algorithm.closed)
    }
    @Test fun unavailableAndUnknownRequestsDoNotPrepare() {
        val clock = Clock(); val algorithm = Algorithm(clock)
        val controller = RunController(AlgorithmRegistry(listOf(algorithm)), clock)
        assertIs<RunOutcome.Failure>(controller.run(request().copy(algorithmId = "missing")) {})
        assertIs<RunOutcome.Failure>(controller.run(request().copy(mode = ExecutionMode.CPU_PARALLEL)) {})
        assertEquals(0L, clock.time)
    }

    @Test fun algorithmWithoutExactWorkOrSingleModeRuns() {
        val clock = Clock(); val algorithm = Algorithm(clock)
        algorithm.exactWork = null; algorithm.supportedModes = setOf(ExecutionMode.METAL)
        val outcome = RunController(AlgorithmRegistry(listOf(algorithm)), clock)
            .run(request().copy(mode = ExecutionMode.METAL)) {}
        val result = assertIs<RunOutcome.Success>(outcome).result
        assertNull(result.workRate()); assertNull(RunHistory(AlgorithmRegistry(listOf(algorithm))).speedIncrease(result))
    }
    @Test fun invalidParametersFailBeforePreparation() {
        val clock = Clock(); val algorithm = Algorithm(clock); algorithm.invalid = true
        val outcome = RunController(AlgorithmRegistry(listOf(algorithm)), clock).run(request()) {}
        assertIs<RunOutcome.Failure>(outcome); assertEquals(0L, clock.time)
    }

    @Test fun missingExactWorkCountFails() {
        val clock = Clock(); val algorithm = Algorithm(clock)
        algorithm.progressUpdates = listOf(RunProgress(1, 2, null), RunProgress(2, 2, 12))
        assertIs<RunOutcome.Failure>(RunController(AlgorithmRegistry(listOf(algorithm)), clock).run(request()) {})
    }
    @Test fun workCountCannotDecreaseAfterNullUpdate() {
        val clock = Clock(); val algorithm = Algorithm(clock); algorithm.exactWork = null
        algorithm.progressUpdates = listOf(RunProgress(1, 2, 8), RunProgress(1, 2, null), RunProgress(2, 2, 7))
        assertIs<RunOutcome.Failure>(RunController(AlgorithmRegistry(listOf(algorithm)), clock).run(request()) {})
    }
    @Test fun mismatchedDescriptorIdentityFailsBeforePreparation() {
        for ((id, version) in listOf("other" to "1", "test" to "2")) {
            val clock = Clock(); val algorithm = Algorithm(clock)
            algorithm.keyAlgorithm = id; algorithm.keyVersion = version
            assertIs<RunOutcome.Failure>(RunController(AlgorithmRegistry(listOf(algorithm)), clock).run(request()) {})
            assertEquals(0L, clock.time)
        }
    }
    @Test fun failedRunDoesNotReplaceHistory() {
        val clock = Clock(); val algorithm = Algorithm(clock)
        val controller = RunController(AlgorithmRegistry(listOf(algorithm)), clock)
        val history = RunHistory(AlgorithmRegistry(listOf(algorithm)))
        val first = assertIs<RunOutcome.Success>(controller.run(request()) {}).result
        history.record(first)
        algorithm.failure = "execute"
        val failed = controller.run(request()) {}
        assertIs<RunOutcome.Failure>(failed)
        val gpu = first.copy(request = first.request.copy(mode = ExecutionMode.METAL), computeNs = 20)
        assertEquals(first.computeNs.toDouble() / 20, history.speedIncrease(gpu))
    }
    @Test fun algorithmWithoutSingleModeHasNoMatchingExistingBaseline() {
        val clock = Clock(); val algorithm = Algorithm(clock)
        algorithm.supportedModes = setOf(ExecutionMode.METAL)
        val controller = RunController(AlgorithmRegistry(listOf(algorithm)), clock)
        val result = assertIs<RunOutcome.Success>(controller.run(request().copy(mode = ExecutionMode.METAL)) {}).result
        val otherKey = result.descriptor.key.copy(algorithmId = "other")
        val otherDescriptor = RunDescriptor(otherKey, values, emptyMap(), result.descriptor.work, 12, 2, "batches", emptyList())
        val history = RunHistory(AlgorithmRegistry(listOf(algorithm)))
        history.record(result.copy(request = request().copy(algorithmId = "other"), descriptor = otherDescriptor))
        assertNull(history.speedIncrease(result))
    }

    @Test fun initialRunningSnapshotHasStartAndCountsItsPublicationTime() {
        val clock = Clock(); val algorithm = Algorithm(clock)
        var initial: RunSnapshot? = null
        val outcome = RunController(AlgorithmRegistry(listOf(algorithm)), clock).run(request()) { snapshot ->
            if (snapshot.phase == RunPhase.RUNNING && snapshot.progress?.completedUnits == 0L && initial == null) {
                initial = snapshot
                clock.time += 9
            }
        }
        assertEquals(30L, initial?.computeStartedAtNs)
        assertEquals(44L, assertIs<RunOutcome.Success>(outcome).result.computeNs)
    }
    @Test fun singleModeIsRequiredEvenWhenMatchingBaselineExists() {
        val clock = Clock(); val algorithm = Algorithm(clock)
        algorithm.supportedModes = setOf(ExecutionMode.METAL)
        val result = assertIs<RunOutcome.Success>(
            RunController(AlgorithmRegistry(listOf(algorithm)), clock).run(request().copy(mode = ExecutionMode.METAL)) {},
        ).result
        val history = RunHistory(AlgorithmRegistry(listOf(algorithm)))
        history.record(result.copy(request = request(), computeNs = 80))
        assertNull(history.speedIncrease(result))
    }

    @Test fun measuredGpuStatusRetainsOnlyLastMeasuredInterval() {
        val clock = Clock(); val algorithm = Algorithm(clock); algorithm.gpuSequence = true
        val controller = RunController(AlgorithmRegistry(listOf(algorithm)), clock)
        repeat(2) {
            val snapshots = mutableListOf<RunSnapshot>()
            assertIs<RunOutcome.Success>(controller.run(request().copy(mode = ExecutionMode.METAL)) { snapshots.add(it) })
            val statuses = snapshots.filter { it.phase == RunPhase.RUNNING && it.gpu != null }.map { it.gpu!! }
            val first = statuses.indexOfFirst { it.state == GpuCommandState.SUBMITTED }
            assertNull(statuses[first].lastDurationNs)
            val second = statuses.indexOfLast { it.state == GpuCommandState.SUBMITTED }
            assertEquals(42L, statuses[second].lastDurationNs)
            assertNull(statuses.last { it.state == GpuCommandState.COMPLETED }.lastDurationNs)
        }
    }

}
