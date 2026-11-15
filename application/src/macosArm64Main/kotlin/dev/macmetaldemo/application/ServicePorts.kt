package dev.macmetaldemo.application

import dev.macmetaldemo.domain.*

fun interface MonotonicClock { fun nowNs(): Long }
fun interface ProgressSink { fun publish(progress: RunProgress) }
fun interface GpuStatusSink { fun publish(status: GpuCommandStatus) }
fun interface ResourceSampler { fun sample(): ResourceSnapshot }
fun interface RunSnapshotSink { fun publish(snapshot: RunSnapshot) }
