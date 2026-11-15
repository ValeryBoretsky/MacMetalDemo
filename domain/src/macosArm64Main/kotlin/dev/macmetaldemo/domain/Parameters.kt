package dev.macmetaldemo.domain

sealed interface ParameterValue {
    data class Integer(val value: Long) : ParameterValue
    data class Decimal(val value: Double) : ParameterValue
}

sealed interface ParameterDefinition {
    val id: String
    val label: String
    val errorMessage: String

    data class Integer(
        override val id: String, override val label: String, val defaultValue: Long,
        val minimum: Long, val maximum: Long, override val errorMessage: String,
    ) : ParameterDefinition {
        init { require(id.isNotBlank() && label.isNotBlank() && errorMessage.isNotBlank() && minimum <= defaultValue && defaultValue <= maximum) }
    }

    data class Decimal(
        override val id: String, override val label: String, val defaultValue: Double,
        val minimum: Double, val maximum: Double, override val errorMessage: String,
    ) : ParameterDefinition {
        init { require(id.isNotBlank() && label.isNotBlank() && errorMessage.isNotBlank() && minimum.isFinite() && maximum.isFinite() && defaultValue.isFinite() && minimum <= defaultValue && defaultValue <= maximum) }
    }
}

class ParameterValues(values: Map<String, ParameterValue>) {
    private val stored = values.toMap()
    operator fun get(id: String): ParameterValue? = stored[id]
    fun asMap(): Map<String, ParameterValue> = stored.toMutableMap()
}
