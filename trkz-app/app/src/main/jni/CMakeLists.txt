cmake_minimum_required(VERSION 3.10.2)

project("kaza_native")

add_library(
    kaza_native
    SHARED
    kaza_jni.c
)

find_library(
    log-lib
    log
)

target_link_libraries(
    kaza_native
    ${log-lib}
)
