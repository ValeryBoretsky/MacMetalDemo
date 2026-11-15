package dev.macmetaldemo.domain

enum class ExecutionMode { CPU_SINGLE, CPU_PARALLEL, METAL }
data class AlgorithmInfo(val id: String, val modelVersion: String, val name: String, val description: String)
data class ModeAvailability(val mode: ExecutionMode, val available: Boolean, val reason: String?)
