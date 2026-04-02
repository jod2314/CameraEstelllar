# Investigación Técnica: Camera2 API para CameraStellar v3

Este documento detalla los hallazgos de la investigación sobre el control manual avanzado y la gestión de sesiones para astrofotografía en Android.

## 1. Acceso y Selección de Lentes (Hardware)
Para obtener el control total de los lentes, debemos iterar sobre `CameraManager.getCameraIdList()` y filtrar por capacidades específicas.

### Criterios de Selección para Astrofotografía:
| Característica | Propiedad CameraCharacteristics | Valor Requerido |
| :--- | :--- | :--- |
| **Control Manual** | `REQUEST_AVAILABLE_CAPABILITIES` | `MANUAL_SENSOR` |
| **Soporte RAW** | `REQUEST_AVAILABLE_CAPABILITIES` | `RAW` |
| **Exposición Larga** | `SENSOR_INFO_EXPOSURE_TIME_RANGE` | Max > 10s (1e10 ns) |
| **Enfoque Infinito** | `LENS_INFO_MINIMUM_FOCUS_DISTANCE` | 0.0 (Soportado) |

> **Nota Crítica:** Muchos dispositivos modernos exponen "Cámaras Lógicas". Para astrofotografía, es preferible identificar las "Cámaras Físicas" subyacentes mediante `characteristics.physicalCameraIds` para evitar procesamientos de software indeseados.

## 2. Configuración de Sesión y Flujos (Pipeline)
El error de recorte de exposición se soluciona mediante la configuración correcta de la sesión.

### Protocolo de Creación de Sesión (Anti-Clipping):
1. **Definir Superficies:** `Preview` (SurfaceView) y `Capture` (ImageReader para DNG/JPEG).
2. **Configurar Casos de Uso (API 33+):**
   - Preview: `STREAM_USE_CASE_PREVIEW`
   - Capture: `STREAM_USE_CASE_STILL_CAPTURE`
3. **Parámetros de Sesión (Crucial):**
   - Crear un `CaptureRequest` inicial con `TEMPLATE_MANUAL`.
   - Establecer `CONTROL_AE_TARGET_FPS_RANGE` a `[MIN, MIN]` (ej. [1, 15] o [1, 1]).
   - Pasar este request a `SessionConfiguration.setSessionParameters()`.

## 3. Control de Parámetros de Captura
Para garantizar que el sensor respete los valores científicos:

- **ISO:** `CaptureRequest.SENSOR_SENSITIVITY`.
- **Exposición:** `CaptureRequest.SENSOR_EXPOSURE_TIME` (en nanosegundos).
- **Apertura:** `CaptureRequest.LENS_APERTURE` (si es variable, raro en móviles).
- **Enfoque:** `CaptureRequest.LENS_FOCUS_DISTANCE` fijado a `0.0f` para infinito.

### Verificación de Resultados:
Siempre debemos auditar el `TotalCaptureResult` devuelto en el callback:
```kotlin
val realExposure = result.get(CaptureResult.SENSOR_EXPOSURE_TIME)
if (realExposure != requestedExposure) {
    Log.e("Astro", "El HAL recortó la exposición: $realExposure vs $requestedExposure")
}
```

## 4. Referencias Bibliográficas
1. **Documentación Oficial Android (Sesiones):** [Capture Sessions & Requests](https://developer.android.com/media/camera/camera2/capture-sessions-requests)
2. **Referencia de Clase CameraManager:** [CameraManager Docs](https://developer.android.com/reference/android/hardware/camera2/CameraManager)
3. **Control Manual (TEMPLATE_MANUAL):** [CameraDevice Templates](https://developer.android.com/reference/android/hardware/camera2/CameraDevice#TEMPLATE_MANUAL)
4. **Optimización de Flujos (Stream Use Cases):** [OutputConfiguration.setStreamUseCase](https://developer.android.com/reference/android/hardware/camera2/params/OutputConfiguration#setStreamUseCase(long))
5. **DngCreator (Metadatos RAW):** [DngCreator Reference](https://developer.android.com/reference/android/hardware/camera2/DngCreator)
