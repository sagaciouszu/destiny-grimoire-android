cmake_minimum_required(VERSION 3.22.1)
project(grimoire_native VERSION 0.1.0 LANGUAGES C CXX)

add_library(grimoire_native SHARED
    grimoire_search.cpp
    sqlite3.c
)

target_include_directories(grimoire_native PRIVATE ${CMAKE_CURRENT_SOURCE_DIR})

target_compile_definitions(grimoire_native PRIVATE
    SQLITE_ENABLE_FTS5=1
    SQLITE_THREADSAFE=1
    SQLITE_OMIT_LOAD_EXTENSION=1
    SQLITE_DEFAULT_MEMSTATUS=0
    SQLITE_DQS=0
    SQLITE_DEFAULT_WAL_SYNCHRONOUS=1
    SQLITE_LIKE_DOESNT_MATCH_BLOBS=1
    UNICODE
)

if(CMAKE_C_COMPILER_ID MATCHES "Clang")
    target_compile_options(grimoire_native PRIVATE -Wno-unused-parameter)
endif()

target_link_libraries(grimoire_native PRIVATE android log)
