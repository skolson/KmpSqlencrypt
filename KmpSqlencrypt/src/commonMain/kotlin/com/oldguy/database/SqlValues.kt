package com.oldguy.database

import com.ionspin.kotlin.bignum.decimal.BigDecimal
import com.ionspin.kotlin.bignum.integer.BigInteger
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.atomicfu.atomic
import kotlinx.datetime.format
import kotlinx.datetime.format.FormatStringsInDatetimeFormats
import kotlinx.datetime.format.byUnicodePattern

/**
 * Simple extension to translate a ByteArray to a hex string
 * @param startIndex index in array to start, defaults to zero
 * @param length number of bytes to turn to hex
 * @return String of size [2 * length], all lower case
 * @throws IndexOutOfBoundsException if argument(s) specified are wrong
 */
fun ByteArray.toHex(startIndex:Int = 0, length:Int = size):String {
    val hexChars = "0123456789abcdef"
    val bytes = this
    return buildString(length * 2) {
        for (i in startIndex until (startIndex+length)) {
            append(hexChars[(bytes[i].toInt() and 0xF0).ushr(4)])
            append(hexChars[bytes[i].toInt() and 0x0F])
        }
    }
}

sealed class SqlValue<T>(val name: String, var value: T?): Comparable<SqlValue<T>>
{
    val isNull get() = value == null
    val isNotNull get() = value != null

    fun compareNulls(other: SqlValue<T>): Int {
        if (isNull && other.isNull) return 0
        if (isNull && other.isNotNull) return -1
        if (isNotNull && other.isNull) return 1
        return 2
    }

    companion object {
        const val nullString = "null"
    }

    class StringValue(name: String, value: String? = null) :
        SqlValue<String>(name, value) {

        constructor(value: String? = null): this("", value)

        override fun toString(): String {
            return value ?: nullString
        }

        override fun compareTo(other: SqlValue<String>): Int {
            val rc = compareNulls(other)
            return if (rc > 1)
                value!!.compareTo(other.value!!)
            else rc
        }
    }

    @OptIn(FormatStringsInDatetimeFormats::class)
    class DateValue(name: String, value: LocalDate? = null) :
        SqlValue<LocalDate>(name, value) {

        var format = LocalDate.Format {
            byUnicodePattern(isoFormat)
        }

        constructor(value: LocalDate? = null): this("", value)

        override fun toString(): String {
            return value?.toString() ?: nullString
        }

        override fun compareTo(other: SqlValue<LocalDate>): Int {
            val rc = compareNulls(other)
            return if (rc > 1)
                value!!.compareTo(other.value!!)
            else rc
        }

        companion object {
            const val isoFormat = "yyyy-MM-dd"

            fun parse(dateString: String, throws: Boolean = true): LocalDate? {
                return if (throws) {
                    LocalDate.parse(dateString)
                } else {
                    try {
                        LocalDate.parse(dateString)
                    } catch (e: IllegalArgumentException) {
                        null
                    }
                }
            }
        }
    }

