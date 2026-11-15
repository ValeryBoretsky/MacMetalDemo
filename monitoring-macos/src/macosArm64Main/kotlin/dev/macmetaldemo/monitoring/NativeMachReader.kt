package dev.macmetaldemo.monitoring

import kotlinx.cinterop.*
import platform.darwin.*

@OptIn(ExperimentalForeignApi::class)
internal class NativeMachReader : MachReader {
    private val host = mach_host_self()
    override fun acquire(): MachBuffer? = memScoped {
        val count = alloc<UIntVar>()
        val info = alloc<CPointerVar<IntVar>>()
        val infoCount = alloc<UIntVar>()
        val result = host_processor_info(host, PROCESSOR_CPU_LOAD_INFO, count.ptr, info.ptr, infoCount.ptr)
        val pointer = info.value ?: return@memScoped null
        val elements = infoCount.value.toLong()
        val buffer = MachBuffer(count.value.toInt(), elements, { pointer[it].toUInt() }, { bytes ->
            checkMachRelease(vm_deallocate(mach_task_self_, pointer.rawValue.toLong().toULong(), bytes.toULong()))
        })
        if (result != KERN_SUCCESS) {
            buffer.release(elements * sizeOf<IntVar>())
            null
        } else buffer
    }
    override fun close() { mach_port_deallocate(mach_task_self_, host) }
}

internal fun checkMachRelease(result: Int) {
    if (result != KERN_SUCCESS) throw CpuReleaseException()
}
