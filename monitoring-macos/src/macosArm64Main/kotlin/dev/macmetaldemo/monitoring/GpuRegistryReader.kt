@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
package dev.macmetaldemo.monitoring

import dev.macmetaldemo.domain.GpuLoad
import kotlinx.cinterop.*
import platform.CoreFoundation.*
import platform.IOKit.*

internal fun validatedGpuLoad(id: ULong, cores: Any?, utilization: Any?): GpuLoad {
    val count = (cores as? Number)?.toDouble()?.takeIf { it.isFinite() && it > 0 && it <= Int.MAX_VALUE && it % 1.0 == 0.0 }?.toInt()
    val percent = (utilization as? Number)?.toDouble()?.takeIf { it.isFinite() && it in 0.0..100.0 }
    return GpuLoad(id, count, percent, if (percent == null) "GPU sampling is unavailable." else null)
}

internal fun interface GpuReader { fun read(): List<GpuLoad> }

internal class GpuRegistryReader : GpuReader {
    override fun read(): List<GpuLoad> = memScoped {
        val matching = IOServiceMatching("IOAccelerator") ?: return@memScoped emptyList()
        val iterator = alloc<io_iterator_tVar>()
        if (IOServiceGetMatchingServices(kIOMainPortDefault, matching, iterator.ptr) != 0) return@memScoped emptyList()
        val result = mutableListOf<GpuLoad>()
        try {
            while (true) {
                val entry = IOIteratorNext(iterator.value)
                if (entry == 0u) break
                try {
                    val id = alloc<ULongVar>()
                    if (IORegistryEntryGetRegistryEntryID(entry, id.ptr) != 0) continue
                    val coreValue = property(entry, "gpu-core-count")
                    try {
                        val statistics = property(entry, "PerformanceStatistics")
                        try {
                            val coreCount = number(coreValue)
                            val utilization = if (statistics != null && CFGetTypeID(statistics) == CFDictionaryGetTypeID()) {
                                val key = string("Device Utilization %")
                                try { number(CFDictionaryGetValue(statistics.reinterpret(), key)) } finally { CFRelease(key) }
                            } else null
                            result.add(validatedGpuLoad(id.value, coreCount, utilization))
                        } finally { if (statistics != null) CFRelease(statistics) }
                    } finally { if (coreValue != null) CFRelease(coreValue) }
                } finally { IOObjectRelease(entry) }
            }
        } finally { IOObjectRelease(iterator.value) }
        result
    }
    private fun string(value: String): CFStringRef = checkNotNull(CFStringCreateWithCString(null, value, kCFStringEncodingUTF8))
    private fun property(entry: io_registry_entry_t, name: String): CFTypeRef? {
        val key = string(name)
        return try { IORegistryEntryCreateCFProperty(entry, key, null, 0u) } finally { CFRelease(key) }
    }
    private fun number(value: COpaquePointer?): Double? {
        if (value == null || CFGetTypeID(value) != CFNumberGetTypeID()) return null
        return memScoped {
            val result = alloc<DoubleVar>()
            if (CFNumberGetValue(value.reinterpret(), kCFNumberDoubleType, result.ptr)) result.value else null
        }
    }
}
