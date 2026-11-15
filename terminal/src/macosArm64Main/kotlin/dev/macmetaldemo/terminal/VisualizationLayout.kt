package dev.macmetaldemo.terminal

import dev.macmetaldemo.domain.VisualizationFrame

internal const val PICTURE_COLUMNS = VisualizationFrame.WIDTH / 2
internal const val PICTURE_ROWS = VisualizationFrame.HEIGHT / 4
private const val PANEL_ROWS = PICTURE_ROWS + 1
private const val COLUMN_GAP = 2

internal class VisualizationLayout(val measurements: List<String>, private val sideColumn: Int? = null) {
    fun withPicture(picture: List<String>): List<String> {
        if (sideColumn == null) return measurements + picture
        return List(maxOf(measurements.size, picture.size)) { row ->
            val left = measurements.getOrElse(row) { "" }
            if (row < picture.size) left.padEnd(sideColumn) + picture[row] else left
        }
    }
}

internal fun PanelRenderer.fitVisualization(
    caps: TerminalCapabilities,
    formatMeasurements: (TerminalCapabilities) -> List<String>,
): VisualizationLayout? {
    if (caps.columns <= PICTURE_COLUMNS || caps.rows <= PANEL_ROWS) return null

    val left = caps.copy(columns = caps.columns - PICTURE_COLUMNS - COLUMN_GAP)
    if (left.columns > 0) {
        val lines = formatMeasurements(left)
        if (fits(lines, left)) {
            // Reserve the last column to prevent an automatic line wrap.
            return VisualizationLayout(lines, sideColumn = caps.columns - PICTURE_COLUMNS - 1)
        }
    }
    val above = caps.copy(rows = caps.rows - PANEL_ROWS)
    val lines = formatMeasurements(above)
    return if (fits(lines, above)) VisualizationLayout(lines) else null
}
