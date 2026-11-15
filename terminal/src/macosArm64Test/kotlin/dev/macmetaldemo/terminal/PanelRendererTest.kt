package dev.macmetaldemo.terminal

import dev.macmetaldemo.domain.*
import kotlin.test.*

class PanelRendererTest {
    @Test fun barsRoundAtHalfCellsAndUseBothAlphabets() {
        assertEquals("■■■■■■■■□□", renderBar(80.0, false))
        assertEquals("##........", renderBar(20.0, true))
        assertEquals("..........", renderBar(4.9, true))
        assertEquals("#.........", renderBar(5.0, true))
        assertEquals("#########.", renderBar(94.9, true))
        assertEquals("##########", renderBar(95.0, true))
    }

    @Test fun showsGpuCommandStateAndDurationWithoutGpuLoad() {
        val snapshot = RunSnapshot(RunPhase.RUNNING, null, BackendInfo(null, "Test GPU"), RunProgress(1, 2, 1),
            0, 100, 100, GpuCommandStatus(GpuCommandState.COMPLETED, 42_000_000))
        val text = PanelRenderer().lines(snapshot, ResourceSnapshot(0, emptyList(), null, null), 100, true).joinToString("\n")
        assertTrue(text.contains("Test GPU"))
        assertTrue(text.contains("COMPLETED"))
        assertTrue(text.contains("42 ms"))
        assertFalse(text.contains("GPU load"))
    }
    @Test fun unavailableAndSystemLabelsAreExplicit() {
        val snapshot = RunSnapshot(RunPhase.RUNNING, null, null, RunProgress(0, 1, 0), 0, 0, null, null)
        val resource = ResourceSnapshot(0, listOf(CoreLoad(0, null), CoreLoad(1, 20.0)), 120.0, null)
        val lines = PanelRenderer().lines(snapshot, resource, 0, true)
        val text = lines.joinToString("\n")
        assertTrue(text.contains("System CPU 0") && text.contains("N/A"))
        assertFalse(text.contains("System CPU 0  [..........]"))
        assertTrue(text.contains("System CPU 1") && text.contains("20"))
        assertTrue(text.contains("100% = one logical CPU"))
        assertFalse(text.contains("performance", ignoreCase = true))
        assertFalse(text.contains("efficiency", ignoreCase = true))
    }
    @Test fun elapsedStopsAtCalculationEndDuringChecks() {
        val snapshot = RunSnapshot(RunPhase.CHECKING, null, null, RunProgress(1, 2, 1),
            0, 1_234_567_890L, null, null)
        val text = PanelRenderer().lines(snapshot, ResourceSnapshot(0, emptyList(), null, null), 4_000_000_000L, true).joinToString("\n")
        assertTrue(text.contains("Elapsed: 1.234 s"))
        assertTrue(text.contains("ETA: 1.234 s"))
    }

    @Test fun rateIsUnavailableBeforeCompletedWork() {
        val snapshot = RunSnapshot(RunPhase.RUNNING, null, null, RunProgress(0, 2, null), 0, 0, null, null)
        val text = PanelRenderer().lines(snapshot, ResourceSnapshot(0, emptyList(), null, null), 0, true).joinToString("\n")
        assertTrue(text.contains("Rate: N/A"))
    }

    @Test fun submittedGpuShowsLastCompletedDuration() {
        val snapshot = RunSnapshot(RunPhase.RUNNING, null, BackendInfo(null, "Test GPU"), RunProgress(1, 2, 1),
            0, 100, 100, GpuCommandStatus(GpuCommandState.SUBMITTED, 42_000_000))
        val text = PanelRenderer().lines(snapshot, ResourceSnapshot(0, emptyList(), null, null), 100, true).joinToString("\n")
        assertTrue(text.contains("GPU: Test GPU  SUBMITTED"))
        assertTrue(text.contains("GPU command duration: 42 ms"))
        assertFalse(text.contains("GPU load"))
    }

    @Test fun unavailableGpuDurationIsShownAsUnavailable() {
        val snapshot = RunSnapshot(RunPhase.RUNNING, null, BackendInfo(null, "Test GPU"), RunProgress(1, 2, 1),
            0, 100, 100, GpuCommandStatus(GpuCommandState.SUBMITTED, null))
        val lines = PanelRenderer().lines(snapshot, ResourceSnapshot(0, emptyList(), null, null), 100, true)

        assertTrue(lines.contains("GPU command duration: N/A"))
    }

    @Test fun unknownTotalWorkHasNoLiveRate() {
        val values = ParameterValues(emptyMap())
        val descriptor = RunDescriptor(ComparisonKey("test", "1", "", "", "work"), values, emptyMap(),
            WorkDefinition("work", "samples", 1.0, "samples/s"), null, 2, "batches", emptyList())
        val snapshot = RunSnapshot(RunPhase.RUNNING, descriptor, null, RunProgress(1, 2, 5), 0, 1, null, null)
        assertTrue(PanelRenderer().lines(snapshot, ResourceSnapshot(0, emptyList(), null, null), 1, true).contains("Rate: N/A"))
    }
    @Test fun unknownTerminalSizeDoesNotFitPanel() {
        assertFalse(PanelRenderer().fits(listOf("Progress"), capabilities(true, "xterm", null, true)))
    }

    @Test fun unknownWidthDoesNotAddEmptyGpuRows() {
        val snapshot = RunSnapshot(RunPhase.RUNNING, null, BackendInfo(null, "Test GPU"), null,
            0, 0, null, GpuCommandStatus(GpuCommandState.SUBMITTED, null))
        val lines = PanelRenderer().lines(snapshot, ResourceSnapshot(0, emptyList(), null, null), 0, true,
            TerminalCapabilities(false, null, 0, 0, false))
        assertFalse(lines.any { it.isEmpty() })
    }

    @Test fun compactCpuRowsFitShortTerminal() {
        val snapshot = RunSnapshot(RunPhase.RUNNING, null, null, null, 0, 0, null, null)
        val cores = (0 until 8).map { CoreLoad(it, 50.0) }
        val caps = TerminalCapabilities(true, "xterm", 100, 8, false)
        val lines = PanelRenderer().lines(snapshot, ResourceSnapshot(0, cores, null, null), 0, true, caps)
        assertTrue(PanelRenderer().fits(lines, caps))
        for (core in cores) assertTrue(lines.any { it.contains("System CPU ${core.index}") })
    }

}
