package dev.macmetaldemo.rain

// Each tick uses this order. The codes match RainPhase in the Metal shader.
internal enum class RainPhase(val shaderCode: Int) {
    ADMIT_RAIN(0),
    GRAVITY(1),
    RUNOFF(2),
    DRAIN(3),
}
