cmake_minimum_required(VERSION 3.22.1)
project("camerastellar")

add_library(camerastellar SHARED native-lib.cpp astro_align.cpp)

find_library(log-lib log)

target_link_libraries(camerastellar ${log-lib})

target_link_options(camerastellar PRIVATE "-Wl,-z,max-page-size=16384")
