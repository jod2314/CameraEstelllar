# Transcripción Técnica: ImageReader y Formatos (RAW/YUV)

**Descripción:**
`ImageReader` actúa como el receptor de los datos binarios provenientes del sensor. Es el "puente" entre el hardware de la cámara y la memoria de la aplicación.

**Formatos Críticos:**
1. **RAW_SENSOR (ImageFormat.RAW_SENSOR):** 
   - Datos puros del sensor (normalmente Bayer de 10-14 bits).
   - Sin procesar, sin comprimir.
   - Es el "negativo digital" esencial para astrofotografía de alta calidad.
2. **YUV_420_888:**
   - Formato de color optimizado para procesamiento.
   - Separa la luminosidad (Y) del color (UV).
   - Ideal para algoritmos de detección de estrellas en tiempo real (IWC).

**Gestión de Memoria y Rendimiento:**
- `maxImages`: Define cuántos frames puede retener el buffer antes de bloquear el pipeline. Para ráfagas de astrofotografía, se recomienda un valor de entre 3 y 7, dependiendo de la RAM disponible.
- `acquireLatestImage()`: Obtiene el frame más reciente, descartando los anteriores. Útil para el visor.
- `acquireNextImage()`: Obtiene el siguiente frame en la cola. Crucial para no perder frames durante una captura de larga exposición o ráfaga.

**Notas Importantes:**
- Siempre se debe cerrar (`close()`) el objeto `Image` obtenido del reader para liberar la memoria del hardware.

**Enlaces de Profundización Identificados:**
- [Efficient Image Processing with YUV](https://developer.android.com/training/articles/perf-tips)
- [Working with RAW Metadata using DngCreator](https://developer.android.com/reference/android/hardware/camera2/DngCreator)
