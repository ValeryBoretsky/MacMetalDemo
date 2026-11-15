package dev.macmetaldemo.domain

import kotlin.test.*

class RunRecordsTest {
    @Test
    fun progressUsesLongUnits() {
        val total = Int.MAX_VALUE.toLong() + 20
        assertEquals(total, RunProgress(1, total, total).totalUnits)
        assertFailsWith<IllegalArgumentException> { RunProgress(0, 0, null) }
    }

    @Test
    fun comparisonIdentityIncludesAllFields() {
        val key = ComparisonKey("a", "v", "p", "n", "w")
        assertNotEquals(key, key.copy(algorithmId = "x"))
        assertNotEquals(key, key.copy(modelVersion = "x"))
        assertNotEquals(key, key.copy(canonicalParameters = "x"))
        assertNotEquals(key, key.copy(numericalSettings = "x"))
        assertNotEquals(key, key.copy(workDefinitionId = "x"))
    }

    @Test
    fun resourceValuesCanBeUnavailable() {
        val snapshot = ResourceSnapshot(1, listOf(CoreLoad(0, null)), 800.0, null)
        assertNull(snapshot.cores[0].percent)
        assertEquals(800.0, snapshot.processPercent)
    }
    @Test
    fun collectionRecordsProtectInputsAndReturnedViews() {
        val settings = mutableMapOf("precision" to "FP32")
        val definitions = mutableListOf(ResultFieldDefinition("sum", "Sum", null))
        val descriptor = RunDescriptor(ComparisonKey("a", "v", "p", "n", "w"), ParameterValues(emptyMap()), settings,
            WorkDefinition("w", "item", 1.0, "items per second"), null, 1, "step", definitions)
        settings["precision"] = "changed"
        definitions.clear()
        (descriptor.numericalSettings as MutableMap)["precision"] = "changed again"
        (descriptor.resultFields as MutableList).clear()
        assertEquals("FP32", descriptor.numericalSettings["precision"])
        assertEquals(1, descriptor.resultFields.size)
        val fields = mutableListOf(ResultFieldValue("sum", "1"))
        val summary = CheckedSummary(fields)
        fields.clear()
        (summary.fields as MutableList).clear()
        assertEquals(1, summary.fields.size)
        val cores = mutableListOf(CoreLoad(0, 30.0))
        val resource = ResourceSnapshot(0, cores, null, null)
        cores.clear()
        (resource.cores as MutableList).clear()
        assertEquals(1, resource.cores.size)
    }

    @Test
    fun duplicateResultIdsAreRejected() {
        val field = ResultFieldValue("sum", "1")
        assertFailsWith<IllegalArgumentException> { CheckedSummary(listOf(field, field)) }
    }
}
