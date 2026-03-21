cmake_minimum_required(VERSION 3.22.1)
project("camerastellar")

# Buscar OpenCV (Asumimos que la incluiremos o usaremos una versión local)
# Por ahora configuramos el motor matemático base

add_library(
    camerastellar
    SHARED
    native-lib.cpp
    astro_align.cpp
)

find_library(
    log-lib
    log
)

target_link_libraries(
    camerastellar
    ${log-lib}
)
