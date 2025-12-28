# Proyecto: CameraEstellar - Arquitectura de Soluciones de Élite

## 1. Estado del Arte (Snapshot V1.0)
- **Base:** Motor Nativo Java (Camera2 API) estable.
- **Capacidades:** Control manual absoluto (ISO, Shutter 30s, Foco), RAW+JPEG, Temporizador.
- **Infraestructura:** Gestión de memoria optimizada (DirectBuffers), hilos sincronizados.

## 2. Hoja de Ruta: Fase 2 - Astro-Computation Engine
El objetivo es transicionar de una herramienta de captura a un sistema de procesamiento de señal astronómica.

### 2.1. El Secuenciador (Ráfaga Inteligente)
**Objetivo:** Automatización de adquisición de datos ($n$ muestras).
- Implementar máquina de estados para captura continua (Bucle de Captura).
- Gestión de Buffers para ráfagas de archivos pesados (RAW/DNG).
- Feedback visual de progreso (ej. "Capturando 5/20...").

### 2.2. Algoritmos de Alineación (Star Registration)
**Objetivo:** Compensación matemática de la rotación terrestre.
- **Detección:** Algoritmos de umbral para identificar centroides de estrellas.
- **Transformación:** Cálculo de matrices de traslación y rotación entre frames.
- **Tecnología:** Evaluación de implementación en Java optimizado vs. integración de OpenCV (NDK).

### 2.3. Motor de Apilado (Stacking Engine)
**Objetivo:** Maximización de SNR (Signal-to-Noise Ratio).
- Implementación de algoritmos estadísticos:
    - **Promedio (Average):** Para aumento de señal base.
    - **Mediana (Median):** Para eliminación de ruido transitorio (satélites/aviones).
- Fusión de imágenes alineadas.

### 2.4. Modos de Cielo (Heurísticas)
- **Espacio Profundo:** Max Exposición + Stacking Promedio + Alineación.
- **Vía Láctea:** ISO Alto + Stacking Mediana + Alineación.
- **Star Trails:** Max Exposición + Stacking Máximo (Lighten) + Sin Alineación.

## 3. Stack Tecnológico Híbrido
- **Control:** React Native (UI/Orquestación).
- **Driver:** Android Java (Camera2 API).
- **Cómputo:** Evaluación de OpenCV / C++ para operaciones matriciales pesadas ($O(n^2)$).

## 5. Estado de la Sesión (Punto de Retorno)
- **Última Acción:** Commit de la V1.0 funcional y definición de la arquitectura de la Fase 2.
- **Punto de Retorno:** Iniciar la **Fase 2.1: El Secuenciador**. 
- **Tareas Pendientes Inmediatas:**
    1. Diseñar el estado en React Native para manejar ráfagas (n fotos).
    2. Modificar el puente nativo para permitir disparos consecutivos automáticos.
    3. Preparar la estructura para recibir OpenCV en la Fase 2.2.
- **Nota técnica:** El "Driver" nativo en `AstroCameraView.java` es estable y soporta RAW; la infraestructura está lista para la ráfaga.


## 5. Estado del Entorno
- **JDK:** 17 (C:\Program Files\Android\Android Studio\jbr).
- **Gradle:** 8.13.
- **SDK Android:** API 36 (Android 15).
- **Dispositivo:** Físico detectado y funcional.
- **Idioma de Interacción:** ESPAÑOL (Mandatorio para toda comunicación con el usuario).
