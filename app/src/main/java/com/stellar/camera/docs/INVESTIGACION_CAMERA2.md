# Investigación Detallada: Acceso a Lentes y Control Manual (Camera2)

Este documento es el resultado del análisis de la documentación oficial de Android para el proyecto CameraStellar v3.

## 1. Acceso Profundo a Lentes (Physical Cameras)
El acceso a los lentes individuales es el pilar de nuestra app. 

### Conceptos Clave:
- **Logical Camera:** Un ID (ej. "0") que el sistema expone para uso general.
- **Physical Camera:** Los sensores reales detrás de la cámara lógica.
- **Identificación:** Se utiliza `CameraCharacteristics.getPhysicalCameraIds()`. 
- **Implementación:** Para cada ID físico, debemos consultar sus propias `CameraCharacteristics` para verificar si soporta RAW y control manual.

## 2. Configuración de Sesiones de Alta Precisión
Para evitar que el sistema "recorte" nuestras exposiciones largas:
- **Stream Use Cases:** Usar `SCALER_AVAILABLE_STREAM_USE_CASES_STILL_CAPTURE` para el ImageReader de RAW. Esto optimiza el pipeline para calidad sobre latencia.
- **Session Parameters:** Inyectar el rango de FPS `[1, 1]` y el `SENSOR_FRAME_DURATION` máximo en la configuración de la sesión.

## 3. Parámetros Críticos de Exposición e ISO
| Parámetro | Llave de la API | Impacto en Astro |
| :--- | :--- | :--- |
| **Tiempo de Exposición** | `SENSOR_EXPOSURE_TIME` | Define la cantidad de fotones capturados. |
| **Sensibilidad (ISO)** | `SENSOR_SENSITIVITY` | Ganancia analógica/digital del sensor. |
| **Bloqueo de AE** | `CONTROL_AE_MODE_OFF` | Evita que el móvil "corrija" la oscuridad del cielo. |
| **Enfoque** | `LENS_FOCUS_DISTANCE` | Debe fijarse en `0.0f` para infinito. |

## 4. Referencias Bibliográficas
1. **Google Developers.** (2024). *Capture sessions and requests*. [Enlace](https://developer.android.com/media/camera/camera2/capture-sessions-requests)
2. **Android Open Source Project.** (2024). *Multi-camera API*. [Enlace](https://source.android.com/docs/core/camera/multi-camera)
3. **Android API Reference.** *CameraManager Class*. [Enlace](https://developer.android.com/reference/android/hardware/camera2/CameraManager)
4. **Android API Reference.** *CameraCharacteristics Class*. [Enlace](https://developer.android.com/reference/android/hardware/camera2/CameraCharacteristics)
5. **Android API Reference.** *OutputConfiguration.setStreamUseCase*. [Enlace](https://developer.android.com/reference/android/hardware/camera2/params/OutputConfiguration#setStreamUseCase(long))
