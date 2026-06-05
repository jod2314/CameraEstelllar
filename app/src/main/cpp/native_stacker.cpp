#include <jni.h>
#include <android/log.h>
#include <android/hardware_buffer.h>
#include <android/hardware_buffer_jni.h>
#include <opencv2/core.hpp>
#include <opencv2/imgproc.hpp>
#include <opencv2/calib3d.hpp>
#include <vector>
#include <memory>
#include <mutex>
#include <cstring>
#include <cmath>
#include <algorithm>

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

    // Coleccion de frames de luz calibrados y alineados en memoria nativa
    std::vector<std::unique_ptr<uint16_t[]>> g_aligned_light_frames;

    // Centroides de estrellas de la toma de referencia para alineacion
    std::vector<cv::Point2f> g_reference_stars;

    // Indica si se ha fijado la toma de referencia en la sesion actual
    bool g_is_reference_set = false;

    // TODO: Añadir contextos de Vulkan y pools de memoria aquí
}

namespace {
    // Convierte el frame Bayer de 16 bits a un plano L de luminancia monocromo de W/2 x H/2 promediando bloques 2x2.
    cv::Mat createSuperPixelLPlane(const uint16_t* bayer_data, int width, int height) {
        int half_width = width / 2;
        int half_height = height / 2;
        cv::Mat l_plane(half_height, half_width, CV_16UC1);

        for (int y = 0; y < half_height; ++y) {
            uint16_t* row_ptr = l_plane.ptr<uint16_t>(y);
            int src_y0 = y * 2;
            int src_y1 = src_y0 + 1;
            const uint16_t* src_row0 = bayer_data + src_y0 * width;
            const uint16_t* src_row1 = bayer_data + src_y1 * width;

            for (int x = 0; x < half_width; ++x) {
                int src_x0 = x * 2;
                int src_x1 = src_x0 + 1;

                uint32_t sum = static_cast<uint32_t>(src_row0[src_x0]) +
                               static_cast<uint32_t>(src_row0[src_x1]) +
                               static_cast<uint32_t>(src_row1[src_x0]) +
                               static_cast<uint32_t>(src_row1[src_x1]);

                row_ptr[x] = static_cast<uint16_t>((sum + 2) / 4); // Promedio con redondeo
            }
        }
        return l_plane;
    }

    // Estructura para registrar informacion de la estrella detectada
    struct DetectedStar {
        cv::Point2f centroid;
        double intensity;
    };

    // Detecta las estrellas mas brillantes en el plano L monocromo y calcula su centroide con precision subpixel.
    std::vector<cv::Point2f> detectStarsAndCentroids(const cv::Mat& l_plane) {
        // Estimar media y desviacion estandar global de forma rapida y eficiente
        cv::Scalar mean_scalar, stddev_scalar;
        cv::meanStdDev(l_plane, mean_scalar, stddev_scalar);
        double mean = mean_scalar[0];
        double stddev = stddev_scalar[0];

        // Muestreo rapido para estimar la mediana del fondo
        std::vector<uint16_t> sample;
        sample.reserve(1000);
        int step = std::max(1, (l_plane.rows * l_plane.cols) / 1000);
        for (int y = 0; y < l_plane.rows; ++y) {
            const uint16_t* row = l_plane.ptr<uint16_t>(y);
            for (int x = 0; x < l_plane.cols; ++x) {
                int index = y * l_plane.cols + x;
                if (index % step == 0) {
                    sample.push_back(row[x]);
                }
            }
        }
        double median = mean;
        if (!sample.empty()) {
            std::sort(sample.begin(), sample.end());
            median = sample[sample.size() / 2];
        }

        // Umbral adaptativo para deteccion
        double threshold = median + 4.0 * stddev;

        std::vector<DetectedStar> detected_stars;

        // Deteccion en ventana local de 5x5 excluyendo bordes
        for (int y = 2; y < l_plane.rows - 2; ++y) {
            const uint16_t* row = l_plane.ptr<uint16_t>(y);
            for (int x = 2; x < l_plane.cols - 2; ++x) {
                uint16_t val = row[x];
                if (val <= threshold) continue;

                // Comprobar si es un maximo local estricto
                bool is_max = true;
                for (int dy = -2; dy <= 2; ++dy) {
                    const uint16_t* neighbor_row = l_plane.ptr<uint16_t>(y + dy);
                    for (int dx = -2; dx <= 2; ++dx) {
                        if (dy == 0 && dx == 0) continue;
                        uint16_t neighbor_val = neighbor_row[x + dx];
                        if (neighbor_val > val) {
                            is_max = false;
                            break;
                        }
                        // Romper empates de forma determinista para evitar duplicados
                        if (neighbor_val == val) {
                            if (dy < 0 || (dy == 0 && dx < 0)) {
                                is_max = false;
                                break;
                            }
                        }
                    }
                    if (!is_max) break;
                }

                if (is_max) {
                    // Calculo del centroide por momento de primer orden (centro de gravedad)
                    double sum_i = 0;
                    double sum_x = 0;
                    double sum_y = 0;
                    for (int dy = -2; dy <= 2; ++dy) {
                        const uint16_t* window_row = l_plane.ptr<uint16_t>(y + dy);
                        for (int dx = -2; dx <= 2; ++dx) {
                            double pixel_val = static_cast<double>(window_row[x + dx]);
                            // Restamos el fondo estimado para aislar el perfil de la estrella
                            double intensity = std::max(0.0, pixel_val - median);
                            sum_i += intensity;
                            sum_x += (x + dx) * intensity;
                            sum_y += (y + dy) * intensity;
                        }
                    }

                    if (sum_i > 0) {
                        cv::Point2f centroid(static_cast<float>(sum_x / sum_i), static_cast<float>(sum_y / sum_i));
                        detected_stars.push_back({centroid, sum_i});
                    }
                }
            }
        }

        // Ordenar estrellas en orden descendente de brillo
        std::sort(detected_stars.begin(), detected_stars.end(), [](const DetectedStar& a, const DetectedStar& b) {
            return a.intensity > b.intensity;
        });

        // Limitar a los mejores 100 centroides
        std::vector<cv::Point2f> best_stars;
        int max_stars = std::min(100, static_cast<int>(detected_stars.size()));
        best_stars.reserve(max_stars);
        for (int i = 0; i < max_stars; ++i) {
            best_stars.push_back(detected_stars[i].centroid);
        }

        return best_stars;
    }
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

