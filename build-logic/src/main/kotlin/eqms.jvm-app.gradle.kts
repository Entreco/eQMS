plugins {
    id("org.jetbrains.kotlin.jvm")
    application
}

val projectMainClass = when (project.path) {
    ":apps:cmd" -> "com.entreco.eqms.cmd.MainKt"
    ":apps:desktop" -> "com.entreco.eqms.desktop.MainKt"
    else -> null
}

if (projectMainClass != null) {
    application {
        mainClass.set(projectMainClass)
    }
}
