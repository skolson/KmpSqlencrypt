package com.oldguy.kiscmp

import com.ionspin.kotlin.bignum.decimal.BigDecimal
import com.oldguy.database.ColumnType
import com.oldguy.database.Passphrase
import com.oldguy.database.SqlValue
import com.oldguy.database.SqlValues
import kotlin.time.Clock
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.fail

import kotlin.time.ExperimentalTime

/**
 * Extension function returns a new LocalDateTime from the current instance, with nanoseconds value truncated to the
 * millisecond. Since sqlite currently only supports
 * milliseconds, instances of this function will be exactly equal once stored in sqlite and then retrieved. Use when the
 * implicit truncation of sub-milliseconds is not desired, as in Unit Tests etc.
 */
fun LocalDateTime.truncateToMillisecond(): LocalDateTime {
    return LocalDateTime(
        year,
        month,
        day,
        hour,
        minute,
        second,
        (nanosecond / 1000000) * 1000000
    )
}

@OptIn(ExperimentalTime::class)
open class SqlCipherTests {
    val sqlCipherVersion = "4.12.0 community"
    val sqlite3Version = "3.51.1"
    val testDate = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).truncateToMillisecond()
    val testString = "Any text1 4"
    val testString2 = "Other text"
    val testBigDecimal = BigDecimal.parseString("12345678901234567890.98")

    // Sqlite numeric columns only support 8 bytes of storage, about 15 digits of precision
    val testBigDecimalRounded = BigDecimal.parseString("12345678901234600000.0")
    val testFloat = 12345.679F
    val testDouble = 999111.999111
    val testLong = Long.MAX_VALUE
    val bool1True = SqlValue.BooleanValue("bool1", true)
    val bindArgs = SqlValues(
        SqlValue.StringValue("name", testString),
        SqlValue.DateValue("date1", testDate.date),
        SqlValue.DateTimeValue("dateTime1", testDate),
        SqlValue.DecimalValue("num1", testBigDecimal),
        SqlValue.FloatValue("real1", testFloat),
        SqlValue.DoubleValue("dub", testDouble),
        SqlValue.LongValue("long1", testLong),
        bool1True
    )
    val unnamedString = "Unnamed test"
    val unnamedArgs = SqlValues(
        listOf<Any>(
            unnamedString,
            testDate.date,
            testDate,
            testBigDecimal,
            testFloat,
            testDouble,
            testLong,
            false
        )
    )

    var rowId: Long = 0
    var rowId2: Long = 0
    var unnamedRowId: Long = 0
    val mapBooleanYN = false

    val createDb get() = sqlcipher {
        createOk = true
        encoding = SqliteEncoding.Utf8
    }

    val createDb16LE get() = sqlcipher {
        createOk = true
        encoding = SqliteEncoding.Utf16LittleEndian
    }

    val createDb16BE get() = sqlcipher {
        createOk = true
        encoding = SqliteEncoding.Utf16BigEndian
    }

    lateinit var db: SqlCipherDatabase

    suspend fun allTests() {
        testVersions()
        pragmaTest()
        script1Test()
        verifyTest1Table()

        testBindInsert()
        testSelect()

        testUpdate()
        testUnnamedInsert()
        testDelete()

        testTableTest2()
        testTimestamps()
    }

    fun testVersions() {
        assertEquals(sqlite3Version, db.sqliteVersion, "sqliteVersion")
        assertEquals(sqlCipherVersion, db.sqlcipherVersion, "sqlcipherVersion")
        assertEquals(0, db.userVersion, "userVersion")
    }

    fun pragmaTest() {
        var count = 0
        db.pragma("database_list") {
            count++
            when (count) {
                1 -> {
                    assertEquals(3, it.size, "pragmaListSize")
                    assertEquals("seq", it[0].name, "plCol0")
                    assertEquals("name", it[1].name, "plCol1")
                    assertEquals("file", it[2].name, "plCol2")
                    assertEquals("0", it.requireString(0), "plVal0")
                    assertEquals("main", it.requireString(1), "plVal1")
                    assertTrue(it.requireString(2).isEmpty(), "plVal2")
                }
            }
            true
        }
    }

    suspend fun script1Test() {
        var line = 0
        db.execute(create1 + insert1 + countSql) {
            line++
            assertEquals(1, line, "scriptLine")
            assertEquals("count(*)", it[0].name, "scriptCol")
            assertEquals(3, it.requireString(0).toInt(), "scriptCount")
            true
        }
    }

    suspend fun verifyTest1Table() {
        db.catalog.retrieveTables()
        //         const val create1 = "create table test1(id INTEGER PRIMARY KEY, name VARCHAR(255), date1 DATE, dateTime1 DATETIME, num1 DECIMAL(25,3), real1 REAL, dub DOUBLE, long1 BIGINT, bool1 char(1));"
        assertEquals(1, db.catalog.tables.size, "Test1Size")
        val table = db.catalog.tables["test1"] ?: throw IllegalStateException("test1 not in Tables")
        assertEquals(
            "insert into test1 ( id, name, date1, dateTime1, num1, real1, dub, long1, bool1 ) values ( :id, :name, :date1, :dateTime1, :num1, :real1, :dub, :long1, :bool1 )",
            table.insertSql,
            "Test1InsertSql"
        )
        assertEquals(
            "select id, name, date1, dateTime1, num1, real1, dub, long1, bool1 from test1 ",
            table.selectSql,
            "Test1SelectSql"
            )

        assertEquals("test1", table.name, "Test1Name")
        assertEquals(9, table.columns.columns.size, "Test1Cols", )
        assertEquals("id", table.columns[0].name, "Test1Col0Name")
        assertEquals(ColumnType.Long, table.columns[0].type, "Test1Col0Type")
        assertEquals("name", table.columns[1].name, "Test1Col1Name")
        assertEquals(ColumnType.String, table.columns[1].type, "Test1Col1Type")
        assertEquals("date1", table.columns[2].name, "Test1Col2Name")
        assertEquals(ColumnType.Date, table.columns[2].type, "Test1Col2Type")
        assertEquals("dateTime1", table.columns[3].name, "Test1Col3Name")
        assertEquals(ColumnType.DateTime, table.columns[3].type, "Test1Col3Type")

    }

    suspend fun testBindInsert() {
        db.execute(primaryKey)
        db.statement(primaryKeyInsert1).use {
            rowId = it.insert(bindArgs)
            (bindArgs[7] as SqlValue.BooleanValue).value = false
            rowId2 = it.insert(bindArgs)
            assertTrue(rowId > 0, "bindInsertRowid")
            assertTrue(rowId2 > rowId, "bindInsertRowid1")
            (bindArgs[0] as SqlValue.StringValue).value = testString2
            it.insert(bindArgs)
        }
    }

    suspend fun testSelect() {
        db.usingSelect("select * from test2") {rowCount: Int, sqlValues: SqlValues ->
            assertEquals(9, sqlValues.count(), "selColsSize")
            when (rowCount) {
                1 -> {
                    assertEquals(rowId, sqlValues.requireLong("id"), "sel1Test2RowId")
                    assertEquals(testString, sqlValues.requireString("name"), "sel1Test2Name")
                    assertTrue(sqlValues.getBoolean("bool1"), "sel1Test2Bool", )
                }
                2 -> {
                    assertEquals(rowId2, sqlValues.requireLong("id"), "sel2Test2RowId")
                    assertEquals(testString, sqlValues.requireString("name"), "sel2Test2Name",)
                    assertTrue(!sqlValues.getBoolean("bool1"), "sel2Test2Bool")
                }
                3 -> {
                    assertEquals(bindArgs["name"].value, sqlValues.requireString("name"), "sel3Test2RowId")
                    assertEquals(testString2, sqlValues.requireString("name"), "sel3Test2Name")
                    assertTrue(!sqlValues.getBoolean("bool1"), "sel3Test2Bool")
                    if (mapBooleanYN)
                        assertEquals("N", sqlValues.requireString("bool1"), "sel3Test2BoolN",)
                    else
                        assertEquals("false", sqlValues.requireString("bool1"), "sel3Test2BoolFalse")
                }
                else ->
                    fail("Test2 select should have 3 rows. rowCount: $rowCount")
            }
            assertEquals(
                0,
                bindArgs.requireDate("date1").compareTo(sqlValues.requireDate("date1")),
                    "selTest2BindSelDateRow$rowCount"
            )
            assertEquals(
                0,
                testDate.date.compareTo(sqlValues.requireDate("date1")),
                "selTest2LitSelDateRow$rowCount"
                )
            assertEquals(
                bindArgs.requireDateTime("dateTime1").toString(),
                sqlValues.requireDateTime("dateTime1").toString(),
                "selTest2BindSelDateTimeRow$rowCount"
                )
            assertEquals(
                0,
                bindArgs.requireDateTime("dateTime1")
                    .compareTo(sqlValues.requireDateTime("dateTime1")),
                "selTest2BindSelDateTimeRow$rowCount",
                )
            assertEquals(
                0,
                testDate.compareTo(sqlValues.requireDateTime("dateTime1")),
                "selTest2LitSelDateTimeRow$rowCount"
            )
            assertEquals(
                0,
                testBigDecimalRounded.compareTo(sqlValues.requireDecimal("num1")),
                "selTest2BigRow$rowCount"
                )
            assertEquals(
                testDouble,
                sqlValues.requireDouble("dub"),
                0.000005,
                "selTest2DoubleRow$rowCount"
            )
            assertEquals(
                testFloat,
                sqlValues.requireFloat("real1"),
                0.0005F,
                "selTest2FloatRow$rowCount"
            )
            assertEquals(
                testLong,
                sqlValues.requireLong("long1"),
                "selTest2LongRow$rowCount")
            true
        }.also {
            assertEquals(3, it, "selTest2Rows")
        }

        val count2 = db.usingSelect("select * from test2 where name = ?",
            SqlValues(SqlValue.StringValue(value = testString2))) { i: Int, row: SqlValues ->
            assertEquals(1, i, "selTest2WhereRow1")
            assertEquals(testString2, row.requireString("name"), "selTest2WhereNameval")
            true
        }
        assertEquals(1, count2, "selTest2WhereCount")
        assertEquals(0, db.activeStatements.size, "dbActives1")
    }

    suspend fun testUnnamedInsert() {
        db.statement(unnamedInsert).use {
            unnamedRowId = it.insert(unnamedArgs)
            assertTrue(unnamedRowId > rowId2, "uInsertRowId")
        }
        val count = db.usingSelect(
            "select * from test2 where id = ?",
            SqlValues(listOf<Any>(unnamedRowId))
        ) { rowCount: Int, row: SqlValues ->
            assertEquals(1, rowCount, "uInsertRowCount")
            assertEquals(unnamedRowId, row.requireLong("id"), "uInsertRowId")
            assertEquals(unnamedString, row.requireString("name"), "uInsertName")
            true
        }
        assertEquals(1, count, "uInsertRows")
    }

    suspend fun testUpdate() {
        val newVal = "Any text1 update"
        db.statement(update1).use {
            val args = SqlValues(
                SqlValue.StringValue("name", newVal),
                SqlValue.LongValue("id", 1)
            )
            val count = it.execute(args)
            assertEquals(1, count, "updateRows")
        }
        db.usingSelect("select * from test1") { rowCount: Int, sqlValues: SqlValues ->
            when (sqlValues.requireLong(0)) {
                1L -> {
                    assertEquals(1, rowCount, "updateRow1Count")
                    assertEquals(newVal, sqlValues.requireString("name"), "updateRow1Val")
                }
                2L -> {
                    assertEquals(2, rowCount, "updateRow2Count")
                    assertEquals(
                        "Any text1 2",
                        sqlValues.requireString("name"),
                        "updateRow2Val"
                    )
                }
            }
            true
        }
    }

    suspend fun testDelete() {
        db.statement(delete1).use {
            val args = SqlValues()
            args.add(SqlValue.LongValue("id", 1))
            val count = it.execute(args)
            assertEquals(1, count, "delRowCount")
        }
        var line = 0
        db.execute(drop1) {
            line++
            assertEquals(1, line, "dropCount")
            true
        }
    }

    suspend fun testTableTest2() {
        db.execute(primaryKey) {
            true
        }
        try {
            db.usingSelect("select count(*) from test2") { _, row ->
                assertEquals(0L, row.requireLong(0), "Test2EmptySel")
                true
            }
        } catch (e: Throwable) {
            fail(e.message, e)
        }

    }

    suspend fun testTimestamps() {
        SqlValue.DateTimeValue.addFormat("yyyy-MM-dd HH:mm:ss")
        db.execute(createTbl3)
        val str = "2021-07-03 15:21:23"
        val testTime = SqlValue.DateTimeValue.parse(str)
            ?: fail("testTime parse failed")
        assertNotNull(testTime)
        val testNow = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).truncateToMillisecond()
        db.statement(table3Insert).use {
            val row = SqlValues(
                SqlValue.DateTimeValue("dateTime1", testTime),
                SqlValue.DateTimeValue("dateTime2", testDate),
                SqlValue.DateTimeValue("dateTime3", testNow)
            )
            rowId = it.insert(row)
            assertTrue(rowId > 0, "timestampRowid1")
            val count = db.usingSelect("select * from $testTbl3")
            { _: Int, sqlValues: SqlValues ->
                assertEquals(1L, sqlValues.requireLong(0), "timestampVal0")
                assertTrue(testTime.compareTo(sqlValues.requireDateTime(1)) == 0, "timestampVal1",)
                assertTrue(testDate.compareTo(sqlValues.requireDateTime(2)) == 0, "timestampVal2")
                assertTrue(testNow.compareTo(sqlValues.requireDateTime(3)) == 0, "timestampVal3")
                true
            }
            assertEquals(1, count, "timestampRows")
        }
    }

    suspend fun testPasswordsAndUpgrade(dbFolderPath: String) {
        val dbName = "KeyTest1.db"
        val path = "$dbFolderPath/$dbName"
        val passphrase = Passphrase(goodPassphrase)
        var badPwd = false
        db = sqlcipher {
            createOk = true
            newUserVersion = 1
            userVersionUpgrade = { _: SqlCipherDatabase, version: Int, newVersion: Int ->
                assertTrue(version == 0 || version == 1, "upgradeVer")
                assertEquals(1, newVersion, "upgradeNewVer")
                false
            }
        }
        db.use(path, passphrase,
            invalidPassphrase = { _: SqlCipherDatabase, password: Passphrase ->
                badPwd = true
                fail("Invalid password called incorrectly. Passphrase $passphrase, error passphrase: $password")
            }) { database ->
            try {
                database.execute(drop2)
            } catch (e: SqliteException) {
                assertTrue(e.fullMessage.contains("no such table"), "pwdTableDrop")
            }
            testBindInsert()
            testSelect()
            assertEquals(1, database.tableCount(), "pwdTables")
            database.catalog.retrieveTables()
            assertEquals(1, database.catalog.tables.size, "pwdCatalogTables")
            val table = database.catalog.tables[testTbl]
                ?: fail("pwdTableLookup")
            assertNotNull(table, "pwdTableLookup")
            table.let {
                assertEquals(testTbl, it.name, "pwdTableName")
                assertEquals(2, it.properties.size, "pwdTablePropsSize")
                assertNotNull(it.properties[SqliteSystemCatalog.columnNames[3]], "pwdTablePropsCol3",)
                assertNotNull(it.properties[SqliteSystemCatalog.columnNames[4]], "pwdTablePropsCol4",)
            }
        }
        db.use(path, Passphrase(badPassphrase),
            invalidPassphrase = { _: SqlCipherDatabase, password: Passphrase ->
                assertEquals(badPassphrase, password.passphrase, "pwdBad")
                badPwd = true
            }) {
            assertEquals(1, it.tableCount(), "pwdBadCount")
        }
        assertTrue(badPwd, "pwbBadConfirm")
    }

    companion object {
        const val create1 = "create table test1(id INTEGER PRIMARY KEY, name VARCHAR(255), date1 DATE, dateTime1 DATETIME, num1 DECIMAL(25,3), real1 REAL, dub DOUBLE, long1 BIGINT, bool1 char(1));"
        const val insert1 = "insert into test1 (id, name, date1, dateTime1, num1, real1, dub, long1, bool1) values(1, 'Any text1 1', '2020-09-01', '2020-09-01T10:00:00', '12345678912345.25', 12345.678901, 999111.999111, 3, 'Y');" +
                "insert into test1 (id, name, date1, dateTime1, num1, real1, dub, long1, bool1) values(2, 'Any text1 2', '2020-01-01', '2020-01-01T10:00:00', '45678912345.25', 1112345.678901, 11999111.999111, -9223372036854775808, 'N');" +
                "insert into test1 (id, name, date1, dateTime1, num1, real1, dub, long1, bool1) values(3, 'Any text1 3', '2020-02-28', '2020-02-28T10:00:00', '45.25', 12345.678901, 999111.999111, 9223372036854775807, 'Y');"

        const val update1 = "update test1 set name = :name where id = :id;"
        const val delete1 = "delete from test1 where id = :id;"

        const val countSql = "select count(*) from test1;"

        const val testTbl = "test2"
        const val primaryKey = "create table $testTbl(id INTEGER PRIMARY KEY, name VARCHAR(255), date1 DATE, dateTime1 timestamp, num1 DECIMAL(25,3), real1 REAL, dub DOUBLE, long1 BIGINT, bool1 char(1));"
        const val primaryKeyInsert1 = "insert into $testTbl (name, date1, dateTime1, num1, real1, dub, long1, bool1) values(:name, :date1, :dateTime1, :num1, :real1, :dub, :long1, :bool1);"
        const val unnamedInsert = "insert into $testTbl (name, date1, dateTime1, num1, real1, dub, long1, bool1) values(?, ?, ?, ?, ?, ?, ?, ?);"

        const val testTbl3 = "test3"
        const val createTbl3 = "create table $testTbl3(id INTEGER PRIMARY KEY, dateTime1 timestamp, dateTime2 datetime, dateTime3 datetime);"
        const val table3Insert = "insert into $testTbl3 (dateTime1, dateTime2, dateTime3) values(:dateTime1, :dateTime2, :dateTime3);"

        private const val drop2 = "drop table $testTbl;"
        const val drop1 = "drop table test1;$drop2"

        const val goodPassphrase = "Anykey1234!"
        const val badPassphrase = "xxxx"

    }
}

