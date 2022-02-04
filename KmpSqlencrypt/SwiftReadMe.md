## Swift usage of KmpSqlencrypt

This library was designed for use with Kotlin. It uses coroutine support throughout with many suspend functions and lamdbas defined with suspend. That being said, as a learning experience I have tried using it directly with Swift. The notes below discuss the behaviors/tradeoffs that happened, in case the info is useful to anyone else :-).

In existing apps this library is used by kotlin code and only Kotlin view models are referenced directly by Swift/SwiftUI. So these notes are not intended to be instructions for how to use the library from swift as there are much better Swift-tailored ways to access a SqlCipher instance.  The intent of the notes is to describe tradeoffs that happen with any Swift access of a non-trivial Kotlin library using just the Kotlin multiplatform Swift bindings, using this library as the example.

Context is Kotlin 1.6.10 and Swift 5.5.

The sample IOS app described below can be found in the library repo under the `iosApp` subdirectory. It was tested using Xcode 13.2.1 and the IOS Simulator's IPhone 13 Pro device using IOS 15.2.

**Note** The sample app has a current showstopper. Kotlin maps suspend functions to the Swift async/await support.  But it has a restriction that Kotlin suspend functions can only be called from the main thread (DispatchQueue.main in Swift). For some reason Swift or the Kotlin support are using background threads after a suspend lambda is used.  So the next attempt to use async/await to call another Kotlin suspend function fails with an Objective C exception. Until I learn enough about Swift to force these to all be executeded using DispatchQueue.main, this exploration remains incomplete :-)  

### Conclusions

Many of the lessons I learned while doing this though exercise will likely be obvious to anyone looking at the code below as well. Here are some notes to scare away anyone from perusing the details :-)

- Kotlin and Swift have a lot of similarities, both are really nice in my opinion.
- The Kotlin team has done a truly impressive job, in my opinion, at mapping between the two languages. There's obviously room for improvement, but what's here is alreay good.
- There are lots of scenarios where Kotlin wrappers should be used to make Swift usage easier and more concise. Wrappers can play to the strenghts of the mapping and avoid weaknesses.
  - Don't expose suspend functions to Swift if practical.  Do all coroutine scoping, launching, and cancelling in Ktolin code.
  - This is especially true of suspend lambdas - the mapping requires implementation of a generated protocol that is functional but messy.
  - Any suspend functions that must be used directly in Swift have the current restriction that they must be called on the main thread.
- The cocoapods plugin has at least one basic assumption - there is no help for publishing a cocoapod.  The assumed use of the cocoapod from a local project is something to be aware of - if you intend to publish the cocoapod, you have some work to do on top of what the plugin does.
- There are mapping issues with collections and also sealed classes using generics. Kotlin Any covers all objects, where Swift has Any for basic value types like String, Int32, Double etc, and AnyObject for reference types like classes and actors.  The mappings don't have work-arounds for all the combinations that can occur, making certain type-safe array operations impossible.

I may add to the above list as I learn :-)  

On to the details below. Contents include
- Cocoapods setup in build.gradle.kts
- pod install requirements and steps
- Code samples for using the library directly from Swift
- Kotlin suspend related issues with the Swift mappings
- Tradeoffs/problems with Kotlin generics using Any vs Swift Any and AnyObject 

### Cocoapods Usage

The library has a basic cocoapods setup using the Kotlin native.cocoapods plugin. The relevant `build.gradle.kts` entries look like this:

```
plugins {
    id("com.android.library")
    kotlin("multiplatform")
    kotlin("native.cocoapods")
    ...
}

...
    
val mavenArtifactId = "kmp-sqlencrypt"
val appleFrameworkName = "KmpSqlencrypt"

...

kotlin {
    ...
    val githubUri = "skolson/$appleFrameworkName"
    val githubUrl = "https://github.com/$githubUri"
    cocoapods {
        ios.deploymentTarget = iosMinSdk
        summary = "Kotlin Multiplatform API for SqlCipher/OpenSSL"
        homepage = githubUrl
        license = "Apache 2.0"
        framework {
            baseName = appleFrameworkName
            isStatic = true
            embedBitcode(org.jetbrains.kotlin.gradle.plugin.mpp.BitcodeEmbeddingMode.BITCODE)
        }
        // Maps custom Xcode configuration to NativeBuildType
        xcodeConfigurationToNativeBuildType["CUSTOM_DEBUG"] = NativeBuildType.DEBUG
        xcodeConfigurationToNativeBuildType["CUSTOM_RELEASE"] = NativeBuildType.RELEASE
    }
    ...
}
```
In the above code, the Framework name is the same as the gradle project name. Getting the cocoapods plugin to set a different framework name and still get a correct podspec file generated was problematic, so I avoided that by making them match.  This required changing the maven publish setup in a couple spots to get the maven artifactId to look like maven (all lower case, etc). There has been no attempt to publish this podspec, the current setup is intended for a local copy of the repo being built locally, as will be seen more below. The current cocoapods plugin hard-codes the `spec.source` for a local install (see below) so that has to be overridden with additional gradle code to set up for a github spec repo.

