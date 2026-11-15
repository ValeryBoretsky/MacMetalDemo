package integration

import dev.macmetaldemo.app.NativeVisualizationExchange
import dev.macmetaldemo.domain.VisualizationFrame
import kotlin.test.*

class VisualizationExchangeTest {
    @Test fun requestsMergeAndFramesKeepTheirOwnDots() {
        val exchange = NativeVisualizationExchange(true)
        exchange.setVisible(true)
        exchange.request()
        exchange.request()
        assertTrue(exchange.captureRequested())
        assertFalse(exchange.captureRequested())
        val dots = BooleanArray(80 * 60)
        dots[81] = true
        val frame = VisualizationFrame(1, dots)
        exchange.publish(frame)
        dots[81] = false
        assertTrue(checkNotNull(exchange.latest()).isSet(1, 1))
        exchange.setVisible(false)
        exchange.request()
        assertFalse(exchange.captureRequested())
        exchange.close()
        assertNull(exchange.latest())
        exchange.publish(frame)
        assertNull(exchange.latest())
    }

    @Test fun inactiveAndFailedExchangesCannotRequestFrames() {
        for (enabled in listOf(false, true)) {
            val exchange = NativeVisualizationExchange(enabled)
            exchange.setVisible(true)
            if (enabled) exchange.disable()
            exchange.request()
            assertFalse(exchange.captureRequested())
        }
    }

    @Test fun hidingDropsPendingAndRejectsLateFrames() {
        val exchange = NativeVisualizationExchange(true)
        exchange.setVisible(true)
        exchange.request()
        assertTrue(exchange.captureRequested())
        exchange.setVisible(false)
        exchange.publish(VisualizationFrame(1, BooleanArray(4800)))
        assertNull(exchange.latest())
        exchange.setVisible(true)
        assertFalse(exchange.captureRequested())
    }
}
