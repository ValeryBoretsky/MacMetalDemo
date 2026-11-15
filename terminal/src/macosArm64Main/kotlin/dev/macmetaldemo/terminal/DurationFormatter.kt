package dev.macmetaldemo.terminal

fun formatDuration(durationNs: Long?): String {
    if (durationNs == null) return "N/A"
    require(durationNs >= 0L) { "Duration must be nonnegative." }

    val nanosecondsPerMillisecond = 1_000_000L
    val nanosecondsPerSecond = 1_000_000_000L
    val secondsPerMinute = 60L
    val secondsPerHour = 3_600L

    if (durationNs < nanosecondsPerMillisecond) return "$durationNs ns"
    if (durationNs < nanosecondsPerSecond) {
        val milliseconds = durationNs / nanosecondsPerMillisecond
        val fraction = durationNs % nanosecondsPerMillisecond / 1_000L
        return formatFraction(milliseconds, fraction, "ms")
    }

    val totalSeconds = durationNs / nanosecondsPerSecond
    if (totalSeconds < secondsPerMinute) {
        val seconds = totalSeconds
        val fraction = durationNs % nanosecondsPerSecond / 1_000_000L
        return formatFraction(seconds, fraction, "s")
    }

    val remainingAfterHours = totalSeconds % secondsPerHour
    val minutes = remainingAfterHours / secondsPerMinute
    val seconds = remainingAfterHours % secondsPerMinute
    if (totalSeconds >= secondsPerHour) return "${totalSeconds / secondsPerHour} h $minutes min $seconds s"
    return "$minutes min $seconds s"
}

private fun formatFraction(whole: Long, fraction: Long, unit: String): String {
    if (fraction == 0L) return "$whole $unit"
    val digits = fraction.toString().padStart(3, '0').trimEnd('0')
    return "$whole.$digits $unit"
}
