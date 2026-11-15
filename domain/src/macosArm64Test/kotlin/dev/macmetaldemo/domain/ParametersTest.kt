package dev.macmetaldemo.domain

import kotlin.test.*

class ParametersTest {
    @Test
    fun parameterValuesCopyTheirInput() {
        val source = mutableMapOf<String, ParameterValue>("N" to ParameterValue.Integer(16384))
        val values = ParameterValues(source)
        source["N"] = ParameterValue.Integer(2)
        assertEquals(ParameterValue.Integer(16384), values["N"])
    }

    @Test
    fun integerAndDecimalDefinitionsKeepTypes() {
        val integer = ParameterDefinition.Integer("n", "Count", 4, 1, 10, "Invalid count")
        val decimal = ParameterDefinition.Decimal("d", "Rate", 0.5, 0.0, 1.0, "Invalid rate")
        assertEquals(4L, integer.defaultValue)
        assertEquals(1L, integer.minimum)
        assertEquals(10L, integer.maximum)
        assertEquals(0.5, decimal.defaultValue)
        assertEquals(0.0, decimal.minimum)
        assertEquals(1.0, decimal.maximum)
    }

    @Test
    fun rejectsInvalidDefinitions() {
        assertFailsWith<IllegalArgumentException> { ParameterDefinition.Integer("n", "Count", 0, 1, 2, "Invalid") }
        assertFailsWith<IllegalArgumentException> { ParameterDefinition.Decimal("d", "Rate", Double.NaN, 0.0, 1.0, "Invalid") }
    }
    @Test
    fun returnedParameterMapDoesNotExposeOwnedValues() {
        val values = ParameterValues(mapOf("n" to ParameterValue.Integer(4)))
        (values.asMap() as MutableMap)["n"] = ParameterValue.Integer(2)
        assertEquals(ParameterValue.Integer(4), values["n"])
    }
}