    /**
     * Sqlite datetime handling is based on UTC - no explicit timezone support. So by default,
     * datetimes are converted to UTC strings in Sqlite format, a subset of ISO-8601. The default
     * formatter uses this pattern: "yyyy-MM-ddTHH:mm:ss.SSS". The default formatter used when
     * retrieving datetimes can be changed, as can the formatter on DateTimeValue instances
     *
     *
     */
    @OptIn(FormatStringsInDatetimeFormats::class)
    class DateTimeValue(name: String, value: LocalDateTime? = null) :
        SqlValue<LocalDateTime>(name, value) {
        private val format = isoFormatter

        constructor(value: LocalDateTime? = null): this("", value)

        override fun toString(): String {
            return value?.format(format) ?: nullString
        }

        override fun compareTo(other: SqlValue<LocalDateTime>): Int {
            val rc = compareNulls(other)
            return if (rc > 1)
                value!!.compareTo(other.value!!)
            else rc
        }

        companion object {
            const val isoFormatNoMillis = "yyyy-MM-dd'T'HH:mm:ss"
            const val isoFormat = "$isoFormatNoMillis.SSS"
            val isoFormatter = LocalDateTime.Format {
                byUnicodePattern(isoFormat)
            }
            val isoFormatterNoMillis = LocalDateTime.Format {
                byUnicodePattern(isoFormatNoMillis)
            }

            private val formatsRef = atomic(listOf(isoFormatter, isoFormatterNoMillis))
            private val formatStringsRef = atomic(listOf(isoFormat, isoFormatNoMillis))

            /**
             * List of the formatters currently defined, in the order they are tried when
             * invoking the static parse method
             */
            val validFormats get() = formatsRef.value.toList()
            val validFormatStrings get() = formatStringsRef.value.toList()

            /**
             * Push a new format string onto the stack to be tried.
             * @param format a valid format string like [isoFormat] or [isoFormatNoMillis] or similar
             */
            fun addFormat(format: String) {
                formatsRef.value = listOf(LocalDateTime.Format {
                    byUnicodePattern(format)
                }) + validFormats
                formatStringsRef.value = listOf(format) + validFormatStrings
            }

            /**
             * looks for an existing formatter with the specified pattern, and removes it from the
             * list of formats.
             * @param format string that indicates a formatter to be removed
             * @return true if success, false if none found.
             */
            fun removeFormat(format: String): Boolean {
                val index = formatStringsRef.value.indexOf(format)
                return if (index < 0) false
                else {
                    val list = formatStringsRef.value.toMutableList()
                    list.removeAt(index)
                    formatStringsRef.value = list
                    val l = formatsRef.value.toMutableList()
                    l.removeAt(index)
                    formatsRef.value = l
                    true
                }
            }

            /**
             * Attempt parsing using the current list of formatters, currently [isoFormatter],
             * then [isoFormatterNoMillis]. Whichever works first returns a UTC DateTime.
             * If none work, null is returned. Note that DateTimes are always stored in the
             * database using isoFormatter = yyyy-MM-ddTHH:mm:ss.SSS, but will be retrieved using
             *
             */
            fun parse(dateTimeString: String, throws: Boolean = true): LocalDateTime? {
                var exc: Throwable? = null
                val formats = validFormats
                formats.forEach {
                    try {
                        return it.parse(dateTimeString)
                    } catch (e: IllegalArgumentException) {
                        if (throws)
                            exc = e
                    }
                }
                if (throws && exc != null) {
                    val patterns = buildString {
                        formatStringsRef.value.forEach {
                            append(it)
                            append(", ")
                        }
                    }
                    throw IllegalArgumentException(
                        "Datetime parse failed, value: $dateTimeString, formats: $patterns",
                        exc
                    )
                }
                return null
            }
        }
    }

    class BytesValue(name: String, value: ByteArray? = null) :
        SqlValue<ByteArray>(name, value) {

        constructor(value: ByteArray? = null): this("", value)

        override fun toString(): String {
            return value?.let  {
                if (it.size > 10)
                    "Binary ${it.size} bytes"
                else "0x${it.toHex()}"

            } ?: nullString
        }

        override fun compareTo(other: SqlValue<ByteArray>): Int {
            val rc = compareNulls(other)
            return if (rc > 1) {
                if (value!!.size > other.value!!.size) 1
                else if (value!!.size < other.value!!.size) -1
                else {
                    var b = 0
                    for (i in value!!.indices) {
                        if (value!![i] < other.value!![i]) {b = -1; break }
                        if (value!![i] > other.value!![i]) {b = 1; break }
                    }
                    b
                }

            } else rc
        }
    }

