plugins { alias(libs.plugins.kotlin.multiplatform) apply false }
tasks.register("check") { dependsOn(subprojects.map { "${it.path}:macosArm64Test" }); dependsOn(":algorithm-nbody:verifyNBodyShaderEmbedding") }
