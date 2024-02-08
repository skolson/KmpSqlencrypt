buildscript {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }
    libs.versions.also {
        dependencies {
            classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:${it.kotlin.get()}")
            classpath("com.android.tools.build:gradle:${it.androidGradlePlugin.get()}")
        }
    }
}

repositories {
    mavenCentral()
}

plugins {
    libs.plugins.also {
        alias(it.kotlin.multiplatform) apply false
        alias(it.android.library) apply false
        alias(it.kotlinx.atomicfu) apply false
        alias(it.android.junit5) apply false
    }
}

allprojects {
    repositories {
        google()
        mavenCentral()
    }
}
