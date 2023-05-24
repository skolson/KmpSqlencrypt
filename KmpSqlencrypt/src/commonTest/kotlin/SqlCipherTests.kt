package com.oldguy.kiscmp

import com.ionspin.kotlin.bignum.decimal.BigDecimal
import com.oldguy.database.ColumnType
import com.oldguy.database.Passphrase
import com.oldguy.database.SqlValue
import com.oldguy.database.SqlValues
import korlibs.time.DateTime
import kotlin.test.DefaultAsserter.assertEquals
import kotlin.test.DefaultAsserter.assertTrue
import kotlin.test.DefaultAsserter.fail
import kotlin.test.assertNotNull

open class SqlCipherTests {
    val testDate = DateTime.now()
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
        assertEquals("sqliteVersion","3.41.2", db.sqliteVersion)
        assertEquals("sqlcipherVersion","4.5.4 community", db.sqlcipherVersion)
        assertEquals("userVersion", 0, db.userVersion)
    }

    fun pragmaTest() {
        var count = 0
        db.pragma("database_list") {
            count++
            when (count) {
                1 -> {
                    assertEquals("pragmaListSize", 3, it.size)
                    assertEquals("plCol0","seq", it[0].name)
                    assertEquals("plCol1", "name", it[1].name)
                    assertEquals("plCol2", "file", it[2].name)
                    assertEquals("plVal0","0", it.requireString(0))
                    assertEquals("plVal1", "main", it.requireString(1))
                    assertTrue("plVal2", it.requireString(2).isEmpty())
                }
            }
            true
        }
    }

    suspend fun script1Test() {
        var line = 0
        db.execute(create1 + insert1 + countSql) {
            line++
            assertEquals("scriptLine",1, line)
            assertEquals("scriptCol","count(*)", it[0].name)
            assertEquals("scriptCount", 3, it.requireString(0).toInt())
            true
        }
    }

    suspend fun verifyTest1Table() {
        db.catalog.retrieveTables()
        //         const val create1 = "create table test1(id INTEGER PRIMARY KEY, name VARCHAR(255), date1 DATE, dateTime1 DATETIME, num1 DECIMAL(25,3), real1 REAL, dub DOUBLE, long1 BIGINT, bool1 char(1));"
        assertEquals("Test1Size", 1, db.catalog.tables.size)
        val table = db.catalog.tables["test1"] ?: throw IllegalStateException("test1 not in Tables")
        assertEquals("Test1InsertSql",
            "insert into test1 ( id, name, date1, dateTime1, num1, real1, dub, long1, bool1 ) values ( :id, :name, :date1, :dateTime1, :num1, :real1, :dub, :long1, :bool1 )",
            table.insertSql
        )
        assertEquals(
            "Test1SelectSql",
            "select id, name, date1, dateTime1, num1, real1, dub, long1, bool1 from test1 ",
            table.selectSql
        )

        assertEquals("Test1Name","test1", table.name)
        assertEquals("Test1Cols", 9, table.columns.columns.size)
        assertEquals("Test1Col0Name", "id", table.columns[0].name)
        assertEquals("Test1Col0Type", ColumnType.Long, table.columns[0].type)
        assertEquals("Test1Col1Name", "name", table.columns[1].name)
        assertEquals("Test1Col1Type", ColumnType.String, table.columns[1].type)
        assertEquals("Test1Col2Name","date1", table.columns[2].name)
        assertEquals("Test1Col2Type", ColumnType.Date, table.columns[2].type)
        assertEquals("Test1Col3Name", "dateTime1", table.columns[3].name)
        assertEquals("Test1Col3Type", ColumnType.DateTime, table.columns[3].type)

    }

    suspend fun testBindInsert() {
        db.execute(primaryKey)
        db.statement(primaryKeyInsert1).use {
            rowId = it.insert(bindArgs)
            (bindArgs[7] as SqlValue.BooleanValue).value = false
            rowId2 = it.insert(bindArgs)
            assertTrue("bindInsertRowid", rowId > 0)
            assertTrue("bindInsertRowid1", rowId2 > rowId)
            (bindArgs[0] as SqlValue.StringValue).value = testString2
            it.insert(bindArgs)
        }
    }

    suspend fun testSelect() {
        db.usingSelect("select * from test2") {rowCount: Int, sqlValues: SqlValues ->
            assertEquals("selColsSize", 9, sqlValues.count())
            when (rowCount) {
                1 -> {
                    assertEquals("sel1Test2RowId", rowId, sqlValues.requireLong("id"))
                    assertEquals("sel1Test2Name", testString, sqlValues.requireString("name"))
                    assertTrue("sel1Test2Bool", sqlValues.getBoolean("bool1"))
                }
                2 -> {
                    assertEquals("sel2Test2RowId", rowId2, sqlValues.requireLong("id"))
                    assertEquals("sel2Test2Name",testString, sqlValues.requireString("name"))
                    assertTrue("sel2Test2Bool",!sqlValues.getBoolean("bool1"))
                }
                3 -> {
                    assertEquals("sel3Test2RowId", bindArgs["name"].value, sqlValues.requireString("name"))
                    assertEquals("sel3Test2Name",testString2, sqlValues.requireString("name"))
                    assertTrue("sel3Test2Bool",!sqlValues.getBoolean("bool1"))
                    if (mapBooleanYN)
                        assertEquals("sel3Test2BoolN","N", sqlValues.requireString("bool1"))
                    else
                        assertEquals("sel3Test2BoolFalse","false", sqlValues.requireString("bool1"))
                }
                else ->
                    fail("Test2 select should have 3 rows. rowCount: $rowCount")
            }
            assertEquals(
                "selTest2BindSelDateRow$rowCount",
                0,
                bindArgs.requireDate("date1").compareTo(sqlValues.requireDate("date1"))
            )
            assertEquals(
                "selTest2LitSelDateRow$rowCount",
                0,
                testDate.date.compareTo(sqlValues.requireDate("date1"))
            )
            assertEquals(
                "selTest2BindSelDateTimeRow$rowCount",
                0,
                bindArgs.requireDateTime("dateTime1")
                    .compareTo(sqlValues.requireDateTime("dateTime1"))
            )
            assertEquals(
                "selTest2LitSelDateTimeRow$rowCount",
                0,
                testDate.compareTo(sqlValues.requireDateTime("dateTime1"))
            )
            assertEquals(
                "selTest2BigRow$rowCount",
                0,
                testBigDecimalRounded.compareTo(sqlValues.requireDecimal("num1"))
            )
            kotlin.test.assertEquals(
                testDouble,
                sqlValues.requireDouble("dub"),
                0.000005,
                "selTest2DoubleRow$rowCount"
            )
            kotlin.test.assertEquals(
                testFloat,
                sqlValues.requireFloat("real1"),
                0.0005F,
                "selTest2FloatRow$rowCount"
            )
            assertEquals("selTest2LongRow$rowCount", testLong, sqlValues.requireLong("long1"))
            true
        }.also {
            assertEquals("selTest2Rows", 3, it)
        }

        val count2 = db.usingSelect("select * from test2 where name = ?",
            SqlValues(SqlValue.StringValue(value = testString2))) { i: Int, row: SqlValues ->
            assertEquals("selTest2WhereRow1", 1, i)
            assertEquals("selTest2WhereNameval", testString2, row.requireString("name"))
            true
        }
        assertEquals("selTest2WhereCount", 1, count2)
        assertEquals("dbActives1", 0, db.activeStatements.size)
    }

    suspend fun testUnnamedInsert() {
        db.statement(unnamedInsert).use {
            unnamedRowId = it.insert(unnamedArgs)
            assertTrue("uInsertRowId", unnamedRowId > rowId2)
        }
        val count = db.usingSelect(
            "select * from test2 where id = ?",
            SqlValues(listOf<Any>(unnamedRowId))
        ) { rowCount: Int, row: SqlValues ->
            assertEquals("uInsertRowCount", 1, rowCount)
            assertEquals("uInsertRowId", unnamedRowId, row.requireLong("id"))
            assertEquals("uInsertName", unnamedString, row.requireString("name"))
            true
        }
        assertEquals("uInsertRows",1, count)
    }

    suspend fun testUpdate() {
        val newVal = "Any text1 update"
        db.statement(update1).use {
            val args = SqlValues(
                SqlValue.StringValue("name", newVal),
                SqlValue.LongValue("id", 1)
            )
            val count = it.execute(args)
            assertEquals("updateRows", 1, count)
        }
        db.usingSelect("select * from test1") { rowCount: Int, sqlValues: SqlValues ->
            when (sqlValues.requireLong(0)) {
                1L -> {
                    assertEquals("updateRow1Count", 1, rowCount)
                    assertEquals("updateRow1Val", newVal, sqlValues.requireString("name"))
                }
                2L -> {
                    assertEquals("updateRow2Count", 2, rowCount)
                    assertEquals("updateRow2Val", "Any text1 2", sqlValues.requireString("name"))
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
            assertEquals("delRowCount", 1, count)
        }
        var line = 0
        db.execute(drop1) {
            line++
            assertEquals("dropCount", 1, line)
            true
        }
    }

    suspend fun testTableTest2() {
        db.execute(primaryKey) {
            true
        }
        try {
            db.usingSelect("select count(*) from test2") { _, row ->
                assertEquals("Test2EmptySel", 0L, row.requireLong(0))
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
        assertNotNull(testTime)
        val testNow = DateTime.now()
        db.statement(table3Insert).use {
            val row = SqlValues(
                SqlValue.DateTimeValue("dateTime1", testTime),
                SqlValue.DateTimeValue("dateTime2", testDate),
                SqlValue.DateTimeValue("dateTime3", testNow)
            )
            rowId = it.insert(row)
            assertTrue("timestampRowid1", rowId > 0)
            val count = db.usingSelect("select * from $testTbl3")
            { _: Int, sqlValues: SqlValues ->
                assertEquals("timestampVal0", 1L, sqlValues.requireLong(0))
                assertTrue("timestampVal1",testTime.compareTo(sqlValues.requireDateTime(1)) == 0)
                assertTrue("timestampVal2",testDate.compareTo(sqlValues.requireDateTime(2)) == 0)
                assertTrue("timestampVal3",testNow.compareTo(sqlValues.requireDateTime(3)) == 0)
                true
            }
            assertEquals("timestampRows", 1, count)
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
                assertTrue("upgradeVer", version == 0 || version == 1)
                assertEquals("upgradeNewVer", 1, newVersion)
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
                assertTrue("pwdTableDrop", e.fullMessage.contains("no such table"))
            }
            testBindInsert()
            testSelect()
            assertEquals("pwdTables", 1, database.tableCount())
            database.catalog.retrieveTables()
            assertEquals("pwdCatalogTables", 1, database.catalog.tables.size)
            val table = database.catalog.tables[testTbl]
            assertNotNull(table, "pwdTableLookup")
            table.let {
                assertEquals("pwdTableName", testTbl, it.name)
                assertEquals("pwdTablePropsSize",2, it.properties.size)
                assertNotNull(it.properties[SqliteSystemCatalog.columnNames[3]], "pwdTablePropsCol3",)
                assertNotNull(it.properties[SqliteSystemCatalog.columnNames[4]], "pwdTablePropsCol4",)
            }
        }
        db.use(path, Passphrase(badPassphrase),
            invalidPassphrase = { _: SqlCipherDatabase, password: Passphrase ->
                assertEquals("pwdBad", badPassphrase, password.passphrase)
                badPwd = true
            }) {
            assertEquals("pwdBadCount", 1, it.tableCount())
        }
        assertTrue("pwbBadConfirm", badPwd)
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

