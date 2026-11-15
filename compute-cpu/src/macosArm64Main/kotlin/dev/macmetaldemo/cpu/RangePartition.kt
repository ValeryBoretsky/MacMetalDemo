package dev.macmetaldemo.cpu

data class IndexRange(val start: Int, val endExclusive: Int)

fun partitionRanges(elementCount: Int, requestedWorkers: Int): List<IndexRange> {
    require(elementCount > 0)
    require(requestedWorkers > 0)
    val workers = minOf(elementCount, requestedWorkers)
    val size = elementCount / workers
    val remainder = elementCount % workers
    var start = 0
    return List(workers) { index ->
        val end = start + size + if (index < remainder) 1 else 0
        IndexRange(start, end).also { start = end }
    }
}
