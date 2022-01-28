## KmpSqlencrypt

This project is a Kotlin multi-platform library for using Zetetic's SqlCipher encrypted Sqlite as an embedded SQL database, with an API that is intended to be identical across Android, iOs or other platforms. It currently supports 64-bit platforms; Android, MacOS, iOS, Linux, and Windows (mingwX64).

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
Tag 0.4.1 is for SqlCipher 4.5.0, which uses Sqlite 3.36.0. SqlCipher is built from source for each platform using the gradle plugin [GradleSqlCipher plugin](https://github.com/skolson/sqlcipher-openssl-build). The SqlCipher build in this version is built with OpenSSL 3.0.0. See the gradle.build.kts files for details.

# Dependencies

Dependencies are intentionally kept to a minimum and only using Kotlin multi-platform libraries.

- BigDecimal support using  [ionspin BigDecimal](https://github.com/skolson/kotlin-multiplatform-bignum)
- Date and DateTime support using [Klock](https://github.com/korlibs/klock). Intent is to replace or add datetime support using Kotlinx-datetime once it has multi-platform support for parsing/formatting from/to strings built-in. As of this writing that's not there YET :-)
- Kotlin 1.6.10 
- Kotlin atomicfu
 

## Usage

This library has been used extensively in one app, so has not so far been published to maven. It can be easily published to mavenLocal using the gradle "publishToMavenLocal" task.

At some point the library may be published to the public Maven repository.

Use the gradle Build task copySqlcipherAndroid to cause gradle to build OpenSSL and SqlCipher using the [GradleSqlCipher plugin](https://github.com/skolson/sqlcipher-openssl-build). See the kmp-sc/build.gradle.kts file for the details of build options selected, NDK version used, and other build-specific options used to create the native .so files for android. This task will download source from OpenSSL and SqlCipher, and build and link both. Finally the resulting libsqlcipher.so file and matching sqlite.h for the ABIs requested are copied to src/AndroidMain/sqlcipher directory tree for use by the build. This build task is long-running - the OpenSSL build takes multiple minutes for each ABI. Also SqlCipher 4.5.0 (and earlier) uses some deprecated OpenSSL APIs related to HMAC, so there are warnings produced that can be ignored. 

Use the normal gradle build tasks to build the project. Gradle will build the Kotlin code and the JNI wrapper (written in one C++ module) and link in the SqlCipher build from src/AndroidMain/sqlcipher/<ABI>. The JNI code is built by gradle using the specified version of CMake (3.21.4 currently). The result of the build is an aar that can be published.0

Use the gradle Publish task 'publishToMavenLocal' to run a build and publish the artifacts produced to a local maven repository.

Define the library as a gradle dependency:

```
    dependencies {
        implementation("com.oldguy.kmpsc:kmp-sqlencrypt:0.4.1")
    }  
```

## Features

## Kotlin Type-safe access to SqlCipher
- Sqlite and therefore SqlCipher have known issues with precision on large decimals as all decimals are basically treated like doubles with about 15 digits of precision and the rounding issues inherent to float and double.  This library maps BigDecimal types of any precision to/from text columns in Sqlite tables. This allows use of arbitrary numerics of any precision to be stored and retrieved with no precision loss, and full rounding and scale control available.  However, note that precision loss can/will still occur if large precision values are used in SQL numeric functions. Bottom line - if you don't want precision loss, don't do numeric calculations in SQL using Sqlite.
- Date and DateTime support offer mappings between Sqlite date, datetime, and timestamp columns to matching Kotlin types, with default mappings that can be customized.
- Boolean support offer mappings between Sqlite text columns to Kotlin Boolean, with default mappings ("true","false") that can be customized.
- Basic types; Int, Long, ByteArray (Blob/Clob), Float, Double, String
    
## Kotlin coroutines support
Pretty much all SqlCipher usage should be using something like the Dispatcher.IO coroutine context for doing database access work.  This library has full coroutine support and uses suspend functions extensively. The caller, like a view model or other business logic controller, provides the coroutine scopes used.  The library does none of its own launches.

## Kotlin syntax for database usage
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

## Releases

Version 0.4.1 is built with:
- Kotlin 1.6.10
- OpenSSL 3.0.1  
- SqlCipher 4.5.0
- Android NDK 24.0.7956693. Minimum SDK 24, target SDK 31
- IOS minimum version 14.0
- MacOSX Big Sur or later, Xcode 13 or later
- Cocoapods 

## Usage

The library is not yet published to Maven. So once the repo is cloned, open it with Android Studio or Intellij Idea, sync, and use the "publishToMavenLocal" task to publish the repo. Gradle 7.3.3 is used.  Any host will publish the android artifacts (which are packaged as aars). A Mac OSX Big Sur or later host is required with current Xcode installed.  

Kotlin projects can use with new gradle dependency:

```
    dependencies {
        implementation("com.oldguy:kmp-sqlencrypt:0.4.1")
    }
```

The library is intended primarily for use by Kotlin multiplatform projects. The extensive use of coroutines and lambdas do not prevent direct usage of the library from Swift 5.5 in Xcode, but it does cause restrictions and extra coding not required by Kotlin users. If Swift usage is desired, the project is deployed as a cocoapods framework named "KmpSqlencrypt", see file [kmp-sqlencrypt.podspec] for details.

* TODO quick pod install instructions in an existing Xcode project *

Basic usage steps for any platform (with details for each below) are:

- Declare/configure a database instance using the "sqlcipher" DSL. Examples of configuration include:
    
  - allow create (default false)
  - specify encoding (only on creates)
  - user version - on an existing database, if this number is greater than the current user version, an optional upgrade lambda is invoked at open time.   
  
- Open/create the database. There basic scenarios:

  - Create an unencrypted database as configured (with no Passphrase), either in memory (empty path), or at a specified file path. This is essentially equivalent to using Sqlite with no encryption support
  - Create an encrypted database as configured, using a Passphrase, either in memory (empty path), or at a specified file path. The database is keyed/encrypted using the Passphrase. Sqlcipher supports string passwords (non-empty), a hash, or a hash with a salt.  See the [Passphrase] class for rules/usage details, especially when using hashes.
  - Open an existing database using a file path.  Encrypted files require the correct passphrase (of course). 
  - convenience functions for quick open/use/close tasks if desired  

- Use the open instance with simple functions

  - Scripts, any number of semicolon separated SQL DDL statements or DML not needing binds
  - Insert/update/delete statements with or without bind variables (positional or named), with type-safe bindings
  - Select statements with or without bind variables (positional or named), with type-safe bindings for both the bind arguments and the returned columns in each row. 
  - Transactions with or without save points. Nested transactions, rollbacks on uncaught exceptions supported using the "transaction { }" support.
  - Query tables and columns metadata on a table using the SystemCatalog support

- Close the database.   

### Configuration

Use a pretty simple DSL-like syntax to create and configure a database instance. Parameters that can be configured on the new instance include:
- **createOk** defaults to false. False indicates that if the path specified at open time is not found, the open fails. True indicates that if the path specified is not found, a new file should be created.
- **encoding** defaults to SqliteEncoding.Utf_8. This is only useful if createOk = true and a new database is created.  Immediately after a new database is created, causes a pragma to be executed that sets the encoding to use within Sqlite.  As per Sqlite doc, this can only be set at create time.
- **readOnly** defaults to false. Set to true if changes to the database are to be disabled.
- **path** String value that defaults to "", which indicates an in-memory database. Otherwise, specify the absolute path where the database file is to be opened or created. Can also be specified/overridden at open time.
- **userVersion** an integer value that specifies the Sqlite user version desired. If creating a database, this value is recorded in the database. If opening a database, this version is compared to the stored version in the database.  If they are different, then open invokes an optional function (if configured) where upgrade scripts can run. If there is no optional function, or the one specified returns true, the Sqlite userVersion is updated to the new value. If the upgrade function returns false, the version is not changed.
- **userVersionUpgrade** a nullable suspend lambda type with default value of null.  If the function is specified, it typically examines the currentVersion and newVersion, and applies any desired script(s) to do the version upgrade. At success it returns [true].
- **softHeapLimit** a sqliteParm that can be configured if desired. default is 4MB
- **onOpenPragmas** a nullable lambda, that if specified can do certain pragmas that must happen after open but before the first database usage. Attempts at doing anything else here will likely fail and should not be necessary. Typically this is only needed in cases where a sqlcipher key migration or some other special pragma needs to happen.
- **integrityCheck** a boolean that defaults to false.  If true, causes some checks to run after a successful open has happened.
- **observers** an empty mutable list of [DatabaseObserver] implementations. if this is populated, these listeners get notified at open time and at close time.

### Opening Database

These steps happen at open time:

- First step uses specified [path] and [passphrase] to open the database with the specified key. Sqlite is ready for pragmas, but at this point the database may not yet be valid as determining if the key will decrypt the database successfully. Basically just ensures the path is valid and accessible and Sqlite can set up its file handle. API sqlite3_open_v2 is used. Any error throws a SqliteException.
- Sets the softHeapLimit
- if creating, sets the encoding, otherwise queries current encoding
- issues the pragma setting the key for the database from the [Passphrase] specified (if there is one)
- if [onOpenPragmas] lambda is specified, invokes it.  
- a simple query is attempted as first database usage (currently "select count(*) from sqlite_master").  If this fails with a NOT A DATABASE error, the [invalidPassphrase] lambda is invoked and the open attempt stops. Any other error or no specification of the [invalidPassphrase] lambda will cause a SqliteException and open stops.
- if [integrityCheck] is true, the "integrity_check" pragma is run and its response verified. If this pragma fails, a SqliteException is thrown and open fails.
- the current userVersion is queried from Sqlite and compared to the configured one. if they are different and the [userVersionUpgrade] lambda is configured, then it is invoked.  If it returns false, open still continues but the database userVersion is not updated to match the configured one.
- At this point open has succeeded, so the [SystemCatalog] instance is created and configured based on what is present in the database. It is populated with a map of Table instances, which each have a list of Column instances with metadata about each column in the table.
- Final open step looks to see if any [DatabaseObserver] instances have been configured into the [observers] list, and if they have, notify each in list order. 

### Other operations

Once a database has been opened, there are helpers for executing scripts or other raw SQL. 

## Examples

Here's an example of a simple test that creates a new database in memory with UTF-8 encoding that is encrypted with a passphrase as key, creates a table with a primary key, inserts a row, queries the row, and closes the database. It also shows accessing some of the provided properties set at open time, and simple use of the SystemCatalog support.

```
    /**
     * note this sample is a suspend function, which is typical as many database functions should be launched from a Dispatchers.IO scope
     */
    suspend fun simpleTest() {
        sqlcipher {
            createOk = true
            encoding = SqliteEncoding.Utf_16le    // text will be encoded with UTF-16 little endian encoding
            path = ""                             // no file path indicates an inmemory database, Sqlite path ":memory"
            userVersion = 1                       // if specified becodes the new userVersion in the Sqlite database
            userVersionUpgrade = { database, currentVersion, newVersion ->
              // use this to invoke any appropriate script to migrate [database] from [currentVersion] which is the user version 
              // queried at open time, to [newVersion] which is the version configured above
              true  // return true to indicate success
            }
            observers.addAll(listOf( ... one or more implementations of [DatabaseObserver]))
        }.apply {  // in the scope of this block, the configured database instance is [this]
            use(Passphrase("anypwd123!"), 
                invalidPassphrase = { database, passphrase ->
                    // do something when database can't be opened due to invalid password, can't happen at create time :-)
                }) {   // in this lambda, [this] is the configured instance of [SqlcipherDatabase]
                    // at this point database is open, values are available and suspend functions are usable. 
                    val v = sqlcipherVersion         // Typical version string is "4.5.0 community"
                    val vs = sqliteVersion           // Typical version string "3.36.00"
                    val b = isOpen                   // will be true here
                    val vu = userVersion             // an Int value, 0 if versioning not used. In this example would == 1 
                    val err = errorMessage           // gets the most recent error text from Sqlite, in this example would be empty string since no error yet.
                    val tableMap = catalog.tables    // [catalog] property is an instance of [SqliteSystemCatalog] built at open time. [tables] is a map keyed by table name of Table instances, which contain a number of properties, including a collection of Column instances.
                    
                    // here is a simple create table
                    execute("create table test(id INTEGER PRIMARY KEY, name VARCHAR(255), date1 DATE, dateTime1 timestamp, num1 DECIMAL(25,3), real1 REAL, dub DOUBLE, long1 BIGINT, bool1 char(1));")
                    
                    // here is a simple Sqlite transaction that does a couple inserts using named bind arguments. If it succeeds, the changes are committed.
                    transaction {
                        val insertValues = 
                            val testDate = DateTime.now() // this is using the klock library for KMP datetime support
                            val args = SqlValues(
                                SqlValue.StringValue("name", "Any length string"),
                                SqlValue.DateValue("date1", testDate.date),
                                SqlValue.DateTimeValue("dateTime1", testDate),
                                SqlValue.DecimalValue("num1", BigDecimal.parseString("12345678901234567890.98")),
                                SqlValue.FloatValue("real1", 3.9F),
                                SqlValue.DoubleValue("dub", 3.5),
                                SqlValue.LongValue("long1", 2L),
                                SqlValue.BooleanValue("bool1", true
                            )
                        db.statement("insert into test (name, date1, dateTime1, num1, real1, dub, long1, bool1) values(:name, :date1, :dateTime1, :num1, :real1, :dub, :long1, :bool1);").use {
                            // do the insert, save the generated primary key
                            val rowId = it.insert(args)     
                            // do the insert again, save the next generated primary key
                            val rowId2 = it.insert(args)
                        }    
                    }
                    
                    // the transaction above has committed the inserts, so query the two rows:
                    db.usingSelect("select * from test") {rowCount: Int, row: SqlValues ->
                        // this lambda gets invoked for each row, with rowCOunt starting at one. Row contains a collection
                        // of sealed class [SqlValue] instances, that can be looked up by name or by column index. There 
                        // are lots of different getters, helpers with isNull and other details, but below are examples of each basic type
                        val rowId = row.requireLong(0) 
                        val name = row.requireString(1) 
                        val date1 = row.requireDate(2)      // is an instance of Klock library [Date]
                        val dateTime1 = row.getDateTime(3)  // is a nullable instance of Klock library [DateTime]
                        val num1 = row.requireDecimal(4)    // is an instance of Ionspin library [BigDecimal] similar to java's BigDecimal
                        val real1 = row.requireFloat(5)
                        val dub = row.requieDouble("dub")      
                        val long1 = row.getLong(7)          // is an instance of Long?
                        val num1 = row.requireBoolean(8)    // mapping for booleans is configurable using the SqlValue.BooleanValue class
                                                            // default mapping is for text column getting values "true" or "false"
                                                            // could also be char(1) columns with 'Y' and 'N' values
                                                            // could also be integer columns with 0 and 1 values
                }
        }
    }
```

### Type safety notes

Since Sqlite is by design not type safe, conventions have to be followed for some types to get good usability.  Below are some notes on these.

**Dates and DateTimes** these are saved using ISO formats compatible with the Sqlite DATE and DATETIME functions. So if it is desired to use DATE() or DATETIME() or related functions in SQL, they work without issue.  If other non-iso mappings are desired, they can be supported with mappings added to [SqlValue.DateValue] and [SqlValue.DateTime]. But custom mappings will likely result is preventing usage of DATE and DATETIME functions in SQL.

**Boolean** since Sqlite doesn't have this, a TEXT column or CHAR column or INTEGER column can be used and whatever mappings you desire can be set up using [SqlValue.BooleanValue]

**Decimal** internally all numeric functions in Sqlite are implemented with doubles.  Doubles have well defined behaviors that result in precision loss (with more than 15 to 16 digits of precision in the mantissa), and rounding issues.  The library supports using TEXT columns to store BigDecimal instances of ANY precision that never have precision loss and offer complete rounding control and scale setting similar to java [BigDecimal].  The values are stored in a plain format compatible with Sqlite DECIMAL functions, i.e: `CAST(textCol AS DECIMAL)`. So Sqlite numeric functions can be used if desired, although only at risk of losing precision/rounding again.  

### Other notes

There's a bunch more helpers and other stuff that is yet to be documented, but above are the basics.  If this library turns out to be interesting to **anyone** :-), the doc will be expanded.

## Internals
Android and JVM-based platforms access the native libraries through a thin JNI layer written in C++. There is minimal logic in C++. For other platforms, Kotlin Native is used. Since the original stuff was written Kotlin now supports native Android, so likely should be able to eliminate the JNI. But the current Android implementation is production quality, so it remains JNI for now. 

[Github IonSpin](https://github.com/ionspin/kotlin-multiplatform-bignum) library is used for BigDecimal and BigInteger support is used for the unlimited precision stuff. If/when Kotlin Multi-platform releases BigDecimal support, this will be revisted.

Klock library is used for multi-platform Date and DateTime support. Once kotlinx.datetime releases support for formatting/parsing of at least ISO datetime formats, this will be revisited.  