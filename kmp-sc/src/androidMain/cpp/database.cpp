#include <jni.h>
#include <string>
#include <sqlite3.h>

/**
 * These "shim" functions were developed under these self-imposed strategic constraints:
 * 1) Minimize logic developed in C++. Only do what is needed for translating over the JNI boundary.
 * 2) Do not require user of these functions to duplicate any SQL constants or have any other
 * direct-to-api references. This enables callers to be kotlin common (multiplatform), where the
 * shims are only used as actual implementations in the JVM and Android-specific targets.
 */
extern "C" {
static const char *throwErrorName = "throwError";
static const char *throwErrorSignature = "(Ljava/lang/String;ILjava/lang/String;)V";
static const char *throwErrorSignature2 = "(Ljava/lang/String;ILjava/lang/String;Ljava/lang/String;)V";
static const char *errorStatementNotOpen = "statement not open";
static const char *apiExpandedSql = "expanded_sql";
static const char *callbackName = "callback";
static const char *callbackSignature = "([Ljava/lang/String;[Ljava/lang/String;)V";
}

/**
 * Enums seem to be problematic with kotlin, I had trouble getting signatures for valueOF function
 * that worked, Steps attempted and bailed on were:
 * 1) GetStaticMethodID valueOf, signature returns java.lang.Enum, not actual enum class
 *      "(Ljava/lang/Class;Ljava/lang/String;)Ljava/lang/Enum;"
 * 2) CallStaticObjectMethod using enum name does return a valid jobject, but can't be decoded
 *  back into actual enum class - SIGABRT occurs.
 *
 *  So worked around the issue by not return enum types from external functions.
 */

/*
 * Intended to be a singleton, and contain essentially static lookups that only happen once.  Since
 * overhead of these are low, to avoid leaking this reference, it is allocated and initialized at
 * database open, and deleted at database close.  a reference counter is implemented to support
 * multiple simultaneous opens.
 * Note that jclass instances are not intended to be global by default.  The String class, the
 * Sqlite3JniShim class, and the Sqlite3StatementJniShim class are all kept as global refs.  If
 * for some ungodly reason these classes are ever unloaded/reloaded, this stuff breaks.
 */
class SqliteEnvironment {
public:
    jclass shimClass = nullptr;
    jclass stringClass = nullptr;
    jfieldID handleField = nullptr;
    jmethodID errorMethod = nullptr;
    jclass statementClass = nullptr;
    jfieldID statementHandleField = nullptr;
    jmethodID statementErrorMethod = nullptr;
    jmethodID statementErrorMethod2 = nullptr;

    void setDbStatics(JNIEnv *env, jclass dbClass) {
        if (shimClass != nullptr)
            env->DeleteGlobalRef(shimClass);
        shimClass = reinterpret_cast<jclass>(env->NewGlobalRef(dbClass));
        if (stringClass != nullptr)
            env->DeleteGlobalRef(stringClass);
        jclass temp = env->FindClass("java/lang/String");
        stringClass = reinterpret_cast<jclass>(env->NewGlobalRef(temp));
        handleField = env->GetFieldID(shimClass, "handle", "J");
        if (handleField == nullptr) return;
        errorMethod = env->GetMethodID(shimClass,
                                       throwErrorName,
                                       throwErrorSignature);
    }

    void setStatement(JNIEnv *env, jclass stmtClass) {
        if (statementClass != nullptr)
            env->DeleteGlobalRef(statementClass);
        statementClass = reinterpret_cast<jclass>(env->NewGlobalRef(stmtClass));
        statementHandleField = env->GetFieldID(statementClass, "handle", "J");
        statementErrorMethod = env->GetMethodID(statementClass,
                                                throwErrorName,
                                                throwErrorSignature);
        statementErrorMethod2 = env->GetMethodID(statementClass,
                                                throwErrorName,
                                                throwErrorSignature2);
    }
};

