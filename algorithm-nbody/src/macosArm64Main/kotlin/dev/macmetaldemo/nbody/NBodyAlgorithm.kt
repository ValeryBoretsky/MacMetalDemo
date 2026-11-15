package dev.macmetaldemo.nbody

import dev.macmetaldemo.application.*
import dev.macmetaldemo.domain.*
import dev.macmetaldemo.nbody.cpu.NBodyCpuRun
import dev.macmetaldemo.nbody.metal.*
import dev.macmetaldemo.nbody.domain.NBodyConfig

class NBodyAlgorithm(private val cpuFactory: CpuRuntimeFactory, private val metalFactory: MetalRuntimeFactory) : DemoAlgorithm {
    override val visualizationTitle = "Particle positions"
    override val info = AlgorithmInfo("nbody", "1", "N-body", "particles under mutual gravity")
    override val parameters = listOf(
        ParameterDefinition.Integer("N", "Particle count", 16384, 2, 262144, "Enter a particle count from 2 to 262144."),
        ParameterDefinition.Integer("S", "Step count", 1000, 1, 1000000, "Enter a step count from 1 to 1000000."),
        ParameterDefinition.Integer("seed", "Seed", 42, 0, 4294967295L, "Enter a seed from 0 to 4294967295."),
    )
    override val supportedModes = ExecutionMode.entries.toSet()
    override fun availability(mode: ExecutionMode) = if (mode == ExecutionMode.METAL) metalFactory.availability() else ModeAvailability(mode, true, null)
    override fun validate(values: ParameterValues): ParameterValidation {
        val normalized = ParameterValues(values.asMap() + ("seed" to (values["seed"] ?: ParameterValue.Integer(42))))
        val errors = mutableMapOf<String, String>()
        for (definition in parameters) {
            val value = normalized[definition.id] as? ParameterValue.Integer
            if (value == null || value.value !in definition.minimum..definition.maximum) errors[definition.id] = definition.errorMessage
        }
        for (id in values.asMap().keys - parameters.map { it.id }.toSet()) errors[id] = "Unknown parameter."
        return if (errors.isEmpty()) ParameterValidation.Valid(normalized) else ParameterValidation.Invalid(errors)
    }
    private fun config(values: ParameterValues): NBodyConfig {
        val normalized = (validate(values) as? ParameterValidation.Valid)?.values ?: throw IllegalArgumentException("Invalid parameters.")
        return NBodyConfig((normalized["N"] as ParameterValue.Integer).value.toInt(), (normalized["S"] as ParameterValue.Integer).value.toInt(), (normalized["seed"] as ParameterValue.Integer).value.toUInt())
    }
    override fun describe(values: ParameterValues): RunDescriptor {
        val config = config(values)
        val settings = mapOf("G" to "1.0", "dt" to "0.001", "epsilon" to "0.01", "precision" to "FP32", "seed" to config.seed.toString())
        val work = WorkDefinition("directed-interactions-v1", "interactions", 1000000.0, "million interactions/s")
        val key = ComparisonKey(info.id, info.modelVersion, "N=${config.particleCount},S=${config.stepCount},seed=${config.seed}", settings.entries.joinToString(",") { "${it.key}=${it.value}" }, work.id)
        return RunDescriptor(key, (validate(values) as ParameterValidation.Valid).values, settings, work, config.workCount(), config.stepCount.toLong(), "steps", summaryIds.map { ResultFieldDefinition(it, it, null) })
    }
    override fun prepare(request: RunRequest, gpu: GpuStatusSink): NBodyPreparedRun {
        require(request.algorithmId == info.id && availability(request.mode).available)
        val config = config(request.parameters)
        return if (request.mode == ExecutionMode.METAL) NBodyMetalRun(config, metalFactory.open(NBodyShaderSource.text, "nbodyStep"))
        else NBodyCpuRun(config, cpuFactory.open(request.mode, config.particleCount))
    }
}
