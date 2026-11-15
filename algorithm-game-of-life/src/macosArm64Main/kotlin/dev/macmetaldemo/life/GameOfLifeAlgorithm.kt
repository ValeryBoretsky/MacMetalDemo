package dev.macmetaldemo.life

import dev.macmetaldemo.application.*
import dev.macmetaldemo.domain.*

class GameOfLifeAlgorithm(private val cpuFactory: CpuRuntimeFactory, private val metalFactory: MetalRuntimeFactory) : DemoAlgorithm {
    override val info = AlgorithmInfo("game-of-life", "1", "Game of Life", "cells evolve from their eight neighbors")
    override val parameters get() = LifeParameters.fields
    override val supportedModes = ExecutionMode.entries.toSet()
    override val visualizationTitle = "Full board | Detail"

    override fun visualizationTitle(values: ParameterValues): String {
        val config = LifeParameters.config(values)
        return boardTitle(config.width, config.height)
    }

    override fun resolveParameter(definition: ParameterDefinition, accepted: ParameterValues) = LifeParameters.resolve(definition, accepted)
    override fun validate(values: ParameterValues) = LifeParameters.validate(values)
    override fun availability(mode: ExecutionMode) =
        if (mode == ExecutionMode.METAL) metalFactory.availability() else ModeAvailability(mode, true, null)

    override fun describe(values: ParameterValues): RunDescriptor {
        val config = LifeParameters.config(values)
        val settings = mapOf("rule" to "B3/S23", "boundary" to "torus", "storage" to "binary-fp32", "generator" to "patterns-lcg32-v1")
        val work = WorkDefinition("cell-updates-v1", "cell updates", 1_000_000.0, "million cell updates/s")
        val canonical = parameters.joinToString(",") { "${it.id}=${(values[it.id] as ParameterValue.Integer).value}" }
        val key = ComparisonKey(info.id, info.modelVersion, canonical, settings.entries.joinToString(",") { "${it.key}=${it.value}" }, work.id)
        return RunDescriptor(key, values, settings, work, config.workCount, config.generations.toLong(), "generations",
            listOf(ResultFieldDefinition("liveCells", "Live cells", null), ResultFieldDefinition("boardHash", "Board hash", null)))
    }

    override fun prepare(request: RunRequest, gpu: GpuStatusSink): LifePreparedRun {
        require(request.algorithmId == info.id) { "Unknown algorithm." }
        val config = LifeParameters.config(request.parameters)
        val mode = request.mode
        val availability = availability(mode)
        require(availability.available) { availability.reason ?: "Mode is unavailable." }
        val boardStorageBytes = storageBudget(config, mode)
        val backend = if (mode == ExecutionMode.METAL) {
            MetalLifeBackend(metalFactory.open(LIFE_SHADER, "lifeStep"))
        } else {
            CpuLifeBackend(cpuFactory.open(mode, config.cellCount))
        }
        try {
            return MeasuredLifeRun(config, backend)
        } catch (error: Throwable) {
            closeAfterFailure(error, backend::close)
            if (error is OutOfMemoryError) throw RunFailure("Cannot allocate $boardStorageBytes bytes for Life boards.")
            throw error
        }
    }
}
