package integration.fixtures

import dev.macmetaldemo.application.*
import dev.macmetaldemo.domain.*
import dev.macmetaldemo.nbody.NBodyAlgorithm
import dev.macmetaldemo.nbody.domain.ParticleState

data class NBodyTestResult(val state: ParticleState, val progress: List<RunProgress>, val descriptor: RunDescriptor)

fun executeNBodyForTest(algorithm: NBodyAlgorithm, request: RunRequest): NBodyTestResult {
    val gpu = GpuStatusSink { }
    val run = algorithm.prepare(request, gpu)
    try {
        val progress = mutableListOf<RunProgress>()
        run.warmUp()
        run.execute(ProgressSink { progress.add(it) }, gpu)
        run.checkAndSummarize()
        return NBodyTestResult(run.finalStateCopy(), progress.toList(), algorithm.describe(request.parameters))
    } finally { run.close() }
}