    class BooleanValue(name: String, value: Boolean? = null) :
        SqlValue<Boolean>(name, value) {

        constructor(value: Boolean? = null): this("", value)

        constructor(name: String, dbValue: String?): this(name, false) {
            dbValue?.let {
                if (valueTypeInt.value) {
                    try {
                        value = it.toInt() == trueValue.value.toInt()
                    } catch (e: NumberFormatException) {
                        throw IllegalStateException("Boolean mapping to integers requested, value is not: $value")
                    }
                } else {
                    value = it == trueValue.value
                }
            }
        }

        fun mapToDb(): Any {
            val v = value ?: false
            if (valueTypeInt.value) {
                return if (v) trueValue.value.toInt() else falseValue.value.toInt()
            } else {
                return if (v) trueValue.value else falseValue.value
            }
        }

        override fun toString(): String {
            return value?.let {
                if (it) trueValue.value
                else falseValue.value
            } ?: nullString
        }

        override fun compareTo(other: SqlValue<Boolean>): Int {
            val rc = compareNulls(other)
            return if (rc > 1)
                value!!.compareTo(other.value!!)
            else rc
        }

        companion object {
            private val valueTypeInt = atomic(false)
            val trueValue = atomic(true.toString())
            val falseValue = atomic(false.toString())

            fun mapping(forFalse: Int = 0, forTrue: Int = 1) {
                valueTypeInt.value = true
                trueValue.value = forTrue.toString()
                falseValue.value = forFalse.toString()
            }

            fun mapping(forFalse: String = "false", forTrue: String = "true") {
                valueTypeInt.value = false
                trueValue.value = forTrue
                falseValue.value = forFalse
            }

            internal fun mapFromString(value: String): Boolean {
                return when (value) {
                    trueValue.value -> true
                    falseValue.value -> false
                    else -> throw IllegalStateException("Boolean found $value, must be either ${trueValue.value} or ${falseValue.value}")
                }
            }
        }
    }

    class DecimalValue(name: String, value: BigDecimal? = null) :
        SqlValue<BigDecimal>(name, value) {

        constructor(value: BigDecimal? = null): this("", value)

        override fun toString(): String {
            return value?.toStringExpanded() ?: nullString
        }

        override fun compareTo(other: SqlValue<BigDecimal>): Int {
            val rc = compareNulls(other)
            return if (rc > 1)
                value!!.compareTo(other.value!!)
            else rc
        }

    }

    class BigIntegerValue(name: String, value: BigInteger? = null) :
        SqlValue<BigInteger>(name, value) {

        constructor(value: BigInteger? = null): this("", value)

        override fun toString(): String {
            return value?.toString() ?: nullString
        }

        override fun compareTo(other: SqlValue<BigInteger>): Int {
            val rc = compareNulls(other)
            return if (rc > 1)
                value!!.compareTo(other.value!!)
            else rc
        }

    }

    class LongValue(name: String, value: Long? = null) :
        SqlValue<Long>(name, value) {

        constructor(value: Long? = null): this("", value)

        override fun toString(): String {
            return value?.toString(10) ?: nullString
        }

        override fun compareTo(other: SqlValue<Long>): Int {
            val rc = compareNulls(other)
            return if (rc > 1)
                value!!.compareTo(other.value!!)
            else rc
        }

    }

    enum class IntSize(val length: Int) { Byte(1), Short(2), Integer(4) }

    class IntValue(name: String, value: Int? = null) :
        SqlValue<Int>(name, value) {

        constructor(value: Int? = null): this("", value)

        override fun toString(): String {
            return value?.toString(10) ?: nullString
        }

        override fun compareTo(other: SqlValue<Int>): Int {
            val rc = compareNulls(other)
            return if (rc > 1)
                value!!.compareTo(other.value!!)
            else rc
        }

    }

    class FloatValue(name: String, value: Float? = null) :
        SqlValue<Float>(name, value) {

        constructor(value: Float? = null): this("", value)

        override fun toString(): String {
            return value?.toString() ?: nullString
        }

        override fun compareTo(other: SqlValue<Float>): Int {
            val rc = compareNulls(other)
            return if (rc > 1)
                value!!.compareTo(other.value!!)
            else rc
        }

    }

    class DoubleValue(name: String, value: Double? = null) :
        SqlValue<Double>(name, value) {

        constructor(value: Double? = null): this("", value)

        override fun toString(): String {
            return value?.toString() ?: nullString
        }

        override fun compareTo(other: SqlValue<Double>): Int {
            val rc = compareNulls(other)
            return if (rc > 1)
                value!!.compareTo(other.value!!)
            else rc
        }

    }
}

