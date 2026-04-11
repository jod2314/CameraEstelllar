# CameraManager y CameraCharacteristics

## Descripción General
`CameraManager` es el servicio del sistema encargado de detectar, caracterizar y conectar con los `CameraDevice`. `CameraCharacteristics` describe las capacidades estáticas de un sensor físico o lógico.

## Métodos Técnicos Analizados

### getCameraIdList
`String[] getIdList()`
- **Descripción:** Devuelve la lista de IDs de cámaras disponibles, incluyendo cámaras externas y físicas individuales (en dispositivos multi-lente).

### getCameraCharacteristics
`CameraCharacteristics getCameraCharacteristics(String cameraId)`
- **Descripción:** Consulta las capacidades de la cámara. Es el método más importante para la auditoría de hardware.

### CameraCharacteristics.Key
`public static final Key<T> ...`
- **Claves Críticas para Astrofotografía:**
    - `SENSOR_INFO_EXPOSURE_TIME_RANGE`: Define el límite máximo de exposición (ej. 30s).
    - `SENSOR_INFO_SENSITIVITY_RANGE`: Define el rango de ISO soportado.
    - `SENSOR_INFO_COLOR_FILTER_ARRANGEMENT`: Identifica el patrón Bayer (RGGB, BGGR) para el procesado RAW.
    - `SENSOR_INFO_WHITE_LEVEL` y `SENSOR_INFO_BLACK_LEVEL_PATTERN`: Valores de calibración esenciales para linealizar los datos RAW.
    - `LENS_INFO_MINIMUM_FOCUS_DISTANCE`: Si es 0, la cámara es de enfoque fijo.

## Hipervínculos y Referencias Externas
1. **Camera Performance:** [Understanding Camera Capabilities](https://source.android.com/devices/camera/versioning)
    - *Transcripción:* Guía sobre los niveles de soporte (`LIMITED`, `FULL`, `LEVEL_3`). Explica que `LEVEL_3` es obligatorio para astrofotografía avanzada ya que garantiza el soporte de `RAW_SENSOR` y `Reprocessing`.
2. **Foro de Desarrolladores:** [Google Groups - Multi-camera and Physical IDs](https://groups.google.com/g/android-camera-dev)
    - *Transcripción:* Discusión técnica sobre cómo iterar sobre sensores físicos individuales (ej. el sensor de 108MP) en lugar de usar la cámara lógica "combinada" que Android ofrece por defecto.
