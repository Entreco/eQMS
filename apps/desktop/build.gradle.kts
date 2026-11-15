import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import java.time.Duration

plugins {
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.jvm")
}

val stageComposeDrawables by tasks.registering(Sync::class) {
    from(layout.projectDirectory.dir("src/main/resources/branding")) {
        include("about.jpg", "icon.png")
        into("drawable")
    }
    into(layout.buildDirectory.dir("generated/desktopComposeResources"))
}

compose.resources {
    packageOfResClass = "com.entreco.eqms.desktop.generated.resources"
    customDirectory(
        sourceSetName = "main",
        directoryProvider = layout.dir(stageComposeDrawables.map { it.destinationDir }),
    )
}

val desktopVersion = providers.gradleProperty("dutchDoxxVersion").getOrElse("1.0.0-dev")
require(Regex("[1-9][0-9]*\\.[0-9]+\\.[0-9]+(?:-[0-9A-Za-z.-]+)?(?:\\+[0-9A-Za-z.-]+)?").matches(desktopVersion)) {
    "dutchDoxxVersion must have a positive major version and semantic version format."
}

val integrationTestSourceSet = sourceSets.create("integrationTest")
kotlin.target.compilations.named("integrationTest") {
    associateWith(kotlin.target.compilations.getByName("main"))
}
configurations[integrationTestSourceSet.implementationConfigurationName]
    .extendsFrom(configurations.testImplementation.get())
configurations[integrationTestSourceSet.runtimeOnlyConfigurationName]
    .extendsFrom(configurations.testRuntimeOnly.get())

tasks.register<Test>("integrationTest") {
    description = "Runs real native desktop windows and OS input exclusively in GitHub Actions."
    group = "verification"
    testClassesDirs = integrationTestSourceSet.output.classesDirs
    classpath = integrationTestSourceSet.runtimeClasspath
    maxParallelForks = 1
    forkEvery = 1
    timeout.set(Duration.ofMinutes(5))
    testLogging.events("started", "passed", "failed", "skipped")
    outputs.upToDateWhen { false }
    doFirst {
        check(System.getenv("GITHUB_ACTIONS") == "true" && System.getenv("RUNNER_OS") == "macOS") {
            "Native integration tests are CI-only and require an isolated macOS GitHub Actions runner."
        }
    }
}

compose.desktop {
    application {
        mainClass = "com.entreco.eqms.desktop.MainKt"
        jvmArgs += listOf("-Dapple.awt.application.name=DutchDoxx", "-Dapple.laf.useScreenMenuBar=true")
        if (System.getProperty("os.name").startsWith("Mac")) jvmArgs += "-Xdock:name=DutchDoxx"
        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "DutchDoxx"
            packageVersion = desktopVersion.substringBefore('-').substringBefore('+')
            modules("java.net.http", "java.prefs")
            macOS {
                bundleID = "com.entreco.dutchdoxx"
                iconFile.set(project.file("src/main/resources/branding/icon.icns"))
            }
            windows { iconFile.set(project.file("src/main/resources/branding/icon.ico")) }
            linux { iconFile.set(project.file("src/main/resources/branding/icon.png")) }
        }
    }
}

tasks.processResources {
    inputs.property("desktopVersion", desktopVersion)
    filesMatching("dutchdoxx-build.properties") { expand("version" to desktopVersion) }
}

val qmsExecutableName = if (System.getProperty("os.name").startsWith("Windows")) "qms.exe" else "qms"
val qmsExecutable = providers.environmentVariable("EQMS_QMS_PATH")
    .filter { it.isNotBlank() }
    .getOrElse(rootProject.layout.projectDirectory.file("target/release/$qmsExecutableName").asFile.absolutePath)

tasks.withType<JavaExec>().configureEach {
    environment("EQMS_QMS_PATH", qmsExecutable)
    if (name.startsWith("hotRun")) {
        systemProperty("apple.awt.application.name", "DutchDoxx")
        systemProperty("apple.laf.useScreenMenuBar", "true")
        if (System.getProperty("os.name").startsWith("Mac")) jvmArgs("-Xdock:name=DutchDoxx")
    }
}

dependencies {
    implementation(project(":feature:scaffold"))
    implementation(compose.desktop.currentOs) { exclude(group = "org.jetbrains.compose.material") }
    implementation(libs.jewel.standalone)
    implementation(libs.jewel.decorated.window)
    implementation(compose.components.resources)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(kotlin("test"))
    testImplementation(compose.desktop.uiTestJUnit4)
}
