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

val qmsExecutableName = if (System.getProperty("os.name").startsWith("Windows")) "qms.exe" else "qms"
val qmsExecutable = providers.environmentVariable("EQMS_QMS_PATH")
    .filter { it.isNotBlank() }
    .getOrElse(rootProject.layout.projectDirectory.file("target/release/$qmsExecutableName").asFile.absolutePath)

tasks.withType<JavaExec>().configureEach {
    environment("EQMS_QMS_PATH", qmsExecutable)
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
