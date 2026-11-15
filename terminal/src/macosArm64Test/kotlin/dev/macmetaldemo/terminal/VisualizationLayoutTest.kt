package dev.macmetaldemo.terminal

import kotlin.test.*

class VisualizationLayoutTest {
    private val measurements = listOf("Progress: 50%", "System CPU 0  10%")
    private val picture = listOf("Picture") + List(15) { "*".padEnd(40) }

    private fun layout(columns: Int, rows: Int) =
        PanelRenderer().fitVisualization(TerminalCapabilities(true, "xterm", columns, rows, false)) { measurements }

    @Test fun placesPictureBesideMeasurementsWhenBothFit() {
        val result = assertNotNull(layout(100, 20))
        assertTrue(result.withPicture(picture).first().contains("Progress: 50%"))
        assertTrue(result.withPicture(picture).first().contains("Picture"))
    }

    @Test fun sidePictureKeepsItsColumnWhenMeasurementLengthsChange() {
        val caps = TerminalCapabilities(true, "xterm", 120, 38, true)
        fun pictureColumn(rate: String): Int {
            val layout = assertNotNull(PanelRenderer().fitVisualization(caps) { listOf(rate) })
            return layout.withPicture(picture).first().indexOf("Picture")
        }
        assertEquals(pictureColumn("Rate: N/A"), pictureColumn("Rate: 551.65 million interactions/s"))
    }

    @Test fun placesPictureBelowMeasurementsWhenSideDoesNotFit() {
        val result = assertNotNull(layout(50, 25))
        assertEquals(measurements + picture, result.withPicture(picture))
    }

    @Test fun hidesPictureWhenNeitherLayoutFits() {
        assertNull(layout(40, 10))
    }
}
