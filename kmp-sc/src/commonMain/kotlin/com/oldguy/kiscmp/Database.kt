package com.oldguy.kiscmp

import com.oldguy.database.*

class SqliteColumn(name: String, index: Int, type: ColumnType = ColumnType.String, isNullable: Boolean = false):
    Column(name, index, type, isNullable)
{
    data class ColumnMetadata(val type: ColumnType, val precision:Int = 0, val scale:Int = 0)

    companion object {
        val integerTypes = listOf("INT", "INTEGER", "TINYINT", "SMALLINT", "MEDIUMINT", "BIGINT",
            "UNSIGNED BIG INT", "INT2", "INT8")
        private val decimalTypes = listOf("REAL", "DOUBLE", "DOUBLE PRECISION", "FLOAT", "NUMERIC", "DECIMAL")
        val dateTypes = listOf("DATE", "DATETIME", "TIMESTAMP")
        private val stringTypes = listOf("CHAR", "CHARACTER", "VARCHAR", "VARYING CHARACTER", "NCHAR",
            "NATIVE CHARACTER", "NVARCHAR", "TEXT")
        private const val booleanType = "BOOLEAN"
        private const val blobType = "BLOB"
        const val clobType = "CLOB"

        fun parseColumnDeclaration(type:String, useBigDecimal: Boolean): ColumnMetadata {
            val specifier = if (type.contains("(") && type.contains(")"))
                type.substringAfter('(').substringBefore(')')
            else
                ""
            var colType: String = type.uppercase()
            var precision = 0
            var scale = 0  // only non-zero on DECIMAL
            if (specifier.isNotEmpty()) {
                colType = colType.substringBefore('(')
                val tokens = specifier.split(",")
                if (tokens.size > 2)
                    throw SqliteException("Type declaration: $type has precision with more than two values: $tokens")
                else {
                    if (tokens.isNotEmpty())
                        precision = tokens[0].toInt()
                    if (tokens.size == 2)
                        scale = tokens[1].toInt()
                }
            }
            if (integerTypes.contains(colType) && scale == 0) {
                val t = when (precision) {
                    0 -> ColumnType.Long
                    in 1..4 -> ColumnType.Short
                    in 5..9 -> ColumnType.Int
                    in 10..18 -> ColumnType.Long
                    else -> ColumnType.BigInteger
                }
                return ColumnMetadata(t, precision, scale)
            }
            if (decimalTypes.contains(colType) || scale > 0) {
                if (useBigDecimal)
                    return ColumnMetadata(ColumnType.Decimal, precision, scale)
                val t = when (precision) {
                    in 1..6 -> ColumnType.Float
                    in 7..16 -> ColumnType.Double
                    else -> ColumnType.Decimal
                }
                return ColumnMetadata(t, precision, scale)
            }
            if (stringTypes.contains(colType))
                return ColumnMetadata(ColumnType.String, precision)
            if (dateTypes.contains(colType)) {
                return if (dateTypes[0] == colType)
                    ColumnMetadata(ColumnType.Date)
                else
                    ColumnMetadata(ColumnType.DateTime)
            }
            return when (colType) {
                booleanType -> ColumnMetadata(ColumnType.Boolean)
                blobType -> ColumnMetadata(ColumnType.Blob)
                clobType -> ColumnMetadata(ColumnType.Clob)
                else -> throw IllegalStateException("Unrecognized type: $type")
            }
        }
    }
}

class SqliteSystemCatalog(val db: SqlCipherDatabase): SystemCatalog() {
    override val catalogTableName = SqlCipherDatabase.catalogTable
    override val schemasSupported = false

    private val queryPrefix = "select * from $catalogTableName WHERE type ="
    private val queryTablesSql = "$queryPrefix 'table' AND name NOT LIKE 'sqlite_%';"
    private val queryIndexesSql = "$queryPrefix 'index';"

    override suspend fun retrieveTables() {
        db.usingSelect(queryTablesSql) { _: Int, row: SqlValues ->
            if (row.size < columnNames.size)
                throw SqliteException("$catalogTableName is missing expected columns: $row")
            add(Table(row.requireString(columnNames[1])))
                .addProperty(row[3].name, row.requireLong(columnNames[3]).toString())
                .addProperty(row[4].name, row.requireString(columnNames[4]))
            true
        }
        tables.values.forEach {
            describeTable(it)
        }
        db.usingSelect(queryIndexesSql) {_: Int, row: SqlValues ->
            tables[row.requireString(2)]?.let {
                it.add(Index(row.requireString(1), it, row.requireString(4)))
            }
            true
        }
    }

