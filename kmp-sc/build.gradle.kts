import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.gradle.kotlin.dsl.signing
import com.oldguy.gradle.OpensslExtension
import com.oldguy.gradle.SqlcipherExtension
import org.gradle.internal.os.OperatingSystem

plugins {
    id("com.android.library")
    kotlin("multiplatform")
    id("maven-publish")
    id("signing")
    id("kotlinx-atomicfu")
    id("org.jetbrains.dokka") version "1.6.0"
    id("com.oldguy.gradle.sqlcipher-openssl-build") version "0.2.0"
    id("com.github.ben-manes.versions") version "0.39.0"
}

repositories {
    gradlePluginPortal()
    google()
    mavenCentral()
}

group = "com.oldguy"
version = "0.3.7"
val mavenArtifactId = "kmp-sc"

val ndkVersionValue = "23.1.7779620"
val minSdk = 24
val targetSdkVersion = 31

sqlcipher {
    useGit = false
    version = "4.5.0"
    compilerOptions = SqlcipherExtension.androidCompilerOptions

    builds("x86_64", "arm64-v8a")

    tools {
        windows {
            msys2InstallDirectory = "D:\\msys64"
            sdkInstall = "D:\\Program Files (x86)\\Windows Kits\\10"
            sdkLibVersion = "10.0.18362.0"
            perlInstallDirectory = "D:\\SqlCipher\\Strawberry\\perl"
        }
        android {
            sdkLocation = if (OperatingSystem.current().isLinux)
                "/home/steve/Android/Sdk"
            else
                "D:\\Android\\sdk"
            ndkVersion = ndkVersionValue
            minimumSdk = minSdk
        }
    }
    openssl {
        tagName = "openssl-3.0.0"
        useGit = false
        configureOptions = OpensslExtension.smallConfigureOptions
        buildSpecificOptions = mapOf(
            "arm64-v8a" to OpensslExtension.nonWindowsOptions,
            "x86_64" to OpensslExtension.nonWindowsOptions)    }
}

var androidMainDirectory = projectDir.resolve("src").resolve("androidMain")

android {
    compileSdk = targetSdkVersion
    ndkVersion = ndkVersionValue
    buildToolsVersion = "31.0.0"

    sourceSets {
        getByName("main") {
            java.srcDir(androidMainDirectory.resolve("kotlin"))
            manifest.srcFile(androidMainDirectory.resolve("AndroidManifest.xml"))
            jni.srcDir("src/androidMain/cpp")
        }
        getByName("test") {
            java.srcDir("src/androidTest/kotlin")
        }
        getByName("androidTest") {
            java.srcDir("src/androidAndroidTest/kotlin")
        }
    }

    defaultConfig {
        minSdk = minSdk
        targetSdk = targetSdkVersion

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

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
                //"-DCMAKE_MAKE_PROGRAM=ninja"

                cppFlags("-std=c++17")
            }
        }
        consumerProguardFiles("tools/proguard-rules.pro")
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
        }
    }

    externalNativeBuild {
        cmake {
            path("src/androidMain/cpp/CMakeLists.txt")
        }
    }
    compileOptions {
        targetCompatibility = JavaVersion.VERSION_11
    }

    dependencies {
        testImplementation("junit:junit:4.13.2")
        androidTestImplementation("androidx.test:core:1.4.0")
        androidTestImplementation("androidx.test:runner:1.4.0")
        androidTestImplementation("androidx.test.ext:junit:1.1.3")
    }
}

kotlin {
    android {
        publishLibraryVariants("release", "debug")
    }
    iosX64 {
        binaries {
            framework {
                baseName = "kmp-sc-x64"
            }
        }
    }
    iosArm64 {
        binaries {
            framework {
                baseName = "kmp-sc-arm64"
            }
        }
    }
    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation("com.soywiz.korlibs.klock:klock:2.4.10")
                implementation("com.ionspin.kotlin:bignum:0.3.3")

            }
        }
        named("commonTest") {
            dependencies {
                implementation(kotlin("test-common"))
                implementation(kotlin("test-annotations-common"))
            }
        }
        val androidMain by getting {
            dependsOn(commonMain)
        }

        named("androidTest") {
            dependencies {
                implementation(kotlin("test-junit"))
                implementation("junit:junit:4.13.2")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.6.0")
            }
        }
        val androidAndroidTest by getting {
            dependsOn(commonMain)
            dependsOn(androidMain)
        }
        val iosMain = create("iosMain") {
            kotlin.srcDir("src/iosMain/kotlin")
        }
        val iosX64Main by getting {
            dependsOn(iosMain)
        }
        //named("iosX64Test")
        val iosArm64Main by getting {
            dependsOn(iosMain)
        }
        //val iosArm64Test by getting
    }
}

val packForXcode by tasks.creating(Sync::class) {
    group = "build"
    val mode = System.getenv("CONFIGURATION") ?: "DEBUG"
    val sdkName = System.getenv("SDK_NAME") ?: "iphonesimulator"
    val targetName = "ios" + if (sdkName.startsWith("iphoneos")) "Arm64" else "X64"
    val framework = kotlin.targets.getByName<KotlinNativeTarget>(targetName).binaries.getFramework(mode)
    inputs.property("mode", mode)
    dependsOn(framework.linkTask)
    val targetDir = File(buildDir, "xcode-frameworks")
    from({ framework.outputDirectory })
    into(targetDir)
}

tasks {
    getByName("build").dependsOn(packForXcode)

    dokkaGfm {
        moduleName.set("Kotlin Multiplatform SqlCipher/Sqlite")
        dokkaSourceSets {
            named("commonMain") {
                noAndroidSdkLink.set(false)
                includes.from("kmp-sc.md")
            }
        }
    }
    create<Jar>("javadocJar") {
        dependsOn(dokkaGfm)
        archiveClassifier.set("javadoc")
        from(dokkaGfm.get().outputDirectory)
    }

    val libsDirectory = androidMainDirectory.resolve("sqlcipher")
    val sqlcipherBuild = getByName("sqlcipherBuildAll")
    val arm64v8aTask = getByName("sqlcipherBuildarm64-v8a") as com.oldguy.gradle.SqlcipherBuildTask
    val copyArm = register<Copy>("copySqlcipherAndroidArm64v8a") {
        group = "build"
        dependsOn(sqlcipherBuild)
        from(arm64v8aTask.targetDirectory)
        into(libsDirectory.resolve("arm64-v8a"))
    }
    val x86Task = getByName("sqlcipherBuildx86_64") as com.oldguy.gradle.SqlcipherBuildTask
    val copyX86 = register<Copy>("copySqlcipherAndroidX86") {
        group = "build"
        dependsOn(sqlcipherBuild)
        from(x86Task.targetDirectory)
        into(libsDirectory.resolve("x86_64"))
    }
    register("copySqlcipherAndroid") {
        group = "build"
        dependsOn(copyArm, copyX86)
    }
}

signing {
    isRequired = false
    sign(publishing.publications)
}

afterEvaluate {
    publishing {
        val githubUri = "skolson/kmp-sc"
        val githubUrl = "https://github.com/$githubUri"

        publications.withType<MavenPublication> {
            artifact(tasks["javadocJar"])
            pom {
                name.set("KMP-SC Kotlin Multiplatform SqlCipher/Sqlite")
                description.set("Kotlin common library for use of SqlCipher/Sqlite using the same API on supported platforms; Android IOS, Windows, Linux, MacOS")
                url.set(githubUrl)
                licenses {
                    license {
                        name.set("The Apache License, Version 2.0")
                        url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
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
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.7.0")
}
