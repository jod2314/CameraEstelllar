# 🤖 REGLAS Y DIRECTRICES DE DESARROLLO - CAMERASTELLAR V3

## 🌟 Principios Fundamentales
1. **Responder siempre en español:** Todas las interacciones, comentarios en el código, planes, commits y logs deben estar en español.
2. **Revisión Obligatoria:** Para cada decisión técnica o de diseño, se debe revisar este archivo `GEMINI.md` en busca de pautas y restricciones.

---

## 🏗️ Pautas Arquitectónicas y Estándares de Código (Android/Kotlin)

### 1. Arquitectura de Referencia (Clean Architecture + MVVM)
*   **Separación de Conceptos:** La lógica de la interfaz de usuario (Fragmentos) debe estar completamente separada de la lógica de negocio y del hardware de la cámara.
*   **Fragments/Activities (Presentación - Vista):**
    *   No deben inicializar la cámara directamente ni realizar lógica de procesamiento.
    *   Su única responsabilidad es observar estados reactivos (`StateFlow` / `LiveData`) del ViewModel y actualizar la interfaz gráfica.
*   **ViewModels (Presentación - Lógica):**
    *   Mantienen el estado de la UI y exponen flujos reactivos.
    *   Coordinan llamadas a los Casos de Uso/Repositorios.
*   **Controller / Repository (Datos - Cámara):**
    *   `CameraController` centralizará la inicialización de la API `Camera2`, la gestión de la ráfaga de captura y el ciclo de vida de `CameraCaptureSession`.
    *   El procesamiento de imágenes y la interacción con JNI/C++ debe ocurrir en hilos dedicados (background threads).

### 2. Gestión de Hilos y Prevención de Fugas de Memoria (Memory Leaks)
*   **Coroutines (Kotlin):**
    *   Las operaciones de I/O (guardado de archivos `DNG`, logs) deben ejecutarse en `Dispatchers.IO`.
    *   El procesamiento pesado o manipulación de imágenes debe correr en `Dispatchers.Default`.
    *   La actualización de interfaz debe ocurrir estrictamente en `Dispatchers.Main`.
*   **Ciclos de Vida:**
    *   Cerrar de forma segura todas las sesiones de cámara (`CameraCaptureSession.close()`), liberar el dispositivo (`CameraDevice.close()`) y los lectores de imágenes (`ImageReader.close()`) cuando el fragmento o el controlador sea destruido.
    *   No retener referencias a contextos de Activity o Fragment dentro de clases de larga duración (Singletons, Repositorios o ViewModels).

---

## 🛠️ Protocolo de Orquestación Móvil

### Activación del Modo Plan
*   Se activará siempre que el usuario use el prefijo `[PLAN]` o la tarea requiera $\ge 3$ pasos técnicos distintos.

### Flujo de Ejecución en 4 Fases

#### Fase 0: Preparación
1. Verificar `.agents/stack.config.md` en el repositorio.
2. Verificar `.agents/AGENT_CATALOG.md`. Si falta algún agente especialista, definirlo dinámicamente con `define_subagent`.

#### Fase 1: Diseño del Plan
1. Crear o actualizar `PROXIMA_TAREA.md` en la raíz del repositorio con:
   *   Objetivo claro.
   *   Pasos técnicos secuenciales numerados.
   *   Subagente asignado.
   *   Archivos que modificará.
   *   Criterio de aceptación por paso.
   *   Punto de rollback.
2. Presentar el plan al usuario y esperar confirmación explícita.

#### Fase 2: Ejecución Orquestada
Por cada paso aprobado del plan:
1. Invocar el subagente correspondiente (preferiblemente en su propia rama/contexto aislado si es complejo).
2. Tras la ejecución, invocar al **Code Review Agent (Android Edition)**.
   *   *System Prompt del Code Reviewer:*
       ```text
       Eres un agente experto en revisión de código Kotlin/Android para CameraStellar v3.
       Verifica:
       1. Uso correcto de Coroutines (Dispatchers adecuados) y flujos reactivos (StateFlow/SharedFlow).
       2. Gestión segura de recursos de hardware (cámara, buffers, handlers) y prevención de leaks.
       3. Adherencia al patrón MVVM (desacoplamiento de Fragments).
       4. Comentarios y documentación estrictamente en español.
       Responde SOLAMENTE con: APROBADO o RECHAZADO + lista detallada de problemas encontrados.
       ```
3. Si la revisión es RECHAZADA, pausar e informar al usuario. Si es APROBADA, proceder a la fase de pruebas.
4. Marcar el paso como completado en `PROXIMA_TAREA.md` e ir registrando en `docs/HITOS.md`.

#### Fase 3: Gate de Testing y Commit
Antes de realizar cualquier commit en Git:
1. Ejecutar el script de verificación local `.agents/scripts/run_tests.ps1`.
2. Si las pruebas y análisis estático (Lint) **pasan**:
   *   `git add -A`
   *   `git commit -m "hito(scope): descripción concisa"`
   *   Registrar el commit en `docs/HITOS.md`.
3. Si **fallan**:
   *   Ejecutar rollback si es necesario (o corregir inmediatamente).
   *   Registrar el fallo en `docs/HITOS.md` con estado ❌ y notificar al usuario.

#### Fase 4: Cierre
1. Actualizar `docs/walkthrough.md` con un resumen de los cambios.
2. Archivar `PROXIMA_TAREA.md` moviéndolo a `.agents/historia/PROXIMA_TAREA_[fecha].md`.
