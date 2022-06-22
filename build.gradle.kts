import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

buildscript {
    val kotlinVersion: String by extra("1.6.21")
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:$kotlinVersion")
        classpath("com.android.tools.build:gradle:7.3.0-beta04")
        classpath("org.jetbrains.kotlinx:atomicfu-gradle-plugin:0.17.3")
    }
}

repositories {
    mavenCentral()
}

allprojects {
    repositories {
        google()
    }
    tasks.withType<KotlinCompile> {
        kotlinOptions {
            jvmTarget = "11"
            sourceCompatibility = "11"
        }
    }
}
