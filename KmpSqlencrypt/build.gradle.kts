import org.gradle.kotlin.dsl.signing
import com.oldguy.gradle.OpensslExtension
import com.oldguy.gradle.SqlcipherExtension
import com.oldguy.gradle.BuildType
import org.gradle.internal.os.OperatingSystem
import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFramework
import org.jetbrains.kotlin.gradle.plugin.mpp.NativeBuildType

plugins {
    id("com.android.library")
    kotlin("multiplatform")
    kotlin("native.cocoapods")
    id("maven-publish")
    id("signing")
    id("kotlinx-atomicfu")
    id("org.jetbrains.dokka") version "1.7.10"
    id("com.oldguy.gradle.sqlcipher-openssl-build") version "0.3.4"
    id("com.github.ben-manes.versions") version "0.42.0"
}

repositories {
    gradlePluginPortal()
    google()
    mavenCentral()
}

val mavenArtifactId = "kmp-sqlencrypt"
val appleFrameworkName = "KmpSqlencrypt"
group = "com.oldguy"
version = "0.4.5"

val ndkVersionValue = "25.1.8937393"
val androidMinSdk = 24
val androidTargetSdkVersion = 33
val iosMinSdk = "14"
val kmpPackageName = "com.oldguy.sqlcipher"

val androidMainDirectory = projectDir.resolve("src").resolve("androidMain")
val nativeInterop = projectDir.resolve("src/nativeInterop")
val nativeInteropPath: String = nativeInterop.absolutePath
val javadocTaskName = "javadocJar"

val kotlinCoroutinesVersion = "1.6.4"
val kotlinCoroutines = "org.jetbrains.kotlinx:kotlinx-coroutines-core:$kotlinCoroutinesVersion"
val kotlinCoroutinesTest = "org.jetbrains.kotlinx:kotlinx-coroutines-test:$kotlinCoroutinesVersion"
val klock = "com.soywiz.korlibs.klock:klock:2.7.0"
val bignum = "com.ionspin.kotlin:bignum:0.3.6"


sqlcipher {
    useGit = false
    version = "4.5.2"
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
            minimumSdk = androidMinSdk
        }
        apple {
            sdkVersion = "15"
            sdkVersionMinimum = iosMinSdk
        }
    }
    openssl {
        tagName = "openssl-3.0.5"
        useGit = false
        configureOptions = OpensslExtension.smallConfigureOptions
        buildSpecificOptions = OpensslExtension.buildOptionsMap
    }
}

