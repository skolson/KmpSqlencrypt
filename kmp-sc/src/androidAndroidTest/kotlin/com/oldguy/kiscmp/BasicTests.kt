package com.oldguy.kiscmp

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ionspin.kotlin.bignum.decimal.BigDecimal
import com.oldguy.database.ColumnType
import com.oldguy.database.Passphrase
import com.oldguy.database.SqlValue
import com.oldguy.database.SqlValues
import com.soywiz.klock.DateTime
import org.junit.Assert
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlinx.coroutines.*
import kotlin.test.assertNotNull
import kotlin.test.fail

@RunWith(AndroidJUnit4::class)
class SqlcipherTests {
    private val testDate = DateTime.now()
    private val testString = "Any text1 4"
    private val testString2 = "Other text"
    private val testBigDecimal = BigDecimal.parseString("12345678901234567890.98")
    // Sqlite numeric columns only support 8 bytes of storage, about 15 digits of precision
    private val testBigDecimalRounded = BigDecimal.parseString("12345678901234600000.0")
    private val testFloat = 12345.679F
    private val testDouble = 999111.999111
    private val testLong = Long.MAX_VALUE
    private val bool1True = SqlValue.BooleanValue("bool1", true)
    private val bindArgs = SqlValues(
            SqlValue.StringValue("name", testString),
            SqlValue.DateValue("date1", testDate.date),
            SqlValue.DateTimeValue("dateTime1", testDate),
            SqlValue.DecimalValue("num1", testBigDecimal),
            SqlValue.FloatValue("real1", testFloat),
            SqlValue.DoubleValue("dub", testDouble),
            SqlValue.LongValue("long1", testLong),
            bool1True
        )
    private val unnamedString = "Unnamed test"
    private val unnamedArgs = SqlValues(listOf<Any>(
        unnamedString,
        testDate.date,
        testDate,
        testBigDecimal,
        testFloat,
        testDouble,
        testLong,
        false))

    private var rowId: Long = 0
    private var rowId2: Long = 0
    private var unnamedRowId: Long = 0
    private val mapBooleanYN = false


    @Test
    fun openCloseTest() {
        val db = sqlcipher {
            createOk = true
        }
        if (mapBooleanYN)
            SqlValue.BooleanValue.mapping("N", "Y")
        runBlocking {
            db.use("") {
                val cipher = it as SqlCipherDatabase
                Assert.assertEquals("3.36.0", cipher.sqliteVersion)
                Assert.assertEquals("4.5.0 community", cipher.sqlcipherVersion)
                Assert.assertEquals(0, cipher.userVersion)

                pragmaTest(db)
                script1Test(db)
                db.catalog.retrieveTables()
                //         const val create1 = "create table test1(id INTEGER PRIMARY KEY, name VARCHAR(255), date1 DATE, dateTime1 DATETIME, num1 DECIMAL(25,3), real1 REAL, dub DOUBLE, long1 BIGINT, bool1 char(1));"
                assertEquals(1, db.catalog.tables.size)
                val table =
                    db.catalog.tables["test1"] ?: throw IllegalStateException("test1 not in Tables")
                val insertSql = table.insertSql
                assertEquals(
                    "insert into test1 ( id, name, date1, dateTime1, num1, real1, dub, long1, bool1 ) values ( :id, :name, :date1, :dateTime1, :num1, :real1, :dub, :long1, :bool1 )",
                    insertSql
                )
                val selectSql = table.selectSql
                assertEquals(
                    "select id, name, date1, dateTime1, num1, real1, dub, long1, bool1 from test1 ",
                    selectSql
                )

                assertEquals("test1", table.name)
                assertEquals(9, table.columns.columns.size)
                assertEquals("id", table.columns[0].name)
                assertEquals(ColumnType.Long, table.columns[0].type)
                assertEquals("name", table.columns[1].name)
                assertEquals(ColumnType.String, table.columns[1].type)
                assertEquals("date1", table.columns[2].name)
                assertEquals(ColumnType.Date, table.columns[2].type)
                assertEquals("dateTime1", table.columns[3].name)
                assertEquals(ColumnType.DateTime, table.columns[3].type)
                testBindInsert(db)
                testSelect(db)
                Assert.assertEquals(0, cipher.activeStatements.size)
                testUpdate(db)
                testUnnamedInsert(db)
                testDelete(db)
                db.execute(primaryKey) {
                    true
                }
                try {
                it.usingSelect("select count(*) from test2") { _, row ->
                    assertEquals(0, row.requireLong(0))
                    true
                }
                } catch (e: Throwable) {
                    fail(e.message)
                }
                testTimestamps(db)
            }
        }
    }

