# Do not let R8 mess with the JNI class, member function names, or name of the member field "handle"
-keepclasseswithmembers public class com.oldguy.sqlcipher.android.** {
    *;
}