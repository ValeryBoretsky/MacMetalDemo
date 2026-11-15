plugins { alias(libs.plugins.kotlin.multiplatform) }
kotlin {
    macosArm64()
    sourceSets {
        macosArm64Main.dependencies {

        }
        macosArm64Test.dependencies { implementation(kotlin("test")) }
    }
}
