package com.oldguy.kiscmp

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.oldguy.database.SqlValue
import org.junit.Test
import org.junit.runner.RunWith
import kotlinx.coroutines.*
import kotlin.test.assertEquals

@RunWith(AndroidJUnit4::class)
class AndroidJunitTests: SqlCipherTests() {

    @Test
    fun openCloseTest() {
        db = createDb
        if (mapBooleanYN)
            SqlValue.BooleanValue.mapping("N", "Y")
        runBlocking {
            db.use("") {
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
        }
    }

    @Test
    fun encryption1Test() {
        val dbName = "KeyTest1.db"
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        appContext.deleteDatabase(dbName)
        val path = appContext.getDatabasePath(dbName).absolutePath
        runBlocking {
            testPasswordsAndUpgrade(path)
        }
    }

    @Test
    fun openCloseUtf16leTest() {
        db = createDb16LE
        if (mapBooleanYN)
            SqlValue.BooleanValue.mapping("N", "Y")
        runBlocking {
            db.use("") {
                testVersions()
                assertEquals("UTF-16le", db.encoding.pragma)
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
        }
    }

}