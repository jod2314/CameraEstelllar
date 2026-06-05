cmake_minimum_required(VERSION 3.22)

project("stelllar_native_stacker")

# --- 1. Fetch opencv-mobile ---
include(FetchContent)

# Proporcionar la URL a opencv-mobile precompilado para Android
# Se utiliza la versión v30 (OpenCV 4.10.0) que es compatible y estable.
# Se añade TLS_VERIFY OFF para evitar fallos de certificados SSL en entornos restringidos.
FetchContent_Declare(
    opencv_mobile
    URL "https://github.com/nihui/opencv-mobile/releases/download/v30/opencv-mobile-4.10.0-android.zip"
    TLS_VERIFY FALSE
)
FetchContent_MakeAvailable(opencv_mobile)

# El paquete contiene OpenCVConfig.cmake, por lo que podemos usar find_package.
# Establecemos la ruta al lugar donde FetchContent lo extrajo.
set(OpenCV_DIR ${opencv_mobile_SOURCE_DIR}/sdk/native/jni)
find_package(OpenCV REQUIRED)

# --- 2. Setup Native Library ---
add_library(
    stelllar_native_stacker
    SHARED
    native_stacker.cpp
)

# Desactivar RTTI y excepciones para alinearse con la compilación optimizada de opencv-mobile
target_compile_options(
    stelllar_native_stacker
    PRIVATE
    -fno-rtti
    -fno-exceptions
)

# --- 3. Find System Libraries ---
find_library(log-lib log)
find_library(android-lib android)

# --- 4. Target Linking ---
target_link_libraries(
    stelllar_native_stacker
    ${log-lib}
    ${android-lib}
    ${OpenCV_LIBS}
)
