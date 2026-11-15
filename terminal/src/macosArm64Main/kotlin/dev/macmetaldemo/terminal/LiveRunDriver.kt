package dev.macmetaldemo.terminal

import dev.macmetaldemo.application.*
import dev.macmetaldemo.domain.*

class LiveRunDriver(
    private val worker: RunWorker,
    private val clock: MonotonicClock,
    private val sampler: ResourceSampler,
    private val wait: WaitStrategy,
    private val io: TerminalIo,
    private val capabilities: () -> TerminalCapabilities,
    private val screen: TerminalScreen = PlainTerminalScreen,
    private val algorithmName: (String) -> String = { it },
    private val visualizationTitle: (String, ParameterValues) -> String? = { _, _ -> null },
) : RunDriver {
    override fun run(request: RunRequest): RunOutcome {
        val title = if (request.visualizationEnabled && screen.active) {
            try { visualizationTitle(request.algorithmId, request.parameters) } catch (_: Throwable) { null }
        } else null
        return run(request, title)
    }

    override fun run(request: RunRequest, visualizationTitle: String?): RunOutcome {
        val renderer = PanelRenderer()
        var resources = ResourceSnapshot(0, emptyList(), null, "CPU sampling is unavailable.")
        var nextSample = 0L
        var lastRender: Long? = null
        var fixedPanelVisible = false
        fun erasePanel() {
            if (fixedPanelVisible) screen.clear()
            fixedPanelVisible = false
        }
        try {
            val handle = worker.startObserved(request)
            try {
                var drawingEnabled = request.visualizationEnabled && visualizationTitle != null
                val title = visualizationTitle
                fun setVisibility(visible: Boolean): Boolean {
                    return try {
                        handle.setVisualizationVisible(visible)
                        visible
                    } catch (_: Throwable) {
                        drawingEnabled = false
                        if (visible) {
                            try { handle.setVisualizationVisible(false) } catch (_: Throwable) { }
                        }
                        false
                    }
                }
                fun renderTransitions() {
                    val transitions = handle.drainTransitions()
                    if (transitions.isNotEmpty()) {
                        if (transitions.any { it.phase == RunPhase.PREPARING || it.phase == RunPhase.WARMING_UP }) erasePanel()
                        val state = RunSnapshotRenderer(io)
                        transitions.forEach { transition ->
                            state.render(transition)
                            if (transition.phase == RunPhase.WARMING_UP && transition.backend != null) {
                                handle.acknowledgeContext()
                            }
                        }
                    }
                }
                while (true) {
                    val version = handle.changeVersion()
                    renderTransitions()
                    val done = handle.outcome()
                    if (done != null) {
                        setVisibility(false)
                        renderTransitions()
                        if (!screen.active && done is RunOutcome.Success && request.mode == ExecutionMode.METAL) {
                            val final = handle.latest()
                            val gpu = final.gpu
                            io.write("GPU: ${final.backend?.deviceName ?: "N/A"}  ${gpu?.state ?: "N/A"}\n")
                            val duration = formatDuration(gpu?.lastDurationNs)
                            io.write("GPU command duration: $duration\n")
                        }
                        val completed = handle.awaitCompletion()
                        return if (completed is RunOutcome.Success && request.mode == ExecutionMode.METAL) {
                            RunOutcome.Success(completed.result.copy(gpu = handle.latest().gpu))
                        } else completed
                    }
                    var now = clock.nowNs()
                    if (now >= nextSample) {
                        if (nextSample > 0L && request.mode == ExecutionMode.METAL) {
                            val missed = ((now - nextSample) / 500_000_000L).coerceIn(0, 20).toInt()
                            repeat(missed) { renderer.recordMissingGpu() }
                        }
                        resources = try { sampler.sample() } catch (error: Throwable) {
                            ResourceSnapshot(now, resources.cores.map { CoreLoad(it.index, null) }, null,
                                error.message ?: "CPU sampling is unavailable.")
                        }
                        if (request.mode == ExecutionMode.METAL) renderer.recordGpu(handle.latest(), resources)
                        nextSample = now + 500_000_000L
                        now = clock.nowNs()
                    }
                    val visibleResources = if (now - resources.sampledAtNs >= 1_500_000_000L) {
                        ResourceSnapshot(resources.sampledAtNs, resources.cores.map { CoreLoad(it.index, null) }, null,
                            resources.unavailableReason ?: "CPU sample is unavailable.", resources.gpus)
                    } else resources
                    val caps = capabilities()
                    val snapshot = handle.latest()
                    val descriptor = snapshot.descriptor
                    val context = if (screen.active && descriptor != null) {
                        val backend = snapshot.backend
                        listOf(
                            "Algorithm: ${algorithmName(request.algorithmId)}",
                            "Mode: ${request.mode}",
                            "Visualization: ${if (request.visualizationEnabled) "on" else "off"}",
                            "Parameters: ${descriptor.key.canonicalParameters}",
                            "Settings: ${descriptor.key.numericalSettings}",
                            "Work: ${descriptor.exactWorkCount?.toString() ?: "N/A (exact work unavailable)"} ${descriptor.work.unit}",
                        ) + listOfNotNull(backend?.workerCount?.let { "CPU workers: $it" },
                            backend?.deviceName?.let { "Device: $it" })
                    } else emptyList()
                    fun measurements(space: TerminalCapabilities, contextRows: List<String>): List<String> {
                        val available = space.copy(rows = (space.rows - contextRows.size).coerceAtLeast(0))
                        return contextRows + renderer.lines(snapshot, visibleResources, now, !caps.unicode, available)
                    }
                    fun fixedMeasurements(space: TerminalCapabilities) =
                        measurements(space, wrapContext(context, space.columns - 1))
                    val canShow = drawingEnabled && screen.active && title != null && descriptor != null && snapshot.phase == RunPhase.RUNNING
                    val layout = if (canShow) renderer.fitVisualization(caps, ::fixedMeasurements) else null
                    val selectedMeasurements = layout?.measurements ?: fixedMeasurements(caps)
                    val visible = setVisibility(layout != null)
                    val fixed = screen.active && (descriptor != null || snapshot.phase != RunPhase.PREPARING) &&
                        renderer.fits(selectedMeasurements, caps)
                    val interval = if (fixed) 200_000_000L else 1_000_000_000L
                    if (lastRender == null || now - lastRender >= interval) {
                        var lines = selectedMeasurements
                        if (visible && layout != null) {
                            try {
                                handle.requestVisualization()
                                val frame = handle.latestVisualization()
                                if (frame != null) {
                                    val count = "${frame.completedUnits}/${descriptor!!.progressTotal}"
                                    val separator = if (caps.unicode) " · " else " - "
                                    val titleWidth = (PICTURE_COLUMNS - separator.length - count.length).coerceAtLeast(0)
                                    val safeTitle = title!!.map { if (it in ' '..'~') it else '?' }.joinToString("")
                                    val label = if (titleWidth == 0) count else safeTitle.take(titleWidth) + separator + count
                                    val picture = listOf(label.padEnd(PICTURE_COLUMNS)) + renderVisualization(frame, !caps.unicode)
                                    lines = layout.withPicture(picture)
                                }
                            } catch (_: Throwable) {
                                drawingEnabled = false
                                setVisibility(false)
                            }
                        }
                        if (fixed) {
                            io.write(fixedPanelText(lines))
                            fixedPanelVisible = true
                        } else {
                            erasePanel()
                            val scrolling = if (context.isEmpty()) lines else measurements(caps, context)
                            io.write(scrolling.joinToString("\n", postfix = "\n"))
                        }
                        lastRender = now
                    }
                    val nextRender = lastRender + interval
                    handle.waitForChange(version, (minOf(nextSample, nextRender) - now).coerceAtLeast(1L), wait)
                }
            } catch (error: Throwable) {
                try { handle.setVisualizationVisible(false) } catch (_: Throwable) { }
                handle.cancelContext()
                handle.awaitCompletion()
                throw error
            }
        } finally {
            (sampler as? AutoCloseable)?.close()
        }
    }
}

private fun wrapContext(lines: List<String>, width: Int): List<String> {
    if (width <= 0 || lines.all { it.length <= width }) return lines
    return buildList {
        for (line in lines) {
            var start = 0
            while (line.length - start > width) {
                val prefix = line.substring(start, start + width)
                val separator = prefix.indexOfLast { it == ',' || it == ' ' }
                val size = if (separator > 0) separator + 1 else width
                add(prefix.take(size).trimEnd())
                start += size
            }
            add(line.substring(start))
        }
    }
}

private fun fixedPanelText(lines: List<String>): String = buildString {
    // Replace each row before clearing its unused cells. The layout reserves the last screen row.
    append("\u001b[H")
    for (line in lines) {
        append(line)
        append("\u001b[K\r\n")
    }
    append("\u001b[J")
}
