# Module KmpSqlencrypt

This module contains the core API for accessing SqlCipher/Sqlite functionality use Kotlin Common code on any of the supported target platforms. Platforms currently supported are:

* Android - minimum SDK 23, target SDK 32. ABIs - arm64-v8, x86_64
* IOS - iosArm64, iosX64
* Windows - mingwX64, jvm
* MacosX64 - native
* LinuxX64 - jvm

The library for each platform includes the respective platform-specific build of SqlCipher, built using the *com.oldguy.gradle.sqlcipher-openssl-build* gradle plugin.

| Platform  | Target    | SqlCipher build   | Implementation         |
|-----------| ----------| ---------------   | ---------------------- |
| Android   | X86_64    | NDK               | Kotlin, JNI, C++       |
|           | arm64-v8a | NDK               | Kotlin, JNI, C++       |
| IOS       | Arm64     |                   | Kotlin Native          |
|           | X64       |                   | Kotlin Native          |
| Windows   | mingw64   | MSYS2 gcc         | Kotlin Native          |
| Windows   | jvm       | VStudio 2019      | Kotlin Native          |

See **[Github Gradle plugin sqlcipher-openssl-build](https://github.com/skolson/sqlcipher-openssl-build)** for details on the build plugin. See the build.gradle.kts file's `sqlcipher` block for build configuration.

The module is a standard Kotlin multiplatform structure, with the platform-agnostic common source in `commonMain` and platform-specific implementations in their corresponding Main sourcesets. All JVM implementations use JNI with a C++ wrapper of a subset of the Sqlite3 C API. The other platforms use a Kotlin Native wrapper of the same subset of the Sqlite3 C API.

* Dependencies * There are two current external dependencies used by this module; One to supply a BigDecimal data type, and one to supply Date and DateTime data types. The other standard data type mappings for String (CHAR, VARCHAR, etc), Int, Long, Float, Double, ByteArray (BLOB) require no external dependencies.

| Type          | Library |
| --------------| ------- |
| BigDecimal    | Since floats/doubles are inherently poor at decimal precision, the API supports use of BigDecimal using **[kotlin-multiplatform-bignum Github](https://github.com/skolson/kotlin-multiplatform-bignum)**. Since Kotlin Common does not yet support BigDecimal functionality, this library supplies the required precision handling, rounding, and scaling functionality for SQL decimal numbers. When Kotlin Common (kotlinx.math ?) does have this support, it will also be made available for use. Note that Sqlite does not handle precision well as it is essentially using a double under its covers. It is limited to about 15 digits of precison, and has no problem accepting SQL definitions requiring higher precision and then ignoring them.  So the ability to store/retrieve BigDecimal values as text is provided when precision loss must be avoided (large currency amounts are an example) |
| Date          | Until a standard Kotlin Common date and time API become available, the API supports DATE and DATIME using the corresponding classes using **[Klock library Github](https://github.com/korlibs/klock)** |

There are two packages. `com.oldguy.database` is a simple set of common interfaces and abstract classes for SQL database access. `com.oldguy.kiscmp` is the SqlCipher implementation of the interfaces/classes in `com.oldguy.database`.

# Package com.oldguy.database

This package has a simple abstraction of standard SQL database concepts; Database, Table (or view), DML statements, SELECT statements, bind argument(s) of basic data types, rows of basic data types, and of course the underlying basic data types. The main Sqlite data types are all supported. Since Kotlin is type-safe and Sqlite has only "type affinity", standard and configurable type mappings are available. See the sealed `SqlValue` classes for details.

# Package com.oldguy.kiscmp

This package contains the SqlCipher-specific implementations of the above interfaces and abstract classes. Opening/creating a SqlCipher database with no key supplied results in a standard Sqlite database with no encryption. To enable encryption, a non-empty key, aka Passphrase, has to be supplied. SqlCipher has a particular structure for database key formats it supports, see the `Passphrase` class for details.

