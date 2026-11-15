package dev.macmetaldemo.terminal

import dev.macmetaldemo.application.*
import dev.macmetaldemo.domain.*

fun interface RunDriver {
    fun run(request: RunRequest): RunOutcome
    fun run(request: RunRequest, visualizationTitle: String?): RunOutcome = run(request)
}

class TerminalSession(private val registry: AlgorithmRegistry, private val driver: RunDriver,
                      private val history: RunHistory, private val io: TerminalIo,
                      private val screen: TerminalScreen = PlainTerminalScreen) {
    private val remembered = mutableMapOf<String, ParameterValues>()
    private val rememberedVisualization = mutableMapOf<String, Boolean>()

    private fun promptVisualization(default: Boolean): Boolean? {
        while (true) {
            io.write("Show visualization? ${if (default) "[Y/n]" else "[y/N]"}: ")
            val input = io.readLine()?.trim()?.lowercase() ?: return null
            when (input) {
                "" -> return default
                "y", "yes" -> return true
                "n", "no" -> return false
                else -> io.write("Select yes or no.\n")
            }
        }
    }

    fun run(): Int {
        var clearMenu = true
        while (true) {
            if (clearMenu) screen.clear()
            clearMenu = false
            io.write("Select an algorithm:\n")
            registry.entries.forEachIndexed { index, entry -> io.write("${index + 1}  ${entry.info.name} - ${entry.info.description}\n") }
            io.write("0  Exit\n")
            val choice = io.readLine()?.trim() ?: return 0
            if (choice == "0") return 0
            val algorithm = choice.toIntOrNull()?.let { registry.entries.getOrNull(it - 1) }
            if (algorithm == null) { io.write("Select a valid algorithm.\n"); continue }
            try {
            screen.clear()
            while (true) {
                io.write("Select a mode:\n")
                val labels = listOf("CPU - one thread", "CPU - all cores", "GPU - Metal")
                ExecutionMode.entries.forEachIndexed { index, mode ->
                    val state = algorithm.availability(mode)
                    val reason = if (mode !in algorithm.supportedModes) state.reason ?: "Mode is not supported." else if (!state.available) state.reason ?: "Mode is unavailable." else null
                    io.write("${index + 1}  ${labels[index]}${reason?.let { " - $it" } ?: ""}\n")
                }
                io.write("0  Back\n")
                val input = io.readLine()?.trim() ?: return 0
                if (input == "0") break
                val mode = input.toIntOrNull()?.let { ExecutionMode.entries.getOrNull(it - 1) }
                if (mode == null || mode !in algorithm.supportedModes || !algorithm.availability(mode).available) {
                    io.write("Select an available mode.\n")
                    continue
                }
                screen.clear()
                var values: ParameterValues
                while (true) {
                    values = ParameterPrompter(io).prompt(algorithm.parameters, remembered[algorithm.info.id]) ?: return 0
                    when (val checked = algorithm.validate(values)) {
                        is ParameterValidation.Valid -> { values = checked.values; break }
                        is ParameterValidation.Invalid -> checked.messages.values.forEach { io.write("$it\n") }
                    }
                }
                val title = if (screen.active) try { algorithm.visualizationTitle } catch (_: Throwable) { null } else null
                val visualization = if (title != null) {
                    promptVisualization(rememberedVisualization[algorithm.info.id] ?: true) ?: return 0
                } else false
                remembered[algorithm.info.id] = values
                if (title != null) rememberedVisualization[algorithm.info.id] = visualization
                screen.clear()
                val descriptor = algorithm.describe(values)
                io.write("Algorithm: ${algorithm.info.name}\nMode: ${labels[mode.ordinal]}\nParameters: ${descriptor.key.canonicalParameters}\n")
                io.write("Settings: ${descriptor.key.numericalSettings}\nWork: ${descriptor.exactWorkCount?.toString() ?: "N/A (exact work unavailable)"} ${descriptor.work.unit}\n")
                if (screen.active) io.write("Visualization: ${if (visualization) "on" else "off"}\n")
                when (val outcome = driver.run(RunRequest(algorithm.info.id, mode, values, visualization), title)) {
                    is RunOutcome.Failure -> {
                        screen.clear()
                        io.write("${outcome.message}\n")
                        if (screen.active) {
                            io.write("Press Enter to continue.\n")
                            if (io.readLine() == null) return 0
                        }
                        if (outcome.fatal) return 1
                    }
                    is RunOutcome.Success -> {
                        screen.clear()
                        history.record(outcome.result)
                        ResultRenderer(io, history).render(outcome.result, algorithm.info.name, screen.active)
                        if (screen.active) {
                            io.write("Press Enter to continue.\n")
                            if (io.readLine() == null) return 0
                        }
                    }
                }
                break
            }
            clearMenu = true
            } catch (error: Throwable) {
                screen.clear()
                io.write("${error.message ?: "Run failed."}\n")
                if (screen.active) {
                    io.write("Press Enter to continue.\n")
                    if (io.readLine() == null) return 0
                }
                if ((error as? RunFailure)?.fatal == true) return 1
                clearMenu = true
            }
        }
    }
}