    private fun describeTable(table: Table) {
        table.columns.clear()
        val sql = describeTableSql(table.name)
        db.execute(sql) {
            table.columns.add(SqliteColumn(
                it.requireString(1),
                it.requireInt(0),
                SqliteColumn.parseColumnDeclaration(it.requireString(2), false).type,
                it.requireInt(3) == 0).apply {
                isPrimaryKey = it.requireInt(5) > 0
                declaration = it.requireString(2)
                })
            true
        }
    }

    companion object {
        val columnNames = listOf("type", "name", "tbl_name", "rootpage", "sql")

        fun describeTableSql(tableName: String): String {
            return "pragma table_info('$tableName');"
        }
    }
}

/**
 * DSL setup. Any of the values and variables in [SqlCipherDatabase] are available for use. The
 * [SqlCipherDatabase] returned from this should be ready for opening. Typical usage:
 *  1) schema version upgrades
 *  2) invalid passphrase handling
 *  3) open process flags; readOnly, createOk, integrityCheck, etc.
 *  4) add []DatabaseObserver] instances to be notified of create/open/close events
 *  5) any other desired setup before invoking open
 *
 * See [SqlCipherDatabase] doc for all the available properties/variables
 *
 * @param init lambda is responsible for configuring all options to prepare for the open function.
 */
fun sqlcipher(init: SqlCipherDatabase.() -> Unit): SqlCipherDatabase {
    val db = SqlCipherDatabase()
    db.init()
    return db
}

/**
 * Use this to open and operate on a SqlCipher or Sqlite database.  Opening a database with a
 * [Passphrase] makes use of the Sqlcipher encryption functionality in addition to all Sqlite
 * functionality. Using an empty [Passphrase] is a typical Sqlite database.
 *
 * See the above [sqlcipher] function for doc on how to use the DSL builder to set the various
 * parameters and callbacks desired. See each property below for do on how each can be used for
 * configuration and for state queries.
 *
 */
