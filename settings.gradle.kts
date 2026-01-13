pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
        mavenLocal()
    }
}

val projectNameMavenName = "kmp-sqlencrypt"
rootProject.name = projectNameMavenName

include(":kmp-sqlencrypt")
include(":kmp-android-jni")
