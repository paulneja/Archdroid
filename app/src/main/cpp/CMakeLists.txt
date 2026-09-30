cmake_minimum_required(VERSION 3.22.1)
project(termux C)

add_library(termux SHARED termux.c)

target_link_options(termux PRIVATE "-Wl,-z,max-page-size=16384")

find_library(log-lib log)
target_link_libraries(termux ${log-lib})
