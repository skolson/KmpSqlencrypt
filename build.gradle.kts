import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

buildscript {
    val kotlinVersion: String by extra("1.9.0")
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:$kotlinVersion")
        classpath("com.android.tools.build:gradle:8.2.0-alpha15")
        classpath("org.jetbrains.kotlinx:atomicfu-gradle-plugin:0.21.0")
    }
}

repositories {
    mavenCentral()
}

allprojects {
    repositories {
        google()
        mavenCentral()
    }
    tasks.withType<KotlinCompile> {
        kotlinOptions {
            jvmTarget = "11"
        }
    }
}
