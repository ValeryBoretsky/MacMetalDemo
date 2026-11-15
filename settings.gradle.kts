pluginManagement { repositories { gradlePluginPortal(); mavenCentral() } }
dependencyResolutionManagement { repositories { mavenCentral() } }
rootProject.name = "MacMetalDemo"
include("domain", "application", "compute-cpu", "compute-metal", "algorithm-nbody", "monitoring-macos", "terminal", "app")
