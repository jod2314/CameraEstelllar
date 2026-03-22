# CameraStellar v3 - Registro de Proyecto

Este archivo sirve como memoria del proyecto para el desarrollo de la aplicación Android CameraStellar.

## Resumen del Proyecto
- **Nombre:** CameraStellar v3
- **Plataforma:** Android (Nativo)
- **Lenguaje Principal:** Kotlin
- **Paquete:** `com.stellar.camera`
- **Sistema de Construcción:** Gradle
- **Objetivo:** App creativa para captura del cielo nocturno con múltiples modos de captura, superposición/apilado (stacking), correcciones basadas en métricas estandarizadas y detección avanzada de hardware.

## Estado Actual
- **Hito 1 (Cimentación):** Completado. App Nativa Kotlin con MVVM y Red Light Mode funcional.
- **Hito 2 (Captura RAW):** Completado. Implementación de `CameraController` con soporte para ráfagas DNG y control manual.
- **Hito 2.5 (UI Profesional):** Completado. Controles dinámicos de ISO (100-6400), Exposición (1s-30s) y Ráfaga.
- **Limpieza:** Directorio React Native eliminado; proyecto puramente nativo.
- **Infraestructura:** Skill `astro-engineer` activo y repositorio Git con historial de hitos.

## Logros Técnicos
- Migración exitosa de arquitectura híbrida a Nativa Pura para optimización de memoria.
- Implementación de `DngCreator` con sincronización asíncrona de metadatos.
- Configuración de puente NDK (JNI Zero-Copy) listo para procesamiento matemático.
- Interfaz optimizada para visión nocturna (Filtro Rojo).

## Próximos Pasos (Fase 3 & 4)
- Integración de OpenCV nativo para alineación por asterismos.
- Implementación de Compute Shaders en Vulkan para apilamiento masivo on-device.

## Protocolos de Desarrollo
- **Regla Fundamental:** NO SE AVANZA a ninguna fase de procesamiento, IA, o características avanzadas (incluyendo C++/Vulkan) hasta que no tengamos una aplicación 100% funcional para tomar fotos. Este es el instrumento fundamental del proyecto.
- **Control de Versiones:** Cada cambio importante requiere un `git commit` con descripción concisa en español. **Eficiencia:** Optimización energética y de procesamiento desde la base.
- **Validación:** No se avanza al siguiente hito sin una base 100% funcional y libre de bugs.

## Hoja de Ruta Aprobada (Plan Maestro)
**Regla Estricta:** Ejecutar `git add .` y `git commit -m "[Hito X] Descripción"` al finalizar y validar cada fase.

1. **Fase 1: Cimentación (App Base Funcional)** - Proyecto estructurado, MVVM, gestión de permisos estricta, visor de cámara optimizado (sin memory leaks) y Git configurado.
2. **Fase 2: Motor de Captura RAW** - Control manual absoluto (Camera2), `HandlerThread` para evasión de ANRs, ráfagas continuas y guardado DNG (metadatos científicos).
3. **Fase 3: Puente NDK y Visión Computacional** - Transferencia JNI Zero-Copy, detección de estrellas (IWC) y alineación por asterismos usando OpenCV (C++).
4. **Fase 4: Motor de Apilamiento (GPU/Vulkan)** - Compute Shaders en FP16 para reproyección y Sigma-Clipping ultrarrápido on-device.
5. **Fase 5: UI Profesional y Efectos** - Red Light Mode nativo, métricas de enfoque en vivo (Varianza Laplaciana), Star Trails y procesamiento IA de cielo profundo.

## Preferencias de Desarrollo
- **Idioma:** Todas las respuestas y comentarios en el código deben ser en español.
