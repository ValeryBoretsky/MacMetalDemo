package dev.macmetaldemo.nbody.metal

import dev.macmetaldemo.nbody.domain.*
import kotlinx.cinterop.*

@OptIn(ExperimentalForeignApi::class)
fun nbodyUniforms(count: Int): ByteArray = memScoped {
    val words = allocArray<UIntVar>(4)
    words[0] = count.toUInt()
    words[1] = GRAVITY.toBits().toUInt()
    words[2] = TIME_STEP.toBits().toUInt()
    words[3] = (SOFTENING * SOFTENING).toBits().toUInt()
    words.reinterpret<ByteVar>().readBytes(16)
}
