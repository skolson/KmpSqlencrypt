package com.oldguy.database

import com.oldguy.kiscmp.SqlCipherDatabase

enum class ColumnType { String, Byte, Short, Int, Long, Float, Double,
    Decimal, BigInteger,
    Date, DateTime, Boolean, Blob, Clob, Expression }

open class Column(val name: String, val index: Int, val type: ColumnType = ColumnType.String, val isNullable: Boolean = false) {
    var isPrimaryKey: Boolean = false
    var precision = 0
    var scale = 0
    var declaration = ""

    fun createBindArgument(): SqlValue<out Any> {
        val bindName = ":$name"
        return when (type) {
            ColumnType.Byte,
            ColumnType.String -> SqlValue.StringValue(bindName)
            ColumnType.Short,
            ColumnType.Int -> SqlValue.IntValue(bindName)
            ColumnType.Long -> SqlValue.LongValue(bindName)
            ColumnType.Float -> SqlValue.FloatValue(bindName)
            ColumnType.Double -> SqlValue.DoubleValue(bindName)
            ColumnType.Decimal -> SqlValue.DecimalValue(bindName)
            ColumnType.BigInteger -> SqlValue.BigIntegerValue(bindName)
            ColumnType.Date -> SqlValue.DateValue(bindName)
            ColumnType.DateTime -> SqlValue.DateTimeValue(bindName)
            ColumnType.Boolean -> SqlValue.BooleanValue(bindName)
            ColumnType.Blob,
            ColumnType.Clob -> SqlValue.BytesValue(bindName)
            ColumnType.Expression ->
                throw IllegalStateException("Expression columns not supported")
        }
    }
    override fun toString(): String {
        return "Name: $name, index: $index, type: $type, nullable: $isNullable"
    }
}

class Columns {
    private val columnsList = emptyList<Column>().toMutableList()
    val columns get() = columnsList.toList()
    private val columnsMap = emptyMap<String, Column>().toMutableMap()

    operator fun get(name: String): Column {
        return columnsMap[name] ?: throw IllegalArgumentException("Invalid column name: $name")
    }

    operator fun get(index: Int): Column {
        return columnsList[index]
    }

    fun clear() {
        columnsList.clear()
        columnsMap.clear()
    }

    fun add(column: Column) {
        columnsList.add(column)
        if (columnsMap.containsKey(column.name))
            throw IllegalArgumentException("Column name: ${column.name} already exists")
        columnsMap[column.name] = column
    }

    fun defaultBindArguments(): SqlValues {
        return SqlValues().apply {
            columnsList.forEach { add(it.createBindArgument()) }
        }
    }

    override fun toString(): String {
        val b = StringBuilder("Columns Count: ${columnsList.size} \n")
        columnsList.forEach { b.append("${it}, ") }
        return b.toString()
    }
}

/**
 * An Index is owned by a table, and has access pointers for values of the one or more columns contained in the index.
 * Indexes also provide retrieval methods similar to those offered by a Table, but these use the index data to access
 * the table data based on the index values available. Records are retrieved in the order they are maintained within
 * the index.
 *
 */
open class Index(var name: String, val table: Table, val definition: String = "") {
    val columns = Columns()
}

/**
 * Tables have a name and a collection of Columns.  Since there is no SQL dialect etc, they also have simple
 * retrieve functions. One returns a List of Rows containing the content, and one invokes a lambda for each
 * Row of content.
 */
open class Table(val name: String, val systemCatalog: Boolean = false) {
    private val indexesList = mutableListOf<Index>()
    val indexNames get() = indexesList.map { it.name }
    val indexes get() = indexesList.toList()
    val references = emptyList<Table>().toMutableList()
    val primaryKey = emptyList<Column>().toMutableList()
    val columns = Columns()
    val columnNames get() = columns.columns.map { it.name }
    val properties = emptyMap<String, String>().toMutableMap()
    val selectColumns get() =
        buildString {
            val c = columnNames
            c.forEach { append("$it, ") }
        }.trimEnd(' ', ',')

    val bindColumns get() = buildString {
        val c = columnNames
        c.forEach { append(":$it, ") }
    }.trimEnd(' ', ',')
    val selectSql get() = "select $selectColumns from $name "
    val insertSql get() = "insert into $name ( $selectColumns ) values ( $bindColumns )"

    fun addProperty(name: String, value:String): Table {
        properties[name] = value
        return this
    }

    fun add(index: Index) {
        indexesList.add(index)
    }

    fun rowCount(db: Database): Long {
        var tableCount = 0L
        db.execute("select count(*) from $name") {
            tableCount = it.requireLong(0)
            true
        }
        return tableCount
    }

    override fun toString(): String {
        return "  Table: $name, columns: $columns \n  Indexes: $indexNames"
    }

    companion object {
        const val standardSelect = "select * from "
    }
}