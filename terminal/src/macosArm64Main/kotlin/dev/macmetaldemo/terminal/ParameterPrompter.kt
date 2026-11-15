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
    fun prompt(
        definitions: List<ParameterDefinition>,
        remembered: ParameterValues?,
        resolve: (ParameterDefinition, ParameterValues) -> ParameterDefinition = { definition, _ -> definition },
    ): ParameterValues? {
        val values = mutableMapOf<String, ParameterValue>()
        for (declared in definitions) {
            val definition = resolve(declared, ParameterValues(values))
            val default = defaultValue(definition, remembered?.get(definition.id))
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

    private fun defaultValue(definition: ParameterDefinition, remembered: ParameterValue?): ParameterValue =
        when (definition) {
            is ParameterDefinition.Integer -> ParameterValue.Integer(
                ((remembered as? ParameterValue.Integer)?.value ?: definition.defaultValue)
                    .coerceIn(definition.minimum, definition.maximum))
            is ParameterDefinition.Decimal -> ParameterValue.Decimal(
                ((remembered as? ParameterValue.Decimal)?.value?.takeIf { it.isFinite() } ?: definition.defaultValue)
                    .coerceIn(definition.minimum, definition.maximum))
        }
}
