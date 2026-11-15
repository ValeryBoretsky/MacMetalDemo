package dev.macmetaldemo.terminal

import dev.macmetaldemo.domain.*

fun parseParameter(text: String, definition: ParameterDefinition, defaultValue: ParameterValue): ParameterValue {
    val input = text.trim()
    if (input.isEmpty()) return defaultValue
    return when (definition) {
        is ParameterDefinition.Integer -> {
            val number = if (Regex("[+-]?[0-9]+").matches(input)) input.toLongOrNull() else null
            if (number == null || number !in definition.minimum..definition.maximum) throw IllegalArgumentException(definition.errorMessage)
            ParameterValue.Integer(number)
        }
        is ParameterDefinition.Decimal -> {
            val number = if (Regex("[+-]?(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)(?:[eE][+-]?[0-9]+)?").matches(input)) input.toDoubleOrNull() else null
            if (number == null || !number.isFinite() || number !in definition.minimum..definition.maximum) throw IllegalArgumentException(definition.errorMessage)
            ParameterValue.Decimal(number)
        }
    }
}

internal fun valueText(value: ParameterValue): String = when (value) {
    is ParameterValue.Integer -> value.value.toString()
    is ParameterValue.Decimal -> value.value.toString()
}

class ParameterPrompter(private val io: TerminalIo) {
    fun prompt(definitions: List<ParameterDefinition>, remembered: ParameterValues?): ParameterValues? {
        val values = mutableMapOf<String, ParameterValue>()
        for (definition in definitions) {
            val default = remembered?.get(definition.id) ?: when (definition) {
                is ParameterDefinition.Integer -> ParameterValue.Integer(definition.defaultValue)
                is ParameterDefinition.Decimal -> ParameterValue.Decimal(definition.defaultValue)
            }
            while (true) {
                io.write("${definition.label} [${valueText(default)}]: ")
                val input = io.readLine() ?: return null
                try {
                    values[definition.id] = parseParameter(input, definition, default)
                    break
                } catch (error: IllegalArgumentException) {
                    io.write("${error.message}\n")
                }
            }
        }
        return ParameterValues(values)
    }
}
