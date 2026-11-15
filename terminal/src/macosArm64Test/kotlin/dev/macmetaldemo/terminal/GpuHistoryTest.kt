package dev.macmetaldemo.terminal

import kotlin.test.*

class GpuHistoryTest {
    @Test fun newestSamplesAppearOnRightAndMissingSamplesUseQuestionMarks() {
        val history = GpuHistory()
        history.add(0.0)
        history.add(null)
        history.add(100.0)
        assertEquals(listOf(".?#", ".?#", ".?#", ".?#", ".?#"), history.rows(true, 20))
    }
    @Test fun panelMatchesDeviceAndExpiresCurrentSample() {
        val snapshot = dev.macmetaldemo.domain.RunSnapshot(dev.macmetaldemo.domain.RunPhase.RUNNING, null,
            dev.macmetaldemo.domain.BackendInfo(null, "GPU", 7u), null, 0, null, null,
            dev.macmetaldemo.domain.GpuCommandStatus(dev.macmetaldemo.domain.GpuCommandState.SUBMITTED, null))
        val resources = dev.macmetaldemo.domain.ResourceSnapshot(1, emptyList(), null, null,
            listOf(dev.macmetaldemo.domain.GpuLoad(8u, 16, 70.0)))
        val renderer = PanelRenderer()
        assertTrue(renderer.lines(snapshot, resources, 1, true).joinToString().contains("System GPU  N/A"))
        val match = dev.macmetaldemo.domain.ResourceSnapshot(1, emptyList(), null, null,
            listOf(dev.macmetaldemo.domain.GpuLoad(7u, 16, 70.0)))
        assertTrue(renderer.lines(snapshot, match, 1, true).joinToString().contains("System GPU  70%"))
        assertTrue(renderer.lines(snapshot, match, 1_500_000_001, true).joinToString().contains("System GPU  N/A"))
    }
    @Test fun newHistoryStartsEmpty() {
        val first = GpuHistory()
        first.add(100.0)
        assertEquals(listOf("", "", "", "", ""), GpuHistory().rows(true, 20))
    }
    @Test fun historyKeepsAtMostTwentyColumns() {
        val history = GpuHistory()
        repeat(25) { history.add(100.0) }
        assertEquals(20, history.rows(true, 20).first().length)
        assertEquals(3, history.rows(true, 3).first().length)
    }
}
