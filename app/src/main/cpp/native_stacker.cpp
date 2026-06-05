#include <jni.h>
#include <android/log.h>
#include <android/hardware_buffer.h>
#include <android/hardware_buffer_jni.h>
#include <opencv2/core.hpp>
#include <opencv2/imgproc.hpp>
#include <vector>
#include <memory>

#define TAG "NativeStacker_JNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

// Globals for session state
namespace StackerSession {
    int g_width = 0;
    int g_height = 0;
    bool g_isBayer = false;
    
    // Master Dark buffer
    std::unique_ptr<uint16_t[]> master_dark;
    int num_dark_frames = 0;

    // TODO: Add Vulkan contexts and memory pools here
}

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_stelllar_camera_domain_stacking_NativeStacker_initSession(
    JNIEnv *env, jobject thiz, jint width, jint height, jboolean isBayer) {
    
    LOGI("Initializing session: %dx%d (Bayer: %d)", width, height, isBayer);
    
    StackerSession::g_width = width;
    StackerSession::g_height = height;
    StackerSession::g_isBayer = isBayer;
    StackerSession::num_dark_frames = 0;

    // Allocate memory for master dark frame (assuming 16-bit raw data)
    size_t num_pixels = static_cast<size_t>(width) * height;
    StackerSession::master_dark.reset(new uint16_t[num_pixels]());

    // TODO: Initialize Vulkan compute pipeline here

    return JNI_TRUE;
}

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_stelllar_camera_domain_stacking_NativeStacker_addDarkFrame(
    JNIEnv *env, jobject thiz, jobject buffer, jint rowStride) {
    
    if (!buffer) {
        LOGE("Dark frame buffer is null");
        return JNI_FALSE;
    }

    void* raw_data = env->GetDirectBufferAddress(buffer);
    if (!raw_data) {
        LOGE("Failed to get direct buffer address (Zero-Copy)");
        return JNI_FALSE;
    }

    jlong capacity = env->GetDirectBufferCapacity(buffer);
    LOGI("Adding Dark Frame. Stride: %d, Capacity: %ld", rowStride, capacity);

    // Simple accumulation for dark frames
    uint16_t* data = static_cast<uint16_t*>(raw_data);
    
    // NOTE: This assumes 16-bit unpacked data, 
    // further logic needed for 10/12/14-bit packed formats or using actual stride.
    size_t num_pixels = static_cast<size_t>(StackerSession::g_width) * StackerSession::g_height;

    for (size_t i = 0; i < num_pixels; i++) {
        // Simple running sum (to be divided later)
        // In reality, might need a 32-bit accumulator to avoid overflow if many dark frames.
        // For just 2 frames, uint16_t could overflow if max value is 16383 (14-bit) * 2 = 32766 (fits in 16-bit).
        StackerSession::master_dark[i] += data[i];
    }
    
    StackerSession::num_dark_frames++;
    return JNI_TRUE;
}

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_stelllar_camera_domain_stacking_NativeStacker_finalizeMasterDark(
    JNIEnv *env, jobject thiz) {
    
    LOGI("Finalizing Master Dark using %d frames", StackerSession::num_dark_frames);

    if (StackerSession::num_dark_frames > 0) {
        size_t num_pixels = static_cast<size_t>(StackerSession::g_width) * StackerSession::g_height;
        for (size_t i = 0; i < num_pixels; i++) {
            StackerSession::master_dark[i] /= StackerSession::num_dark_frames;
        }
    }
    
    return JNI_TRUE;
}

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_stelllar_camera_domain_stacking_NativeStacker_processLightFrame(
    JNIEnv *env, jobject thiz, jobject buffer, jint rowStride) {
    
    if (!buffer) return JNI_FALSE;
    
    void* raw_data = env->GetDirectBufferAddress(buffer);
    if (!raw_data) return JNI_FALSE;

    LOGI("Processing Light Frame...");

    // 1. Calibrate (Subtract Master Dark)
    // 2. OpenCV Star Detection & Asterism Matching
    // 3. Vulkan GPU Compute Pipeline for Homography & Sigma-Clipping
    
    // Placeholder logic
    return JNI_TRUE;
}

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_stelllar_camera_domain_stacking_NativeStacker_finalizeStacking(
    JNIEnv *env, jobject thiz, jobject outBuffer) {
    
    if (!outBuffer) return JNI_FALSE;
    
    void* raw_data = env->GetDirectBufferAddress(outBuffer);
    if (!raw_data) return JNI_FALSE;

    LOGI("Finalizing stack into output buffer...");
    
    // 1. Wait for Vulkan compute to finish
    // 2. Download from GPU / SSBO back to CPU buffer
    
    return JNI_TRUE;
}

extern "C"
JNIEXPORT void JNICALL
Java_com_stelllar_camera_domain_stacking_NativeStacker_releaseSession(
    JNIEnv *env, jobject thiz) {
    
    LOGI("Releasing session resources...");
    StackerSession::master_dark.reset();
    StackerSession::num_dark_frames = 0;
    
    // TODO: Free Vulkan resources
}
