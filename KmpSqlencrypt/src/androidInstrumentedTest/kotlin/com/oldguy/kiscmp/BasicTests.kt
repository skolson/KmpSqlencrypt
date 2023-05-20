package com.oldguy.kiscmp

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.oldguy.database.SqlValue
import org.junit.Test
import org.junit.runner.RunWith
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals

@RunWith(AndroidJUnit4::class)
class AndroidJunitTests: SqlCipherTests() {

    /**
     * Run all unencrypted basic tests on UTF-8 encoded in-memory DB
     */
    @ExperimentalStdlibApi
    @Test
    fun testOpenClose() {
        db = createDb
        if (mapBooleanYN)
            SqlValue.BooleanValue.mapping("N", "Y")
        runTest {
            db.use("") {
                allTests()
            }
        }
    }

    /**
     * Run all unencrypted basic tests on UTF-16 big endian encoded in-memory DB
     */
    @Test
    fun testOpenCloseUtf16be() {
        db = createDb16BE
        if (mapBooleanYN)
            SqlValue.BooleanValue.mapping("N", "Y")
        runTest {
            db.use("") {
                assertEquals(SqliteEncoding.Utf16BigEndian, db.queryEncoding())
                allTests()
            }
        }
    }

    /**
     * Run all unencrypted basic tests on UTF-16 little endian encoded in-memory DB
     */
    @Test
    fun testOpenCloseUtf16le() {
        db = createDb16LE
        if (mapBooleanYN)
            SqlValue.BooleanValue.mapping("N", "Y")
        runTest {
            db.use("") {
                assertEquals(SqliteEncoding.Utf16LittleEndian, db.queryEncoding())
                allTests()
            }
        }
    }
}