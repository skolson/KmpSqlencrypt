import org.gradle.kotlin.dsl.signing
import com.oldguy.gradle.OpensslExtension
import com.oldguy.gradle.SqlcipherExtension
import com.oldguy.gradle.BuildType
import org.gradle.internal.os.OperatingSystem
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSetTree
import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFramework
import org.jetbrains.kotlin.gradle.plugin.mpp.NativeBuildType

plugins {
    libs.plugins.also {
        alias(it.android.library)
        alias(it.kotlin.multiplatform)
        alias(it.kotlinx.atomicfu)
        alias(it.dokka)
        alias(it.versionCheck)
    }
    kotlin("native.cocoapods")
    id("maven-publish")
    id("signing")
    id("com.oldguy.gradle.sqlcipher-openssl-build") version "0.3.5"
}

repositories {
    google()
    mavenCentral()
}

val mavenArtifactId = "kmp-sqlencrypt"
val appleFrameworkName = "KmpSqlencrypt"
group = "com.oldguy"
version = libs.versions.appVersion.get()

val ndkVersionValue = "26.1.10909125"
val androidMinSdk = 26
val androidTargetSdkVersion = 34
val iosMinSdk = "14"
val kmpPackageName = "com.oldguy.sqlcipher"

val androidMainDirectory = projectDir.resolve("src").resolve("androidMain")
val nativeInterop = projectDir.resolve("src/nativeInterop")
val nativeInteropPath: String = nativeInterop.absolutePath

sqlcipher {
    useGit = false
    version = libs.versions.sqlcipher.get()
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
        tagName = "openssl-${libs.versions.openssl.get()}"
        useGit = false
        configureOptions = OpensslExtension.smallConfigureOptions
        buildSpecificOptions = OpensslExtension.buildOptionsMap
    }
}

android {
    compileSdk = androidTargetSdkVersion
    ndkVersion = ndkVersionValue
    buildToolsVersion = "34.0.0"
    namespace = "com.oldguy.kiscmp.android"

    sourceSets {
        getByName("main") {
            manifest.srcFile(androidMainDirectory.resolve("AndroidManifest.xml"))
        }
    }

    defaultConfig {
        minSdk = androidMinSdk

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
        targetCompatibility = JavaVersion.VERSION_17
    }

    dependencies {
        testImplementation(libs.junit4)
        androidTestImplementation(libs.bundles.androidx.test)
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
}

kotlin {
    androidTarget {
        java.targetCompatibility = JavaVersion.VERSION_17
        @OptIn(ExperimentalKotlinGradlePluginApi::class)
        instrumentedTestVariant.sourceSetTree.set(KotlinSourceSetTree.test)
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

    applyDefaultHierarchyTemplate()
    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(libs.kotlinx.datetime)
                implementation(libs.bigDecimal)
                implementation(libs.kotlinx.atomicfu)
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test-common"))
                implementation(kotlin("test-annotations-common"))
                implementation(libs.kotlinx.coroutines.test)
            }
        }
        val androidMain by getting {
            dependsOn(commonMain)
        }
        val androidUnitTest by getting {
            dependencies {
                implementation(kotlin("test-junit"))
                implementation(libs.junit4)
                implementation(libs.kotlinx.coroutines.core)
            }
        }
        val androidInstrumentedTest by getting {
            dependencies {
                implementation(kotlin("test-junit"))
                implementation(libs.junit4)
                implementation(libs.kotlinx.coroutines.core)
            }
        }
        val nativeMain by getting {
            dependsOn(commonMain)
        }
        val nativeTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation(libs.kotlinx.coroutines.core)
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
        }
    }

    publishing {
        publications.withType(MavenPublication::class) {
            artifactId = artifactId.replace(project.name, mavenArtifactId)
            
            // workaround for https://github.com/gradle/gradle/issues/26091
            val dokkaJar = tasks.register("${this.name}DokkaJar", Jar::class) {
                group = JavaBasePlugin.DOCUMENTATION_GROUP
                description = "Dokka builds javadoc jar"
                archiveClassifier.set("javadoc")
                from(tasks.named("dokkaHtml"))
                archiveBaseName.set("${archiveBaseName.get()}-${this.name}")
            }
            artifact(dokkaJar)

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
    implementation(libs.androidx.core.ktx)
}

// workaround
task("testClasses").doLast {
    println("workaround for Iguana change")
}