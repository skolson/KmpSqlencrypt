package com.oldguy.kiscmp

import com.ionspin.kotlin.bignum.decimal.BigDecimal
import com.ionspin.kotlin.bignum.integer.BigInteger
import com.oldguy.database.*

class SelectStatement(private val db: SqlCipherDatabase, sql:String): Query(sql)
{
    private val stmt = SqlCipherStatement(db, sql)
    private val shim = stmt.sqliteStatement

    val expandedSql: String get() = shim.expandedSql()

    val isBusy: Boolean get() = shim.isBusy()
    override val isReadOnly: Boolean get() = shim.isReadOnly()
    override val parameterCount: Int get() = shim.parameterCount()
    override val columnCount: Int get() = shim.columnCount()
    val dataCount: Int get() = shim.dataCount()

    init {
        isOpen = stmt.isOpen
        db.track(this)
        parseColumns()
    }

    private fun parseColumns() {
        checkOpen()
        val count = columnCount
        for (index in 0 until count) {
            val name = shim.columnName(index)
            val type = shim.columnDeclaredType(index)
            val meta = if (type.isEmpty()) {
                // check for an expression or subquery
                when (shim.columnType(index)) {
                    SqliteStatement.ColumnType.Null ->
                        SqliteColumn.ColumnMetadata(ColumnType.Expression)
                    SqliteStatement.ColumnType.Integer ->
                        SqliteColumn.ColumnMetadata(ColumnType.Long)
                    SqliteStatement.ColumnType.Text ->
                        SqliteColumn.ColumnMetadata(ColumnType.String)
                    SqliteStatement.ColumnType.Float ->
                        SqliteColumn.ColumnMetadata(ColumnType.Decimal)
                    SqliteStatement.ColumnType.Blob ->
                        SqliteColumn.ColumnMetadata(ColumnType.Blob)
                }
            } else {
                SqliteColumn.parseColumnDeclaration(type, db.useBigDecimal)
            }
            val col = SqliteColumn(name, index, meta.type)
            col.precision = meta.precision
            col.scale = meta.scale
            col.declaration = type
            columns.add(col)
        }
    }

    override fun insert(bindArguments: SqlValues): Long {
        throw SqliteException("Use SqlCipherStatement for inserts")
    }

    /**
     * Iterate one row resulting from select SQL.
     * @return SqlValues will be empty if no rows remain. Otherwise will contain one of the SqlValue
     * sealed class types for each column, appropriate to the column type, each with the value from
     * the row. The SqlValue type can either be left to default, or can be dictated by setting the
     * targetTypes property of the query with your own. If targetTypes is present, the number of them
     * provided must match the column count of the select, or an exception is thrown.
     */
    override fun nextRow(): SqlValues {
        val row = SqlValues()
        val rc = shim.step()
        if (rc == SqliteStatement.StepResult.Done) {
            shim.reset()
            return row
        }
        /*
        if (rc == SQLITE_BUSY) {
            // possibly throw an exception or pass argument that indicates retry-ability, don't reset or close
        }
         */
        if (rc != SqliteStatement.StepResult.Row) {
            stmt.statementAbort("sqlite3_step error code: $rc")
        }
        if (targetTypes.isNotEmpty  && targetTypes.count() != columnCount) {
            stmt.statementAbort("Target types provided not valid. Column count: $columnCount, targetTypes count: ${targetTypes.count()}")
        }
        for (index in 0 until columnCount) {
            val sqliteType = shim.columnType(index)
            val isNull = sqliteType == SqliteStatement.ColumnType.Null
            val name = columns[index].name
            val rv: SqlValue<out Any> =
                if (targetTypes.isNotEmpty) {
                    buildValueUsingTarget(index, isNull, name, sqliteType)
                } else {
                    buildValueUsingMetadata(index, isNull, name, sqliteType)
                }
            row.add(rv)
        }
        return row
    }