The plugin generates a podspec file in the root of the project named `"${project.name}.podspec` any time the project is synced. The podspec looks like this:

```
Pod::Spec.new do |spec|
    spec.name                     = 'KmpSqlencrypt'
    spec.version                  = '0.4.3'
    spec.homepage                 = 'https://github.com/skolson/KmpSqlencrypt'
    spec.source                   = { :git => "Not Published", :tag => "Cocoapods/#{spec.name}/#{spec.version}" }
    spec.authors                  = 'Steven Olson'
    spec.license                  = 'Apache 2.0'
    spec.summary                  = 'Kotlin Multiplatform API for SqlCipher/OpenSSL'

    spec.vendored_frameworks      = "build/cocoapods/framework/KmpSqlencrypt.framework"
    spec.libraries                = "c++"
    spec.module_name              = "#{spec.name}_umbrella"

    spec.ios.deployment_target = '14'

                

    spec.pod_target_xcconfig = {
        'KOTLIN_PROJECT_PATH' => ':KmpSqlencrypt',
        'PRODUCT_MODULE_NAME' => 'KmpSqlencrypt',
    }

    spec.script_phases = [
        {
            :name => 'Build KmpSqlencrypt',
            :execution_position => :before_compile,
            :shell_path => '/bin/sh',
            :script => <<-SCRIPT
                if [ "YES" = "$COCOAPODS_SKIP_KOTLIN_BUILD" ]; then
                  echo "Skipping Gradle build task invocation due to COCOAPODS_SKIP_KOTLIN_BUILD environment variable set to \"YES\""
                  exit 0
                fi
                set -ev
                REPO_ROOT="$PODS_TARGET_SRCROOT"
                "$REPO_ROOT/../gradlew" -p "$REPO_ROOT" $KOTLIN_PROJECT_PATH:syncFramework \
                    -Pkotlin.native.cocoapods.platform=$PLATFORM_NAME \
                    -Pkotlin.native.cocoapods.archs="$ARCHS" \
                    -Pkotlin.native.cocoapods.configuration=$CONFIGURATION
            SCRIPT
        }
    ]
end
```

Note the hardcoded `spec.source`.  The podfile sample below shows how an IOS Xcode project can use the pod.  Remember manually editing this file is counter-productive as it is regenerated by the plugin. 

One note that I found not well documented. The plugin does a vendored framework in this situation as the static framework is Kotlin (using Kotlin native which in turn uses cinterop to access the static sqlcipher and openssl libraries). So there is no Swift source to access. The plugin handles this by building a dummy framework in the library's build/cocoapods/framework folder.  This folder will be changed later by the first build of the Xcode project using this library, which was a new and strange concept for me. Immediately after a gradle build, the contents of this directory has a dummy header and info.plist and other related files.  The actual framework is not built in the library's build folder until **after** the first full Xcode project build that is using this framework via the pod stuff. So after pod install, be sure to do an Xcode build first before trying to use the framework in Swift code.

Once the KmpSqlencrypt repo is local on the machine, it should import and build fine using Intellij Idea on Macos Big Sur. Then using a current Xcode that supports Swift 5.5.x or later, you can create an Xcode project.  The step outline is:

- Create an Xcode project using the Swift App or whatever you like.  In this example the project location is `~/Projects/IosKmpTest`
- Create a `Podfile` that looks like the one below
- Close the project in Xcode
- Using terminal in the project root directory, run `pod install`. This of course assumes you have a current cocoapods installed. Make sure pod install has no errors.
- Open the project in Xcode using the .xcworkspace file. Proper operation of cocoapods requires this
- Run a project build in Xcode to get the framework/pod built
- Use the framework in Swift code

