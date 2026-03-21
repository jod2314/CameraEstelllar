#include <jni.h>
#include <string>
#include <android/log.h>

#define TAG "CameraStellarNative"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, TAG, __VA_ARGS__)

extern "C" JNIEXPORT jstring JNICALL
Java_com_stellar_camera_NativeEngine_getNativeVersion(JNIEnv* env, jobject /* this */) {
    std::string version = "1.0.0-NDK-ZeroCopy";
    return env->NewStringUTF(version.c_str());
}

/**
 * JNI Zero-Copy: Procesa el buffer de la cámara directamente desde su dirección física.
 */
extern "C" JNIEXPORT void JNICALL
Java_com_stellar_camera_NativeEngine_processRawBuffer(JNIEnv* env, jobject, jobject buffer, jint width, jint height) {
    void* pixelAddr = env->GetDirectBufferAddress(buffer);
    if (pixelAddr == nullptr) {
        LOGD("Error: No se pudo obtener la dirección del buffer directo.");
        return;
    }

    LOGD("Procesando buffer RAW de %dx%d en dirección %p", width, height, pixelAddr);
    // Aquí implementaremos el kernel de alineación en el siguiente paso
}
