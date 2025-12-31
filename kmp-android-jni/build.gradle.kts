import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.KotlinMultiplatform
import org.gradle.internal.os.OperatingSystem

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.maven.publish.vannik)
}

val ndkVersionValue: String = libs.versions.androidNdk.get()
val androidMinSdk: Int = libs.versions.androidSdkMinimum.get().toInt()
val androidTargetSdkVersion: Int = libs.versions.androidSdk.get().toInt()
val androidMainDirectory = projectDir.resolve("../KmpSqlencrypt/src/androidMain")

android {
    buildToolsVersion = libs.versions.androidBuildTools.get()
    namespace = "com.oldguy.sqlcipher.android"
    compileSdk {
        version = release(androidTargetSdkVersion)
    }

    defaultConfig {
        minSdk = androidMinSdk
        ndkVersion = ndkVersionValue
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
        ndk {
            abiFilters.addAll(listOf("x86_64", "arm64-v8a"))
        }
        externalNativeBuild {
            val isWindowsOS = if (OperatingSystem.current().isWindows) 1 else 0
            cmake {
                arguments(
                    "-DANDROID_MAIN_PATH=${androidMainDirectory.absolutePath}",
                    "-DOSWINDOWS=$isWindowsOS"
                )
                cppFlags("-std=c++17")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    externalNativeBuild {
        cmake {
            version = libs.versions.cmake.get()
            path("src/main/cpp/CMakeLists.txt")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

dependencies {
    testImplementation(libs.junit4)
    androidTestImplementation(libs.androidx.test.ext)
}
val publishDomain = "io.github.skolson"
val appVersion: String = libs.versions.appVersion.get()
val githubUri = "skolson/KmpSqlencrypt"
val githubUrl = "https://github.com/$githubUri"

mavenPublishing {
    coordinates(publishDomain, name, appVersion)

    pom {
        name.set("Kotlin Multiplatform SqlCipher/Sqlite Android-only JNI library")
        description.set("Library containing thin JNI wrapper of Sqlite API used by KmpSqlencrypt module.")
        url.set(githubUrl)
        licenses {
            license {
                name.set("The Apache License, Version 2.0")
                url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
            }
        }
        developers {
            developer {
                id.set("oldguy")
                name.set("Steve Olson")
                email.set("skolson5903@gmail.com")
            }
        }
        scm {
            url.set(githubUrl)
            connection.set("scm:git:git://git@github.com:${githubUri}.git")
            developerConnection.set("cm:git:ssh://git@github.com:${githubUri}.git")
        }
    }
}