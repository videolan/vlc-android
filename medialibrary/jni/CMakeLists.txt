cmake_minimum_required(VERSION 3.21) # proper support with the NDK

project(medialibrary)

set(CMAKE_C_STANDARD 17)
set(CMAKE_CXX_STANDARD 17)

set(ANDROID_CPP_FEATURES "rtti exceptions")

set(LIBVLCJNI_SRC_DIR "${CMAKE_CURRENT_SOURCE_DIR}/../../libvlcjni" CACHE PATH "libvlcjni base directory")
set(PREBUILT_PREFIX "${CMAKE_CURRENT_SOURCE_DIR}/../prefix/${ANDROID_PLATFORM}-${ANDROID_ABI}")
if(NOT CMAKE_LIBRARY_OUTPUT_DIRECTORY)
    set(CMAKE_LIBRARY_OUTPUT_DIRECTORY "${CMAKE_CURRENT_SOURCE_DIR}/libs/${ANDROID_NDK_ABI_NAME}")
endif()

# prebuilt binaries
## built with bash script (for now)
add_library(libvlc STATIC IMPORTED)
set_target_properties(libvlc
                      PROPERTIES IMPORTED_LOCATION
                      ${LIBVLCJNI_SRC_DIR}/libvlc/jni/libs/${ANDROID_NDK_ABI_NAME}/libvlc.so)

## built with meson script (cannot be called by gradle but could be called by CMake)
add_library(medialibrary STATIC IMPORTED)
set_target_properties(medialibrary
                      PROPERTIES IMPORTED_LOCATION
                      ${PREBUILT_PREFIX}/lib/libmedialibrary.a)

## built with bash script
add_library(sqlite STATIC IMPORTED)
set_target_properties(sqlite
                      PROPERTIES IMPORTED_LOCATION
                      ${PREBUILT_PREFIX}/lib/libsqlite3.a)

find_library(MATH_LIB m)
find_library(LOG_LIB log)

# mla
add_library(mla SHARED
            ${CMAKE_CURRENT_SOURCE_DIR}/medialibrary.cpp 
            ${CMAKE_CURRENT_SOURCE_DIR}/AndroidMediaLibrary.cpp 
            ${CMAKE_CURRENT_SOURCE_DIR}/AndroidDeviceLister.cpp 
            ${CMAKE_CURRENT_SOURCE_DIR}/utils.cpp)

target_link_options(mla PRIVATE "-Wl,-z,max-page-size=16384")
target_link_libraries(mla libvlc medialibrary sqlite ${MATH_LIB} ${LOG_LIB})
target_include_directories(mla PRIVATE ${PREBUILT_PREFIX}/include)

if (${CMAKE_ANDROID_STL_TYPE} STREQUAL "c++_shared")
    # Copy the C++ runtime
    add_custom_command(TARGET mla PRE_BUILD
                    COMMAND ${CMAKE_COMMAND} -E copy_if_different
                    ${ANDROID_TOOLCHAIN_ROOT}/sysroot/usr/lib/${CMAKE_LIBRARY_ARCHITECTURE}/libc++_shared.so
                    "${CMAKE_LIBRARY_OUTPUT_DIRECTORY}")
endif()

