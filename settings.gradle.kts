pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
        mavenLocal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

val projectNameMavenName = "kmp-sqlencrypt"
rootProject.name = projectNameMavenName

include(":kmp-sqlencrypt")
include(":kmp-android-jni")
