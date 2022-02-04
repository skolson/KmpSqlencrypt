//
//  LogonScreen.swift
//  iosApp
//
//  Created by Steve on 2/1/22.
//
// Sample code to explore how the mapping between Swift async/await and Kotlin suspend function works,
// including use of Kotlin suspend lambdas from Swift

import SwiftUI
import KmpSqlencrypt

struct LogonScreen: View {
    var body: some View {
        VStack {
            // Scaffold with title bar, nav, action icons?
            ScrollView {
                VStack(alignment: .leading, spacing: 20) {
                    let user = TextEdit(label: "User", hint: "Enter a user ID")
                    user.body
                    Spacer()
                    AsyncButton("Logon") {
                        await logon(user.text)
                    }
                }
            }
        }
    }
    
    func logon(_ user: String) async {
        let vm = PretendController()
        await vm.logon()
    }
}

class PretendController {
    private(set) var isOpen = ""
    private(set) var userVersion: Int32 = 0
    private(set) var sqliteVersion = ""
    private(set) var sqlcipherVersion = ""
    lazy var db = DatabaseKt.sqlcipher {sql in
        sql.createOk = true
        sql.encoding = SqliteEncoding.utf8
    }
    private(set) var test1Count: Int32 = -1
    private(set) var insertRowid: Int64 = -1
    private(set) var insertArgs1 = SqlValues()
    private(set) var insertArgs2 = SqlValues()
    private(set) var count: Int32 = -1
    private(set) var str: String = ""
    private(set) var isMain: Bool = false

    func logon() async {
        isMain = Thread.isMainThread            // true here
        SqlValueBooleanValue.Companion().mapping(forFalse: "N", forTrue_: "Y")
        do {
            try await openAndQuery()
            isMain = Thread.isMainThread        // false !!
            try await inserts()
            isMain = Thread.isMainThread
            db.close()
        } catch {
            
        }
    }
    
    func openAndQuery() async throws {
        let pwd = Passphrase.init(passphrase: "", isRaw: false, hasSalt: false)
        try await db.open(passphrase: pwd)
        isOpen = db.isOpen.description
        userVersion = db.userVersion
        sqliteVersion = db.sqliteVersion
        sqlcipherVersion = db.sqlcipherVersion
        try await db.execute(sqlScript: "create table test1(id INTEGER PRIMARY KEY, name VARCHAR(255), date1 DATE, dateTime1 DATETIME, num1 DECIMAL(25,3), real1 REAL, dub DOUBLE, long1 BIGINT, bool1 char(1));") { row in
            KotlinBoolean(bool: true)
        }
        isMain = Thread.isMainThread        // still true
        let binds = SqlValues()
        try await db.usingSelect(selectSql: "select count(*) from test1",
                       bindArguments: binds,
                       eachRow: QueryTest1(self)
        )
    }
    
    func inserts() async throws {
        let dt = SqlValueDateValue.Companion().parse(dateString: "2022-01-31", throws: false)!
        let dtTime = SqlValueDateTimeValue.Companion().parse(dateTimeString: "2022-01-31T06:00:00.000", throws: false)!
        let bigD = BignumBigDecimal.Companion().parseString(string: "12345678901234567890.23456")
        self.insertArgs1
            .addValue(name: "name", value: "Any text here")
            .addValue(name: "date1", value: dt)
            .addValue(name: "dateTime1", value: dtTime)
            .addValue(name: "num1", value: bigD)
            .addValue(name: "dub", value: KotlinDouble(value: 123.456))
            .addValue(name: "real1", value: KotlinFloat(value: 789.123))
            .addValue(name: "long1", value: KotlinLong(value: 99988))
            .addValue(name: "bool1", value: KotlinBoolean(bool: true))
        
        self.count = self.insertArgs1.size
        self.str = self.insertArgs1.description()
        self.isMain = Thread.isMainThread
        insertRowid = try await db.useInsert(
            insertSql: "insert into test1(name, date1, dateTime1, num1, real1, dub, long1, bool1) values(:name, :date1, :dateTime1, :num1, :real1, :dub, :long1, :bool1)",
                bindArguments: insertArgs1).int64Value
        
        let vals = [
            "Any row 2 text here",
            dt,
            dtTime,
            bigD,
            KotlinDouble(value: 999.456),
            KotlinFloat(value: 777.123),
            KotlinLong(value: 11111111),
            KotlinBoolean(bool: true)
        ]
        self.insertArgs2 = SqlValues(values: vals)
        self.str = self.insertArgs2.description()
        self.isMain = Thread.isMainThread
        insertRowid = try await db.useInsert(
            insertSql: "insert into test1(name, date1, dateTime1, num1, real1, dub, long1, bool1) values(?, ?, ?, ?, ?, ?, ?, ?)",
                bindArguments: insertArgs2).int64Value
    }
    
    class QueryTest1: KotlinSuspendFunction2 {
        
        var vm: PretendController
        
        init(_ vm: PretendController)  {
            self.vm = vm
        }
        
        func invoke(p1: Any?, p2: Any?) async throws -> Any? {
            if p1 is Int32 && p2 is SqlValues {
                //let rowNumber = p1 as! Int32
                let row = p2 as! SqlValues
                vm.test1Count = row.requireInt(columnIndex: 0, nullZero: false)
            }
            return KotlinBoolean(bool: true)
        }
    }
}
