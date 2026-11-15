plugins { alias(libs.plugins.kotlin.multiplatform) }
kotlin {
    macosArm64 { binaries { executable { baseName = "MacMetalDemo"; entryPoint = "dev.macmetaldemo.app.main" } } }
    sourceSets {
        macosArm64Main.dependencies {
            implementation(project(":domain"))
            implementation(project(":application"))
            implementation(project(":compute-cpu"))
            implementation(project(":compute-metal"))
            implementation(project(":algorithm-nbody"))
            implementation(project(":algorithm-game-of-life"))
            implementation(project(":algorithm-rain-water"))
            implementation(project(":monitoring-macos"))
            implementation(project(":terminal"))
        }
        macosArm64Test.dependencies { implementation(kotlin("test")) }
    }
}
