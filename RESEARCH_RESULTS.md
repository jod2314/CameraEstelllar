# Registro de Investigación Técnica - CameraStellar v3

## Descubrimiento Técnico: Comunicación Camera2 en Samsung (2026)

### El Problema Detectado
Los dispositivos Samsung gama alta (S-series) realizan un *gatekeeping* (bloqueo) de las capacidades de larga exposición (30s) en la API pública de Camera2, reportando límites de 0.2s o 1s para aplicaciones de terceros.

### La Solución: "La Consulta Correcta" (V19)
Para obtener información real y ejecutar capturas de 30s, se implementó la siguiente arquitectura:

1.  **Auditoría de Duración de Frame:** No confiar en `SENSOR_INFO_EXPOSURE_TIME_RANGE`. La fuente de verdad es `SENSOR_INFO_MAX_FRAME_DURATION`. Si este valor es > 1s, el hardware es físicamente capaz de larga exposición.
2.  **Apertura Directa de IDs:** Ignorar el ID lógico "0" si es restrictivo. El escaneo debe iterar sobre IDs del 0 al 100 y mapear `physicalCameraIds`. Abrir el ID (físico o lógico) que reporte la mayor duración de frame directamente con `openCamera()`.
3.  **Configuración de Sesión Pro:** Los parámetros manuales (`CONTROL_MODE_OFF`, `CONTROL_AE_MODE_OFF`) deben inyectarse mediante `SessionConfiguration.setSessionParameters()` en el momento de creación de la sesión, no después.
4.  **Sincronización Cruda:** Para ráfagas largas, la tolerancia de emparejamiento entre metadatos y buffers RAW debe ser de al menos 2ms (`2_000_000` ns) para compensar la latencia del procesador de imagen (ISP) en tomas de 30s.
5.  **Apertura Variable:** Detectar `LENS_INFO_AVAILABLE_APERTURES` y forzar la apertura más cerrada o abierta según el modo para estabilizar el reporte del HAL.

### Estado del Pipeline
- **ISO:** Control manual analógico verificado.
- **Exposición:** 30 segundos reales verificados por logs de hardware.
- **RAW:** Guardado DNG exitoso con metadatos científicos sincronizados.
