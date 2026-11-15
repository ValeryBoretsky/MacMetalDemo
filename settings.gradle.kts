pluginManagement { repositories { gradlePluginPortal(); mavenCentral() } }
dependencyResolutionManagement { repositories { mavenCentral() } }
rootProject.name = "MacMetalDemo"
include("domain", "application", "compute-cpu", "compute-metal", "algorithm-nbody", "algorithm-game-of-life", "monitoring-macos", "terminal", "app")
