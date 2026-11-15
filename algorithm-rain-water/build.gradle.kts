plugins { alias(libs.plugins.kotlin.multiplatform) }

val shader = layout.projectDirectory.file("src/macosArm64Main/metal/rain.metal")
val generated = layout.buildDirectory.dir("generated/rain")
val embedRainShader by tasks.registering {
    inputs.file(shader)
    val output = generated.map { it.file("dev/macmetaldemo/rain/RainShader.kt") }
    outputs.file(output)
    doLast {
        val literal = shader.asFile.readText().replace("\\", "\\\\").replace("\"", "\\\"")
            .replace("$", "\\$").replace("\r", "\\r").replace("\n", "\\n")
        val file = output.get().asFile
        file.parentFile.mkdirs()
        file.writeText("package dev.macmetaldemo.rain\ninternal const val RAIN_SHADER = \"$literal\"\n")
    }
}

kotlin {
    macosArm64()
    sourceSets {
        macosArm64Main {
            kotlin.srcDir(generated)
            dependencies {
                implementation(project(":application"))
                implementation(project(":domain"))
            }
        }
        macosArm64Test.dependencies { implementation(kotlin("test")) }
    }
}
tasks.named("compileKotlinMacosArm64") { dependsOn(embedRainShader) }