All of the above steps should be familiar to anyone familiar with cocoapods. Below is a sample Podfile for a project named IosKmpTest at location ~/Projects/IosKmpTest that will be running the IOS Simulator in a small SiftUI app. Note that the IOS builds of the KmpSqlencryp library currently require IOS 14 or later, so the corresponding platform entry is here as well:

```
platform :ios, '14.0'

target 'IosKmpTest' do
  use_frameworks!

  pod 'KmpSqlencrypt', :path => '~/Projects/SqlCipherKotlinMP/KmpSqlencrypt'

  target 'IosKmpTestTests' do
    inherit! :search_paths
    # Pods for testing
  end

  target 'IosKmpTestUITests' do
    # Pods for testing
  end

end
```

With the above Podfile, the `pod install` command should show something similar to this output in the Terminal session, with no errors:

```
Analyzing dependencies
Downloading dependencies
Installing KmpSqlencrypt 0.4.3 (was 0.4.2)
Generating Pods project
Integrating client project
Pod installation complete! There is 1 dependency from the Podfile and 1 total pod installed.
```

At this point tne project .xcworkspace file can be opened in Xcode and the project built.  Once built, the library should be usable and the fun begins. Remember as part of this build, the real framework defintions used by Xcode are built in the associated KmpSqlencrypt project. Using Finder you can look in  `~/<repo path>>/KmpSqlencryt/build/cocoapods/framework/KmpSqlencrypt.framework` subdirectory to see the header file and associated Xcode files.  If the header file is still an empty stub, then the Xcode build of your project using this pod did not succeed and the framework will not be usable. 

This test project uses SwiftUI with a really simple main screen. None of the SwiftUI code is shown as it is out-of-scope for these notes. It's all play stuff anyway as I'm just starting to learn SwiftUI.  The main screen has an async button that invokes the test code (below) using the framework. I chose to implement this in a view model class. The test code below opens an in-memory database with no password (so no encrypted content - database is in memory :-). It then creates a small table with one of each of the main column types, does an insert, then does a select count(*) on the table followed by a full select of the inserted row. The code is shown below. There is a lot to parse, so see the notes after the code!

```
import KmpSqlencrypt

class PretendController {
    private(set) var isOpen = ""
    private(set) var userVersion: Int32 = 0
    private(set) var sqliteVersion = ""
    private(set) var sqlcipherVersion = ""
    lazy var db = DatabaseKt.sqlcipher {sql in
        sql.createOk = true
        sql.encoding = SqliteEncoding.utf8
    }
    private(set) var test1Count: Int32 = -1
    private(set) var insertRowid: Int64 = -1
    private(set) var insertArgs1 = SqlValues()
    private(set) var insertArgs2 = SqlValues()
    private(set) var count: Int32 = -1
    private(set) var str: String = ""
    private(set) var isMain: Bool = false

    func logon() async {
        isMain = Thread.isMainThread            // true here
        SqlValueBooleanValue.Companion().mapping(forFalse: "N", forTrue_: "Y")
        do {
            try await openAndQuery()
            isMain = Thread.isMainThread        // false !!
            try await inserts()
            isMain = Thread.isMainThread
        } catch {
            
        }
        db.close()
    }
    
    func openAndQuery() async throws {
        let pwd = Passphrase.init(passphrase: "", isRaw: false, hasSalt: false)
        try await db.open(passphrase: pwd)
        isOpen = db.isOpen.description
        userVersion = db.userVersion
        sqliteVersion = db.sqliteVersion
        sqlcipherVersion = db.sqlcipherVersion
        try await db.execute(sqlScript: "create table test1(id INTEGER PRIMARY KEY, name VARCHAR(255), date1 DATE, dateTime1 DATETIME, num1 DECIMAL(25,3), real1 REAL, dub DOUBLE, long1 BIGINT, bool1 char(1));") { row in
            KotlinBoolean(bool: true)
        }
        isMain = Thread.isMainThread        // still true
        let binds = SqlValues()
        try await db.usingSelect(selectSql: "select count(*) from test1",
                       bindArguments: binds,
                       eachRow: QueryTest1(self)
        )
    }
    
    func inserts() async throws {
        let dt = SqlValueDateValue.Companion().parse(dateString: "2022-01-31", throws: false)!
        let dtTime = SqlValueDateTimeValue.Companion().parse(dateTimeString: "2022-01-31T06:00:00.000", throws: false)!
        let bigD = BignumBigDecimal.Companion().parseString(string: "12345678901234567890.23456")
        self.insertArgs1
            .addValue(name: "name", value: "Any text here")
            .addValue(name: "date1", value: dt)
            .addValue(name: "dateTime1", value: dtTime)
            .addValue(name: "num1", value: bigD)
            .addValue(name: "dub", value: KotlinDouble(value: 123.456))
            .addValue(name: "real1", value: KotlinFloat(value: 789.123))
            .addValue(name: "long1", value: KotlinLong(value: 99988))
            .addValue(name: "bool1", value: KotlinBoolean(bool: true))
        
        self.count = self.insertArgs1.size
        self.str = self.insertArgs1.description()
        self.isMain = Thread.isMainThread
        insertRowid = try await db.useInsert(
            insertSql: "insert into test1(name, date1, dateTime1, num1, real1, dub, long1, bool1) values(:name, :date1, :dateTime1, :num1, :real1, :dub, :long1, :bool1)",
                bindArguments: insertArgs1).int64Value
        
        let vals = [
            "Any row 2 text here",
            dt,
            dtTime,
            bigD,
            KotlinDouble(value: 999.456),
            KotlinFloat(value: 777.123),
            KotlinLong(value: 11111111),
            KotlinBoolean(bool: true)
        ]
        self.insertArgs2 = SqlValues(values: vals)
        self.str = self.insertArgs2.description()
        self.isMain = Thread.isMainThread
        insertRowid = try await db.useInsert(
            insertSql: "insert into test1(name, date1, dateTime1, num1, real1, dub, long1, bool1) values(?, ?, ?, ?, ?, ?, ?, ?)",
                bindArguments: insertArgs2).int64Value
    }
    
    class QueryTest1: KotlinSuspendFunction2 {
        
        var vm: PretendController
        
        init(_ vm: PretendController)  {
            self.vm = vm
        }
        
        func invoke(p1: Any?, p2: Any?) async throws -> Any? {
            if p1 is Int32 && p2 is SqlValues {
                //let rowNumber = p1 as! Int32
                let row = p2 as! SqlValues
                vm.test1Count = row.requireInt(columnIndex: 0, nullZero: false)
            }
            return KotlinBoolean(bool: true)
        }
    }
}
```

