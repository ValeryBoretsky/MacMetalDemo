plugins { alias(libs.plugins.kotlin.multiplatform) }
kotlin {
    macosArm64()
    sourceSets {
        macosArm64Main.dependencies {
            implementation(project(":application"))
            implementation(project(":domain"))
        }
        macosArm64Test.dependencies { implementation(kotlin("test")) }
    }
}
