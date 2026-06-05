cmake_minimum_required(VERSION 3.22)

project("stelllar_native_stacker")

# --- 1. Fetch opencv-mobile ---
include(FetchContent)

# Provide the URL to the precompiled opencv-mobile for Android
FetchContent_Declare(
    opencv_mobile
    URL https://github.com/nihui/opencv-mobile/releases/download/v24.10.14/opencv-mobile-4.10.0-android.zip
)
FetchContent_MakeAvailable(opencv_mobile)

# The package contains the OpenCVConfig.cmake, so we can find_package it.
# Set the prefix path to where FetchContent extracted it
set(OpenCV_DIR ${opencv_mobile_SOURCE_DIR}/sdk/native/jni)
find_package(OpenCV REQUIRED)

# --- 2. Setup Native Library ---
add_library(
    stelllar_native_stacker
    SHARED
    native_stacker.cpp
)

# --- 3. Find System Libraries ---
find_library(log-lib log)
find_library(android-lib android)

# In Android 26+, AHardwareBuffer is available in libandroid, but sometimes needs explicit link if older.
# We also need Vulkan.
find_library(vulkan-lib vulkan)

# --- 4. Target Linking ---
target_link_libraries(
    stelllar_native_stacker
    ${log-lib}
    ${android-lib}
    ${vulkan-lib}
    ${OpenCV_LIBS}
)
