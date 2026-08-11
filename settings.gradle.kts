pluginManagement {
    includeBuild("build-logic")

    repositories {
        gradlePluginPortal()
        mavenCentral()
        google()
    }

    plugins {
        id("org.jetbrains.kotlin.multiplatform") version "2.3.10"
        id("org.jetbrains.kotlin.jvm") version "2.3.10"
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
    repositories {
        mavenCentral()
        google()
        ivy("https://nodejs.org/dist") {
            name = "Node.js distributions"
            patternLayout {
                artifact("v[revision]/[artifact](-v[revision]-[classifier]).[ext]")
            }
            metadataSources {
                artifact()
            }
            content {
                includeModule("org.nodejs", "node")
            }
        }
        ivy("https://github.com/yarnpkg/yarn/releases/download") {
            name = "Yarn distributions"
            patternLayout {
                artifact("v[revision]/[artifact]-v[revision].[ext]")
            }
            metadataSources {
                artifact()
            }
            content {
                includeModule("com.yarnpkg", "yarn")
            }
        }
    }
}

rootProject.name = "eQMS"

include(":apps:cmd")
include(":apps:desktop")
include(":apps:web")
include(":core:ledger")
include(":core:search")
