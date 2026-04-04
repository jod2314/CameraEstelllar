# Investigación Técnica: CameraStellar v3 - Resultados de Auditoría y Roadmap Científico

Este documento consolida la ingeniería forense, el análisis de silicio y la estrategia de implementación para el control manual de grado profesional.

## 1. Análisis Forense: Resolviendo el Colapso del Hardware
Se han identificado los fallos raíz que desestabilizan el pipeline en ráfagas RAW largas.

### 1.1. Pánico del JankMonitor y Timestamps
- **Síntoma:** Advertencias `JankMonitorFacade: PHOTO > abs Δ(result sensor timestamp) = 80ms`.
- **Causa:** El sistema asume que el hilo de cámara se ha colgado al dilatar el `SENSOR_FRAME_DURATION` para exposiciones de >10s.
- **Solución:** Aislamiento del pipeline en un `ForegroundService` (Silo de Procesamiento) de tipo `camera|mediaProcessing`.

### 1.2. Inestabilidad por OIS (Estabilización)
- **Síntoma:** `OisListener: Null pointer for OIS data`.
- **Causa:** El driver intenta consultar el giroscopio para OIS mientras los algoritmos 3A están en `OFF`.
- **Solución:** Inyección mandatoria de `LENS_OPTICAL_STABILIZATION_MODE_OFF`.

## 2. Micro-Optimización del CaptureRequest (Manual Override)
Para garantizar linealidad absoluta y evadir filtros ISP destructivos:

| Parámetro | Valor | Propósito |
| :--- | :--- | :--- |
| `EDGE_MODE` | `OFF` | Evitar realce de bordes artificial en estrellas. |
| `NOISE_REDUCTION_MODE` | `OFF` | Preservar el grano RAW para Sigma-Clipping. |
| `TONEMAP_MODE` | `OFF` / `FAST` | Impedir la compresión del rango dinámico. |
| `SHADING_MODE` | `OFF` | Evitar corrección de viñeteo destructiva. |
| `STATISTICS_LENS_SHADING_MAP_MODE` | `ON` | Extraer mapa de calibración Flat-Field. |

## 3. Arquitectura de Datos y Memoria (Zero-Copy)

### 3.1. Pipeline AHardwareBuffer
- **Objetivo:** Eliminar la presión sobre el Garbage Collector (GC) de Java.
- **Implementación:** Uso de `AImage_getHardwareBuffer` en el NDK para mapear el buffer del sensor directamente a `cv::Mat` (OpenCV) o texturas de Vulkan sin copias.

### 3.2. Alineación Android 15 (16 KB)
- **Requerimiento:** Compilación obligatoria con `-Wl,-z,max-page-size=16384`.
- **Impacto:** Optimización del TLB para ráfagas masivas de 48MP.

## 4. Roadmap de Implementación (Hitos Críticos)

### Hito 1: Blindaje y Orquestación (Implementado)
- Negociación HAL v2 con `SessionParameters`.
- Integración de `AstroCaptureService` para prioridad de sistema.
- Uso de `WakeLock` para evitar el Deep Sleep en exposiciones >30s.

### Hito 2: Calibración Estelar (NDK)
- Implementación de sustracción de "Dark Frame" en C++.
- Interpolación bicúbica del `LensShadingMap` para corrección Flat-Field en tiempo real.

### Hito 3: Motor de Apilado (GPU/Vulkan)
- Compute Shaders en SPIR-V para alineación por asterismos (Astroalign).
- Algoritmo de Sigma-clipping para reducción de ruido térmico.

---
*Documento actualizado según la Hoja de Ruta de Ingeniería de Precisión - 4 de Abril de 2026*
