# Transcripción Técnica: CaptureRequest y Builder

**Descripción:**
`CaptureRequest` es un paquete inmutable que contiene toda la configuración necesaria para que el hardware de la cámara capture un solo frame (o ráfaga). El `Builder` es la clase auxiliar para construir este paquete.

**Métodos de Configuración Clave:**
- `set(Key<T> key, T value)`: El método principal para inyectar parámetros manuales.
- `addTarget(Surface surface)`: Define a dónde se enviarán los datos (visor, guardado RAW, guardado JPEG).

**Parámetros para Modo Manual (Astrofotografía):**
- `CONTROL_AE_MODE`: Debe establecerse en `OFF` para control manual total.
- `CONTROL_AF_MODE`: Debe establecerse en `OFF` (manual) o `EDOF`.
- `LENS_FOCUS_DISTANCE`: Ajuste fino del enfoque.
- `SENSOR_EXPOSURE_TIME`: Configuración de larga exposición (ej. 30,000,000,000 ns para 30s).
- `SENSOR_SENSITIVITY`: Ajuste de ISO.
- `SENSOR_FRAME_DURATION`: Debe ser mayor o igual al tiempo de exposición para evitar recortes del HAL.

**Templates de Dispositivo:**
- `TEMPLATE_MANUAL`: Pre-configura el builder para control manual, desactivando algoritmos de 3A (Auto Exposure, Auto Focus, Auto White Balance).

**Enlaces de Profundización Identificados:**
- [CaptureRequest Keys Reference](https://developer.android.com/reference/android/hardware/camera2/CaptureRequest.Key)
- [Managing Camera Session States](https://developer.android.com/training/camera2/capture-sessions)
