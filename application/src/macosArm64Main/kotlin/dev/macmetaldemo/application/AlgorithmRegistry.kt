package dev.macmetaldemo.application

class AlgorithmRegistry(algorithms: List<DemoAlgorithm>) {
    private val stored = algorithms.toList()
    val entries: List<DemoAlgorithm> get() = stored.toList()
    init {
        require(stored.all { it.info.id.isNotBlank() })
        require(stored.map { it.info.id }.distinct().size == stored.size)
    }
    fun find(id: String): DemoAlgorithm? = stored.firstOrNull { it.info.id == id }
}
