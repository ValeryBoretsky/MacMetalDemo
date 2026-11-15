# MacMetalDemo

A Kotlin/Native terminal app for macOS on Apple Silicon.
It compares CPU and GPU performance with an N-body simulation: particles move under their mutual gravity.
The executable does not need a JVM.

![N-body simulation running on Metal with particle positions and CPU and GPU load](docs/public/nbody-metal.png)

## Features

- Three modes: one CPU thread, multiple CPU threads, and GPU calculations through Metal.
- The same initial data, equations, and 32-bit floating-point precision across modes for the same parameters.
- Live progress, calculation speed, estimated time left, and results.
- System CPU load for each core and separate CPU use for the app.
- Optional system GPU activity, history, and command duration in Metal mode.
- Optional particle visualization in the terminal.

## Build and run

The build needs macOS on Apple Silicon, Xcode with the macOS SDK, and JDK 21.
The project uses Kotlin 2.4.10 and Gradle 9.1.0.

Run these commands from the repository root:

```sh
./gradlew check
./gradlew :app:linkReleaseExecutableMacosArm64
./app/build/bin/macosArm64/releaseExecutable/MacMetalDemo.kexe
```

1. Select N-body.
2. Select an execution mode.
3. Enter the particle count, step count, and seed.
4. Choose whether to show particle positions.

The defaults are 16,384 particles, 1,000 steps, and seed 42.

Use the same parameters and visualization choice when you compare modes.
Run the single-thread CPU mode first to get a speed comparison.
GPU activity describes the whole system, including other apps. It shows `N/A` when unavailable.

## Development

Algorithms use separate Gradle modules and the `DemoAlgorithm` interface.
They share the terminal, timing, and monitoring code.
N-body is the only algorithm in this version.

## Run comparison

Recorded on 2026-11-15 with the release executable on an Apple M1 Pro with 10 CPU cores and 16 GPU cores.
Each mode ran once with 16,384 particles, 1,000 steps, and seed 42. Visualization was off.
Each run completed 268,419,072,000 particle interactions. The times cover calculation only.

| Mode | Calculation time | Speedup vs. one CPU thread |
|---|---:|---:|
| CPU - one thread | 478.895 s | 1.00× |
| CPU - all cores | 72.655 s | 6.59× |
| GPU - Metal | 8.736 s | 54.82× |

Results can change with hardware and system activity.