The import gives access to all the stuff in the Kotlin mappings. I chose a mutable class just to have some vars that could be set from framework usage so they are easy to see in the debugger. Start by looking at the Swift code for configuring a SqlcipherDatabase instance:

```
    lazy var db = DatabaseKt.sqlcipher {sql in
        sql.createOk = true
        sql.encoding = SqliteEncoding.utf8
    }
```

This is about the simplest call to the Kotlin sqlcipher DSL function there is. Details on the many options that can be set in this builder are in the [ReadMe](./ReadMe.md). Most of the options map fairly nicely to Swift.  Note the `DatabaseKt.sqlcipher` usage - top-level functions in Kotlin are prepended with the source file name by the Kotlin-Swift mappings. I chose lazy just for fun, nothing requires that. I was learning Swift while doing this code :-) It sets the instance as a new database, specifies no path so an in-memory database is will be created. even the encoding setting is redundant here as the default encoding for a new database is UTF-8. But this encoding line shows that Kotlin enums map nicely to Swift.

The `db` instance is now openable, can't do anything with it until it is open. So let's examine the top-level asyn function:

```
    func logon() async {
       SqlValueBooleanValue.Companion().mapping(forFalse: "N", forTrue_: "Y")
       isMain = Thread.isMainThread            // true here
       do {
            try await openAndQuery()
            isMain = Thread.isMainThread        // false !! Don't know why yet, don't know how to stop the switch
            try await inserts()
            isMain = Thread.isMainThread
            db.close()
        } catch {
            // any catches of SqliteException, etc
        }
    }
```

The first line is how Swift can access a Kotlin companion object. This first line has a number of mapping considerations.
- SqlValue is a Kotlin sealed class with inner subclasses. BooleanValue is an inner class of this sealed class.  The Kotlin mapping exposes this innerclass by concatenating it with the sealed super-class.
- The kotlin `companion object` of `class BooleanValue` is mapped like an instance to Swift, thus the `Companion().` syntax. Finally in this case the `mapping` function has two arguments.
- The argument mapping in my experience **does not** support Kotlin default argument syntax. All functions with default argument values in Kotlin look like required arguments without defaults in Swift
- If the Kotlin-Swift mappings detect one or more overloaded functions, then it will use a trailing underbar on one or more of the argument names to make them unambiguous in Swift, like it did with `forTrue_:` argument name above.

