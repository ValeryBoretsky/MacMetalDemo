plugins { alias(libs.plugins.kotlin.multiplatform) }

val shaderFile = layout.projectDirectory.file("src/macosArm64Main/metal/nbody.metal")
val generatedFixture = layout.buildDirectory.file("generated/nbody-test/dev/macmetaldemo/nbody/ShaderFixture.kt")
val generatedFile = layout.buildDirectory.file("generated/nbody/dev/macmetaldemo/nbody/metal/NBodyShaderSource.kt")
fun escapeShader(source: String): String = buildString {
    append('"')
    source.forEach { c -> append(when (c) {
        '\\' -> "\\\\"
        '"' -> "\\\""
        '$' -> "\\$"
        '\n' -> "\\n"
        '\r' -> "\\r"
        '\t' -> "\\t"
        else -> c.toString()
    }) }
    append('"')
}
val generateNBodyShader by tasks.registering {
    inputs.file(shaderFile)
    outputs.file(generatedFile)
    doLast {
        val file = generatedFile.get().asFile
        file.parentFile.mkdirs()
        file.writeText("package dev.macmetaldemo.nbody.metal\nobject NBodyShaderSource { val text: String = ${escapeShader(shaderFile.asFile.readText())} }\n")
    }
}
fun decodeShaderLiteral(generated: String): String {
    val marker = "val text: String = \""
    val start = generated.indexOf(marker)
    check(start >= 0)
    var index = start + marker.length
    val result = StringBuilder()
    while (index < generated.length) {
        val char = generated[index++]
        if (char == '"') return result.toString()
        if (char != '\\') {
            result.append(char)
        } else {
            check(index < generated.length)
            result.append(when (val escaped = generated[index++]) {
                '\\' -> '\\'
                '"' -> '"'
                '$' -> '$'
                'n' -> '\n'
                'r' -> '\r'
                't' -> '\t'
                else -> error("Unknown shader escape: $escaped")
            })
        }
    }
    error("Shader literal is not closed")
}
val generateShaderFixture by tasks.registering {
    outputs.file(generatedFixture)
    doLast {
        val file = generatedFixture.get().asFile
        file.parentFile.mkdirs()
        file.writeText("package dev.macmetaldemo.nbody\nobject ShaderFixture { val text = ${escapeShader("$\"\\\n")} }\n")
    }
}
tasks.register("verifyNBodyShaderEmbedding") {
    dependsOn(generateNBodyShader)
    doLast {
        check(decodeShaderLiteral(generatedFile.get().asFile.readText()) == shaderFile.asFile.readText())
    }
}

kotlin {
    macosArm64()
    sourceSets {
        macosArm64Main {
            kotlin.srcDir(layout.buildDirectory.dir("generated/nbody"))
            dependencies {
                implementation(project(":application"))
                implementation(project(":domain"))
                implementation(project(":compute-cpu"))
                implementation(project(":compute-metal"))
            }
        }
        macosArm64Test {
            kotlin.srcDir(layout.buildDirectory.dir("generated/nbody-test"))
            dependencies { implementation(kotlin("test")) }
        }
    }
}
tasks.matching { it.name == "compileKotlinMacosArm64" }.configureEach { dependsOn(generateNBodyShader) }

tasks.matching { it.name == "compileTestKotlinMacosArm64" }.configureEach { dependsOn(generateShaderFixture) }
