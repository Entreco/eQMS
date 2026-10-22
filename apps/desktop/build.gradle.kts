import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.jvm")
}

val desktopVersion = providers.gradleProperty("dutchDoxVersion").getOrElse("1.0.0-dev")
require(Regex("[1-9][0-9]*\\.[0-9]+\\.[0-9]+(?:-[0-9A-Za-z.-]+)?(?:\\+[0-9A-Za-z.-]+)?").matches(desktopVersion)) {
    "dutchDoxVersion must have a positive major version and semantic version format."
}

compose.desktop {
    application {
        mainClass = "com.entreco.eqms.desktop.MainKt"
        jvmArgs += listOf("-Dapple.awt.application.name=DutchDox", "-Dapple.laf.useScreenMenuBar=true")
        if (System.getProperty("os.name").startsWith("Mac")) jvmArgs += "-Xdock:name=DutchDox"
        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "DutchDox"
            packageVersion = desktopVersion.substringBefore('-').substringBefore('+')
            modules("java.net.http", "java.prefs")
            macOS { bundleID = "com.entreco.dutchdox" }
        }
    }
}

tasks.processResources {
    inputs.property("desktopVersion", desktopVersion)
    filesMatching("dutchdox-build.properties") { expand("version" to desktopVersion) }
}

val qmsExecutableName = if (System.getProperty("os.name").startsWith("Windows")) "qms.exe" else "qms"
val qmsExecutable = providers.environmentVariable("EQMS_QMS_PATH")
    .filter { it.isNotBlank() }
    .getOrElse(rootProject.layout.projectDirectory.file("target/release/$qmsExecutableName").asFile.absolutePath)

tasks.withType<JavaExec>().configureEach {
    environment("EQMS_QMS_PATH", qmsExecutable)
    if (name.startsWith("hotRun")) {
        systemProperty("apple.awt.application.name", "DutchDox")
        systemProperty("apple.laf.useScreenMenuBar", "true")
        if (System.getProperty("os.name").startsWith("Mac")) jvmArgs("-Xdock:name=DutchDox")
    }
}

dependencies {
    implementation(project(":feature:scaffold"))
    implementation(compose.desktop.currentOs) { exclude(group = "org.jetbrains.compose.material") }
    implementation(libs.jewel.standalone)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(kotlin("test"))
    testImplementation(compose.desktop.uiTestJUnit4)
}
