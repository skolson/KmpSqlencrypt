package com.oldguy.kiscmp

import com.oldguy.database.PreparedStatement
import com.oldguy.database.SqlValue
import com.oldguy.database.SqlValues

class SqlCipherStatement(val db: SqlCipherDatabase, sql: String): PreparedStatement(sql) {
    val sqliteStatement = SqliteStatement(db.sqliteDb)
    override val parameterCount: Int get() = sqliteStatement.parameterCount()
    override val isReadOnly: Boolean get() = sqliteStatement.isReadOnly()
    private var retryable = false
    private val validBindName = "[@:\$][a-zA-Z0-9]+".toRegex()
    var namePrefix = ':'
        set(value) {
            if (":@$".contains(value))
                field = value
            else
                throw IllegalArgumentException("Bind argument name prefix character must be ':', '@', or '$'. Found $value")
        }

    init {
        sqliteStatement.prepare(sql)
        isOpen = true
        db.track(this)
        isBound = parameterCount != 0

    }

    override fun execute(bindParameters: SqlValues): Int {
        bind(bindParameters)
        var rows = -1
        when (sqliteStatement.step()) {
            SqliteStepResult.Done -> {
                retryable = false
                rows = sqliteStatement.changes()
                sqliteStatement.reset()
            }
            SqliteStepResult.Error -> {
                throw SqliteException("Execute error: ${db.errorMessage}", "step")
            }
            SqliteStepResult.Row -> {
                throw IllegalStateException("Row found, SQL should be DML only")
            }
            SqliteStepResult.Busy -> {
                retryable = true
            }
        }
        return rows
    }

    /**
     * This is used for sql that is an Insert statement where table is using a ROWID and the ROWID value
     * for that insert is required. If this statement is not an insert, -1 is returned and the statement IS
     * NOT PERFORMED.
     */
    override fun insert(bindArguments: SqlValues): Long {
        return if (!sql.lowercase().startsWith("insert")) {
            -1
        } else {
            execute(bindArguments)
            db.sqliteDb.lastInsertRowid()
        }
    }

    override fun close() {
        db.untrack(this)
        if (isOpen) {
            if (isBound)
                sqliteStatement.clearBindings()
            sqliteStatement.finalize()
        }
        super.close()
    }

    /**
     * Binds parameter values in the supplied Row.  If all are named parameters, then binding by name is used. If
     * no parameters are named, then binding by index is used, in the order they are added to the Row.
     * When using named, all names must be unique (unambiguous). If any named parms are not supplied
     * arguments, they are treated as if bound to null.
     *
     * If parms are unnamed, they are applied in index order.  Any additional unbound parms are treated as if
     * bound to null
     */
    fun bind(bindParameters: SqlValues) {
        sqliteStatement.clearBindings()
        if (bindParameters.count() > parameterCount)
            throw SqliteException("SQL requires $parameterCount parameters, bindParameters has ${bindParameters.count()}")
        val usingNamed = (bindParameters.all { it.name.isNotBlank() })
        if (!usingNamed &&
            !(bindParameters.all { it.name.isEmpty() }))
            statementAbort("bindParameters must all be named, or none named (indexing used). Mixing named and indexed is unsupported")
        if (usingNamed) {
            if (bindParameters.map { it.name }.distinct().size != bindParameters.count())
                statementAbort("bindParameters names must all be unique")
        }
        val namesUsed = mutableListOf<String>()
        bindParameters.forEachIndexed { index, parm ->
            val parmIndex = if (usingNamed) {
                var parmName = parm.name
                if (!validBindName.matches(parmName)) {
                    parmName = "$namePrefix$parmName"
                }
                val i = sqliteStatement.bindIndex(parmName)
                if (i == 0)
                    statementAbort("Named parameter: ${parmName} does not match any parm in the SQL")
                namesUsed.add(parm.name)
                i
            } else {
                index + 1   // Sqlite parameter indexes are 1-relative
            }
            if (parm.isNotNull) {
                when (parm) {
                    is SqlValue.StringValue -> bindString(parmIndex, parm.value!!)
                    is SqlValue.DateValue -> bindString(parmIndex, parm.toString())
                    is SqlValue.DateTimeValue -> bindString(parmIndex, parm.toString())
                    is SqlValue.BytesValue -> bindBytes(parmIndex, parm.value!!)
                    is SqlValue.BooleanValue -> {
                        when (val v = parm.mapToDb()) {
                            is Int -> bindInt(parmIndex, v)
                            is String -> bindString(parmIndex, v)
                            else -> throw IllegalStateException("Bug: BooleanValue mapValue returned: $v")
                        }
                    }
                    is SqlValue.DecimalValue -> bindString(parmIndex, parm.toString())
                    is SqlValue.BigIntegerValue -> bindString(parmIndex, parm.toString())
                    is SqlValue.LongValue -> bindLong(parmIndex, parm.value!!)
                    is SqlValue.IntValue -> bindInt(parmIndex, parm.value!!)
                    is SqlValue.FloatValue -> bindDouble(parmIndex, parm.value!!.toDouble())
                    is SqlValue.DoubleValue -> bindDouble(parmIndex, parm.value!!)
                }
            } else
                sqliteStatement.bindNull(parmIndex)
        }
        if (usingNamed) {
            val unused = bindParameters.map { it.name }.filter { !namesUsed.contains(it) }
            if (unused.isNotEmpty())
                statementAbort("Bind parameters supplied that were not used. Names: $unused")
        }
        isBound = true
    }

    private fun bindString(index:Int, value: String) {
        val rc = sqliteStatement.bindText(index, value)
        if (rc != 0)
            statementAbort("Attempting to bind string at index: $index failed with rc: $rc. Bind value: $value")
    }

    private fun bindInt(index:Int, value: Int) {
        val rc = sqliteStatement.bindInt(index, value)
        if (rc != 0)
            statementAbort("Attempting to bind int at index: $index failed with rc: $rc. Bind value: $value")
    }

    private fun bindLong(index:Int, value: Long) {
        val rc = sqliteStatement.bindLong(index, value)
        if (rc != 0)
            statementAbort("Attempting to bind long at index: $index failed with rc: $rc. Bind value: $value")
    }

    private fun bindDouble(index:Int, value: Double) {
        val rc = sqliteStatement.bindDouble(index, value)
        if (rc != 0)
            statementAbort("Attempting to bind long at index: $index failed with rc: $rc. Bind value: $value")
    }

    private fun bindBytes(index:Int, value: ByteArray) {
        val rc = sqliteStatement.bindBytes(index, value)
        if (rc != 0)
            statementAbort("Attempting to bind bytes at index: $index failed with rc: $rc. Bytes size: ${value.size}")
    }

    fun statementAbort(error: String) {
        close()
        throw SqliteException(error)
    }
}