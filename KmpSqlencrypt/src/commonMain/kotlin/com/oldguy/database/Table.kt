package com.oldguy.database

/**
 * Enumerates the supported column types
 */
enum class ColumnType { String, Byte, Short, Int, Long, Float, Double,
    Decimal, BigInteger,
    Date, DateTime, Boolean, Blob, Clob, Expression }

/**
 * Each instance contains metadata about a column of a [Table].
 * @param name
 * @param index zero-relative index, must be unique for each column in a [Columns] collection. Every
 * [Table] contains one [Columns] instance.
 * @param type column type from metadata in database
 * @param isNullable true if it is, false if not
 */
open class Column(val name: String, val index: Int, val type: ColumnType = ColumnType.String, val isNullable: Boolean = false) {
    var isPrimaryKey: Boolean = false
    var precision = 0
    var scale = 0
    var declaration = ""

    /**
     * Convenience method for creating one instance of the correct SqlValue subclass that matches the
     * type of this column.
     */
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

    /**
     * Simple formatted column metadata string
     */
    override fun toString(): String {
        return "Name: $name, index: $index, type: $type, nullable: $isNullable"
    }
}

/**
 * Manages a collection of Column instances associated with a Table or View
 */
class Columns {
    private val columnsList = emptyList<Column>().toMutableList()

    /**
     * List of Column instances
     */
    val columns get() = columnsList.toList()

    /**
     * Map of column instances keyed by column name.
     */
    private val columnsMap = emptyMap<String, Column>().toMutableMap()

    /**
     * Get a column by name using index operator, throws [IllegalArgumentException] if no name match
     * found.
     * @param name column name
     * @return Column instance for the specified name
     */
    operator fun get(name: String): Column {
        return columnsMap[name] ?: throw IllegalArgumentException("Invalid column name: $name")
    }

    /**
     * Get a column by index using index operator, throws [IllegalArgumentException] if index is
     * out of bounds.
     * @param name column name
     * @return Column instance for the specified name
     */
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

    /**
     * Create a set of named bind arguments for setting values into.
     * @return Bind values collection, one entry for each column.  A SqlValue sealed class instance
     * of the correct type will be created for each column based on its metadata.
     */
    fun defaultBindArguments(): SqlValues {
        return SqlValues().apply {
            columnsList.forEach { add(it.createBindArgument()) }
        }
    }

    /**
     * Simple unformatted list of columns
     */
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
 * Description/metadata for a Table or View and its collection of Columns.  Also offers a number of read-only
 * properties for basic SQL statements and named bind arguments.
 * Table metadata includes name, a collection of Column definitions, and map of properties keyed
 * by name. Can also include information about related indexes, references, and primary key columns.
 * @param name
 * @param systemCatalog optionally true if Table is special case of system catalog.
 */
open class Table(val name: String, val systemCatalog: Boolean = false) {
    private val indexesList = mutableListOf<Index>()
    val indexNames get() = indexesList.map { it.name }
    val indexes get() = indexesList.toList()
    val references = emptyList<Table>().toMutableList()
    val primaryKey = emptyList<Column>().toMutableList()

    /**
     * Columns instance is collection of Column instances containing metadata about each column definition
     */
    val columns = Columns()

    /**
     * Map into columns by name
     */
    val columnNames get() = columns.columns.map { it.name }

    /**
     * Collection of named properties.  For example, in Sqlite's case there are two named properties:
     * rootpage - sqlite's internal page number for start of table, dunno if its ever useful
     * sql - the SQL create statement used to create the table.
     */
    val properties = emptyMap<String, String>().toMutableMap()

    /**
     * Returns a comma separated String of column names
     */
    val selectColumns get() =
        buildString {
            val c = columnNames
            c.forEach { append("$it, ") }
        }.trimEnd(' ', ',')

    /**
     * returns a comma-separated string of columnNames with a ':' in front of each, for use as
     * default bind arguments
     */
    val bindColumns get() = buildString {
        val c = columnNames
        c.forEach { append(":$it, ") }
    }.trimEnd(' ', ',')

    /**
     * Returns a string with a basic select statement, with each column explicitly named. Column
     * names are in same order as Column definitions int the Columns instance.
     */
    val selectSql get() = "select $selectColumns from $name "

    /**
     * returns insert SQL with all columns and in the values clause, matching named bind arguments
     */
    val insertSql get() = "insert into $name ( $selectColumns ) values ( $bindColumns )"

    fun addProperty(name: String, value:String): Table {
        properties[name] = value
        return this
    }

    fun add(index: Index) {
        indexesList.add(index)
    }

    /**
     * Convenience method for executing "select count(*) from $name".
     * @param db open database
     * @return count of rows in table.
     */
    suspend fun rowCount(db: Database): Long {
        var tableCount = 0L
        db.execute("select count(*) from $name") {
            tableCount = it.requireLong(0)
            true
        }
        return tableCount
    }

    /**
     * Dumps table name and column descriptors.
     */
    override fun toString(): String {
        return "  Table: $name, columns: $columns \n  Indexes: $indexNames"
    }

    companion object {
        const val standardSelect = "select * from "
    }
}