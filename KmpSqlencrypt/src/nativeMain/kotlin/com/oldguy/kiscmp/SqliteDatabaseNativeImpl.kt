package com.oldguy.kiscmp

import com.oldguy.sqlcipher.*
import kotlinx.cinterop.*

/**
 * All native implementations supply their own cinterop setup using their own classes and cinterop
 * configs.  Since they all use the same Sqlite API, they can all share this implementation. It will then
 * be compiled and linked as apporpriate for each target. This minimizes the amount of redundant code in the
 * various classes in each of the target-specific sourcesets.
 */
open class SqliteDatabaseNativeImpl {

    var dbContext: CPointer<sqlite3>? = null
        private set
    var encoding = SqliteEncoding.Utf_8
    val sqliteNotadb = SQLITE_NOTADB

    open fun error(): String {
        return sqlite3_errmsg(dbContext)?.toKString() ?: ""
    }

    open fun fileName(): String {
        return sqlite3_db_filename(dbContext, "main")?.toKString() ?: ""
    }

    open fun openImpl(
        path: String,
        readOnly: Boolean,
        createOk: Boolean
    ): Int {
        memScoped {
            val dbPtr = alloc<CPointerVar<sqlite3>>()
            val openFlags = if (readOnly)
                SQLITE_OPEN_READONLY
            else if (createOk)
                SQLITE_OPEN_READWRITE + SQLITE_OPEN_CREATE
            else
                SQLITE_OPEN_READWRITE
            val rc = sqlite3_open_v2(path, dbPtr.ptr, openFlags, null)
            if (rc != SQLITE_OK)
                throw IllegalStateException("Cannot open database: $path, rc: $rc, error: ${sqlite3_errmsg(dbPtr.value)?.toKString()}")
            dbContext = dbPtr.value!!
            return rc
        }
    }

    open fun close(): Int {
        dbContext?.let {
            val rc = sqlite3_close_v2(it)
            if (rc == SQLITE_OK)
                dbContext = null
            return rc
        }
        return 0
    }

    open fun softHeapLimit(limit: Long): Long {
        return sqlite3_soft_heap_limit64(limit)
    }

    open fun busyTimeout(timeout: Int) {
        dbContext?.let {
            sqlite3_busy_timeout(it, timeout)
        }
    }

    open fun exec(
        sql: String,
        callback: ((values: Array<String>, columnNames: Array<String>) -> Int)?
    ): Int {
        var result = 0
        dbContext?.let {
            memScoped {
                val error = alloc<CPointerVar<ByteVar>>()
                result = if (callback == null) {
                    sqlite3_exec(it, sql, null, null, error.ptr)
                } else {
                    val callbackStable = StableRef.create(callback)
                    defer { callbackStable.dispose() }
                    sqlite3_exec(it, sql, staticCFunction { ptr, count, data, columns ->
                        val callbackFunction = ptr!!.asStableRef<(Array<String>, Array<String>) -> Int>().get()
                        callbackFunction(
                            Array(count) { data?.get(it)?.toKString() ?: "" },
                            Array(count) { columns?.get(it)?.toKString() ?: "" }
                        )
                    },
                        callbackStable.asCPointer(),
                        error.ptr
                    )
                }
                defer { sqlite3_free(error.value) }
                if (result != SQLITE_OK && result != SQLITE_ABORT)
                    throw SqliteException(
                        "exec() error. result: $result, error: ${error.value!!.toKString()}, sql: $sql",
                        "sqlite3_exec",
                        result
                    )
            }
        }
        return result
    }

    open fun exec(sql: String): Int {
        return exec(sql, null)
    }

    open fun version(): String {
        return SQLITE_VERSION
    }

    /**
     * Useful after an Insert statement when a ROWID is expected to be generated by Sqlite during
     * the insert
     */
    open fun lastInsertRowid(): Long {
        return dbContext?.let {
            sqlite3_last_insert_rowid(it)
        } ?: throw SqliteException("Attempt to invoke lastInsertRowid on closed database")
    }

    open fun sleep(millis: Int) {
        sqlite3_sleep(millis)
    }
}

open class SqliteStatementNativeImpl constructor(private val db: SqliteDatabaseNativeImpl) {
    private val dbClosedError = SqliteException("Db closed")
    private val stmtClosedError = SqliteException("Statement closed")
    private var statementContext: CPointer<sqlite3_stmt>? = null
    private val openStatement get() = statementContext ?: throw stmtClosedError
    private val openDb get() = db.dbContext ?: throw dbClosedError

    open fun parameterCount(): Int {
        return sqlite3_bind_parameter_count(openStatement)
    }

    open fun isReadOnly(): Boolean {
        return sqlite3_stmt_readonly(openStatement) == 1
    }

    open fun prepare(sql: String): Int {
        db.dbContext?.let {
            statementContext?.let {
                finalize()
            }
            memScoped {
                val tailPtr = alloc<CPointerVar<ByteVar>>()
                val stmtPtr = alloc<CPointerVar<sqlite3_stmt>>()
                val result = sqlite3_prepare_v2(it, sql.cstr.ptr, sql.length, stmtPtr.ptr, tailPtr.ptr)
                if (result != SQLITE_OK) {
                    throw SqliteException("Cannot prepare statement: ${sqlite3_errstr(result)?.toKString()}")
                }
                statementContext = stmtPtr.value!!
                return result
            }
        } ?: throw dbClosedError
    }

