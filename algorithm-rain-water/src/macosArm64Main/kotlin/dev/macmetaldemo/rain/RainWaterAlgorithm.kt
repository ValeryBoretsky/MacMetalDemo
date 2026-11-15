package dev.macmetaldemo.rain

import dev.macmetaldemo.application.*
import dev.macmetaldemo.domain.*

class RainWaterAlgorithm(private val cpu: CpuRuntimeFactory, private val metal: MetalRuntimeFactory) : DemoAlgorithm {
    override val info = AlgorithmInfo("rain-water", "2", "Rain Water", "rain runs down sloping planks")
    override val supportedModes = ExecutionMode.entries.toSet()
    override val visualizationTitle = "Rain on sloping planks"
    override val parameters get() = RainParameters.fields
    override fun validate(values: ParameterValues) = RainParameters.validate(values)

    override fun availability(mode: ExecutionMode) =
        if (mode == ExecutionMode.METAL) metal.availability() else ModeAvailability(mode, true, null)

    override fun describe(values: ParameterValues): RunDescriptor {
        val config = RainParameters.config(values)
        val canonical = RainParameters.canonical(values)
        val settings = mapOf(
            "mass" to "integer-0..${RainConfig.CELL_CAPACITY}",
            "storage" to "exact-fp32-mass-32bit-counters",
            "boundary" to "closed-sides-open-bottom",
            "rules" to "rain-gravity-downhill-runoff-drain",
            "generator" to "seeded-planks-v2",
        )
        val work = WorkDefinition("rain-cell-ticks-v1", "cell ticks", 1_000_000.0, "million cell ticks/s")
        val numericalSettings = settings.entries.joinToString(",") { "${it.key}=${it.value}" }
        val key = ComparisonKey(info.id, info.modelVersion, canonical, numericalSettings, work.id)
        return RunDescriptor(key, values, settings, work, config.workCount, config.ticks.toLong(), "ticks", rainResultFields)
    }

    override fun prepare(request: RunRequest, gpu: GpuStatusSink): RainPreparedRun {
        require(request.algorithmId == info.id) { "Unknown algorithm." }
        val config = RainParameters.config(request.parameters)
        val availability = availability(request.mode)
        require(availability.available) { availability.reason ?: "Mode is unavailable." }
        val bytes = storageBytes(config, request.mode) + storageBytes(RainConfig.WARM_UP, request.mode)
        require(bytes <= 96L * 1024 * 1024) { "Water fields and counters exceed the 96 MiB allocation limit." }
        try {
            val scene = generateScene(config)
            val backend = if (request.mode == ExecutionMode.METAL) MetalRainBackend(metal.open(RAIN_SHADER, "rainStep"))
                else CpuRainBackend(cpu.open(request.mode, config.cellCount))
            try {
                return MeasuredRainRun(config, scene, backend)
            } catch (error: Throwable) {
                try { backend.close() } catch (cleanup: Throwable) { error.addSuppressed(cleanup) }
                throw error
            }
        } catch (_: OutOfMemoryError) {
            throw RunFailure("Cannot allocate water fields and counters. The run needs about $bytes bytes.")
        }
    }

    private fun storageBytes(config: RainConfig, mode: ExecutionMode): Long {
        // The field limits bound each product before allocation.
        val arrays = if (mode == ExecutionMode.METAL) 5L else 3L
        // Metal also needs a CPU buffer to read the integer counter bits.
        val columnArrays = if (mode == ExecutionMode.METAL) 7L else 3L
        return Float.SIZE_BYTES * (arrays * config.cellCount + columnArrays * config.width)
    }
}
