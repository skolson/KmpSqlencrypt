plugins {
    libs.plugins.also {
        alias(it.kotlin.multiplatform) apply false
        alias(it.android.library) apply false
        alias(it.kotlinx.atomicfu) apply false
        alias(it.android.junit5) apply false
        alias(it.maven.publish.vannik) apply false
    }
}

allprojects {
    repositories {
        google()
        mavenCentral()
        //mavenLocal()
    }
}