    /**
     * This produces RowValue types as indicated by the column definitions where available.  If the column is an
     * expression that has no metadata, a StringValue is built. If precision loss is detected, an exception is thrown
     * @throws kotlin.IllegalStateException
     */
    private fun buildValueUsingMetadata(
        index: Int,
        isNull: Boolean,
        name: String,
        sqliteType: SqliteStatement.ColumnType): SqlValue<out Any>
    {
        val column = columns[index]
        return when (column.type) {
            ColumnType.String -> getStringValue(name, isNull, index)
            ColumnType.Byte,
            ColumnType.Short,
            ColumnType.Int -> getIntValue(name, isNull, index, sqliteType)
            ColumnType.Long -> getLongValue(name, isNull, index, sqliteType)
            ColumnType.Float,
            ColumnType.Double -> getDoubleValue(name, isNull, index, sqliteType)
            ColumnType.Decimal -> getDecimalValue(name, isNull, index)
            ColumnType.BigInteger -> getBigIntegerValue(name, isNull, index)
            ColumnType.Date -> getDateValue(name, isNull, index)
            ColumnType.DateTime -> getDateTimeValue(name, isNull, index)
            ColumnType.Boolean -> getBooleanValue(name, isNull, index)
            ColumnType.Blob -> getBytesValue(name, isNull, index)
            ColumnType.Clob -> getStringValue(name, isNull, index)
            ColumnType.Expression -> {
                when (sqliteType) {
                    SqliteStatement.ColumnType.Integer ->
                        getLongValue(name, isNull, index, sqliteType)
                    SqliteStatement.ColumnType.Null,
                    SqliteStatement.ColumnType.Text ->
                        getStringValue(name, isNull, index)
                    SqliteStatement.ColumnType.Float ->
                        getDecimalValue(name, isNull, index)
                    SqliteStatement.ColumnType.Blob ->
                        getBytesValue(name, isNull, index)
                }
            }
        }
    }

    /**
     * extracts value based on type provided in targetTypes Row. This relies on Sqlite conversions
     */
    private fun buildValueUsingTarget(
        index: Int,
        isNull:Boolean,
        name:String,
        sqliteType: SqliteStatement.ColumnType): SqlValue<out Any> {
        return when(targetTypes[index - 1]) {
            is SqlValue.StringValue -> getStringValue(name, isNull, index)
            is SqlValue.DateTimeValue -> getDateTimeValue(name, isNull, index)
            is SqlValue.DateValue -> getDateValue(name, isNull, index)
            is SqlValue.BytesValue -> getBytesValue(name, isNull, index)
            is SqlValue.BooleanValue -> getBooleanValue(name, isNull, index)
            is SqlValue.DecimalValue -> getDecimalValue(name, isNull, index)
            is SqlValue.BigIntegerValue -> getBigIntegerValue(name, isNull, index)
            is SqlValue.LongValue -> getLongValue(name, isNull, index, sqliteType)
            is SqlValue.IntValue -> getIntValue(name, isNull, index, sqliteType)
            is SqlValue.FloatValue -> {
                if (isNull) SqlValue.FloatValue(name)
                else
                    SqlValue.FloatValue(name, shim.columnDouble(index).toFloat())
            }
            is SqlValue.DoubleValue -> getDoubleValue(name, isNull, index, sqliteType)
        }
    }

    override fun retrieveList(bindParameters: SqlValues): List<SqlValues> {
        stmt.bind(bindParameters)
        val list = mutableListOf<SqlValues>()
        var row = nextRow()
        while (row.isNotEmpty) {
            list.add(row)
            row = nextRow()
        }
        close()
        return list
    }

    override suspend fun retrieve(bindParameters: SqlValues,
                                  oneRow: suspend (rowCount: Int, sqlValues: SqlValues) -> Boolean): Int {
        stmt.bind(bindParameters)
        var count = 0
        var row = nextRow()
        while (row.isNotEmpty) {
            count++
            if (!oneRow(count, row))
                break
            row = nextRow()
        }
        close()
        return count
    }

    override suspend fun retrieveOne(bindParameters: SqlValues,
                                     oneRow: suspend (rowCount: Int, sqlValues: SqlValues) -> Unit): Int {
        stmt.bind(bindParameters)
        var count = 0
        val row = nextRow()
        if (row.isNotEmpty) {
            count = 1
            oneRow(count, row)
        }
        close()
        return count
    }

    override fun close() {
        localClose()
    }

    private fun localClose() {
        stmt.close()
        db.untrack(this)
        isOpen = false
    }

