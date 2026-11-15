package dev.macmetaldemo.nbody

import dev.macmetaldemo.nbody.metal.NBodyShaderSource
import kotlin.test.*

class NBodyShaderSourceTest {
    @Test fun embeddedFixturePreservesSpecialCharacters() {
        assertEquals("$\"\\\n", ShaderFixture.text)
    }
    @Test fun embeddedShaderProvidesKernelWithoutFileAccess() {
        assertTrue(NBodyShaderSource.text.contains("nbodyStep"))
        assertTrue(NBodyShaderSource.text.contains("thread_position_in_grid"))
    }
}