android {
    compileSdk = androidTargetSdkVersion
    ndkVersion = ndkVersionValue
    buildToolsVersion = "33.0.3"
    namespace = "com.oldguy.kiscmp.android"

    sourceSets {
        getByName("main") {
            java.srcDir(androidMainDirectory.resolve("kotlin"))
            manifest.srcFile(androidMainDirectory.resolve("AndroidManifest.xml"))
        }
        getByName("test") {
            java.srcDir("src/androidTest/kotlin")
        }
        getByName("androidTest") {
            java.srcDir("src/androidAndroidTest/kotlin")
        }
    }

    defaultConfig {
        minSdk = androidMinSdk
        targetSdk = androidTargetSdkVersion

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
            version = "3.22.1"
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

tasks {
    dokkaHtml {
        moduleName.set("Kotlin Multiplatform SqlCipher/Sqlite")
        dokkaSourceSets {
            named("commonMain") {
                noAndroidSdkLink.set(false)
                includes.from("$appleFrameworkName.md")
            }
        }
    }
    create<Jar>(javadocTaskName) {
        dependsOn(dokkaHtml)
        archiveClassifier.set("javadoc")
        from(dokkaHtml.get().outputDirectory)
    }
}

@Suppress("UNUSED_VARIABLE")
kotlin {
    android {
        publishLibraryVariants("release", "debug")
        mavenPublication {
            artifactId = artifactId.replace(project.name, mavenArtifactId)
        }
    }

    val githubUri = "skolson/$appleFrameworkName"
    val githubUrl = "https://github.com/$githubUri"
    cocoapods {
        ios.deploymentTarget = iosMinSdk
        summary = "Kotlin Multiplatform API for SqlCipher/OpenSSL"
        homepage = githubUrl
        license = "Apache 2.0"
        authors = "Steven Olson"
        framework {
            baseName = appleFrameworkName
            isStatic = true
            embedBitcode(org.jetbrains.kotlin.gradle.plugin.mpp.BitcodeEmbeddingMode.BITCODE)
        }
        // Maps custom Xcode configuration to NativeBuildType
        xcodeConfigurationToNativeBuildType["CUSTOM_DEBUG"] = NativeBuildType.DEBUG
        xcodeConfigurationToNativeBuildType["CUSTOM_RELEASE"] = NativeBuildType.RELEASE
    }

    /**
     * Once a github podspec repo is set up, the following is a work-around for issue KT-42105 that is supposedly fixed in 1.6.20
    val podspec = tasks["podspec"] as org.jetbrains.kotlin.gradle.tasks.PodspecTask
    podspec.doLast {
        val spec = file("${project.name.replace("-", "_")}.podspec")
        val newPodspecContent = spec.readLines().map {
            if (it.contains("spec.source")) "    spec.source = { :git => '$githubUrl.git', :tag => '${project.version}' }" else it
        }
        spec.writeText(newPodspecContent.joinToString(separator = "\n"))
    }
    */

    val appleXcf = XCFramework()
    macosX64 {
        binaries {
            framework {
                baseName = appleFrameworkName
                appleXcf.add(this)
                isStatic = true
            }
        }
        val main by this.compilations.getting {
            val sqlcipherInterop by cinterops.creating {
                defFile(nativeInterop.resolve("macosX64/Sqlcipher.def"))
                packageName(kmpPackageName)
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
                baseName = appleFrameworkName
                appleXcf.add(this)
                isStatic = true
            }
        }
        val main by this.compilations.getting {
            val sqlcipherInterop by cinterops.creating {
                defFile(nativeInterop.resolve("iosX64/Sqlcipher.def"))
                packageName(kmpPackageName)
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
                baseName = appleFrameworkName
                appleXcf.add(this)
                isStatic = true
                embedBitcode("bitcode")
                freeCompilerArgs = freeCompilerArgs +
                        listOf("-Xoverride-konan-properties=osVersionMin=$iosMinSdk")
            }
        }
        val main by this.compilations.getting {
            val sqlcipherInterop by cinterops.creating {
                defFile(nativeInterop.resolve("iosArm64/Sqlcipher.def"))
                packageName(kmpPackageName)
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
                implementation(klock)
                implementation(bignum)

            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test-common"))
                implementation(kotlin("test-annotations-common"))
                implementation(kotlinCoroutinesTest)
            }
        }
        val androidMain by getting {
            dependsOn(commonMain)
        }

        val androidTest by getting {
            dependencies {
                implementation(kotlin("test-junit"))
                implementation("junit:junit:4.13.2")
                implementation(kotlinCoroutines)
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
            dependsOn(commonTest)
            dependencies {
                implementation(kotlin("test"))
                implementation(kotlinCoroutines)
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
            if (name.endsWith("Test")) {
                languageSettings {
                    optIn("kotlin.ExperimentalCoroutinesApi")
                }
            }
        }
    }

    publishing {
        publications.withType(MavenPublication::class) {
            artifactId = artifactId.replace(project.name, mavenArtifactId)
            artifact(tasks.getByPath(javadocTaskName))
            pom {
                name.set("$appleFrameworkName Kotlin Multiplatform SqlCipher/Sqlite")
                description.set("Library for use of SqlCipher/Sqlite using the same Kotlin API on supported 64 bit platforms; Android IOS, Windows, Linux, MacOS")
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

signing {
    isRequired = false
    sign(publishing.publications)
}

dependencies {
    implementation("androidx.core:core-ktx:1.8.0")
}
