package com.oldguy.kiscmp

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.oldguy.database.SqlValue
import org.junit.Test
import org.junit.runner.RunWith
import kotlinx.coroutines.*

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

}