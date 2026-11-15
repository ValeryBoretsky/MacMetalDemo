package dev.macmetaldemo.app.di

import dev.macmetaldemo.app.NativeRunWorker
import dev.macmetaldemo.application.*
import dev.macmetaldemo.cpu.DefaultCpuRuntimeFactory
import dev.macmetaldemo.domain.RunRequest
import dev.macmetaldemo.life.GameOfLifeAlgorithm
import dev.macmetaldemo.metal.DefaultMetalRuntimeFactory
import dev.macmetaldemo.monitoring.MacosClock
import dev.macmetaldemo.monitoring.MacosResourceSampler
import dev.macmetaldemo.nbody.NBodyAlgorithm
import dev.macmetaldemo.rain.RainWaterAlgorithm
import dev.macmetaldemo.terminal.*
import platform.posix.usleep

fun createApplication(io: TerminalIo, screen: TerminalScreen = PlainTerminalScreen): TerminalSession {
    val cpu = DefaultCpuRuntimeFactory()
    val metal = DefaultMetalRuntimeFactory()
    val registry = AlgorithmRegistry(listOf(
        NBodyAlgorithm(cpu, metal), GameOfLifeAlgorithm(cpu, metal), RainWaterAlgorithm(cpu, metal),
    ))
    val history = RunHistory(registry)
    val clock = MacosClock()
    val worker = NativeRunWorker(RunController(registry, clock))
    val driver = object : RunDriver {
        private fun newDriver() = LiveRunDriver(worker, clock, MacosResourceSampler(clock), WaitStrategy { duration ->
            usleep((duration / 1_000).coerceAtLeast(1).toUInt())
        }, io, ::terminalCapabilities, screen, { id -> registry.find(id)?.info?.name ?: id },
            { id, values -> registry.find(id)?.visualizationTitle(values) })

        override fun run(request: RunRequest) = newDriver().run(request)
        override fun run(request: RunRequest, visualizationTitle: String?) = newDriver().run(request, visualizationTitle)
    }
    return TerminalSession(registry, driver, history, io, screen)
}
