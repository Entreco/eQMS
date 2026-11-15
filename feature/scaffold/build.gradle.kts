import java.time.Duration

plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
}

description = "Project shell, navigation and workspace selection"

kotlin {
    jvm {
        val testCompilation = compilations.getByName("test")
        val integrationTestCompilation = compilations.create("integrationTest") {
            associateWith(testCompilation)
            defaultSourceSet.kotlin.srcDir("src/integrationTest/kotlin")
        }
        tasks.register<Test>("integrationTest") {
            description = "Runs native About-window tests exclusively on isolated macOS CI."
            group = "verification"
            testClassesDirs = integrationTestCompilation.output.classesDirs
            classpath = integrationTestCompilation.output.allOutputs + integrationTestCompilation.runtimeDependencyFiles
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
    }
    @OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)
    wasmJs { browser() }
    sourceSets {
        commonMain.dependencies {
            api(project(":feature:document"))
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