class SqlCipherDatabase:
    Database() {
    internal val sqliteDb = SqliteDatabase()
    override var isOpen = false
    override val fileName: String
        get() = sqliteDb.fileName()

    /**
     * get the last Sqlite error message
     */
    val errorMessage: String get() = sqliteDb.error()

    /**
     * The Sqlite version compiled with SqlCipher
     */
    val sqliteVersion get() = sqliteDb.version()

    /**
     * The SqlCipher version
     */
    val sqlcipherVersion:String get() {
        var version = ""
        pragma(pragmaVersion) {
            if (it.size == 1)
                version = it.requireString(0)
            false
        }
        return version
    }

    /**
     * Set this to request the open process to check the current user version against this value.
     * If current userVersion is >= to this value, nothing happens.  If current userVersion is <
     * this value, invoke the userVersionUpgrade function if set.
     */
    var newUserVersion: Int = -1

    /**
     * Get and/or set the user version, an integer >= 1, which is typically used to trigger schema change scripts.
     * The value always contains the current user version AFTER open is executed.  This property
     * is the most useful within the body of the onOpen lambda in the open function. Typical sequence
     * of use is:
     *    1) At sqlcipher (or constructor) time, set newUserVersion to the desired version if a change
     *    is desired.  If no user version change desired, leave newUserVersion default to zero
     *    2) Open function will retrieve [userVersion], compare it to [newUserVersion]
     *    3) If [newUserVersion] is > current [userVersion], invoke userVersionUpgrade function.
     * Typically the setter here is only used by internal code during the open process. But it is
     * public to allow non-typical use cases to have direct control over the userVersion.
     *
     * Values   -1 indicates never queried, should only see this in debugger
     *          0 never been set
     *          >=1 set by this version mechanism
     */
    var userVersion: Int = -1
        get() {
            var version = 0
            pragma(pragmaUserVersion) {
                if (it.size == 1)
                    version = it.requireInt(0)
                false
            }
            return version
        }
        set(value) {
            if (value < 1)
                throw IllegalArgumentException("userVersion value must be >= 1")
            pragma("$pragmaUserVersion = $value") {
                if (it.size == 1)
                    field = it.requireInt(0)
                false
            }
        }
    private var softHeapLimit: Long = 0
        get() = sqliteDb.softHeapLimit(-1)
        set(value) {
            sqliteDb.softHeapLimit(value)
            field = value
        }

    /**
     * Set this to a function to be invoked at open time only if the [newUserVersion] value is
     * greater than the current user version found at open.  This function is typically responsible
     * for running SQL script(s) to make changes to the current schema; new tables/views/indexes,
     * column changes, table copies, etc.
     * If newUserVersion indicates this should be called, but readOnly is set also, a [SqliteException]
     * is thrown.
     * Lambda parameters
     *  <b>db</b> the database this is invoked for, in open and writable state
     *  <b>currentVersion</b> the integer value of the userVersion in the database just opened.
     *  <b>newVersion</b>  the integer value of the newUserVersion property.
     *  <b>true</b> if open process should change the version to newVersion, false if not
     */
    var userVersionUpgrade: ((db: SqlCipherDatabase, currentVersion: Int, newVersion: Int) -> Boolean)? = null

    override val catalog = SqliteSystemCatalog(this)

    /**
     * Set this to true to cause the open process to perform an integrity check during the open
     * process.  This will happen immediately after returning from the onOpen lamdba (if specified)
     * and verifying the database is openable.
     *
     */
    var integrityCheck:Boolean = false

    /**
     * Set to true if the database to be opened is not in-memory (explicit path), and it is OK to
     * create a new one.
     */
    var createOk: Boolean = false
    /**
     * Set to true if the database to be opened should throw an exception if any DML that changes
     * the database is attempted.
     */
    var readOnly: Boolean = false

    override suspend fun use(path:String,
                     passphrase: Passphrase,
                     invalidPassphrase: (suspend (db: SqlCipherDatabase, passphrase: Passphrase) -> Unit)?,
                     block: suspend (db: Database) -> Unit) {
        try {
            open(path, passphrase, invalidPassphrase = invalidPassphrase)
            if (isOpen) {
                block(this)
            }
            close()
        } catch (error: Throwable) {
            throw error
        }
    }

    /**
     * Opens or creates a Sqlite/SqlCipher database.
     * Open process steps:
     * 1) Use the specified arguments to perform an initial open operation on the specified path.
     * This basically only verifies the path is accessible consistent with the requested configuration.
     * If any error occurs, and excetpion is thrown.
     * 2) If a non-empty passphrase is specified, set the key
     * 3) invoke the onOpen lambda if specified.  Use this lambda to perform any pre-open pragmas
     * for querying or changing database configuration. Any SqlCipher or Sqlite supported pragma can
     * be used.  See the pragma function, or the already defined pragma helper functions available
     * below, that by convention all have names starting with "pragma".
     * 4) Attempt a simple select against sqlite_master.  If this fails and a passphrase is in use,
     * the invalidPassphrase function is invoked, if set and open is stopped and can be reattempted
     * with a different passphrase.  Otherwise an exception is thrown. If all of this
     * succeeds, the database is available for use.
     * 5) perform an integrity check if requested.
     * 6) check if newUserVersion is non-zero. if it is, query the current user version and compare.
     * If newUserVersion is == userVersion, proceed.  If they are different, invoke the userVersionUpgrade
     * function, if set, to allow any required DML to be excuted.
     * 7) Open complete

     * @param path absolute path to database file, or empty string if temporary/new in-memory database is
     * desired.
     * @param passphrase optional, if specified used as key for Sqlcipher database. If empty, database
     * is essentially a Sqlite database with no Sqlcipher functionality.
     * @param onOpen lambda is invoked after open is successful, after passphrase use, but before
     * first actual read access to database. For Sqlite/SqlCipher, this is ideal spot for pragmas
     * that must run before database is fully open.
     * @param invalidPassphrase Will be invoked if a non empty passphrase is in use, and database is
     * not readable. It is possible (but unlikely) that a database is just corrupted badly enough
     * that it is unopenable. When a passphrase is in use, a corrupted database and one that can't
     * be decrypted using that passphrase are indistinguishable.
     */
    override suspend fun open(path: String,
                      passphrase: Passphrase,
                      onOpen: (suspend () -> Unit)?,
                      invalidPassphrase: (suspend (db: SqlCipherDatabase, passphrase: Passphrase) -> Unit)?
    ) {
        val workPath = path.ifEmpty { inMemoryPath }
        val rc = sqliteDb.open(workPath, readOnly, createOk)
        if (rc != 0) {
            throw SqliteException(errorMessage, "open_v2", rc)
        }
        transactionDepth = 0
        val tableCount: Int
        try {
            setup(passphrase)
            if (onOpen != null)
                onOpen()
            try {
                tableCount = tableCount()
            } catch (e: SqliteException) {
                if (passphrase.passphrase.isNotEmpty())
                    invalidPassphrase?.let {
                        it(this, passphrase)
                        return@open
                    }
                throw e
            }
            if (tableCount == 0 && !createOk)
                throw SqliteException("createOk false and database is empty", "open", -1)
            isOpen = true
            integrityCheck()
            if (newUserVersion >= 0) {
                val version = userVersion
                if (version != newUserVersion) {
                    userVersionUpgrade?.let {
                        if (it(this, version, newUserVersion))
                            userVersion = newUserVersion
                    }
                }
            }
        } catch (e:SqliteException) {
            val closeResult = sqliteDb.close()
            if (closeResult > 0)
                throw SqliteException("Open error occurred: ${e.fullMessage}. Close failed, rc: $closeResult")
            else
                throw e
        } catch (e1: Throwable) {
            val closeResult = sqliteDb.close()
            if (closeResult > 0)
                throw SqliteException("Open error (low level) occurred: ${e1.message}. Close failed, rc: $closeResult")
            else
                throw e1
        }
        if (tableCount > 0)
            catalog.retrieveTables()
        if (tableCount == 0)
            observers.forEach { it.onCreate(this) }
        else
            observers.forEach { it.onOpen(this) }
    }

    /**
     * Cleanly closes the database if open. All open statements should be closed before calling this.
     * If any are not, this attempts to close them which likely will cause an exception. If the
     * database close sees SQLITE_BUSY (5), it will try 3 more times to close before bailing
     */
    override fun close() {
        if (transactionDepth > 0)
            throw IllegalStateException("Cannot close database with active transaction")
        activeStatements.forEach {
            it.close()
            untrack(it)
        }
        var rc = sqliteDb.close()
        var count = 0
        while (rc == 5 && count < 3) {
            sqliteDb.sleep(250)
            rc = sqliteDb.close()
            count++
        }
        if (rc != 0)
            throw SqliteException("Close error: $rc", "close", rc)
        isOpen = false
        observers.forEach { it.onClose(this) }
    }

    fun tableCount(): Int {
        var tableCount = 0
        execute(openQuery) {
            tableCount = it.requireInt(0)
            true
        }
        return tableCount
    }

    /**
     * Runs a set of SQL commands, separated by semicolons.  Useful for any DML or pragmas that don't
     * require bind variables.
     * Some pragmas return a simple list of results. Use this and specify the lambda if the results
     * are required. Also for regular DML scripts that don't need bind variables, each sql statement
     * in the script will invoke the lambda to show any results.
     * @param sqlScript one or more SQL or PRAGMA statements separated by semicolons.
     * @param results lambda will be invoked once for each statement. The SqlValue objects in the
     * SqlValues argument will contain any results.
     * @throws SqliteException if any errors occur.
     */
    override fun execute(sqlScript: String, results: ((SqlValues) -> Boolean)?) {
        if (results == null)
            executeRaw(sqlScript)
        else {
            executeRaw(sqlScript) { names: Array<String>, data: Array<String> ->
                val row = SqlValues()
                for (i in names.indices) {
                    row.add(SqlValue.StringValue(names[i], data[i]))
                }
                if (results(row)) 0 else 1
            }
        }
    }

    /**
     * Use this for DML that requires bind variables. See [SqlCipherStatement] for details on how
     * to use the returned [PreparedStatement] to bind and execute the statement as many times as
     * desired. The SQL supplied will be immediately parsed by Sqlite. any usage of bind variables
     * in the command will be detected, and the returned [PreparedStatement] will be ready for
     * binding(s) and execution(s).
     * @param sql one valid SQL command, optionally requiring bind variable(s).
     * @return if the SQL is parseable and no other errors occur, returns an implementation of
     * [PreparedStatement] - an instance of [SqlCipherStatement]
     * @throws SqliteException if any errors occur.
     */
    override fun statement(sql: String): PreparedStatement {
        return SqlCipherStatement(this, sql)
    }

    override fun query(selectSql: String): Query {
        return SelectStatement(this, selectSql)
    }

    /**
     * A convenience method for submitting SELECT statements without using the [SelectStatement] class
     * directly. Statement will be parsed, and if no errors occur, bind variables will be applied and
     * the query will be executed. Each selected row in the result ser will be passed to the lambda.
     * Any errors along the way cause a SqliteException to be thrown.
     * @throws selectSql - must be one select statement of any complexity, anything else will cause
     * an exception to be thrown
     * @param bindArguments optional. if the above select statement contains bind variables using any
     * of the syntax conventions supported by Sqlite, then this argument should contain one SqlValue
     * for each of the bind variables found. If [bindArguments].size is not equal to the parameter
     * count found by parsing the SQL, an exception is thrown.  Both named arguments and positional
     * arguments are supported, but do not mix-and-match. Use all named [SqlValue] objects in
     * [bindArguments] for named parameters in the SQL. or use all unnamed [SqlValue] objects in
     * [bindArguments] for positional parameters. In this case of course the order, number, and types
     * of the SqlValue objects in [bindArguments] must match the arguments found when parsing. Any
     * mismatches cause an exception.
     * @param eachRow if all of the above works without errors, and one or more rows are returned in
     * the result set, this lambda will be invoked. The rowCount argument will be 1 for the first
     * row and incremented for each additional row. the sqlValues argument will contain one SqlValue
     * subtype for each column found in the result, null or not. Note that the lambda must return
     * true for additional rows to be retrieved.  Returning false cancels the query.
     * @return total number of rows returned.
     */
    override suspend fun usingSelect(
        selectSql: String,
        bindArguments: SqlValues,
        eachRow: suspend (rowCount: Int, sqlValues: SqlValues) -> Boolean
    ): Int {
        val stmt = query(selectSql)
        var count = 0
        stmt.retrieve(bindArguments) { rowCount: Int, sqlValues: SqlValues ->
            count = rowCount
            eachRow(rowCount, sqlValues)
        }
        stmt.close()
        return count
    }

    override suspend fun useInsert(
        insertSql: String,
        bindArguments: SqlValues): Long {
        val stmt = statement(insertSql)
        try {
            return stmt.insert(bindArguments)
        } finally {
            stmt.close()
        }
    }

    override suspend fun useStatement(
        dmlSql: String,
        bindArguments: SqlValues): Int {
        val stmt = statement(dmlSql)
        try {
            return stmt.execute(bindArguments)
        } finally {
            stmt.close()
        }
    }

    /**
     * This is invoked immediately after successful open whenever a non-empty passphrase is
     * specified to open.
     * @param passphrase specifies the key to be used to decrypt an existing database, or to
     * set the key for a database to be created.
     * @throws SqliteException if an unexpected result is returned by the pragma.
     *      the SqlCipher pragma is expected to return one row, column name: "ok", column value: "ok".
     */
    fun pragmaKey(passphrase: Passphrase) {
        pragma( "$pragmaKeyPrefix ${passphrase.keyPragmaText()}") {
            checkPragmaKeyResult(it)
            true
        }
    }

    private fun checkPragmaKeyResult(it: SqlValues) {
        if (it.size == 1) {
            val result = it[0].value
            if (result is String) {
                if (!(it[0].name.equals(pragmaSuccess, true))
                    || !(result.equals(pragmaSuccess, true))) {
                    throw SqliteException("pragma key error: ${it[0].name}=${it[0].value}")
                }
            } else {
                throw SqliteException("pragma key result invalid: ${it[0].name}=${it[0].value}")
            }
        } else {
            throw SqliteException("pragma key result size invalid: $it")
        }
    }

    /**
     * This must be invoked before any queries or statements are issued to the database. It will
     * change the key of the database from the one passed into the initial open, to the new one.
     * @param newPassphrase specifies new key to change to.
     */
    fun pragmaRekey(newPassphrase: Passphrase, results: ((SqlValues) -> Boolean)) {
        pragma( "$pragmaRekeyPrefix ${newPassphrase.keyPragmaText()}") {
            results(it)
            true
        }
    }

    /**
     * Convenience method for executing a PRAGMA. Some pragmas return small result sets, some just
     * indicate a change. This function can be used for any pragmas. Query-only pragmas can be run
     * anytime once a database is open.  Pragmas that change the database MUST be run before any
     * Select or DML statements are run, or they will fail.
     * @param pragmaText of one single PRAGMA. This function prepends the "PRAGMA " command and
     * adds appends a semicolon at the end.
     * Any valid text for both Sqlite pragmas and SqlCipher pragmas are ok.
     * @param results pragmas return one or more rows of results, usually string values.  Lambda will
     * be called once for each row until rows are exhausted or lambda returns false
     */
    fun pragma(pragmaText: String, results: ((SqlValues) -> Boolean)) {
        execute("PRAGMA $pragmaText;") {
            results(it)
        }
    }

    override fun beginTransaction(mode: TransactionMode) {
        val sql = buildString {
            append("BEGIN ")
            append(when (mode) {
                TransactionMode.Deferred -> "DEFERRED"
                TransactionMode.Immediate -> "IMMEDIATE"
                TransactionMode.Exclusive -> "EXCLUSIVE"})
            append(";")
        }
        execute(sql)
    }

    override fun commit() {
        execute("COMMIT;")
    }

    override fun rollback(savepointName: String) {
        val sql = buildString {
            append("ROLLBACK ")
            append(if (savepointName.isNotBlank()) {
                "TO SAVEPOINT $savepointName;"
            } else ";")
        }
        execute(sql)
        if (savepointName.isBlank())
            transactionDepth = 0
    }

    override fun savepoint(savepointName: String) {
        if (savepointName.isNotBlank())
            execute("SAVEPOINT $savepointName;")
    }

    override fun releaseSavepoint(savepointName: String) {
        if (savepointName.isNotBlank())
            execute("RELEASE $savepointName;")
    }

    override suspend fun transaction(mode: TransactionMode, unitOfWork: suspend () -> Unit) {
        if (transactionDepth == 0) {
            beginTransaction(mode)
        }
        transactionDepth++
        try {
            unitOfWork()
            if (transactionDepth == 1)
                commit()
        } catch (e:Exception) {
            val depth = transactionDepth
            if (transactionDepth == 1) {
                try {
                    rollback()
                } catch (_: Exception) {
                }
            }
            throw SqlTransactionException("Nesting depth: $depth", e)
        } finally {
            if (transactionDepth > 0)
                transactionDepth--
        }
    }

    private fun setup(passphrase: Passphrase) {
        softHeapLimit = defaultSoftHeapLimit
        sqliteDb.busyTimeout(defaultTimeout)
        if (passphrase.passphrase.isNotEmpty()) {
            pragmaKey(passphrase)
        }
    }

    private fun integrityCheck() {
        if (integrityCheck) {
            pragma(pragmaIntegrityCheck) {
                val response = it.requireString(0)
                if (response != pragmaSuccess)
                    throw SqliteException(response, "integrityCheck", -1)
                false
            }
        }
    }

    /**
     * Execute sql statement or statements,
     * Used by JNI
     * @param sql one or more valid SQL statements separated by semicolons
     */
    private fun executeRaw(sql: String, callback: ((Array<String>, Array<String>)-> Int)? = null) {
        sqliteDb.exec(sql) { values: Array<String>, columns: Array<String> ->
            if (callback != null) {
                callback(columns, values)
            } else
                0
        }
    }

    companion object {
        private const val inMemoryPath = ":memory:"
        private const val defaultSoftHeapLimit = (4 * 1024 * 1024).toLong()
        private const val defaultTimeout = 1000
        private const val pragmaIntegrityCheck = "integrity_check"
        const val catalogTable = "sqlite_master"
        private const val openQuery = "select count(*) from $catalogTable;"
        private const val pragmaKeyPrefix = "key ="
        private const val pragmaRekeyPrefix = "rekey ="
        private const val pragmaSuccess = "ok"
        private const val pragmaVersion = "cipher_version"
        private const val pragmaUserVersion = "user_version"
    }
}