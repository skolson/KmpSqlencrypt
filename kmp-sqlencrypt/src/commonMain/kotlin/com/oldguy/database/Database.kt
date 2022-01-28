package com.oldguy.database

import com.oldguy.kiscmp.SqlCipherDatabase
import kotlinx.atomicfu.atomic

abstract class SystemCatalog {
    val tables = emptyMap<String, Table>().toMutableMap()
    val schemas = emptyMap<String, Map<String, Table>>().toMutableMap()
    val linkedDatabaseNames = emptyList<String>().toMutableList()
    open val schemasSupported = true

    abstract val catalogTableName: String

    abstract suspend fun retrieveTables()

    fun close() {
        tables.clear()
        linkedDatabaseNames.clear()
        schemas.clear()
    }

    fun add(table: Table): Table {
        tables[table.name] = table
        return table
    }

    fun table(tableName: String) =
        tables[tableName]
            ?: throw IllegalArgumentException("No such system catalog name: $tableName")

    override fun toString(): String {
        val b = StringBuilder("Catalog Tables: ")
        tables.forEach {
            b.append("  ${it.value}\n")
        }
        return b.toString()
    }
}

/**
 * Helper to construct the appropriate SqlCipher pragma key command. Three types of key are
 * supported.
 * @param passphrase can be a string of any length, will be used as a key if length is non zeron
 * and no other parms are specified.
 * @param isRaw if false, passphrase is used as is. If true passphrase must be 64 characters
 * long (hex encoded), with one exception if hasSalt is true
 * @param hasSalt if true, passphrase must be 96 characters (hex encoded).
 */
open class Passphrase(val passphrase: String = "",
                      val isRaw: Boolean = false,
                      val hasSalt:Boolean = false
) {

    /**
     * Use this if the key is in bytes, length will determine if salt is included or not. The constructor
     * will hex encode the bytes.
     * @param bytes is a ByteArray that must be either 32 bytes long, indicating a raw key, or
     * 48 bytes long, indicating a 32 byte raw key and a 16 byte hash.
     */
    constructor(bytes: ByteArray): this(
        bytes.toHex(),
        bytes.size == (rawKeyLength.value / 2),
        bytes.size == ((rawKeyLength.value / 2) + (saltLength.value / 2))  )

    init {
        if (hasSalt && !isRaw)
            throw IllegalArgumentException("If hasSalt is true, isRaw must be true")
        if (hasSalt && passphrase.length != (rawKeyLength.value + saltLength.value))
            throw IllegalArgumentException("Raw key with salt must be 96 characters, hex encoded. Found: ${passphrase.length} characters")
        if (isRaw && passphrase.length != rawKeyLength.value)
            throw IllegalArgumentException("Raw key with no salt must be 64 characters, hex encoded. Found: ${passphrase.length} characters")
    }

    val salt = if (hasSalt)
        passphrase.substring(rawKeyLength.value)
    else
        ""

    open fun keyPragmaText(): String {
        return if (hasSalt || isRaw)
            "\"x'$passphrase'\""
        else
            "'$passphrase'"
    }

    companion object {
        val rawKeyLength = atomic(64)
        val saltLength = atomic(32)
    }
}

interface DatabaseObserver {
    suspend fun onCreate(db: Database)
    suspend fun onOpen(db: Database)
    fun onClose(db: Database)
}

class SqlTransactionException(message: String, exc: Throwable): Exception(message, exc) {
    override val message: String
        get() {
            var c = this.cause
            while(c != null && c is SqlTransactionException) {
                c = c.cause
            }
            return c?.message ?: "Unknown"
        }
}

abstract class Database {
    abstract val fileName: String
    abstract var path: String
    abstract val catalog: SystemCatalog
    abstract var isOpen: Boolean
    var transactionDepth = 0
    var useBigDecimal: Boolean = true
    val observers = mutableListOf<DatabaseObserver>()
    var trackActiveStatements = false
    private val activeStatementList = mutableListOf<PreparedStatement>()
    val activeStatements get() = activeStatementList.toList()
    abstract suspend fun tableCount(): Int

    /**
     * Open a database, perform the work in a lambda, close the database. Useful for small amounts
     * of work in a single scope. Uses the path property, invalidPassphrase property, and onOpenPragmas proprty that
     * are all set at configuration time.
     * @param passphrase optional, specify if opening/creating an encrypted database
     * and the supplied path does not exist, create a database at that path.
     * @param block lambda do work on an open database. Database will be closed at return of block.
     */
    abstract suspend fun use(
        passphrase: Passphrase = Passphrase(),
        block: suspend (db: Database) -> Unit)

    /**
     * Open a database, perform the work in a lambda, close the database. Useful for small amounts
     * of work in a single scope.
     * @param path is an absolute path or a URI or some other unique string identifying a database. If
     * path is empty, implementations may provide a default. Overrides configured path.
     * @param passphrase optional, specify if opening/creating an encrypted database
     * and the supplied path does not exist, create a database at that path.
     * @param invalidPassphrase lambda overrides configured [invalidPassphrase]
     * @param block lambda do work on an open database. Database will be closed at return of block.
     */
    abstract suspend fun use(
        path:String = "",
        passphrase: Passphrase = Passphrase(),
        invalidPassphrase: (suspend (db: SqlCipherDatabase, passphrase: Passphrase) -> Unit)? = null,
        block: suspend (db: Database) -> Unit)

