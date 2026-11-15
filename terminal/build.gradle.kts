plugins { alias(libs.plugins.kotlin.multiplatform) }
kotlin {
    macosArm64 {
        compilations.getByName("main") { cinterops.create("screen") }
    }
    sourceSets {
        macosArm64Main.dependencies {
            implementation(project(":application"))
            implementation(project(":domain"))
        }
        macosArm64Test.dependencies { implementation(kotlin("test")) }
    }
}
