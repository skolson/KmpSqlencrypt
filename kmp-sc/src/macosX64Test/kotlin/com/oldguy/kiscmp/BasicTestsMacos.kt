package com.oldguy.kiscmp

import com.oldguy.database.SqlValue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.DefaultAsserter.assertTrue
import kotlin.test.Test

@ExperimentalCoroutinesApi
class BasicTestsMacos: SqlCipherTests() {

    /**
     * Run all unencrypted basic tests on UTF-8 encoded in-memory DB
     */
    @ExperimentalStdlibApi
    @Test
    fun testOpenClose() {
        db = createDb
        assertTrue("nativeMemoryModel", isExperimentalMM())
        println("testOpenClose start")
        if (mapBooleanYN)
            SqlValue.BooleanValue.mapping("N", "Y")
        runTest {
            db.use("") {
                allTests()
            }
        }
        println("testOpenClose complete")
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
                allTests()
            }
        }
    }
}