    /**
     * Open a database instance, given a path to the database, and an optional password. Assumes database
     * instance is fully configured before [open] is called.
     * @param passphrase optional, specify if opening/creating an encrypted database
     * and the supplied path does not exist, create a database at that path.
     */
    abstract suspend fun open(passphrase: Passphrase = Passphrase())

    /**
     * Execute sql statement or statements. Bind variables are not supported, so use this for individual
     * SQL statements, or SQL scripts that require no binds.
     *
     * @param sqlScript one or more valid SQL statements separated by semicolons
     * @param results will be invoked once for each statement. The [SqlValues] argument will contain zero or more
     * RowValues of type string.  The results lambda returns true to continue processing additional statements.
     * If it returns false, the processing of SQL statements is stopped and no more are executed.
     * If results is null (no lambda specified), no results are returned but statements are executed.
     * @throws IllegalStateException or an implementation-specific exception with error text
     * about the error if one is encountered
     */
    abstract suspend fun execute(sqlScript: String, results: ((SqlValues) -> Boolean)? = null)


    /**
     * Use this for any SQL DML, i.e; insert, update, delete, where the SQL statement contains bind
     * variables of the syntax supported by sqlite3.
     *
     * This is a convenience wrapper for [PreparedStatement]. See that documentation for how to
     * bind and execute the returned PreparedStatement one or more times as desired.
     *
     * @param sql one sql statement. Scripts are not supported
     */
    abstract fun statement(sql: String): PreparedStatement

    /**
     * Compile one select statement, and apply any bindArguments. Returned Query is ready for
     * applying any bind variables (see Query function bind()), and iterating the result set (see
     * Query function nextRow()).  Any SQL errors will throw an IllegalArgumentException.
     *
     * User is responsible for closing the query when done with it.  See [usingSelect] function for
     * doing queries easier. This "manual" method is for use when cursor iteration logic needs more
     * flexibility than is provided by [usingSelect].
     *
     * @param selectSql for the select statement. Bind variables can be "?" for un-nanmed arguments, or
     * named arguments.
     * @return Query statement ready for an optional call to Query's bind() function, and then for calls to
     * Query's nextRow() function to iterate the result set from the query
     */
    abstract fun query(selectSql: String): Query

    /**
     * Convenience method for building a Select statment. Prepares the statement, throwing an
     * exception on any SQL error.  Applies any supplied bind arguments, and then iterates through
     * the result set, invoking the lambda once for each row.
     * @param selectSql select statement with or without bind variables
     * @param bindArguments a SqlValues collection, defaults to empty (no bind arguments).
     * If the above select statement contains bind variables using any
     * of the syntax conventions supported by the database, then this argument should contain one SqlValue
     * for each of the bind variables found. If using unnamed/positional arguments and
     * [bindArguments].size is not equal to the parameter count found by parsing the SQL, an
     * exception is thrown. Both named arguments and positional arguments are supported, but do not
     * mix-and-match. Use all named [SqlValue] objects in [bindArguments] for named parameters in
     * the select SQL, or ensure [SqlValue] objects in [bindArguments] are in the correct order for
     * positional parameters. In this case of course the order, number, and types
     * of the SqlValue objects in [bindArguments] must match the arguments found when parsing. Any
     * mismatches cause an exception.
     * @param eachRow invoked for each row found. Arguments are the row count in the result set and
     * the row. If method returns true, next row is retrieved. If false, select ends
     */
    abstract suspend fun usingSelect(
        selectSql: String,
        bindArguments: SqlValues = SqlValues(),
        eachRow: suspend (rowCount: Int, sqlValues: SqlValues) -> Boolean
    ): Int

    abstract suspend fun useInsert(
        insertSql: String,
        bindArguments: SqlValues = SqlValues()): Long

    abstract suspend fun useStatement(
        dmlSql: String,
        bindArguments: SqlValues = SqlValues()): Int

    abstract fun close()

    fun addObserver(observer: DatabaseObserver) {
        if (!observers.contains(observer)) observers.add(observer)
    }

    fun removeObserver(observer: DatabaseObserver) {
        if (observers.contains(observer)) observers.remove(observer)
    }

    fun track(stmt: PreparedStatement): PreparedStatement {
        if (trackActiveStatements) {
            if (!activeStatementList.contains(stmt)) activeStatementList.add(stmt)
        }
        return stmt
    }

    fun untrack(stmt: PreparedStatement): PreparedStatement {
        if (trackActiveStatements) {
            activeStatementList.remove(stmt)
        }
        return stmt
    }

    /**
     * The following set of functions are convenience methods for managing transactions aka logical
     * units of work.
     */

    enum class TransactionMode {
        Deferred, Immediate, Exclusive
    }

    /**
     *
     */
    abstract suspend fun beginTransaction(mode: TransactionMode = TransactionMode.Deferred)

    abstract suspend fun commit()

    abstract suspend fun rollback(savepointName: String = "")

    abstract suspend fun savepoint(savepointName: String)

    abstract suspend fun releaseSavepoint(savepointName: String)

    /**
     * The work within the lambda will be performed as part of one database transaction. At the end,
     * if the lamdba throws any exception, this function will attempt a rollback, otherwise a commit
     * is performed.
     * Implementations track whether a transaction is in progress for the current database. if it is,
     * no commit is done. This allows unlimited nesting of these lambdas, and only the first one that
     * started the transaction will commit it. Same for rollback - any exception thrown is rethrown,
     * but only the top-level transaction function will perform the rollback. After the top-level
     * rollback, the active exception is rethrown
     */
    abstract suspend fun transaction(mode: TransactionMode = TransactionMode.Deferred, unitOfWork: suspend () -> Unit)
}
