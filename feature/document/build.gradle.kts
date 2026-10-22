plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
}

description = "Draft document contracts, editor and history"

kotlin {
    jvm()
    @OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)
    wasmJs { browser() }
    sourceSets {
        commonMain.dependencies {
            api(project(":feature:search"))
        }
        jvmMain.dependencies {
            implementation(project(":feature:ui"))
            implementation(libs.jewel.standalone)
        }
        jvmTest.dependencies {
            implementation(kotlin("test"))
            implementation(compose.desktop.uiTestJUnit4)
            implementation(compose.desktop.currentOs) { exclude(group = "org.jetbrains.compose.material") }
        }
    }
}