extern "C" {
static SqliteEnvironment *pShimEnv = nullptr;

JNIEXPORT void JNICALL
Java_com_oldguy_kiscmp_Sqlite3JniShim_nativeInit(JNIEnv *env, jclass clazz) {
    if (pShimEnv == nullptr) {
        pShimEnv = new SqliteEnvironment();
    }
    pShimEnv->setDbStatics(env, clazz);
}

JNIEXPORT void JNICALL
Java_com_oldguy_kiscmp_Sqlite3StatementJniShim_nativeInit(JNIEnv *env, jclass clazz) {
    if (pShimEnv == nullptr) {
        pShimEnv = new SqliteEnvironment();
    }
    pShimEnv->setStatement(env, clazz);
}

jstring emptyString(JNIEnv *env) {
    return env->NewStringUTF("");
}

jstring getJString(JNIEnv *env, const char *pString) {
    if (pString != nullptr) {
        return env->NewStringUTF(pString);
    }
    return emptyString(env);
}

void throw_exception(
        JNIEnv *env,
        jobject thiz,
        const char *api,
        int errcode,
        const char *sqliteMessage) {
    jstring apiStr = getJString(env, api);
    jstring msgStr = getJString(env, sqliteMessage);
    env->CallVoidMethod(thiz, pShimEnv->errorMethod, apiStr, errcode, msgStr);
}

sqlite3 *getDb(JNIEnv *env, jobject thiz) {
    if (pShimEnv == nullptr) {
        return nullptr;
    }
    return (sqlite3 *) env->GetLongField(thiz, pShimEnv->handleField);
}

void throw_statement_exception(
        JNIEnv *env,
        jobject thiz,
        const char *api,
        int errcode,
        const char *sqliteMessage) {
    jstring apiStr = getJString(env, api);
    jstring msgStr = getJString(env, sqliteMessage);
    env->CallVoidMethod(thiz, pShimEnv->statementErrorMethod, apiStr, errcode, msgStr);
}

void throw_statement_exception2(
        JNIEnv *env,
        jobject thiz,
        const char *api,
        int errcode,
        const char *sqliteMessage,
        const char *sqliteErrorText) {
    jstring apiStr = getJString(env, api);
    jstring msgStr = getJString(env, sqliteMessage);
    jstring msg2Str = getJString(env, sqliteErrorText);
    env->CallVoidMethod(thiz, pShimEnv->statementErrorMethod2, apiStr, errcode, msgStr, msg2Str);
}

/**
 * Returns the current statement context pointer, or nullPtr if none.
 * @param env
 * @param thiz must be an instance of Sqlite3StatementJniShim
 * @return statement context pointer or nullptr
 */
sqlite3_stmt *getStatement(JNIEnv *env, jobject thiz, const char *apiName) {
    auto pStmt = (sqlite3_stmt *) env->GetLongField(thiz, pShimEnv->statementHandleField);
    if (pStmt == nullptr) {
        throw_statement_exception(env, thiz, apiName, -1, errorStatementNotOpen);
    }
    return pStmt;
}

JNIEXPORT jstring JNICALL
Java_com_oldguy_kiscmp_Sqlite3JniShim_fileName(JNIEnv *env, jobject thiz) {
    auto *handle = getDb(env, thiz);
    if (handle == nullptr) return emptyString(env);
    return getJString(env, sqlite3_db_filename(handle, "main"));
}

JNIEXPORT void JNICALL
Java_com_oldguy_kiscmp_Sqlite3JniShim_sleep([[maybe_unused]] JNIEnv *env,
                                               [[maybe_unused]] jobject thiz,
                                               jint millis) {
    sqlite3_sleep(millis);
}

JNIEXPORT jint JNICALL
Java_com_oldguy_kiscmp_Sqlite3JniShim_open(JNIEnv *env,
                                              jobject thiz,
                                              jstring path,
                                              jboolean read_only,
                                              jboolean create_ok) {
    if (pShimEnv == nullptr) return -1;
    if (pShimEnv->handleField == nullptr) return -2;
    if (pShimEnv->errorMethod == nullptr) return -3;

    const char *path8 = env->GetStringUTFChars(path, nullptr);
    int sqliteFlags = SQLITE_OPEN_READWRITE;
    if (read_only == JNI_TRUE)
        sqliteFlags = SQLITE_OPEN_READONLY;
    if (create_ok == JNI_TRUE)
        sqliteFlags += SQLITE_OPEN_CREATE;
    sqlite3 *handle = nullptr;
    int err = sqlite3_open_v2(path8, &handle, sqliteFlags, nullptr);
    if (err != SQLITE_OK) {
        const char *msg = path8;
        if (handle != nullptr) {
            msg = sqlite3_errmsg(handle);
        }
        throw_exception(env, thiz, "open_v2", err, msg);
        goto done;
    }
    env->SetLongField(thiz, pShimEnv->handleField, (intptr_t) handle);

    done:
    if (path8 != nullptr) env->ReleaseStringUTFChars(path, path8);
    return 0;
}

JNIEXPORT jint JNICALL
Java_com_oldguy_kiscmp_Sqlite3JniShim_close(JNIEnv *env, jobject thiz) {
    auto *handle = getDb(env, thiz);
    int result = 0;
    if (handle != nullptr) {
        result = sqlite3_close(handle);
        if (result == SQLITE_OK) {
            env->SetLongField(thiz, pShimEnv->handleField, 0L);
        }
    }
    return result;
}

JNIEXPORT jstring JNICALL
Java_com_oldguy_kiscmp_Sqlite3JniShim_error(JNIEnv *env, jobject thiz) {
    auto *handle = getDb(env, thiz);
    if (handle == nullptr)
        return emptyString(env);
    const char *err = sqlite3_errmsg(handle);
    return getJString(env, err);
}

JNIEXPORT jlong JNICALL
Java_com_oldguy_kiscmp_Sqlite3JniShim_softHeapLimit([[maybe_unused]] JNIEnv *env,
                                                       [[maybe_unused]] jobject thiz,
                                                       jlong limit) {
    return sqlite3_soft_heap_limit64(limit);
}

JNIEXPORT void JNICALL
Java_com_oldguy_kiscmp_Sqlite3JniShim_busyTimeout(JNIEnv *env, jobject thiz, jint timeout) {
    auto *handle = getDb(env, thiz);
    if (handle != nullptr)
        sqlite3_busy_timeout(handle, timeout);
}

/**
 * Determines info required for calling back to a lambda with a specified argument signature
 * this broke with kotlin 1.5.31 and java 11 - release dependent. Signature changed, and when
 * lambda found with new signature, attempting to call it caused SIGABRT
 * @param env current JNI environment
 * @param callback object owning the lambda to be called
 * @param signature parameter signature string of the lambda
 * @return callbackInfo struct that contains the calling object and the method ID of the lambda for
 * that object.
callbackInfo *getCallback(JNIEnv *env, jobject callback, const char *signature) {
    jclass klass = env->GetObjectClass(callback);
    jmethodID classMethodId = env->GetMethodID(klass,"getClass","()Ljava/lang/Class;");
    jobject klassObj = env->CallObjectMethod(callback, classMethodId);
    auto klassObject = env->GetObjectClass(klassObj);
    auto nameMethodId = env->GetMethodID(klassObject,"getName","()Ljava/lang/String;");
    auto classString = (jstring) env->CallObjectMethod(klassObj, nameMethodId);
    auto className = env->GetStringUTFChars(classString, nullptr);
    std::string s = className;
    std::replace(s.begin(), s.end(), '.', '/');
    jclass lambdaClass = env->FindClass(s.c_str());
    jmethodID lambdaId = env->GetMethodID(lambdaClass, "invoke", signature);
    env->ReleaseStringUTFChars(classString, className);
    auto *info = new callbackInfo;
    info->env = env;
    info->callback = callback;
    info->lambdaId = lambdaId;
    return info;
}
 */

struct CallbackEnv {
    JNIEnv *env;
    jobject thiz;
};

int execCallback(void *pInfoIn, int numColumns, char **results, char **columnNames) {
    auto *pInfo = static_cast<CallbackEnv *>(pInfoIn);
    auto *env = pInfo->env;
    auto resultArray = env->NewObjectArray(numColumns, pShimEnv->stringClass, nullptr);
    for (int i = 0; i < numColumns; i++) {
        jstring str = getJString(env, results[i]);
        env->SetObjectArrayElement(resultArray, i, str);
    }
    auto columnArray = env->NewObjectArray(numColumns, pShimEnv->stringClass, nullptr);
    for (int i = 0; i < numColumns; i++) {
        jstring str = getJString(env, columnNames[i]);
        env->SetObjectArrayElement(columnArray, i, str);
    }
    auto callbackMethod = env->GetMethodID(pShimEnv->shimClass,
                                      callbackName,
                                      callbackSignature);
    env->CallVoidMethod(
            pInfo->thiz,
            callbackMethod,
            resultArray,
            columnArray);
    return 0;
}

JNIEXPORT jint JNICALL
Java_com_oldguy_kiscmp_Sqlite3JniShim_exec(
        JNIEnv *env,
        jobject thiz,
        jstring sql) {
    auto *handle = getDb(env, thiz);
    if (handle == nullptr) return -1;
    const char *sql8 = env->GetStringUTFChars(sql, nullptr);
    char *errorText = nullptr;
    auto *info = new CallbackEnv;
    info->env = env;
    info->thiz = reinterpret_cast<jobject>(env->NewGlobalRef(thiz));
    int rc = sqlite3_exec(handle, sql8, execCallback, info, &errorText);
    if (rc != SQLITE_OK && rc != SQLITE_ABORT) {
        throw_exception(env, info->thiz, "exec", rc, errorText);
    }
    if (errorText != nullptr)
        sqlite3_free(errorText);
    env->ReleaseStringUTFChars(sql, sql8);
    env->DeleteGlobalRef(info->thiz);
    return rc;
}


JNIEXPORT jlong JNICALL
Java_com_oldguy_kiscmp_Sqlite3JniShim_lastInsertRowid(JNIEnv *env, jobject thiz) {
    auto *handle = getDb(env, thiz);
    if (handle == nullptr) return -1;
    return sqlite3_last_insert_rowid(handle);
}

/**
 * Return the current version. Turns a char array into null terminated string and converts that to jstring
 * @param env
 * @param _
 * @return
 */
JNIEXPORT jstring JNICALL
Java_com_oldguy_kiscmp_Sqlite3JniShim_version(JNIEnv *env, [[maybe_unused]] jobject thiz) {
    int sz = sizeof(SQLITE_VERSION);
    char *wrk = new char(sz + 1);
    memcpy(wrk, SQLITE_VERSION, sz);
    wrk[sz] = 0;
    jstring s = getJString(env, wrk);
    delete wrk;
    return s;
}

JNIEXPORT jint JNICALL
Java_com_oldguy_kiscmp_Sqlite3StatementJniShim_parameterCount(JNIEnv *env, jobject thiz) {
    sqlite3_stmt *pStmt = getStatement(env, thiz, "bind_parameter_count");
    if (pStmt == nullptr) return 0;
    return sqlite3_bind_parameter_count(pStmt);
}

JNIEXPORT jboolean JNICALL
Java_com_oldguy_kiscmp_Sqlite3StatementJniShim_isReadOnly(JNIEnv *env, jobject thiz) {
    sqlite3_stmt *pStmt = getStatement(env, thiz, "sqlite3_stmt_readonly");
    if (pStmt == nullptr) return JNI_FALSE;
    return sqlite3_stmt_readonly(pStmt);
}

JNIEXPORT jint JNICALL
Java_com_oldguy_kiscmp_Sqlite3StatementJniShim_prepare(JNIEnv *env, jobject thiz,
                                                          jlong db_handle, jstring sql) {
    auto *handle = (sqlite3 *) db_handle;
    if (handle == nullptr) {
        throw_statement_exception(env, thiz, "prepare_v2", -1, "No open database");
        return -1;
    }
    int bytesLength = env->GetStringLength(sql) * 2;
    auto *pSql = env->GetStringChars(sql, nullptr);

    sqlite3_stmt *pStmt = nullptr;
    int result = sqlite3_prepare16_v2(handle, pSql, bytesLength, &pStmt, nullptr);
    if (result != SQLITE_OK) {
        const char *err = sqlite3_errstr(result);
        const char *errMsg = sqlite3_errmsg(handle);
        throw_statement_exception2(env, thiz, "prepare_v2", result, err, errMsg);
        goto done;
    }
    env->SetLongField(thiz, pShimEnv->statementHandleField, (intptr_t) pStmt);
done:
    env->ReleaseStringChars(sql, pSql);
    return result;
}

JNIEXPORT jint JNICALL
Java_com_oldguy_kiscmp_Sqlite3StatementJniShim_bindIndex(JNIEnv *env, jobject thiz,
                                                            jstring name) {
    auto pStmt = getStatement(env, thiz, "bind_parameter_index");
    if (pStmt == nullptr) {
        return -1;
    }
    const char *pName = env->GetStringUTFChars(name, nullptr);
    int result = sqlite3_bind_parameter_index(pStmt, pName);
    env->ReleaseStringUTFChars(name, pName);
    return result;
}

JNIEXPORT jint JNICALL
Java_com_oldguy_kiscmp_Sqlite3StatementJniShim_bindNull(JNIEnv *env, jobject thiz, jint index) {
    auto pStmt = getStatement(env, thiz, "bind_null");
    if (pStmt == nullptr) {
        return -1;
    }
    return sqlite3_bind_null(pStmt, index);
}

JNIEXPORT jint JNICALL
Java_com_oldguy_kiscmp_Sqlite3StatementJniShim_bindText(JNIEnv *env, jobject thiz, jint index,
                                                           jstring text) {
    auto pStmt = getStatement(env, thiz, "bind_text");
    if (pStmt == nullptr) {
        return -1;
    }
    int bytesLength = env->GetStringLength(text) * 2;
    auto *pValue = env->GetStringChars(text, nullptr);
    int result = sqlite3_bind_text16(pStmt, index, pValue, bytesLength, SQLITE_TRANSIENT);
    env->ReleaseStringChars(text, pValue);
    return result;
}

JNIEXPORT jint JNICALL
Java_com_oldguy_kiscmp_Sqlite3StatementJniShim_bindInt(JNIEnv *env, jobject thiz, jint index,
                                                          jint value) {
    auto pStmt = getStatement(env, thiz, "bind_int");
    if (pStmt == nullptr) {
        return -1;
    }
    return sqlite3_bind_int(pStmt, index, value);
}

JNIEXPORT jint JNICALL
Java_com_oldguy_kiscmp_Sqlite3StatementJniShim_bindLong(JNIEnv *env, jobject thiz, jint index,
                                                           jlong value) {
    auto pStmt = getStatement(env, thiz, "bind_int64");
    if (pStmt == nullptr) {
        return -1;
    }
    return sqlite3_bind_int64(pStmt, index, value);
}

JNIEXPORT jint JNICALL
Java_com_oldguy_kiscmp_Sqlite3StatementJniShim_bindDouble(JNIEnv *env, jobject thiz, jint index,
                                                             jdouble value) {
    auto pStmt = getStatement(env, thiz, "bind_double");
    if (pStmt == nullptr) {
        return -1;
    }
    return sqlite3_bind_double(pStmt, index, value);
}

JNIEXPORT jint JNICALL
Java_com_oldguy_kiscmp_Sqlite3StatementJniShim_bindBytes(JNIEnv *env, jobject thiz, jint index,
                                                            jbyteArray array) {
    auto pStmt = getStatement(env, thiz, "bind_blob");
    if (pStmt == nullptr) {
        return -1;
    }
    int len = env->GetArrayLength(array);
    auto *buf = new unsigned char[len];
    env->GetByteArrayRegion(array, 0, len, reinterpret_cast<jbyte *>(buf));
    int result = sqlite3_bind_blob(pStmt, index, buf, len, SQLITE_TRANSIENT);
    delete[] buf;
    return result;
}

/**
 * Does a step, translates the returned reslt to an int that is to be decoded by the JniShim.
 * @param env
 * @param thiz
 * @return  1 - Error
 *          2 - SQLITE_DONE
 *          3 - SQLITE_ROW
 *          4 - SQLITE_BUSY
 */
JNIEXPORT jint JNICALL
Java_com_oldguy_kiscmp_Sqlite3StatementJniShim_stepInt(JNIEnv *env, jobject thiz) {
    auto pStmt = getStatement(env, thiz, "step");
    if (pStmt != nullptr) {
        int result = sqlite3_step(pStmt);
        if (result == SQLITE_DONE) return 2;
        if (result == SQLITE_ROW) return 3;
        if (result == SQLITE_BUSY) return 4;
    }
    return 1;
}

JNIEXPORT jint JNICALL
Java_com_oldguy_kiscmp_Sqlite3StatementJniShim_changes(JNIEnv *env, jobject thiz, jlong db_handle) {
    auto *handle = (sqlite3 *) db_handle;
    if (handle == nullptr) return -1;
    return sqlite3_changes(handle);
}

JNIEXPORT jint JNICALL
Java_com_oldguy_kiscmp_Sqlite3StatementJniShim_finalize(JNIEnv *env, jobject thiz) {
    auto pStmt = getStatement(env, thiz, "finalize");
    if (pStmt != nullptr) {
        env->SetLongField(thiz, pShimEnv->statementHandleField, 0);
        return sqlite3_finalize(pStmt);
    }
    return -1;
}

JNIEXPORT void JNICALL
Java_com_oldguy_kiscmp_Sqlite3StatementJniShim_clearBindings(JNIEnv *env, jobject thiz) {
    auto pStmt = getStatement(env, thiz, "clear_bindings");
    if (pStmt != nullptr) {
        sqlite3_clear_bindings(pStmt);
    }
}

JNIEXPORT void JNICALL
Java_com_oldguy_kiscmp_Sqlite3StatementJniShim_reset(JNIEnv *env, jobject thiz) {
    auto pStmt = getStatement(env, thiz, "reset");
    if (pStmt != nullptr) {
        sqlite3_reset(pStmt);
    }
}

JNIEXPORT jstring JNICALL
Java_com_oldguy_kiscmp_Sqlite3StatementJniShim_expandedSql(JNIEnv *env, jobject thiz) {
    auto pStmt = getStatement(env, thiz, apiExpandedSql);
    if (pStmt != nullptr) {
        return getJString(env, sqlite3_expanded_sql(pStmt));
    }
    return emptyString(env);
}

JNIEXPORT jboolean JNICALL
Java_com_oldguy_kiscmp_Sqlite3StatementJniShim_isBusy(JNIEnv *env, jobject thiz) {
    auto pStmt = getStatement(env, thiz, "busy");
    if (pStmt != nullptr) {
        int rc = sqlite3_stmt_busy(pStmt);
        if (rc)
            return JNI_TRUE;
        return JNI_FALSE;
    }
    return JNI_FALSE;
}

JNIEXPORT jint JNICALL
Java_com_oldguy_kiscmp_Sqlite3StatementJniShim_columnCount(JNIEnv *env, jobject thiz) {
    auto pStmt = getStatement(env, thiz, "column_count");
    if (pStmt != nullptr) {
        return sqlite3_column_count(pStmt);
    }
    return -1;
}

JNIEXPORT jint JNICALL
Java_com_oldguy_kiscmp_Sqlite3StatementJniShim_dataCount(JNIEnv *env, jobject thiz) {
    auto pStmt = getStatement(env, thiz, "data_count");
    if (pStmt != nullptr) {
        return sqlite3_data_count(pStmt);
    }
    return -1;
}

JNIEXPORT jstring JNICALL
Java_com_oldguy_kiscmp_Sqlite3StatementJniShim_columnName(JNIEnv *env, jobject thiz,
                                                             jint index) {
    auto pStmt = getStatement(env, thiz, "column_name");
    if (pStmt != nullptr) {
        return getJString(env, sqlite3_column_name(pStmt, index));
    }
    return emptyString(env);
}

JNIEXPORT jstring JNICALL
Java_com_oldguy_kiscmp_Sqlite3StatementJniShim_columnDeclaredType(JNIEnv *env, jobject thiz,
                                                                     jint index) {
    auto pStmt = getStatement(env, thiz, "column_decltype");
    if (pStmt != nullptr) {
        return getJString(env, sqlite3_column_decltype(pStmt, index));
    }
    return emptyString(env);
}

/**
 * Returns an int indicating the column type, to be decoded by the JniShim
 * @param env
 * @param thiz
 * @param index
 * @return  1 - SQLITE_NULL
 *          2 - SQLITE_TEXT
 *          3 - SQLITE_INTEGER
 *          4 - SQLITE_FLOAT
 *          5 - SQLITE_BLOB
 */
JNIEXPORT jint JNICALL
Java_com_oldguy_kiscmp_Sqlite3StatementJniShim_columnTypeInt(JNIEnv *env, jobject thiz,
                                                                jint index) {
    auto pStmt = getStatement(env, thiz, "column_decltype");
    if (pStmt != nullptr) {
        int ct = sqlite3_column_type(pStmt, index);
        if (ct == SQLITE_NULL) return 1;
        if (ct == SQLITE_TEXT) return 2;
        if (ct == SQLITE_INTEGER) return 3;
        if (ct == SQLITE_FLOAT) return 4;
        if (ct == SQLITE_BLOB) return 5;
        throw_statement_exception(env, thiz, "column_type", -1, "Unsupported column type");
    }
    return 0;
}

JNIEXPORT jstring JNICALL
Java_com_oldguy_kiscmp_Sqlite3StatementJniShim_columnText(JNIEnv *env, jobject thiz,
                                                             jint index) {
    auto pStmt = getStatement(env, thiz, "column_text");
    if (pStmt != nullptr) {
        int len = sqlite3_column_bytes16(pStmt, index);
        int charsLen = len / 2;
        return env->NewString(static_cast<const jchar *>(sqlite3_column_text16(pStmt, index)), charsLen);
    }
    return emptyString(env);
}

JNIEXPORT jint JNICALL
Java_com_oldguy_kiscmp_Sqlite3StatementJniShim_columnInt(JNIEnv *env, jobject thiz, jint index) {
    auto pStmt = getStatement(env, thiz, "column_int");
    if (pStmt != nullptr) {
        return sqlite3_column_int(pStmt, index);
    }
    return 0;
}

JNIEXPORT jlong JNICALL
Java_com_oldguy_kiscmp_Sqlite3StatementJniShim_columnLong(JNIEnv *env, jobject thiz,
                                                             jint index) {
    auto pStmt = getStatement(env, thiz, "column_int64");
    if (pStmt != nullptr) {
        return sqlite3_column_int64(pStmt, index);
    }
    return 0;
}

JNIEXPORT jdouble JNICALL
Java_com_oldguy_kiscmp_Sqlite3StatementJniShim_columnDouble(JNIEnv *env, jobject thiz,
                                                               jint index) {
    auto pStmt = getStatement(env, thiz, "column_int64");
    if (pStmt != nullptr) {
        return sqlite3_column_double(pStmt, index);
    }
    return 0;
}

JNIEXPORT jbyteArray JNICALL
Java_com_oldguy_kiscmp_Sqlite3StatementJniShim_columnBlob(JNIEnv *env, jobject thiz,
                                                             jint index) {
    auto pStmt = getStatement(env, thiz, "column_int64");
    if (pStmt != nullptr) {
        const auto *pBlob = static_cast<const signed char *>(sqlite3_column_blob(pStmt, index));
        int count = sqlite3_column_bytes(pStmt, index);
        jbyteArray bytes = env->NewByteArray(count);
        env->SetByteArrayRegion(bytes, 0, count, pBlob);
        return bytes;
    }
    return env->NewByteArray(0);
}

}