This line tells the BooleanValue class that boolean will be mapped to/from the Sqlite column as text N and Y values, since Sqlite has no Boolean type column.

The next line is checking that the main thread is the current thread. If it isn't then the Kotlin suspend support fails.  So this is required and is currently problematic given my beginning knowledge of Swift.  Because for some reason I don't yet understand, after returning from the call to the `opendAndQuery()` function, a different thread is now in use. Something caused a thread context switch and I don't know how to stop that. Which is why the main thread probes are in the code.

The do scope is set up because most of the Kotlin API functions can throw exceptions, mostly `SqliteException`. Each one that can uses the `try` keyword.

If not for that thread-context-change problem, I think the rest of the sample code would work.

So assuming away the threading issue, lets look at the two (so far) sample functions.
- openAndQuery opens the database, creates a table, and runs a simple query.  
- inserts inserts two rows into the new table. One uses named bind variables, and the other uses positional bind variables.

### openAndQuery()
```
    func openAndQuery() async throws {
        let pwd = Passphrase.init(passphrase: "", isRaw: false, hasSalt: false)
        try await db.open(passphrase: pwd)
        isOpen = db.isOpen.description
        userVersion = db.userVersion
        sqliteVersion = db.sqliteVersion
        sqlcipherVersion = db.sqlcipherVersion
        try await db.execute(sqlScript: "create table test1(id INTEGER PRIMARY KEY, name VARCHAR(255), date1 DATE, dateTime1 DATETIME, num1 DECIMAL(25,3), real1 REAL, dub DOUBLE, long1 BIGINT, bool1 char(1));") { row in
            KotlinBoolean(bool: true)
        }
        isMain = Thread.isMainThread        // still true
        let binds = SqlValues()
        try await db.usingSelect(selectSql: "select count(*) from test1",
                       bindArguments: binds,
                       eachRow: QueryTest1(self)
        )
    }
```
The first line above makes an empty Passphrase. In Kotlin all of this is default arguments, but since the mapping doesn't support default arguments in Swift, an explicit one is built here with all explicit arguments for use by the open function.

The open function is the first one that uses Kotlin coroutines.  The Swift async/await syntax mapped to Kotlin coroutines is experimental so be aware. **Repeated Note** - Kotlin coroutine support ONLY works when Swift dispatches from the main thread. If an async call from Swift to a Kotlin suspend function is made on any other thread, the support throws an Objective C exception `NSGenericException` that can't be caught with the normal Swift do/catch support. So for example if a suspend lambda tries to use another async function, an exception is thrown.  Hopefully at some point this support will be more robust, but not with Kotlin 1.6.10. 

At this point if no exception is thrown, the instance is open an usable. See the [ReadMe](./ReadMe.md) for a list of all the stuff that happens at open time to be sure the instance is usable.

The next few lines show simple access to some of the Kotlin properties of the instance from Swift, and do the create table operation:

```
            isOpen = db.isOpen.description
            userVersion = db.userVersion
            sqliteVersion = db.sqliteVersion
            sqlcipherVersion = db.sqlcipherVersion
            try await db.execute(sqlScript: "create table test1(id INTEGER PRIMARY KEY, name VARCHAR(255), date1 DATE, dateTime1 DATETIME, num1 DECIMAL(25,3), real1 REAL, dub DOUBLE, long1 BIGINT, bool1 char(1));") { row in
                KotlinBoolean(bool: True)
            }
```
The `isOpen` line accesses the Kotlin Boolean property `isOpen` and does the Swift equivalent of a `toString()` to get a string representation of the Kotlin Boolean which in this case should be "true".

The `db.userVersion` property is a KotlinInt which maps to a Swift Int32.

The `sqliteVersion` and 'sqlcipherVersion' properties both map to NSString and show the appropriate version strings.

The async db.execute call is an example of running a script, and examining the result in the lambda that is invoked. This particular lambda does not use suspend, so it maps pretty well.  The Kotlin definition of execut is `fun execute(sql:String, (row: SqlValues) -> Boolean`, so you can see that mapped to Swift nicely. The lambda requires a Boolean result, which here is the Swift syntax for a Kotlin `true`.

In the case of the `create table`, there is nothing interesting in the row argument of the lambda so it isn't used. See the select example below for more on this in Swift.

The last part of the function does a simple query.
```
        let binds = SqlValues()
        try await db.usingSelect(selectSql: "select count(*) from test1",
                       bindArguments: binds,
                       eachRow: QueryTest1(self)
        )
```