    @Test
    fun encryption1Test() {
        val dbName = "KeyTest1.db"
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        appContext.deleteDatabase(dbName)
        val path = appContext.getDatabasePath(dbName)
        val passphrase = Passphrase(goodPassphrase)
        var badPwd = false
        val db = sqlcipher {
            createOk = true
            newUserVersion = 1
            userVersionUpgrade = { _: SqlCipherDatabase, version: Int, newVersion: Int ->
                assertTrue(version == 0 || version == 1)
                Assert.assertEquals(1, newVersion)
                false
            }
        }
        runBlocking {
            db.use(path.absolutePath, passphrase,
                invalidPassphrase = { _: SqlCipherDatabase, password: Passphrase ->
                    Assert.fail("Invalid password called incorrectly. Passphrase $passphrase, error passphrase: $password")
                    badPwd = true
                }) {
                testBindInsert(db)
                testSelect(db)
                Assert.assertEquals(1, db.tableCount())
                db.catalog.retrieveTables()
                Assert.assertEquals(1, db.catalog.tables.size)
                val table = db.catalog.tables[testTbl]
                Assert.assertNotNull(table)
                table!!.let {
                    Assert.assertEquals(testTbl, it.name)
                    Assert.assertEquals(2, it.properties.size)
                    Assert.assertNotNull(it.properties[SqliteSystemCatalog.columnNames[3]])
                    Assert.assertNotNull(it.properties[SqliteSystemCatalog.columnNames[4]])
                }
            }
            db.use(path.absolutePath, Passphrase(badPassphrase),
                invalidPassphrase = { _: SqlCipherDatabase, password: Passphrase ->
                    Assert.assertEquals(badPassphrase, password.passphrase)
                    badPwd = true
                }) {
                Assert.assertEquals(1, db.tableCount())
            }
            assertTrue(badPwd)
        }
    }

    private fun pragmaTest(db:SqlCipherDatabase) {
        var count = 0
        db.pragma("database_list") {
            count++
            when (count) {
                1 -> {
                    Assert.assertEquals(3, it.size)
                    Assert.assertEquals("seq", it[0].name)
                    Assert.assertEquals("name", it[1].name)
                    Assert.assertEquals("file", it[2].name)
                    Assert.assertEquals("0", it.requireString(0))
                    Assert.assertEquals("main", it.requireString(1))
                    assertTrue(it.requireString(2).isEmpty())
                }
            }
            true
        }
    }

    private fun script1Test(db:SqlCipherDatabase) {
        var line = 0
        db.execute(create1 + insert1 + countSql) {
            line++
            Assert.assertEquals(1, line)
            Assert.assertEquals("count(*)", it[0].name)
            Assert.assertEquals(3, it.requireString(0).toInt())
            true
        }
    }

    private fun testTimestamps(db:SqlCipherDatabase) = runBlocking {
        SqlValue.DateTimeValue.addFormat("yyyy-MM-dd HH:mm:ss")
        db.execute(createTbl3)
        val str = "2021-07-03 15:21:23"
        val testTime = SqlValue.DateTimeValue.parse(str)
        assertNotNull(testTime)
        val testNow = DateTime.now()
        db.statement(table3Insert).use {
            val row = SqlValues(
                SqlValue.DateTimeValue("dateTime1", testTime),
                SqlValue.DateTimeValue("dateTime2", testDate),
                SqlValue.DateTimeValue("dateTime3", testNow)
            )
            rowId = it.insert(row)
            assert(rowId > 0)
            val count = db.usingSelect("select * from $testTbl3")
            { _: Int, sqlValues: SqlValues ->
                assertEquals(1L, sqlValues.requireLong(0))
                assertTrue(testTime.compareTo(sqlValues.requireDateTime(1)) == 0)
                val x = sqlValues.requireString(2)
                val y = sqlValues.requireDateTime(2)
                Log.e("log", "$x, $y, ${testDate.format(SqlValue.DateTimeValue.isoFormat)}")
                assertTrue(testDate.compareTo(sqlValues.requireDateTime(2)) == 0)
                assertTrue(testNow.compareTo(sqlValues.requireDateTime(3)) == 0)
                true
            }
            assertEquals(1, count)
        }
    }

    private fun testBindInsert(db:SqlCipherDatabase) = runBlocking {
        db.execute(primaryKey)
        db.statement(primaryKeyInsert1).use {
            rowId = it.insert(bindArgs)
            (bindArgs[7] as SqlValue.BooleanValue).value = false
            rowId2 = it.insert(bindArgs)
            assert(rowId > 0)
            assert(rowId2 > rowId)
            (bindArgs[0] as SqlValue.StringValue).value = testString2
            it.insert(bindArgs)
        }
    }

