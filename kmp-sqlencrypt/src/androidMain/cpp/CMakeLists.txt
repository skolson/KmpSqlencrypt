# Build the C++ side of the JNI interface used by Android and other JVM targets.
# header and ABI-specific build of ibsqlcipher must exist in src\\jniLibs\\${abi}\\ directories
# these come from build process in gradle plugin "com.oldguy.gradle.sqlcipher-openssl-build"

cmake_minimum_required(VERSION 3.18.1)
project(kmp-sc)

#Using r23b NDK on windows and newer CMake 3.21.4 caused sync to fail with:
# [CXX1410] D:\WorkingShared\SqlCipherKotlinMP\kmp-sc\.cxx\Debug\6u332q2l\arm64-v8a\android_gradle_build.json debug|arm64-v8a : expected buildTargetsCommandComponents command 'ninja.exe' to exist
# got below from https://github.com/android/ndk/issues/1581

# set(CMAKE_FIND_USE_SYSTEM_ENVIRONMENT_PATH 1)
# set(CMAKE_MAKE_PROGRAM "D:\\Android\\CMake\\ninja.exe")

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