class SqlValues() : Iterable<SqlValue<out Any>>
{
    private val rowValues = emptyList<SqlValue<out Any>>().toMutableList()
    val size get() = rowValues.size
    val isEmpty:Boolean get() = size == 0
    val isNotEmpty:Boolean get() = size > 0

    private val nameMap = emptyMap<String, SqlValue<out Any>>().toMutableMap()

    constructor(vararg args:SqlValue<out Any>): this() {
        args.forEach {
            add(it)
        }
    }

    /**
     * This is a convenience constructor when using unnamed bind variables.
     * @param values one value of a supported type, for each bind variable.  Order is important, as
     * bind operations are one by index when no names are present. The supported types for the Any
     * are the same as supported by the SqlValue sealed class. Note that the Any cannot be null since
     * null as a bind variable value is not useful ("columnName IS NULL" does not use bind value)
     * If the Any value type does not match one of the SqlValue sealed class types, then an exception
     * is thrown
     */
    constructor(values: List<Any>): this() {
        values.forEach {
            addValue(it)
        }
    }

    fun addValue(value: Any): SqlValues {
        rowValues.add(when (value) {
            is Int -> SqlValue.IntValue(value = value)
            is Long -> SqlValue.LongValue(value = value)
            is String -> SqlValue.StringValue(value = value)
            is LocalDate -> SqlValue.DateValue(value = value)
            is LocalDateTime -> SqlValue.DateTimeValue(value = value)
            is ByteArray -> SqlValue.BytesValue(value = value)
            is BigInteger -> SqlValue.BigIntegerValue(value = value)
            is BigDecimal -> SqlValue.DecimalValue(value = value)
            is Boolean -> SqlValue.BooleanValue(value = value)
            is Float -> SqlValue.FloatValue(value = value)
            is Double -> SqlValue.DoubleValue(value = value)
            else -> throw IllegalArgumentException("Unsupported bind argument value type: $value")
        })
        return this
    }

    fun addValue(name: String, value: Any): SqlValues {
        when (value) {
            is Int -> SqlValue.IntValue(name = name, value = value)
            is Long -> SqlValue.LongValue(name = name, value = value)
            is String -> SqlValue.StringValue(name = name, value = value)
            is LocalDate -> SqlValue.DateValue(name = name, value = value)
            is LocalDateTime -> SqlValue.DateTimeValue(name = name, value = value)
            is ByteArray -> SqlValue.BytesValue(name = name, value = value)
            is BigInteger -> SqlValue.BigIntegerValue(name = name, value = value)
            is BigDecimal -> SqlValue.DecimalValue(name = name, value = value)
            is Boolean -> SqlValue.BooleanValue(name = name, value = value)
            is Float -> SqlValue.FloatValue(name = name, value = value)
            is Double -> SqlValue.DoubleValue(name = name, value = value)
            else -> throw IllegalArgumentException("Unsupported bind argument value type: $value")
        }.apply {
            if (name.isNotEmpty()) nameMap[name] = this
            rowValues.add(this)
        }
        return this
    }

    private fun noSuchColumn(columnName: String): IllegalArgumentException {
        return IllegalArgumentException("No such column $columnName")
    }

    private fun requireGotNull(columnName: String): IllegalArgumentException {
        return IllegalArgumentException("null value encountered for $columnName")
    }

    private fun requireGotNull(columnIndex: Int): IllegalArgumentException {
        return IllegalArgumentException("null value encountered for index $columnIndex")
    }

    private fun numericError(columnName: String, value:String):IllegalArgumentException {
        throw IllegalArgumentException("$columnName type is string, value is not parseable: $value")
    }

    fun isNull(columnIndex: Int): Boolean {
        verifyColumnIndex(columnIndex)
        return rowValues[columnIndex].isNull
    }

    fun isNull(columnName: String): Boolean {
        return nameMap[columnName]?.isNull
            ?: throw noSuchColumn(columnName)
    }

