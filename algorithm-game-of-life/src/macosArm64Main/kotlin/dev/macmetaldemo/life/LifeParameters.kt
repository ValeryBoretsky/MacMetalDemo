package dev.macmetaldemo.life

import dev.macmetaldemo.application.ParameterValidation
import dev.macmetaldemo.domain.*

internal object LifeParameters {
    private val width = ParameterDefinition.Integer("width", "Board width", 512, 3, 4096, "Enter a width from 3 to 4096.")
    private val height = ParameterDefinition.Integer("height", "Board height", 384, 3, 4096, "Enter a height from 3 to 4096.")
    private val population = ParameterDefinition.Integer("initialLiveCells", "Live cells at start", 1000, 0, 4096L * 4096,
        "Enter a count within the board capacity.")
    private val generations = ParameterDefinition.Integer("generations", "Generation count", 1000, 1, 1_000_000,
        "Enter a generation count from 1 to 1000000.")
    private val seed = ParameterDefinition.Integer("seed", "Seed", 42, 0, UInt.MAX_VALUE.toLong(), "Enter a seed from 0 to 4294967295.")
    val fields = listOf(width, height, population, generations, seed)

    private fun integer(values: ParameterValues, field: ParameterDefinition.Integer): Long? =
        (values[field.id] as? ParameterValue.Integer)?.value?.takeIf { it in field.minimum..field.maximum }

    fun resolve(field: ParameterDefinition, accepted: ParameterValues): ParameterDefinition {
        if (field.id != population.id) return field
        val w = integer(accepted, width) ?: return population
        val h = integer(accepted, height) ?: return population
        val capacity = checkedLifeProduct(w, h)
        return population.copy(defaultValue = minOf(population.defaultValue, capacity), maximum = capacity,
            errorMessage = "Enter a count from 0 to $capacity for this $w by $h board.")
    }

    fun validate(values: ParameterValues): ParameterValidation {
        val errors = mutableMapOf<String, String>()
        for (field in fields) {
            val resolved = resolve(field, values) as ParameterDefinition.Integer
            if (integer(values, resolved) == null) errors[field.id] = resolved.errorMessage
        }
        for (id in values.asMap().keys) {
            if (fields.none { it.id == id }) errors[id] = "Unknown parameter: $id."
        }
        return if (errors.isEmpty()) ParameterValidation.Valid(values) else ParameterValidation.Invalid(errors)
    }

    fun config(values: ParameterValues): LifeConfig {
        val validation = validate(values)
        if (validation is ParameterValidation.Invalid) throw IllegalArgumentException(validation.messages.values.joinToString(" "))
        return LifeConfig(integer(values, width)!!.toInt(), integer(values, height)!!.toInt(),
            integer(values, population)!!.toInt(), integer(values, generations)!!.toInt(), integer(values, seed)!!.toUInt())
    }
}
