plugins {
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.jvm")
}

compose.desktop {
    application {
        mainClass = "com.entreco.eqms.desktop.MainKt"
    }
}

dependencies {
    implementation(project(":core:ledger"))
    implementation(project(":core:search"))
    implementation(compose.desktop.currentOs)
    implementation(compose.material)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(kotlin("test"))
}