    fun add(vararg colVals: SqlValue<out Any>): SqlValues {
        for (colVal in colVals) {
            rowValues.add(colVal)
            nameMap[colVal.name] = colVal
        }
        return this
    }

    fun addAll(list:List<SqlValue<out Any>>): SqlValues {
        list.forEach { add(it) }
        return this
    }

    fun remove(columnName: String) {
        val value = nameMap[columnName] ?: throw IllegalArgumentException("No such column name: $columnName")
        nameMap.remove(value.name)
        rowValues.remove(value)
    }

    fun remove(index: Int) {
        val value = rowValues[index]
        nameMap.remove(value.name)
        rowValues.removeAt(index)
    }

    fun remove(value: SqlValue<out Any?>) {
        nameMap.remove(value.name)
        rowValues.remove(value)
    }

    fun getValue(columnIndex: Int): SqlValue<out Any> {
        verifyColumnIndex(columnIndex)
        return rowValues[columnIndex]
    }

    fun getValue(columnName: String): SqlValue<out Any> {
        return nameMap[columnName]
            ?: throw noSuchColumn(columnName)
    }

    fun getBoolean(columnIndex: Int): Boolean {
        verifyColumnIndex(columnIndex)
        return getBoolean(rowValues[columnIndex].name)
    }

    fun getBoolean(columnName: String): Boolean {
        return when (val value = getValue(columnName).value) {
            null -> false
            is Boolean -> value
            is String -> SqlValue.BooleanValue.mapFromString(value)
            else -> throw IllegalArgumentException("$columnName is not Boolean")
        }
    }

    fun getInt(columnIndex: Int): Int? {
        verifyColumnIndex(columnIndex)
        return getInt(rowValues[columnIndex].name)
    }

    fun getInt(columnName: String): Int? {
        if (isNull(columnName)) return null
        return when (val value = getValue(columnName).value) {
            is Int -> value
            is Byte -> value.toInt()
            is Short -> value.toInt()
            is String -> {
                try {
                    value.toInt()
                } catch (e:NumberFormatException) {
                    throw numericError(columnName, value)
                }
            }
            is Long -> {
                if (value == value.toInt().toLong())
                    value.toInt()
                else
                    throw IllegalArgumentException("$columnName Long value will lose precision if cast to Int: $value")
            }
            else -> throw IllegalArgumentException("$columnName value is not an Int, Short or Byte. type: ${value!!::class.simpleName}")
        }
    }

    fun requireInt(columnName: String, nullZero: Boolean = false): Int {
        return getInt(columnName)
            ?: if (nullZero) 0 else throw requireGotNull(columnName)
    }

    fun requireInt(columnIndex: Int, nullZero: Boolean = false): Int {
        return getInt(columnIndex)
            ?: if (nullZero) 0 else throw requireGotNull(columnIndex)
    }

    fun requireShort(columnName: String, nullZero: Boolean = false): Short {
        val v = getValue(columnName)
        if (v.isNull) {
            if (nullZero) return 0 else throw requireGotNull(columnName)
        }
        return if (v is SqlValue.IntValue && !v.isNull && (v.value in Short.MIN_VALUE..Short.MAX_VALUE))
            v.value?.toShort() ?: throw IllegalArgumentException("Could not get Short from $columnName, value ${v.value} without precision loss")
        else
            throw IllegalArgumentException("Could not get Short from $columnName, value ${v.value}")
    }

    fun getDecimal(columnIndex: Int): BigDecimal? {
        verifyColumnIndex(columnIndex)
        return getDecimal(rowValues[columnIndex].name)
    }

    fun getDecimal(columnName: String): BigDecimal? {
        if (isNull(columnName)) return null
        return when (val value = getValue(columnName).value) {
            is Int -> BigDecimal.fromInt(value)
            is Byte -> BigDecimal.fromByte(value)
            is Short -> BigDecimal.fromShort(value)
            is Long -> BigDecimal.fromLong(value)
            is Float -> BigDecimal.fromFloat(value)
            is Double -> BigDecimal.fromDouble(value)
            is BigDecimal -> value
            is String -> BigDecimal.parseString(value)
            else -> throw IllegalArgumentException("$columnName is not numeric type, value: $value")
        }
    }

