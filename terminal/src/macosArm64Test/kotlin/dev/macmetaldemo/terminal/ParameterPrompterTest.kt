package dev.macmetaldemo.terminal

import dev.macmetaldemo.domain.*
import kotlin.test.*

class ParameterPrompterTest {
    private val integer = ParameterDefinition.Integer("n", "Count", 5, 2, 100, "Invalid count.")
    private val decimal = ParameterDefinition.Decimal("gain", "Gain", 1.0, -2.0, 2.0, "Invalid gain.")

    @Test fun parsesIntegerAndDefault() {
        assertEquals(ParameterValue.Integer(17), parseParameter(" 17 ", integer, ParameterValue.Integer(5)))
        assertEquals(ParameterValue.Integer(9), parseParameter(" ", integer, ParameterValue.Integer(9)))
    }

    @Test fun rejectsInvalidIntegerSyntaxAndBounds() {
        for (text in listOf("1.0", "1e1", "1 7", "99999999999999999999", "9".repeat(1000), "1", "101", "١٧")) {
            val error = assertFailsWith<IllegalArgumentException> { parseParameter(text, integer, ParameterValue.Integer(5)) }
            assertEquals("Invalid count.", error.message)
        }
    }

    @Test fun parsesFiniteDecimalAndRejectsNonFiniteValues() {
        assertEquals(ParameterValue.Decimal(-1.25), parseParameter(" -1.25 ", decimal, ParameterValue.Decimal(1.0)))
        for (text in listOf("NaN", "Infinity", "-Infinity", "3", "1 0")) {
            assertFailsWith<IllegalArgumentException> { parseParameter(text, decimal, ParameterValue.Decimal(1.0)) }
        }
    }
    @Test fun repeatsPromptAfterEachInvalidValueAndStopsAtEof() {
        val inputs = listOf("1.0", "1e1", "1 7", "9".repeat(1000), "17").iterator()
        var output = ""
        val io = object : TerminalIo {
            override fun readLine(): String? = if (inputs.hasNext()) inputs.next() else null
            override fun write(text: String) { output += text }
        }
        val values = ParameterPrompter(io).prompt(listOf(integer), null)
        assertEquals(ParameterValue.Integer(17), values?.get("n"))
        assertEquals(5, Regex("Count \\[5\\]:").findAll(output).count())
        assertEquals(4, Regex("Invalid count\\.").findAll(output).count())
    }

    @Test fun rememberedDefaultsStayWithinIntegerAndDecimalLimits() {
        val cases = listOf(
            listOf(ParameterValue.Integer(1), ParameterValue.Decimal(-3.0)) to
                listOf(ParameterValue.Integer(2), ParameterValue.Decimal(-2.0)),
            listOf(ParameterValue.Integer(101), ParameterValue.Decimal(3.0)) to
                listOf(ParameterValue.Integer(100), ParameterValue.Decimal(2.0)),
        )
        for ((saved, expected) in cases) {
            val io = object : TerminalIo {
                override fun readLine() = ""
                override fun write(text: String) = Unit
            }
            val values = ParameterPrompter(io).prompt(listOf(integer, decimal),
                ParameterValues(mapOf("n" to saved[0], "gain" to saved[1])))!!
            assertEquals(expected[0], values["n"])
            assertEquals(expected[1], values["gain"])
        }
    }

    @Test fun dependentDecimalUsesOnlyEarlierAcceptedValues() {
        val inputs = listOf("3", "4", "").iterator()
        var output = ""
        val io = object : TerminalIo {
            override fun readLine() = inputs.next()
            override fun write(text: String) { output += text }
        }
        val values = ParameterPrompter(io).prompt(listOf(integer, decimal),
            ParameterValues(mapOf("n" to ParameterValue.Integer(8), "gain" to ParameterValue.Decimal(9.0)))) { definition, accepted ->
            if (definition.id == "n") {
                assertTrue(accepted.asMap().isEmpty())
                definition
            } else {
                assertEquals(setOf("n"), accepted.asMap().keys)
                val limit = (accepted["n"] as ParameterValue.Integer).value.toDouble()
                (definition as ParameterDefinition.Decimal).copy(maximum = limit, errorMessage = "Gain must not exceed $limit.")
            }
        }!!
        assertEquals(ParameterValue.Decimal(3.0), values["gain"])
        assertContains(output, "Gain [3.0]: Gain must not exceed 3.0.\nGain [3.0]:")
    }
}
