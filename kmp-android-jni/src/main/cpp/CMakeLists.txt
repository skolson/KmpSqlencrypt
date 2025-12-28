# Build the C++ side of the JNI interface used by Android and other JVM targets.
# header and ABI-specific build of ibsqlcipher must exist in src\\jniLibs\\${abi}\\ directories
# these come from build process in gradle plugin "com.oldguy.gradle.sqlcipher-openssl-build"

cmake_minimum_required(VERSION 4.0.0)
project(kmp-sc)

add_library( sqlcipher-kotlin SHARED
             database.cpp )

if (${OSWINDOWS})
    set(PS "\\")
else()
    set(PS "/")
endif()
set(SQLCIPHERLIBS ${ANDROID_MAIN_PATH}${PS}sqlcipher${PS}${ANDROID_ABI})

add_library( sqlcipher SHARED IMPORTED )
set_target_properties(
        sqlcipher
        PROPERTIES IMPORTED_LOCATION
        ${SQLCIPHERLIBS}${PS}libsqlcipher.so )

include_directories(${SQLCIPHERLIBS})

target_link_libraries(sqlcipher-kotlin sqlcipher)