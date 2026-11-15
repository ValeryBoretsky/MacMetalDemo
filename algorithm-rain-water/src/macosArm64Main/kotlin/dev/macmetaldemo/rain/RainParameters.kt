package dev.macmetaldemo.rain

import dev.macmetaldemo.application.ParameterValidation
import dev.macmetaldemo.domain.ParameterDefinition
import dev.macmetaldemo.domain.ParameterValue
import dev.macmetaldemo.domain.ParameterValues

internal object RainParameters {
    private val width = field("width", "Field width", 1024, RainConfig.WIDTH_RANGE)
    private val height = field("height", "Field height", 640, RainConfig.HEIGHT_RANGE)
    private val drops = field("dropsPerTick", "Drops per tick", 2048, RainConfig.DROP_COUNT_RANGE)
    private val mass = field("rainMass", "Mass per drop", 1, RainConfig.DROP_MASS_RANGE)
    private val ticks = field("ticks", "Tick count", 4096, RainConfig.TICK_RANGE)
    private val seed = field("seed", "Seed", 77, 0L..UInt.MAX_VALUE.toLong())
    val fields = listOf(width, height, drops, mass, ticks, seed)

    fun validate(values: ParameterValues): ParameterValidation {
        val errors = mutableMapOf<String, String>()
        for (field in fields) {
            val number = (values[field.id] as? ParameterValue.Integer)?.value
            if (number == null || number !in field.minimum..field.maximum) errors[field.id] = field.errorMessage
        }
        for (id in values.asMap().keys) {
            if (fields.none { it.id == id }) errors[id] = "Unknown parameter: $id."
        }
        return if (errors.isEmpty()) ParameterValidation.Valid(values) else ParameterValidation.Invalid(errors)
    }

    fun config(values: ParameterValues): RainConfig {
        val validation = validate(values)
        if (validation is ParameterValidation.Invalid) {
            throw IllegalArgumentException(validation.messages.values.joinToString(" "))
        }
        return RainConfig(
            width = values.number(width).toInt(),
            height = values.number(height).toInt(),
            dropsPerTick = values.number(drops).toInt(),
            rainMass = values.number(mass).toInt(),
            ticks = values.number(ticks).toInt(),
            seed = values.number(seed).toUInt(),
        )
    }

    fun canonical(values: ParameterValues) = fields.joinToString(",") { "${it.id}=${values.number(it)}" }

    private fun ParameterValues.number(field: ParameterDefinition.Integer) =
        (this[field.id] as ParameterValue.Integer).value

    private fun field(id: String, label: String, default: Long, range: LongRange) =
        ParameterDefinition.Integer(id, label, default, range.first, range.last,
            "Enter a value from ${range.first} to ${range.last}.")
}
