import org.gradle.kotlin.dsl.signing
import com.oldguy.gradle.OpensslExtension
import com.oldguy.gradle.SqlcipherExtension
import com.oldguy.gradle.BuildType
import org.gradle.internal.os.OperatingSystem
import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFramework

plugins {
    id("com.android.library")
    kotlin("multiplatform")
    id("maven-publish")
    id("signing")
    id("kotlinx-atomicfu")
    id("org.jetbrains.dokka") version "1.6.0"
    id("com.oldguy.gradle.sqlcipher-openssl-build") version "0.3.3"
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

val ndkVersionValue = "24.0.7956693"
val minSdk = 24
val targetSdkVersion = 31

val androidMainDirectory = projectDir.resolve("src").resolve("androidMain")
val nativeInterop = projectDir.resolve("src/nativeInterop")
val nativeInteropPath = nativeInterop.absolutePath

sqlcipher {
    useGit = false
    version = "4.5.0"
    compilerOptions = SqlcipherExtension.defaultCompilerOptions
    buildCompilerOptions = mapOf(
        BuildType.androidX64 to SqlcipherExtension.androidCompilerOptions,
        BuildType.androidArm64 to SqlcipherExtension.androidCompilerOptions,
        BuildType.iosX64 to SqlcipherExtension.iosCompilerOptions,
        BuildType.iosArm64 to SqlcipherExtension.iosCompilerOptions,
        BuildType.macosX64 to SqlcipherExtension.macOsCompilerOptions
    )

    builds(BuildType.appleBuildTypes)
    val abiMap = mapOf(
        BuildType.androidX64 to "x86_64",
        BuildType.androidArm64 to "arm64-v8a"
    )
    targetsCopyTo = { buildType ->
        if (buildType.isAndroid)
            androidMainDirectory.resolve("sqlcipher").resolve(abiMap[buildType]!!)
        else
            nativeInterop.resolve(buildType.name)
    }
    copyCinteropIncludes = true

    tools {
        windows {
            msys2InstallDirectory = "D:\\msys64"
            sdkInstall = "D:\\Program Files (x86)\\Windows Kits\\10"
            sdkLibVersion = "10.0.18362.0"
            perlInstallDirectory = "D:\\SqlCipher\\Strawberry\\perl"
        }
        android {
            linuxSdkLocation = "/home/steve/Android/Sdk"
            windowsSdkLocation = "D:\\Android\\sdk"
            macosSdkLocation = "/Users/steve/Library/Android/sdk"
            ndkVersion = ndkVersionValue
            minimumSdk = minSdk
        }
        apple {
            sdkVersion = "15"
            sdkVersionMinimum = "14"
        }
    }
    openssl {
        tagName = "openssl-3.0.1"
        useGit = false
        configureOptions = OpensslExtension.smallConfigureOptions
        buildSpecificOptions = OpensslExtension.buildOptionsMap
    }
}

android {
    compileSdk = targetSdkVersion
    ndkVersion = ndkVersionValue
    buildToolsVersion = "32.0.0"

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
            version = "3.18.1"
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

    val frameworkName = "KotlinSqlcipher"
    val appleXcf = XCFramework()
    macosX64 {
        binaries {
            framework {
                baseName = frameworkName
                appleXcf.add(this)
                isStatic = true
            }
        }
        val main by this.compilations.getting {
            val sqlcipherInterop by cinterops.creating {
                defFile(nativeInterop.resolve("macosX64/Sqlcipher.def"))
                packageName("com.oldguy.sqlcipher")
                includeDirs.apply {
                    allHeaders(nativeInterop.resolve("macosX64"))
                }
                compilerOpts += listOf(
                    "-I$nativeInteropPath/macosX64"
                )
            }
        }
    }
    iosX64 {
        binaries {
            framework {
                baseName = frameworkName
                appleXcf.add(this)
                isStatic = true
            }
        }
        val main by this.compilations.getting {
            val sqlcipherInterop by cinterops.creating {
                defFile(nativeInterop.resolve("iosX64/Sqlcipher.def"))
                packageName("com.oldguy.sqlcipher")
                includeDirs.apply {
                    allHeaders(nativeInterop.resolve("iosX64"))
                }
                compilerOpts += listOf("-I$nativeInteropPath/iosX64")
            }
        }
    }
    iosArm64 {
        binaries {
            framework {
                baseName = frameworkName
                appleXcf.add(this)
                isStatic = true
                embedBitcode("bitcode")
                freeCompilerArgs += listOf("-Xoverride-konan-properties=osVersionMin=14")
            }
        }
        val main by this.compilations.getting {
            val sqlcipherInterop by cinterops.creating {
                defFile(nativeInterop.resolve("iosArm64/Sqlcipher.def"))
                packageName("com.oldguy.sqlcipher")
                includeDirs.apply {
                    allHeaders(nativeInterop.resolve("iosArm64"))
                }
                compilerOpts += listOf("-I$nativeInteropPath/iosArm64")
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
        val nativeMain by creating {
            dependsOn(commonMain)
            kotlin.srcDir("src/nativeMain/kotlin")
        }
        val nativeTest by creating {
            kotlin.srcDir("src/nativeTest/kotlin")
            dependencies {
                implementation(kotlin("test"))
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.6.0")
            }
        }
        val iosX64Main by getting {
            dependsOn(nativeMain)
        }
        val iosX64Test by getting {
            dependsOn(nativeTest)
        }
        val iosArm64Main by getting {
            dependsOn(nativeMain)
        }
        val macosX64Main by getting {
            dependsOn(nativeMain)
        }
        val macosX64Test by getting {
            dependsOn(nativeTest)
        }

        all {
            languageSettings {
                optIn("kotlin.ExperimentalCoroutinesApi")
            }
        }
    }
}

tasks {
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
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.7.0")
}