    open fun bindIndex(name: String): Int {
        return sqlite3_bind_parameter_index(openStatement, name)
    }

    open fun bindNull(index: Int): Int {
        return sqlite3_bind_null(openStatement, index)
    }

    open fun bindText(index: Int, text: String): Int {
        return when (db.encoding) {
            SqliteEncoding.Utf_8 -> {
                sqlite3_bind_text(openStatement, index, text, text.length, SQLITE_TRANSIENT)
            }
            SqliteEncoding.Utf_16,
            SqliteEncoding.Utf16LittleEndian,
            SqliteEncoding.Utf16BigEndian -> {
                val utf16 = text.utf16
                sqlite3_bind_text16(openStatement, index, utf16, utf16.size * 2, SQLITE_TRANSIENT)
            }
        }
    }

    open fun bindInt(index: Int, value: Int): Int {
        return sqlite3_bind_int(openStatement, index, value)
    }

    open fun bindLong(index: Int, value: Long): Int {
        return sqlite3_bind_int64(openStatement, index, value)
    }

    open fun bindDouble(index: Int, value: Double): Int {
        return sqlite3_bind_double(openStatement, index, value)
    }

    open fun bindBytes(index: Int, array: ByteArray): Int {
        return sqlite3_bind_blob(openStatement, index, array.toCValues(), array.size, SQLITE_TRANSIENT)
    }

    open fun step(): SqliteStepResult {
        return when (val rc = sqlite3_step(openStatement)) {
            SQLITE_ERROR -> SqliteStepResult.Error
            SQLITE_DONE -> SqliteStepResult.Done
            SQLITE_ROW -> SqliteStepResult.Row
            SQLITE_BUSY -> SqliteStepResult.Busy
            SQLITE_MISUSE -> throw SqliteException("Bug: SQL_MISUSE returned from sqlite3_step")
            else -> throw SqliteException("Unsupported return from sqlite3_step: $rc")
        }
    }

    open fun changes(): Int {
        return sqlite3_changes(openDb)
    }

    open fun finalize(): Int {
        return statementContext?.let {
            val rc = sqlite3_finalize(it)
            statementContext = null
            rc
        } ?: 0
    }

    open fun clearBindings() {
        sqlite3_clear_bindings(openStatement)
    }

    open fun reset() {
        statementContext?.let {
            sqlite3_reset(it)
        }
    }

    /**
     * The following functions are only useful when preparing and running a select statement.
     */
    open fun expandedSql(): String {
        return sqlite3_expanded_sql(openStatement)?.toKString() ?: ""
    }

    open fun isBusy(): Boolean {
        return sqlite3_stmt_busy(openStatement) > 0
    }

    open fun columnCount(): Int {
        return sqlite3_column_count(openStatement)
    }

    open fun dataCount(): Int {
        return sqlite3_data_count(openStatement)
    }

    open fun columnName(index: Int): String {
        return sqlite3_column_name(openStatement, index)?.toKString() ?: ""
    }

    open fun columnDeclaredType(index: Int): String {
        return sqlite3_column_decltype(openStatement, index)?.toKString() ?: ""
    }

    open fun columnType(index: Int): SqliteColumnType {
        return when (val rc = sqlite3_column_type(openStatement, index)) {
            SQLITE_NULL -> SqliteColumnType.Null
            SQLITE_TEXT -> SqliteColumnType.Text
            SQLITE_INTEGER -> SqliteColumnType.Integer
            SQLITE_FLOAT -> SqliteColumnType.Float
            SQLITE_BLOB -> SqliteColumnType.Blob
            else -> throw SqliteException("Unsupported return from columnTypeInt($index): $rc")
        }
    }

    /**
     * Kotlin native doesn't have a conversion from UTF-16 bytes to String
     */
    open fun columnText(index: Int): String {
        val len = sqlite3_column_bytes(openStatement, index)
        return when (db.encoding) {
            SqliteEncoding.Utf_8 -> {
                sqlite3_column_text(openStatement, index)?.readBytes(len)?.toKString() ?: ""
            }
            SqliteEncoding.Utf_16,
            SqliteEncoding.Utf16LittleEndian,
            SqliteEncoding.Utf16BigEndian -> {
                val le = db.encoding == SqliteEncoding.Utf16LittleEndian || Platform.isLittleEndian
                sqlite3_column_text16(openStatement, index)?.readBytes(len)?.let { bytes ->
                    val chars = len / 2
                    buildString {
                        for (i in 0 until chars step 2) {
                            append(Char(
                                if (le)
                                    (bytes[i]*256) + bytes[i+1]
                                else
                                    bytes[i] + (bytes[i+1]*256)
                            ))
                        }
                    }
                } ?: ""
            }
        }
    }

    open fun columnBlob(index: Int): ByteArray {
        val len = sqlite3_column_bytes(openStatement, index)
        return sqlite3_column_blob(openStatement, index)?.readBytes(len) ?: ByteArray(0)
    }

    open fun columnDouble(index: Int): Double {
        return sqlite3_column_double(openStatement, index)
    }

    open fun columnInt(index: Int): Int {
        return sqlite3_column_int(openStatement, index)
    }

    open fun columnLong(index: Int): Long {
        return sqlite3_column_int64(openStatement, index)
    }
}