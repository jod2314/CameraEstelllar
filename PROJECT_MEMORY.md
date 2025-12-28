# Proyecto: CameraEstellar

## 1. Información General
- **Objetivo Principal:** Desarrollar una aplicación de cámara profesional para Android.
- **Objetivo Especializado (Fase 2):** Astrofotografía, Larga Exposición (manual shutter/ISO), Stacking (apilado de imágenes) y soporte RAW.
- **Tecnología Base:** React Native (CLI).
- **Lenguajes:** TypeScript (Frontend), Kotlin/Java (Módulos Nativos Android).
- **Estado:** Inicialización.

## 2. Decisiones Técnicas
- **Plataforma:** Android (win32 development environment).
- **IDE:** Visual Studio Code (Desarrollo), Android Studio (Compilación/SDK).
- **Librería Core (Fase 1):** `react-native-vision-camera`.
- **Estrategia Fase 2:** Implementación de módulos nativos (Native Modules) para acceso a Camera2 API nivel hardware.

## 3. Historial de Cambios Relevantes
- **[Fecha Actual]:** 
    - Mejora en Selección de Cámara: Implementado algoritmo inteligente en `AstroCameraView.java` que prioriza sensores con capacidad `MANUAL_SENSOR` y mayor tamaño de píxel (mejor para baja luz) sobre la resolución bruta.
    - Solución a "Pantalla Negra": Implementación de solicitud de permisos en Runtime (App.tsx) y corrección de LayoutParams en AstroCameraView.java.
    - Build exitoso del módulo nativo usando JDK 17 (Android Studio JBR).
- **[Anterior]:** Creación del archivo de memoria. Definición de stack tecnológico.

## 4. Próximos Pasos Inmediatos (Fase de Estabilización y Expansión)
1. **Auditoría del Sistema:** Inspección profunda de código para prevenir fugas de memoria, conflictos de hilos y errores a largo plazo (En Progreso).
2. **Soporte RAW (DNG):** Implementar captura de archivos DNG para edición profesional.
3. **Temporizador:** Añadir retardo (3s/10s) al disparo para evitar vibraciones.
4. **Enfoque Manual:** Implementar control deslizante de foco (Focus Distance) para fijar enfoque al infinito.
5. **Stacking Básico:** Automatizar captura de múltiples exposiciones consecutivas para apilado.

## 5. Estado del Entorno
- **JDK:** 17 (C:\Program Files\Android\Android Studio\jbr).
- **Gradle:** 8.13.
- **SDK Android:** API 36 (Android 15).
- **Dispositivo:** Físico detectado y funcional.