    fun requireDecimal(columnIndex: Int, nullZero: Boolean = false): BigDecimal {
        return getDecimal(columnIndex)
            ?: if (nullZero) BigDecimal.ZERO else throw requireGotNull(columnIndex)
    }

    fun requireDecimal(columnName: String, nullZero: Boolean = false): BigDecimal {
        return getDecimal(columnName)
            ?: if (nullZero) BigDecimal.ZERO else throw requireGotNull(columnName)
    }

    fun getLong(columnIndex: Int): Long? {
        verifyColumnIndex(columnIndex)
        return getLong(rowValues[columnIndex].name)
    }

    fun getLong(columnName: String): Long? {
        if (isNull(columnName)) return null
        return when (val value = getValue(columnName).value) {
            is Int -> value.toLong()
            is Byte -> value.toLong()
            is Short -> value.toLong()
            is Long -> value
            is String -> {
                try {
                    value.toLong()
                } catch (e:NumberFormatException) {
                    throw numericError(columnName, value)
                }
            }
            else -> throw IllegalArgumentException("$columnName is not a Long, Int, Short or Byte, value: $value")
        }
    }

    fun requireLong(columnName: String, nullZero: Boolean = false): Long {
        val v = getLong(columnName)
        return v ?: if (nullZero) 0L else throw requireGotNull(columnName)
    }

    fun requireLong(columnIndex: Int, nullZero: Boolean = false): Long {
        return getLong(columnIndex) ?: if (nullZero) 0L else throw requireGotNull(columnIndex)
    }

    fun getFloat(columnIndex: Int): Float? {
        verifyColumnIndex(columnIndex)
        return getFloat(rowValues[columnIndex].name)
    }

    /**
     * gets a float value for the specified column.  Note that Sqlite does not have a bind function
     * or a column function specifically for Float. This forces Floats to be cast to Doubles, which
     * often adds phantom precision. This combined with [useBigDecimal = true] on the database connection
     * leads to errors when exact precision is requested. So exact value precision retention is not
     * practical. Floats suck at precision retention anyway, if serious about precision, use [BigDecimal].
     */
    fun getFloat(columnName: String): Float? {
        if (isNull(columnName)) return null
        return when (val value = getValue(columnName).value) {
            is Float -> value
            is BigDecimal -> value.floatValue(false)
            is String -> {
                try {
                    value.toFloat()
                } catch (e:NumberFormatException) {
                    throw numericError(columnName, value)
                }
            }
            else -> throw IllegalArgumentException("$columnName is not a Float, value: $value")
        }
    }

    fun requireFloat(columnName: String, nullZero: Boolean = false): Float {
        val v = getFloat(columnName)
        return v ?: if (nullZero) 0.0f else throw requireGotNull(columnName)
    }

    fun getDouble(columnIndex: Int): Double? {
        verifyColumnIndex(columnIndex)
        return getDouble(rowValues[columnIndex].name)
    }

    fun getDouble(columnName: String): Double? {
        if (isNull(columnName)) return null
        return when (val value = getValue(columnName).value) {
            is Float -> value.toDouble()
            is Double -> value
            is BigDecimal -> value.doubleValue(false)
            is String -> {
                try {
                    value.toDouble()
                } catch (e:NumberFormatException) {
                    throw numericError(columnName, value)
                }
            }
            else -> throw IllegalArgumentException("$columnName is not a Float or Double, value: $value")
        }
    }

    fun requireDouble(columnName: String, nullZero: Boolean = false): Double {
        val v = getDouble(columnName)
        return v ?: if (nullZero) 0.0 else throw requireGotNull(columnName)
    }

    fun getDate(columnIndex: Int): LocalDate? {
        verifyColumnIndex(columnIndex)
        if (isNull(columnIndex)) return null
        return when (val value = getValue(columnIndex).value) {
            is LocalDate -> value
            else -> throw IllegalArgumentException("Index $columnIndex is not a Date, value: $value")
        }
    }

