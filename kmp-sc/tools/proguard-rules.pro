#keep JNI interfaces
-dontwarn java.lang.invoke.StringConcatFactory
-keep class com.oldguy.kiscmp.Sqlite3JniShim { *; }
-keep class com.oldguy.kiscmp.Sqlite3StatementJniShim { *; }
