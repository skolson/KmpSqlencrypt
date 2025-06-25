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

include(":KmpSqlencrypt")
project( ":KmpSqlencrypt" ).name = projectNameMavenName