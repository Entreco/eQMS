plugins {
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    id("org.jetbrains.compose") apply false
    id("org.jetbrains.kotlin.plugin.compose") apply false
}

subprojects {
    if (path == ":core:ui" || path == ":apps:desktop") {
        pluginManager.withPlugin("org.jetbrains.kotlin.jvm") {
            configureJewelRuntime()
        }
        pluginManager.withPlugin("org.jetbrains.kotlin.multiplatform") {
            configureJewelRuntime()
        }
    }
}

fun Project.configureJewelRuntime() {
    val toolchains = extensions.getByType<JavaToolchainService>()
    val jbr = toolchains.launcherFor {
        languageVersion.set(JavaLanguageVersion.of(25))
        vendor.set(JvmVendorSpec.JETBRAINS)
    }
    tasks.withType<JavaExec>().configureEach { javaLauncher.set(jbr) }
    tasks.withType<Test>().configureEach { javaLauncher.set(jbr) }
}
