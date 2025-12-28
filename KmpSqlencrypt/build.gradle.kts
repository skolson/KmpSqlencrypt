import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.KotlinMultiplatform
import com.oldguy.gradle.OpensslExtension
import com.oldguy.gradle.SqlcipherExtension
import com.oldguy.gradle.BuildType
import com.oldguy.gradle.HostOs
import org.jetbrains.kotlin.gradle.plugin.mpp.DefaultCInteropSettings
import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFramework
import org.jetbrains.kotlin.gradle.plugin.mpp.NativeBuildType

plugins {
    libs.plugins.also {
        alias(it.kotlin.multiplatform)
        alias(it.android.kmp.library)
        alias(it.sqlcipher.build)
        alias(it.kotlinx.atomicfu)
        alias(it.dokka)
        alias(it.versionCheck)
        alias(it.maven.publish.vannik)
        alias(it.kotlin.cocoapods)
    }
}

val publishDomain = "io.github.skolson"
val mavenArtifactId = name
val appVersion = libs.versions.appVersion.get()
val appleFrameworkName = "KmpSqlencrypt"
group = publishDomain
version = appVersion

val ndkVersionValue = libs.versions.androidNdk.get()
val androidMinSdk = libs.versions.androidSdkMinimum.get().toInt()
val androidTargetSdkVersion = libs.versions.androidSdk.get().toInt()
val iosMinSdk = libs.versions.iosMinSdk.get()
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
        BuildType.linuxX64 to SqlcipherExtension.androidCompilerOptions,
        BuildType.linuxArm64 to SqlcipherExtension.androidCompilerOptions,
        BuildType.iosX64 to SqlcipherExtension.iosCompilerOptions,
        BuildType.iosArm64 to SqlcipherExtension.iosCompilerOptions,
        BuildType.iosSimulatorArm64 to SqlcipherExtension.iosCompilerOptions,
        BuildType.macosX64 to SqlcipherExtension.macOsCompilerOptions,
        BuildType.macosArm64 to SqlcipherExtension.macOsCompilerOptions
    )

    when (HostOs.query()) {
        HostOs.LINUX -> builds(BuildType.linuxBuildTypes)
        HostOs.WINDOWS -> builds(BuildType.windowsBuildTypes)
        HostOs.MAC -> builds(BuildType.appleBuildTypes)
    }

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
            linuxSdkLocation = "/mnt/Android"
            windowsSdkLocation = "D:\\Android\\sdk"
            macosSdkLocation = "/Volumes/mnt/Library/Android"
            ndkVersion = ndkVersionValue
            minimumSdk = androidMinSdk
        }
        apple {
            sdkVersion = "18"
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

val javaVersion: JavaLanguageVersion = JavaLanguageVersion.of(libs.versions.java.get().toInt())
java {
    toolchain {
        languageVersion.set(javaVersion)
    }
}
val githubUri = "skolson/$appleFrameworkName"
val githubUrl = "https://github.com/$githubUri"

kotlin {
    jvmToolchain {
        languageVersion = javaVersion
    }

    android {
        compileSdk = libs.versions.androidSdk.get().toInt()
        buildToolsVersion = libs.versions.androidBuildTools.get()
        namespace = "com.oldguy.kiscmp"

        minSdk = libs.versions.androidSdkMinimum.get().toInt()

        withHostTest {}
        withDeviceTest {
            instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
            execution = "HOST"
        }

        optimization {
            consumerKeepRules.publish = true
            consumerKeepRules.files.add(project.file("proguard-rules.pro"))
        }
    }

    cocoapods {
        name = appleFrameworkName
        ios.deploymentTarget = iosMinSdk
        summary = "Kotlin Multiplatform API for SqlCipher/OpenSSL"
        homepage = githubUrl
        license = "Apache 2.0"
        authors = "Steven Olson"
        framework {
            baseName = appleFrameworkName
            isStatic = true
        }
        // Maps custom Xcode configuration to NativeBuildType
        xcodeConfigurationToNativeBuildType["CUSTOM_DEBUG"] = NativeBuildType.DEBUG
        xcodeConfigurationToNativeBuildType["CUSTOM_RELEASE"] = NativeBuildType.RELEASE
    }

    fun DefaultCInteropSettings.cinteropConfig(dirName: String) {
        defFile(nativeInterop.resolve("$dirName/Sqlcipher.def"))
        packageName(kmpPackageName)
        includeDirs.apply {
            allHeaders(nativeInterop.resolve(dirName))
        }
        compilerOpts += listOf(
            "-I$nativeInteropPath/$dirName",
        )
        extraOpts("-libraryPath", "$nativeInteropPath/$dirName")
    }

    linuxX64 {
        val main by this.compilations.getting {
            val sqlcipherInterop by cinterops.creating {
                cinteropConfig("linuxX64")
            }
        }
    }
    linuxArm64 {
        val main by this.compilations.getting {
            val sqlcipherInterop by cinterops.creating {
                cinteropConfig("linuxArm64")
            }
        }
    }

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
                cinteropConfig("macosX64")
            }
        }
    }
    macosArm64 {
        binaries {
            framework {
                baseName = appleFrameworkName
                appleXcf.add(this)
                isStatic = true
            }
        }
        val main by this.compilations.getting {
            val sqlcipherInterop by cinterops.creating {
                cinteropConfig("macosArm64")
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
                cinteropConfig("iosX64")
            }
        }
    }
    iosArm64 {
        binaries {
            framework {
                baseName = appleFrameworkName
                appleXcf.add(this)
                isStatic = true
                freeCompilerArgs = freeCompilerArgs +
                        listOf("-Xoverride-konan-properties=osVersionMin=$iosMinSdk")
            }
        }
        val main by this.compilations.getting {
            val sqlcipherInterop by cinterops.creating {
                cinteropConfig("iosArm64")
            }
        }
    }
    iosSimulatorArm64 {
        binaries {
            framework {
                baseName = appleFrameworkName
                appleXcf.add(this)
                isStatic = true
            }
        }
        val main by this.compilations.getting {
            val sqlcipherInterop by cinterops.creating {
                cinteropConfig("iosSimulatorArm64")
            }
        }
    }

    applyDefaultHierarchyTemplate()
    sourceSets {
        getByName("commonMain") {
            dependencies {
                implementation(libs.kotlinx.datetime)
                implementation(libs.bigDecimal)
                implementation(libs.kotlinx.atomicfu)
            }
        }
        getByName("commonTest") {
            dependencies {
                implementation(kotlin("test-common"))
                implementation(kotlin("test-annotations-common"))
                implementation(libs.kotlinx.coroutines.test)
            }
        }
        getByName("androidMain") {
            dependencies {
                implementation(project(":kmp-android-jni"))
            }
        }
        getByName("androidHostTest") {
            dependencies {
                implementation(kotlin("test-junit"))
                implementation(libs.junit4)
                implementation(libs.kotlinx.coroutines.core)
            }
        }
        getByName("androidDeviceTest") {
            dependencies {
                implementation(kotlin("test-junit"))
                implementation(libs.junit4)
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.kotlinx.coroutines.test)
                implementation(libs.kmp.io)
            }
        }
        getByName("nativeMain") {
            dependencies {
                implementation(libs.kmp.io)
            }
        }
        getByName("nativeTest") {
            dependencies {
                implementation(kotlin("test"))
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.kotlinx.coroutines.test)
                implementation(libs.kotlinx.io.core)
            }
        }
    }

    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }
}

dokka {
    moduleName.set("Kotlin Multiplatform SqlCipher/Sqlite")
    dokkaPublications.html {
        suppressInheritedMembers.set(true)
        failOnWarning.set(true)
    }
    dokkaSourceSets.commonMain {
        enableAndroidDocumentationLink
        includes.from("$appleFrameworkName.md")
    }
}

mavenPublishing {
    coordinates(publishDomain, name, appVersion)
    configure(
        KotlinMultiplatform(
            JavadocJar.Dokka("dokkaGeneratePublicationHtml"),
            true,
            listOf("debug", "release")
        )
    )

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