## KmpSqlencrypt Change Log

** 0.8.0 ** 2025-06

- Kotlin 2.1.21
- Coroutines 1.10.2
- Atomicfu 0.27.0
- datetime 0.6.2
- com.ionspin.kotlin:bignum 0.3.10
- SqlCipher 4.9.0
- OpenSSL 3.5.0
- CMake 4.0.2
- com.oldguy.gradle.sqlcipher-openssl-build 0.5.1. Note: this version of the plugin handles multiple changes required by builds of SqlCipher 4.7.0 and later
- Android NDK 28.1.13356709
- Dokka 2.0.0
- Android Gradle Plugin 8.5.2
- Test manniodermaus 1.12.0.0
- Fix Issue #4 - skip any system catalog rows where "sql" column contains a null
- LinuxX64 native support
- New dependency on io.github.skolson:kmp-io only for the common Charset support of UTF-16 (LE and BE) used with the text16 api in Sqlite3 
- Removed redundant SqlCipherApi.kt files from the various native platform source trees, added it to nativeMain sourceset
 
**Note:** SqlCipher 4.7.0 and later Linux cinterop links now need C runtime symbols "fcntl64" and "__isoc23_strtol" which are resolveble dynamically at runtime. So the Linux Sqlcipher.def file specifies link options to ignore these being unresolved during builds.

** 0.7.1 ** 2024-03

- kotlin 1.9.23
- AGP release update

** 0.7.0 ** 2024-03

NOTE: this release contains a breaking change.  All Date and DateTime types have been implemented using kotlinx-datetime LocalDate and ZLocalDateTime classes. Dependency on Klock library for dates and times has been removed.

NOTE: kotlinx datetime supports nanosecond resolution. Sqlite resolution of time is milliseconds. To preserve use of the sqlite functions around time, when a datetime is saved to the database all sub-millisecond resoultion is lost. This library includes a LocalDateTime extension function `truncateToMilliseconds` that does the explicit truncation for unit tests and other cases where it is desired to compare the pre-sqlite value to the same value retrieved from sqlite and expect them to be equal.

- kotlinx.datetime library v0.6.0-RC.2
- klock dependency deleted
- plugin sqlcipher-openssl-build 0.4.0 for Macos Arm64 build support for openssl and SqlCipher
- new target Macos ARM64 for M1 or other ARM64 mac hardware


** 0.6.0 ** 2024-02

- Kotlin 1.9.22
- Gradle 8.6
- Gradle version catalog usage
- SqlCipher 4.5.6
- OpenSSL 3.2.1
- AGP 8.4.0-alpha08
- Android NDK version 26.1.10909125
- Minimum android SDK now 26 (was 24)
- Use Kotlin MP default hierarchy template for source sets. Force androidInstrumentedTest to be part of the test source set tree.
- Kotlinx coroutines 1.8.0-RC2
- Newer patch levels on klock library and bignum library. See libs.versions.toml in gradle directory.
- No functional changes. Had to add a couple explicit imports to the native code class to get a correct build in 1.9.22. No other code changes. Just gradle and build.gradle.kts changes.

** 0.5.3 ** 2023-08

- Kotlin 1.9.10
- Gradle 8.3
- Atomicfu 0.22.0
- Add "NUM" to the list of Sqlite valid Numeric/Decimal types. "NUM" used by tables created with `create table as ... select` statements

- ** 0.5.2 ** 2023-08

No functional changes, just release updates

- Kotlin 1.9.0
- Gradle 8.3-rc-3
- Klock 4.0.9
- Openssl 3.1.2
- Atomicfu 0.21.0
- Coroutines 1.7.3
- Android build tools 34.0.0
- NDK 25.2.9519653
- Changed work-around for Jetbrains issue KT-55751 to handle IosFat configurations in 1.9.0. Supposed to be a fix coming with 1.9.20
- SqlCipher/OpenSSL build plugin 0.3.5

** 0.5.0 ** 2023-05

No API changes, only release upgrades.

- Kotlin 1.8.21
- Atomicfu 0.20.2
- OpenSSL 3.1.0 (building openssl gets errors due to issue #18619, but error is with tests which are not used by sqlcipher)
- Sqlcipher 4.5.4 (Sqlite 3.41.2)
- Gradle 8.1.1
- Kotlin coroutines 1.7.0
- Klock 3.4.0
- Ionspin Bignum 0.3.8

- ** 0.4.5 ** 2022-06

No API changes, only release upgrades.

- OpenSSL 3.0.5 (building openssl gets errors due to issue #18619, but error is with tests which are not used by sqlcipher)
- Sqlcipher 4.5.2 (Sqlite 3.39.2)
- klock 2.7.0
- ionspin bignum 0.3.6
- Kotlin 1.7.10
- kotlinx atomicfu 0.18.2
- kotlinx coroutines 1.6.4
- Android NDK 25.0.8775105
- Android build tools 33.0.0
- Gradle 7.5

**0.4.4**  2022-03

- SqlCipher 4.5.1 (Sqlite 3.37.2) 
- Correct native code supporting strings when database encoding is configured for a 16 bit charset.

**0.4.3**  2022-02-02

- iosApp now has a SwiftUI screen that invokes Swift code using the library. For testing/exploring Swift-Kotlin mappings. 
- Added SwiftReadMe.md for narrative on the structure of the Swift app and the various mapping/usage issues.
- SqlValues toString() fix
- SqlValues has two new convenience methods for adding values. 
- Cocoapods configuration tweaks, maven publishing artifactId changes. Externally Framework name is still "KmpSqlencrypt.framework. Maven artifact prefix for all platforms is still "kmp-sqlencrypt".

**0.4.2**
- 0.4.1 inadvertently made **SqlcipherDatabase var invalidPassword** a suspend function. Removed suspend.
- Readme changes
- added helper function for enabling foreign keys pragma 
- added missing specRepo and a workaround for issue KT-42105 to cocoapods so podspec file points to github
- 0.4.1 breaks database open on encrypted DBs due to new encoding check being done too soon, moved it.
- upgrading to Ionspin 0.3.4 has what turns out to be a breaking change in its narrowing functions on float and double. Fixed.
- added unit test for basic functionality when using UTF-16LE encoding

**0.4.1** Warning this tag has two breaking changes. Not caught because unit tests didn't run. See 0.4.2.

- changed maven artifact name to remove "Sqlcipher" in name, now using "kmp-sqlencrypt". 
- Apple podspec name also changed, Framework name is still "KmpSqlencrypt.framework"
- maven publish moved to kotlin multiplatform setup
- maven publish includes Dokka artifact  
- Ionspin v 0.3.4 (was 0.3.3)
- updated Readme.md
- added javadoc to artifacts
- Gradle 7.3.3 (was 7.3.2)
- added path, invalidPassword lambda, and onOpenPragmas lambda to [sqlcipher] configuration. Added open function overload to rely on the values from [sqlcipher] configuration.
- added encoding pragma to open process

**0.4.0**

- Initial published release. Built using:
    - SqlCipher 4.5.0 
    - Sqlite 3.36.0
    - OpenSSL 3.0.1
    - Note that Sqlcipher is using some HMAC APIs deprecated with OpenSSL 3.x resulting in warnings during builds. Next release of SqlCipher is supposed to fix this. 