    private fun testUnnamedInsert(db:SqlCipherDatabase) = runBlocking {
        db.statement(unnamedInsert).use {
            unnamedRowId = it.insert(unnamedArgs)
            assert(unnamedRowId > rowId2)
        }
        runBlocking {
            val count = db.usingSelect(
                "select * from test2 where id = ?",
                SqlValues(listOf<Any>(unnamedRowId))
            ) { rowCount: Int, row: SqlValues ->
                assertEquals(1, rowCount)
                Assert.assertEquals(unnamedRowId, row.requireLong("id"))
                assertEquals(unnamedString, row.requireString("name"))
                true
            }
            assertEquals(1, count)
        }
    }

    private fun testUpdate(db:SqlCipherDatabase) {
        val newVal = "Any text1 update"
        runBlocking {
            db.statement(update1).use {
                val args = SqlValues(
                    SqlValue.StringValue("name", newVal),
                    SqlValue.LongValue("id", 1)
                )
                val count = it.execute(args)
                assertEquals(1, count)
            }
            db.usingSelect("select * from test1") { rowCount: Int, sqlValues: SqlValues ->
                when (sqlValues.requireLong(0)) {
                    1L -> {
                        assertEquals(1, rowCount)
                        assertEquals(newVal, sqlValues.requireString("name"))
                    }
                    2L -> {
                        assertEquals(2, rowCount)
                        assertEquals("Any text1 2", sqlValues.requireString("name"))
                    }
                }
                true
            }
        }
    }

    private fun testDelete(db:SqlCipherDatabase) = runBlocking {
        db.statement(delete1).use {
            val args = SqlValues()
            args.add(SqlValue.LongValue("id", 1))
            val count = it.execute(args)
            assertEquals(1, count)
        }
        var line = 0
        db.execute(drop1) {
            line++
            Assert.assertEquals(1, line)
            true
        }
    }

    private fun testSelect(db:SqlCipherDatabase) = runBlocking {
        val count = db.usingSelect("select * from test2") {
                rowCount: Int, sqlValues: SqlValues ->
            Assert.assertEquals(9, sqlValues.count())
            when (rowCount) {
                1 -> {
                    Assert.assertEquals(rowId, sqlValues.requireLong("id"))
                    Assert.assertEquals(testString, sqlValues.requireString("name"))
                    assert(sqlValues.getBoolean("bool1"))
                }
                2 -> {
                    Assert.assertEquals(rowId2, sqlValues.requireLong("id"))
                    Assert.assertEquals(testString, sqlValues.requireString("name"))
                    assert(!sqlValues.getBoolean("bool1"))
                }
                3 -> {
                    Assert.assertEquals(bindArgs["name"].value, sqlValues.requireString("name"))
                    Assert.assertEquals(testString2, sqlValues.requireString("name"))
                    assert(!sqlValues.getBoolean("bool1"))
                    if (mapBooleanYN)
                        assertEquals("N", sqlValues.requireString("bool1"))
                    else
                        assertEquals("false", sqlValues.requireString("bool1"))
                }
            }
            Assert.assertEquals(
                0,
                bindArgs.requireDate("date1").compareTo(sqlValues.requireDate("date1"))
            )
            Assert.assertEquals(0, testDate.date.compareTo(sqlValues.requireDate("date1")))
            Assert.assertEquals(
                0,
                bindArgs.requireDateTime("dateTime1")
                    .compareTo(sqlValues.requireDateTime("dateTime1"))
            )
            Assert.assertEquals(0, testDate.compareTo(sqlValues.requireDateTime("dateTime1")))
            Assert.assertEquals(
                0,
                testBigDecimalRounded.compareTo(sqlValues.requireDecimal("num1"))
            )
            Assert.assertEquals(testDouble, sqlValues.requireDouble("dub"), 0.000005)
            Assert.assertEquals(testFloat, sqlValues.requireFloat("real1"), 0.0005F)
            Assert.assertEquals(testLong, sqlValues.requireLong("long1"))
            true
        }
        Assert.assertEquals(3, count)

        val count2 = db.usingSelect("select * from test2 where name = ?",
            SqlValues(SqlValue.StringValue(value = testString2))) { i: Int, row: SqlValues ->
                assertEquals(1, i)
                assertEquals(testString2, row.requireString("name"))
                true
            }
        assertEquals(1, count2)
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