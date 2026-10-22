plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
}

description = "Shared Jewel UI primitives"

kotlin {
    jvm()
    @OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)
    wasmJs { browser() }
    sourceSets {
        commonMain.dependencies {
            api(compose.runtime)
            api(compose.foundation)
        }
        jvmMain.dependencies {
            implementation(libs.jewel.standalone)
            implementation(libs.intellij.icons)
        }
        jvmTest.dependencies {
            implementation(kotlin("test"))
            implementation(compose.desktop.uiTestJUnit4)
            implementation(compose.desktop.currentOs) { exclude(group = "org.jetbrains.compose.material") }
        }
    }
}
