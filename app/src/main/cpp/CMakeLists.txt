cmake_minimum_required(VERSION 3.22.1)
project(termux C)

add_library(termux SHARED termux.c)

# NDK 28 already defaults to 16 KB alignment; state it so a downgrade cannot regress it.
target_link_options(termux PRIVATE "-Wl,-z,max-page-size=16384")

find_library(log-lib log)
target_link_libraries(termux ${log-lib})
