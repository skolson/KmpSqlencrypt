## KotlinSqlCipherMP

This project is a Kotlin multi-platform library for using Zetetic's SqlCipher encrypted Sqlite as an embedded SQL database, with an API that is intended to be identical across Android, iOs or other platforms. It currently supports 64-bit platforms; Android, MacOS, and iOS.

1/26/2022 - This is a brand new publish even though the code has been used for a long time in an Android app.  So this readme and the associated doc will be changing alot, especially in the next week or so.

## SqlCipher
*From the SqlCipher site:*

SQLCipher extends the [SQLite](https://www.sqlite.org) database library to add security enhancements that make it more suitable for encrypted local data storage like:

- on-the-fly encryption
- tamper detection
- memory sanitization
- strong key derivation

SQLCipher is based on SQLite and stable upstream release features are periodically integrated.

SQLCipher is maintained by Zetetic, LLC, and additional information and documentation is available on the official [SQLCipher site](https://www.zetetic.net/sqlcipher/). Github repository:
                                                                                                                                                                         [SqlCipher Github](https://github.com/sqlcipher/sqlcipher)
## Reason for Existence
Provide a common Kotlin API across multiple platforms for using SqlCipher. Intent is for this library to be used by common modules in Kotlin multiplatform projects so that there is no change in database access code across platforms and no platform-specific code required to use an embedded encrypted database.

Supported platforms (KMM targets) all 64 bit only:

- Android Arm, X64
- linuxX64
- macosX64
- iosArm64
- iosX64 Simulator

## Releases
Tag 0.4.0 is for SqlCipher 4.5.0, which uses Sqlite 3.36.0. SqlCipher is built from source for each platform using the gradle plugin [GradleSqlCipher plugin](https://github.com/skolson/sqlcipher-openssl-build). The SqlCipher build in this version is built with OpenSSL 3.0.0. See the gradle.build.kts files for details.

#Dependencies

Dependencies are intentionally kept to a minimum and only using Kotlin multi-platform libraries.

- BigDecimal support using  [ionspin BigDecimal](https://github.com/skolson/kotlin-multiplatform-bignum)
- Date and DateTime support using [Klock](https://github.com/korlibs/klock). Intent is to replace or add datetime support using Kotlinx-datetime once it has multi-platform support for parsing/formatting from/to strings built-in. As of this writing that's not there YET :-)
- Kotlin 1.6.10 
- Kotlin atomicfu
 

##Usage

This library has been used extensively in one app, so has not so far been published to maven. It can be easily published to mavenLocal using the gradle "publishToMavenLocal" task.

At some point the library may be published to the public Maven repository.

Use the gradle Build task copySqlcipherAndroid to cause gradle to build OpenSSL and SqlCipher using the [GradleSqlCipher plugin](https://github.com/skolson/sqlcipher-openssl-build). See the kmp-sc/build.gradle.kts file for the details of build options selected, NDK version used, and other build-specific options used to create the native .so files for android. This task will download source from OpenSSL and SqlCipher, and build and link both. Finally the resulting libsqlcipher.so file and matching sqlite.h for the ABIs requested are copied to src/AndroidMain/sqlcipher directory tree for use by the build. This build task is long-running - the OpenSSL build takes multiple minutes for each ABI. Also SqlCipher 4.5.0 (and earlier) uses some deprecated OpenSSL APIs related to HMAC, so there are warnings produced that can be ignored. 

Use the normal gradle build tasks to build the project. Gradle will build the Kotlin code and the JNI wrapper (written in one C++ module) and link in the SqlCipher build from src/AndroidMain/sqlcipher/<ABI>. The JNI code is built by gradle using the specified version of CMake (3.21.4 currently). The result of the build is an aar that can be published.0

Use the gradle Publish task 'publishToMavenLocal' to run a build and publish the artifacts produced to a local maven repository.

Define the library as a dependency using com.oldguy.kmpsc:kmp-sqlencrypt:0.4.0 (for example):

implementation("com.oldguy.kmpsc:kmp-sqlencrypt:0.4.0")

##Features

##Kotlin Type-safe access to SqlCipher
- Sqlite and therefore SqlCipher have known issues with precision on large decimals as all decimals are basically treated like doubles with about 15 digits of precision and the rounding issues inherent to float and double.  This library maps BigDecimal types of any precision to/from text columns in Sqlite tables. This allows use of arbitrary numerics of any precision to be stored and retrieved with no precision loss, and full rounding and scale control available.  However, note that precision loss can/will still occur if large precision values are used in SQL numeric functions. Bottom line - if you don't want precision loss, don't do numeric calculations in SQL using Sqlite.
- Date and DateTime support offer mappings between Sqlite date, datetime, and timestamp columns to matching Kotlin types, with default mappings that can be customized.
- Boolean support offer mappings between Sqlite text columns to Kotlin Boolean, with default mappings ("true","false") that can be customized.
- Basic types; Int, Long, ByteArray (Blob/Clob), Float, Double, String
    
##Kotlin coroutines support
Pretty much all SqlCipher usage should be using something like the Dispatcher.IO coroutine context for doing database access work.  This library has full coroutine support and uses suspend functions extensively. The caller, like a view model or other business logic controller, provides the coroutine scopes used.  The library does none of its own launches.

##Kotlin syntax for database usage
DSL-like builder syntax for configuring a SqlCipher database 
Extensive use of functions as arguments (lambdas). Requirement to directly implement any interface in the library is rare.
Kotlin-specific ease-of-use syntax for common operations.
- database open/close with invalid password lambdas
- passphrase support including raw and raw-with-hash
- encoding query-at-open or set-on-create
- system catalog metadata
- type-safe insert/update/select
- sql scripts support (multiple DDL and/or DML statements in a script)
- database version upgrade detection and upgrade script configuration
- multiple onOpen and onClose observer support 
- select statements with built-in prepare/bind/use/close support, per row lambdas similar to a cursor, support for named and unnamed bind variables
- transaction syntax for commit-rollback of multiple DML operations
- raw SQL
- pragmas and responses
- type safety using SqlValues collections for row contents and bind variables, containing zero or more SqlValue instances where SqlValue is a sealed class with subclasses for each of the supported types; SqlString, SqlInt, SqlLong, SqlBoolean, SqlBigDecimal, SqlDate, SqlDateTime, SqlBlob.  
- other

Intent is to make Data Access Objects (DAOs) using this library convenient and straightforward.

##Releases

Version 0.4.0 is built with:
- Kotlin 1.6.10
- OpenSSL 3.0.1  
- SqlCipher 4.5.0
- Android NDK 24.0.7956693. Minimum SDK 24, target SDK 31
- IOS minimum SDK 14.0

##Examples

<TODO 2022-1-26>

##Internals
Android and JVM-based platforms access the native libraries through a thin JNI layer written in C++. There is minimal logic in C++. For other platforms, Kotlin Native is used. Since the original stuff was written Kotlin now supports native Android, so likely should be able to eliminate the JNI. But the current Android implementation is production quality, so it remains JNI for now. 

[Github IonSpin](https://github.com/ionspin/kotlin-multiplatform-bignum) library is used for BigDecimal and BigInteger support is used for the unlimited precision stuff. If/when Kotlin Multi-platform releases BigDecimal support, this will be revisted.

Klock library is used for multi-platform Date and DateTime support. Once kotlinx.datetime releases support for formatting/parsing of at least ISO datetime formats, this will be revisited.  