    private fun checkOpen() {
        if (!isOpen)
            throw SqliteException("Prepared Statement is closed")
    }

    /**
     * The following functions are all related to extracting data from a column in a currently open and
     * stepping statement.
     */
    private fun getString(index:Int): String {
        return shim.columnText(index)
    }

    private fun getBytes(index: Int): ByteArray {
        return shim.columnBlob(index)
    }

    private fun getBytesValue(name:String, isNull: Boolean, index: Int): SqlValue.BytesValue {
        return if (isNull)
            SqlValue.BytesValue(name)
        else
            SqlValue.BytesValue(name, getBytes(index))
    }

    private fun getIntValue(name:String, isNull: Boolean, index: Int, sqliteType: SqliteStatement.ColumnType): SqlValue.IntValue {
        return if (isNull)
            SqlValue.IntValue(name)
        else {
            if (sqliteType != SqliteStatement.ColumnType.Integer)
                stmt.statementAbort("Column: $name, index: $index, declaration: ${columns[index].declaration} is not Integer")
            SqlValue.IntValue(name, shim.columnInt(index))
        }
    }

    private fun getLongValue(name:String, isNull: Boolean, index: Int, sqliteType: SqliteStatement.ColumnType): SqlValue.LongValue {
        return if (isNull)
            SqlValue.LongValue(name)
        else {
            if (sqliteType != SqliteStatement.ColumnType.Integer)
                stmt.statementAbort("Column: $name, index: $index, declaration: ${columns[index].declaration} is not Integer")
            SqlValue.LongValue(name, shim.columnLong(index))
        }
    }

    private fun getStringValue(name:String, isNull: Boolean, index: Int): SqlValue.StringValue {
        return if (isNull)
            SqlValue.StringValue(name)
        else {
            SqlValue.StringValue(name, getString(index))
        }
    }

    private fun getDoubleValue(name:String, isNull: Boolean, index: Int, sqliteType: SqliteStatement.ColumnType): SqlValue.DoubleValue {
        return if (isNull)
            SqlValue.DoubleValue(name)
        else {
            if (sqliteType != SqliteStatement.ColumnType.Float)
                stmt.statementAbort("Column: $name, index: $index, declaration: ${columns[index].declaration} is not FLOAT")
            SqlValue.DoubleValue(name, shim.columnDouble(index))
        }
    }

    private fun getDecimalValue(name: String, isNull: Boolean, index: Int): SqlValue.DecimalValue {
        return if (isNull)
            SqlValue.DecimalValue(name)
        else
            SqlValue.DecimalValue(name, BigDecimal.parseString(getString(index)))
    }

    private fun getBigIntegerValue(name: String, isNull: Boolean, index: Int): SqlValue.BigIntegerValue {
        return if (isNull)
            SqlValue.BigIntegerValue(name)
        else
            SqlValue.BigIntegerValue(name, BigInteger.parseString(getString(index)))
    }

    private fun getBooleanValue(name: String, isNull: Boolean, index: Int): SqlValue.BooleanValue {
        return if (isNull)
            SqlValue.BooleanValue(name)
        else {
            SqlValue.BooleanValue(name, shim.columnText(index))
        }
    }

    private fun getDateValue(name: String, isNull: Boolean, index: Int): SqlValue.DateValue {
        return if (isNull)
            SqlValue.DateValue(name)
        else {
            val text = getString(index)
            val dt = SqlValue.DateValue.dateFormatter.tryParse(text)?.local?.date
            if (dt == null) {
                stmt.statementAbort("Unparseable date string: $text at index:$index")
                SqlValue.DateValue(name)
            } else
                SqlValue.DateValue(name, dt)
        }
    }

    private fun getDateTimeValue(name: String, isNull: Boolean, index: Int): SqlValue.DateTimeValue {
        return if (isNull)
            SqlValue.DateTimeValue(name)
        else {
            val text = getString(index)
            val dt = SqlValue.DateTimeValue.parse(text)
            if (dt == null) {
                stmt.statementAbort("Unparseable dateTime string: $text at index:$index")
                SqlValue.DateTimeValue(name)
            } else
                SqlValue.DateTimeValue(name, dt)
        }
    }

    companion object {
        private const val columnTypeError = "Unsupported columnType"
    }
}