    fun getDate(columnName: String): LocalDate? {
        if (isNull(columnName)) return null
        return when (val value = getValue(columnName).value) {
            is LocalDate -> value
            else -> throw IllegalArgumentException("$columnName is not a Date, value: $value")
        }
    }

    fun requireDate(columnIndex: Int): LocalDate {
        return getDate(columnIndex) ?: throw requireGotNull(columnIndex)
    }

    fun requireDate(columnName: String): LocalDate {
        return getDate(columnName) ?: throw requireGotNull(columnName)
    }

    fun getDateTime(columnIndex: Int): LocalDateTime? {
        verifyColumnIndex(columnIndex)
        if (isNull(columnIndex)) return null
        return when (val value = getValue(columnIndex).value) {
            is LocalDateTime -> value
            else -> throw IllegalArgumentException("Index $columnIndex is not a Date, value: $value")
        }
    }

    fun getDateTime(columnName: String): LocalDateTime? {
        if (isNull(columnName)) return null
        return when (val value = getValue(columnName).value) {
            is LocalDateTime -> value
            else -> throw IllegalArgumentException("$columnName is not a DateTime, value: $value")
        }
    }

    fun requireDateTime(columnIndex: Int): LocalDateTime {
        return getDateTime(columnIndex) ?: throw requireGotNull(columnIndex)
    }

    fun requireDateTime(columnName: String): LocalDateTime {
        return getDateTime(columnName) ?: throw requireGotNull(columnName)
    }

    fun getBinary(columnIndex: Int): ByteArray {
        verifyColumnIndex(columnIndex)
        return getBinary(rowValues[columnIndex].name)
    }

    fun getBinary(columnName: String): ByteArray {
        if (isNull(columnName)) return ByteArray(0)
        return when (val value = getValue(columnName).value) {
            is ByteArray -> value
            else -> throw IllegalArgumentException("$columnName is not Binary")
        }
    }

    /**
     * Convenience method for getting a String from most types.  If null on any type, null is returned.
     * String type is returned as is.  DateTime is returned in default format for the locale, Boolean
     * is returned as "true" or "false", never null
     * Attempting this on a binary type throws an exception
     */
    fun getString(columnIndex: Int): String? {
        verifyColumnIndex(columnIndex)
        if (rowValues[columnIndex].isNull) return null
        return rowValues[columnIndex].toString()
    }

    fun getString(columnName: String): String? {
        return if (isNull(columnName)) null else nameMap[columnName]?.toString()
            ?: throw noSuchColumn(columnName)
    }

    fun requireString(columnName: String, nullEmpty:Boolean = false): String {
        val v = getString(columnName)
        return v ?: if (nullEmpty) "" else throw requireGotNull(columnName)
    }

    fun requireString(columnIndex: Int, nullEmpty:Boolean = false): String {
        verifyColumnIndex(columnIndex)
        val v = getString(columnIndex)
        return v ?: if (nullEmpty) "" else throw requireGotNull(columnIndex)
    }

    private fun verifyColumnIndex(columnIndex: Int) {
        if (columnIndex in 0 until rowValues.size)
            return
        throw IllegalArgumentException("Invalid row columnIndex $columnIndex, must be between 0 and ${rowValues.size - 1}")
    }

    override fun toString(): String {
        return buildString {
            append("Row[")
            var count = 0
            if (nameMap.isEmpty())
                rowValues.forEach { append("index=${count++}, value=${it.value?.toString()},") }
            else
                nameMap.forEach { append("name=${it.key}, value=${it.value.value?.toString()},") }
        }.trimEnd(',') + "]"
    }

    override fun iterator(): Iterator<SqlValue<out Any>> {
        return rowValues.iterator()
    }

    operator fun get(i: Int): SqlValue<out Any> {
        return rowValues[i]
    }

    operator fun get(name: String): SqlValue<out Any> {
        return nameMap[name]
            ?: throw IllegalArgumentException("Name $name not in values collection")
    }
}