Note the way suspend lambdas are created.  The mapping requires an implementation of a Swift protocol with a generated name that starts with `KotlinSuspendFunction` followed by a number, in this case a 2. The implementation I did looks like this:
```
    class QueryTest1: KotlinSuspendFunction2 {
        
        var vm: PretendController
        
        init(_ vm: PretendController)  {
            self.vm = vm
        }
        
        func invoke(p1: Any?, p2: Any?) async throws -> Any? {
            if p1 is Int32 && p2 is SqlValues {
                //let rowNumber = p1 as! Int32
                let row = p2 as! SqlValues
                vm.test1Count = row.requireInt(columnIndex: 0, nullZero: false)
            }
            return KotlinBoolean(bool: true)
        }
    }
```

The invoke function gets called by the Kotlin mapping.  The mappings provide no type safety fo the arguments or the return value, you have to DIY it. In this case, the lambda in Kotlin had this definition: `row: suspend (rowCount: Int, values: SqlValues) -> Boolean`. In this case the query returns one row with one value, so you can see the Swift code to get the result.

### inserts()

This function does two inserts. One uses named bind variables, and one uses positional bind variables. This gets messy cuz in Kotlin Date and DateTime instances come from the Klock multiplatform library, and BigDecimal instances come from the Ionspin BigNumber multiplatform library. Again, see the [ReadMe](./ReadMe.md) for details. The library uses a SqlValues instance as a collection of bind arguments for the insert.  This ran into a number of issues with the mappings.
- `SqlValues` is a collection of `SqlValue<Any>` instances. SqlValue is a sealed abstract class so instances are of inner subclasses of SqlValue.  For example a String instance is from `class StringValue: SqlValue<String>`. There is a similar inner class for each of the basic types; String, Int, Long, Float, Double, BigDecimal, Date, DateTime, ByteArray (BLOB), Boolean.
- a Kotlin list or array like `List<SqlValue<out Any>>` maps to a Swift `KotlinArray<SqlValue<AnyObject>>`. This is a problem as in Swift `AnyObject` means a reference type like a class.  String doesn't fit the mapping, so instances of the sealed class subclasses like StringValue can be made, but can't be added to the collection because NSString is not an AnyObject (it's an Any). So a syntax error occurs. I had to work around this by using Kotlin code that hides the subclass usage when building bind argument lists, as that's theonly use case I know of for SqlValues that is impacted by this mapping issue.
- The SqlValues class does have sme convenience methods that hide the issue above, and instantiates the correct SqlValue subclass instances. So those are used here to circumvent the above mapping issue with generic types.
- The debugger didn't show the contents of a Kotlin collection for me. I'm unsure if it should, or if I have something wrong with my build. I used code probes to see if the collection actually had the stuff I thought I put in it, and it seems correct. But it would've obviously been nice if the debugger could show the content.

The insert using named bind arguments looks like this:
```
        let dt = SqlValueDateValue.Companion().parse(dateString: "2022-01-31", throws: false)!
        let dtTime = SqlValueDateTimeValue.Companion().parse(dateTimeString: "2022-01-31T06:00:00.000", throws: false)!
        let bigD = BignumBigDecimal.Companion().parseString(string: "12345678901234567890.23456")
        self.insertArgs1
            .addValue(name: "name", value: "Any text here")
            .addValue(name: "date1", value: dt)
            .addValue(name: "dateTime1", value: dtTime)
            .addValue(name: "num1", value: bigD)
            .addValue(name: "dub", value: KotlinDouble(value: 123.456))
            .addValue(name: "real1", value: KotlinFloat(value: 789.123))
            .addValue(name: "long1", value: KotlinLong(value: 99988))
            .addValue(name: "bool1", value: KotlinBoolean(bool: true))
        
        insertRowid = try await db.useInsert(
            insertSql: "insert into test1(name, date1, dateTime1, num1, real1, dub, long1, bool1) values(:name, :date1, :dateTime1, :num1, :real1, :dub, :long1, :bool1)",
                bindArguments: insertArgs1).int64Value
```

Companion object functions are used to create a Kotlin Date from the Klock library, a DateTime, and a GigDecimal from the Ionspin Bignum library.

The rest of the code builds the SqlValues object containing all the named bind args. `insertRowid` gets the ROWID Sqlite generates as a KotlinLong, which is then converted to a Swift Int64.

The insert using positional bind arguments is similar, and a little cleaner since no direct usage of the sealed class types is required in Swift for this.