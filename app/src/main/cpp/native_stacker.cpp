#include <jni.h>
#include <android/log.h>
#include <android/hardware_buffer.h>
#include <android/hardware_buffer_jni.h>
#include <opencv2/core.hpp>
#include <opencv2/imgproc.hpp>
#include <vector>
#include <memory>
#include <mutex>
#include <cstring>

#define TAG "NativeStacker_JNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, TAG, __VA_ARGS__)

// Estado global de la sesión de apilamiento
namespace StackerSession {
    int g_width = 0;
    int g_height = 0;
    bool g_isBayer = false;
    
    // Acumulador de Darks de 32 bits para evitar desbordes en sumas de múltiples frames
    std::unique_ptr<uint32_t[]> dark_accumulator;
    int num_dark_frames = 0;

    // Master Dark calibrado y promediado final de 16 bits
    std::unique_ptr<uint16_t[]> master_dark;

    // Mutex global para sincronizar el acceso a la sesión nativa desde múltiples hilos
    std::mutex session_mutex;

    // TODO: Añadir contextos de Vulkan y pools de memoria aquí
}

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_stelllar_camera_domain_stacking_NativeStacker_initSession(
    JNIEnv *env, jobject thiz, jint width, jint height, jboolean isBayer) {
    
    std::lock_guard<std::mutex> lock(StackerSession::session_mutex);
    LOGI("Inicializando sesion nativa: %dx%d (Bayer: %d)", width, height, isBayer);
    
    StackerSession::g_width = width;
    StackerSession::g_height = height;
    StackerSession::g_isBayer = isBayer;
    StackerSession::num_dark_frames = 0;

    size_t num_pixels = static_cast<size_t>(width) * height;

    // Reservar memoria del acumulador de Darks y limpiar a cero
    StackerSession::dark_accumulator.reset(new uint32_t[num_pixels]());
    StackerSession::master_dark.reset();

    // TODO: Inicializar pipeline de cómputo de Vulkan aquí

    return JNI_TRUE;
}

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_stelllar_camera_domain_stacking_NativeStacker_addDarkFrame(
    JNIEnv *env, jobject thiz, jobject buffer, jint rowStride) {
    
    std::lock_guard<std::mutex> lock(StackerSession::session_mutex);

    if (!buffer) {
        LOGE("Error: El buffer de dark frame es nulo");
        return JNI_FALSE;
    }

    if (!StackerSession::dark_accumulator) {
        LOGE("Error: El acumulador de Darks no esta inicializado. ¿Se llamo a initSession?");
        return JNI_FALSE;
    }

    void* raw_data = env->GetDirectBufferAddress(buffer);
    if (!raw_data) {
        LOGE("Error: Fallo al obtener direccion de DirectBuffer (Zero-Copy)");
        return JNI_FALSE;
    }

    jlong capacity = env->GetDirectBufferCapacity(buffer);
    size_t num_pixels = static_cast<size_t>(StackerSession::g_width) * StackerSession::g_height;
    size_t required_bytes = num_pixels * sizeof(uint16_t);

    if (static_cast<size_t>(capacity) < required_bytes) {
        LOGE("Error: La capacidad del buffer (%ld bytes) es insuficiente para %zu pixeles (%zu bytes)", 
             capacity, num_pixels, required_bytes);
        return JNI_FALSE;
    }

    LOGI("Añadiendo Dark Frame nativo. Stride: %d, Capacidad: %ld", rowStride, capacity);

    // Validar alineación del buffer de datos a 2 bytes (uint16_t)
    uint16_t* data = nullptr;
    std::unique_ptr<uint16_t[]> aligned_temp_buffer;
    if (reinterpret_cast<uintptr_t>(raw_data) % sizeof(uint16_t) == 0) {
        data = static_cast<uint16_t*>(raw_data);
    } else {
        LOGW("Advertencia: El buffer directo no esta alineado a 2 bytes. Copiando a buffer alineado.");
        aligned_temp_buffer.reset(new uint16_t[num_pixels]);
        std::memcpy(aligned_temp_buffer.get(), raw_data, required_bytes);
        data = aligned_temp_buffer.get();
    }

    // Sumar píxeles al acumulador de 32 bits para promediado posterior
    for (size_t i = 0; i < num_pixels; i++) {
        StackerSession::dark_accumulator[i] += data[i];
    }
    
    StackerSession::num_dark_frames++;
    return JNI_TRUE;
}

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_stelllar_camera_domain_stacking_NativeStacker_finalizeMasterDark(
    JNIEnv *env, jobject thiz) {
    
    std::lock_guard<std::mutex> lock(StackerSession::session_mutex);
    LOGI("Finalizando Master Dark con %d frames...", StackerSession::num_dark_frames);

    if (!StackerSession::dark_accumulator) {
        LOGE("Error: No se puede finalizar, el acumulador de Darks es nulo");
        return JNI_FALSE;
    }

    size_t num_pixels = static_cast<size_t>(StackerSession::g_width) * StackerSession::g_height;
    StackerSession::master_dark.reset(new uint16_t[num_pixels]());

    if (StackerSession::num_dark_frames > 0) {
        for (size_t i = 0; i < num_pixels; i++) {
            // Calcular promedio y reducir a 16 bits
            StackerSession::master_dark[i] = static_cast<uint16_t>(
                StackerSession::dark_accumulator[i] / StackerSession::num_dark_frames
            );
        }
    } else {
        LOGE("Error: No se agregaron dark frames para promediar");
        return JNI_FALSE;
    }
    
    // Liberar acumulador temporal de 32 bits para ahorrar memoria RAM
    StackerSession::dark_accumulator.reset();
    LOGI("Master Dark creado con exito.");
    
    return JNI_TRUE;
}

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_stelllar_camera_domain_stacking_NativeStacker_processLightFrame(
    JNIEnv *env, jobject thiz, jobject buffer, jint rowStride) {
    
    std::lock_guard<std::mutex> lock(StackerSession::session_mutex);
    
    if (!buffer) {
        LOGE("Error: El buffer del Light Frame es nulo");
        return JNI_FALSE;
    }
    
    if (!StackerSession::master_dark) {
        LOGE("Error: No se puede calibrar, Master Dark no inicializado o nulo");
        return JNI_FALSE;
    }

    void* raw_data = env->GetDirectBufferAddress(buffer);
    if (!raw_data) {
        LOGE("Error: Fallo al obtener direccion de DirectBuffer en Light Frame");
        return JNI_FALSE;
    }

    jlong capacity = env->GetDirectBufferCapacity(buffer);
    size_t num_pixels = static_cast<size_t>(StackerSession::g_width) * StackerSession::g_height;
    size_t required_bytes = num_pixels * sizeof(uint16_t);

    if (static_cast<size_t>(capacity) < required_bytes) {
        LOGE("Error: Capacidad del buffer del Light Frame (%ld) es insuficiente para %zu pixeles (%zu bytes)", 
             capacity, num_pixels, required_bytes);
        return JNI_FALSE;
    }

    LOGI("Iniciando Calibracion de Light Frame...");

    // Validar alineación del buffer del Light Frame a 2 bytes
    uint16_t* light_data = nullptr;
    std::unique_ptr<uint16_t[]> aligned_temp_buffer;
    bool is_aligned = (reinterpret_cast<uintptr_t>(raw_data) % sizeof(uint16_t) == 0);
    
    if (is_aligned) {
        light_data = static_cast<uint16_t*>(raw_data);
    } else {
        LOGW("Advertencia: El buffer de Light Frame no esta alineado. Copiando para sustraccion.");
        aligned_temp_buffer.reset(new uint16_t[num_pixels]);
        std::memcpy(aligned_temp_buffer.get(), raw_data, required_bytes);
        light_data = aligned_temp_buffer.get();
    }

    // 1. Calibrar (Sustraer Master Dark con Pedestal en el dominio Bayer CFA lineal)
    const int32_t pedestal = 100; // Pedestal para evitar recortes a cero en el ruido de lectura
    for (size_t i = 0; i < num_pixels; i++) {
        int32_t calib_val = static_cast<int32_t>(light_data[i]) - static_cast<int32_t>(StackerSession::master_dark[i]) + pedestal;
        if (calib_val < 0) calib_val = 0;
        if (calib_val > 65535) calib_val = 65535;
        light_data[i] = static_cast<uint16_t>(calib_val);
    }

    // Si copiamos a un buffer alineado, debemos volcar los datos calibrados de vuelta al buffer original
    if (!is_aligned) {
        std::memcpy(raw_data, light_data, required_bytes);
    }

    LOGI("Calibracion finalizada de forma exitosa (Sustraccion + Pedestal).");

    // TODO: Detección de estrellas de OpenCV y emparejamiento de asterismos
    // TODO: Pipeline de cómputo de GPU Vulkan para homografía y Sigma-Clipping
    
    return JNI_TRUE;
}

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_stelllar_camera_domain_stacking_NativeStacker_finalizeStacking(
    JNIEnv *env, jobject thiz, jobject outBuffer) {
    
    std::lock_guard<std::mutex> lock(StackerSession::session_mutex);
    
    if (!outBuffer) return JNI_FALSE;
    
    void* raw_data = env->GetDirectBufferAddress(outBuffer);
    if (!raw_data) return JNI_FALSE;

    LOGI("Finalizando apilamiento en el buffer de salida...");
    
    // TODO: Esperar a que el computo de Vulkan termine
    // TODO: Descargar de GPU/SSBO de vuelta al buffer de CPU
    
    return JNI_TRUE;
}

extern "C"
JNIEXPORT void JNICALL
Java_com_stelllar_camera_domain_stacking_NativeStacker_releaseSession(
    JNIEnv *env, jobject thiz) {
    
    std::lock_guard<std::mutex> lock(StackerSession::session_mutex);
    LOGI("Liberando recursos de la sesion nativa...");
    
    StackerSession::dark_accumulator.reset();
    StackerSession::master_dark.reset();
    StackerSession::num_dark_frames = 0;
    
    // TODO: Liberar recursos de Vulkan
}
