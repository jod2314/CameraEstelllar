# Módulo Camera2 - Captura RAW de Alta Precisión (Estándares 2025)

## Arquitectura de Sesión Moderna (Multi-Cámara)
- **Host Lógico:** Siempre abrir el ID lógico como host principal.
- **Physical Camera Mapping:** Usar `SessionConfiguration` y `OutputConfiguration.setPhysicalCameraId()` para enrutar el flujo a sensores físicos específicos (Tele, Ultra-Wide) evitando restricciones del OEM.

## Directrices de Configuración
- **Formato:** `ImageFormat.RAW_SENSOR`.
- **Previsualización (Android 15):** Usar `CONTROL_AE_MODE_ON_LOW_LIGHT_BOOST_BRIGHTNESS_PRIORITY` para un visor brillante sin afectar la captura RAW.
- **Modo 3A (Captura):** `CONTROL_MODE_OFF` para bloqueo total de exposición y algoritmos del fabricante.
- **Metadatos:** Uso obligatorio de `DngCreator` vinculando `TotalCaptureResult` dinámico del sensor físico.

## Parámetros Críticos (El Bypass Científico)
- `SENSOR_EXPOSURE_TIME`: Nanosegundos exactos (Long de 64 bits).
- `SENSOR_FRAME_DURATION`: **Obligatorio:** Debe ser igual o superior a la exposición para evadir el recorte (clamp) del driver en largas exposiciones.
- `SENSOR_SENSITIVITY`: Usar `SENSOR_MAX_ANALOG_SENSITIVITY` para limitar el dial y evitar ruido de cuantización digital (Digital Gain).
- `LENS_FOCUS_DISTANCE`: 0.0f para infinito.

## Gestión de Memoria
- Utilizar `ImageReader` con un pool de buffers estricto (maxImages: 2) para prevenir colapsos de RAM en ráfagas DNG.
- Cerrar cada `Image` inmediatamente en un bloque `finally` dentro del `HandlerThread`.