    // 2. Crear plano L de super-pixel para el frame calibrado
    cv::Mat l_plane = createSuperPixelLPlane(light_data, StackerSession::g_width, StackerSession::g_height);

    // 3. Detectar las estrellas y sus centroides en el plano L
    std::vector<cv::Point2f> current_stars = detectStarsAndCentroids(l_plane);
    LOGI("Deteccion de estrellas completada. Se detectaron %zu estrellas.", current_stars.size());

    // 4. Alinear y registrar el frame actual
    if (!StackerSession::g_is_reference_set) {
        // Guardar estrellas como referencia y marcar indicador
        StackerSession::g_reference_stars = current_stars;
        StackerSession::g_is_reference_set = true;
        LOGI("Establecida toma de referencia con %zu estrellas.", current_stars.size());

        // Guardar la toma calibrada actual directamente en la cola de alineadas
        std::unique_ptr<uint16_t[]> frame_copy(new uint16_t[num_pixels]);
        std::memcpy(frame_copy.get(), light_data, required_bytes);
        StackerSession::g_aligned_light_frames.push_back(std::move(frame_copy));
    } else {
        // Emparejamiento por vecino mas cercano (distancia maxima de 30 pixeles)
        std::vector<cv::Point2f> matched_curr;
        std::vector<cv::Point2f> matched_ref;
        std::vector<bool> ref_used(StackerSession::g_reference_stars.size(), false);

        for (const auto& curr_star : current_stars) {
            double min_dist = 30.0;
            int best_ref_idx = -1;

            for (size_t i = 0; i < StackerSession::g_reference_stars.size(); ++i) {
                if (ref_used[i]) continue;

                double dx = curr_star.x - StackerSession::g_reference_stars[i].x;
                double dy = curr_star.y - StackerSession::g_reference_stars[i].y;
                double dist = std::sqrt(dx * dx + dy * dy);

                if (dist < min_dist) {
                    min_dist = dist;
                    best_ref_idx = i;
                }
            }

            if (best_ref_idx != -1) {
                matched_curr.push_back(curr_star);
                matched_ref.push_back(StackerSession::g_reference_stars[best_ref_idx]);
                ref_used[best_ref_idx] = true;
            }
        }

        LOGI("Fase de emparejamiento completada: %zu parejas validas.", matched_curr.size());

        bool alignment_success = false;

        // Se requiere un minimo de 4 correspondencias para estimar la transformacion afin parcial con RANSAC
        if (matched_curr.size() >= 4) {
            cv::Mat inliers;
            cv::Mat affine_matrix = cv::estimateAffinePartial2D(matched_curr, matched_ref, inliers, cv::RANSAC, 3.0);

            if (!affine_matrix.empty()) {
                alignment_success = true;

                // Separar el frame calibrado de 16 bits en 4 canales monocromos (R, Gr, Gb, B) de W/2 x H/2
                int half_width = StackerSession::g_width / 2;
                int half_height = StackerSession::g_height / 2;

                cv::Mat ch0(half_height, half_width, CV_16UC1);
                cv::Mat ch1(half_height, half_width, CV_16UC1);
                cv::Mat ch2(half_height, half_width, CV_16UC1);
                cv::Mat ch3(half_height, half_width, CV_16UC1);

                for (int y = 0; y < half_height; ++y) {
                    const uint16_t* src_row0 = light_data + (y * 2) * StackerSession::g_width;
                    const uint16_t* src_row1 = light_data + (y * 2 + 1) * StackerSession::g_width;

                    uint16_t* dst_ch0 = ch0.ptr<uint16_t>(y);
                    uint16_t* dst_ch1 = ch1.ptr<uint16_t>(y);
                    uint16_t* dst_ch2 = ch2.ptr<uint16_t>(y);
                    uint16_t* dst_ch3 = ch3.ptr<uint16_t>(y);

                    for (int x = 0; x < half_width; ++x) {
                        dst_ch0[x] = src_row0[x * 2];
                        dst_ch1[x] = src_row0[x * 2 + 1];
                        dst_ch2[x] = src_row1[x * 2];
                        dst_ch3[x] = src_row1[x * 2 + 1];
                    }
                }

                // Warp de canales independientes mediante interpolacion bicubica
                cv::Mat ch0_warped, ch1_warped, ch2_warped, ch3_warped;
                cv::Size target_size(half_width, half_height);

                cv::warpAffine(ch0, ch0_warped, affine_matrix, target_size, cv::INTER_CUBIC, cv::BORDER_CONSTANT, cv::Scalar(0));
                cv::warpAffine(ch1, ch1_warped, affine_matrix, target_size, cv::INTER_CUBIC, cv::BORDER_CONSTANT, cv::Scalar(0));
                cv::warpAffine(ch2, ch2_warped, affine_matrix, target_size, cv::INTER_CUBIC, cv::BORDER_CONSTANT, cv::Scalar(0));
                cv::warpAffine(ch3, ch3_warped, affine_matrix, target_size, cv::INTER_CUBIC, cv::BORDER_CONSTANT, cv::Scalar(0));

                // Recomposicion intercalada al patron Bayer original de W x H de 16 bits
                std::unique_ptr<uint16_t[]> aligned_frame(new uint16_t[num_pixels]);

                for (int y = 0; y < half_height; ++y) {
                    uint16_t* dst_row0 = aligned_frame.get() + (y * 2) * StackerSession::g_width;
                    uint16_t* dst_row1 = aligned_frame.get() + (y * 2 + 1) * StackerSession::g_width;

                    const uint16_t* src_ch0 = ch0_warped.ptr<uint16_t>(y);
                    const uint16_t* src_ch1 = ch1_warped.ptr<uint16_t>(y);
                    const uint16_t* src_ch2 = ch2_warped.ptr<uint16_t>(y);
                    const uint16_t* src_ch3 = ch3_warped.ptr<uint16_t>(y);

                    for (int x = 0; x < half_width; ++x) {
                        dst_row0[x * 2] = src_ch0[x];
                        dst_row0[x * 2 + 1] = src_ch1[x];
                        dst_row1[x * 2] = src_ch2[x];
                        dst_row1[x * 2 + 1] = src_ch3[x];
                    }
                }

                // Guardar en g_aligned_light_frames
                StackerSession::g_aligned_light_frames.push_back(std::move(aligned_frame));
                LOGI("Frame alineado y acumulado correctamente en memoria nativa.");
            }
        }

        // Si falla la estimacion RANSAC o no hay estrellas suficientes, guardar sin alinear como fallback
        if (!alignment_success) {
            LOGW("Advertencia: No se pudo alinear el frame (insuficientes correspondencias o estimacion fallida). Guardando sin alinear como fallback.");
            std::unique_ptr<uint16_t[]> fallback_frame(new uint16_t[num_pixels]);
            std::memcpy(fallback_frame.get(), light_data, required_bytes);
            StackerSession::g_aligned_light_frames.push_back(std::move(fallback_frame));
        }
    }
    
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
    LOGI("Cantidad de frames alineados en memoria: %zu", StackerSession::g_aligned_light_frames.size());
    
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

    // Liberar memoria de frames alineados y referencias de estrellas
    StackerSession::g_aligned_light_frames.clear();
    StackerSession::g_reference_stars.clear();
    StackerSession::g_is_reference_set = false;
    
    // TODO: Liberar recursos de Vulkan
}
