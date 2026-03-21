# Información Inicial del Proyecto
(Pega aquí la información técnica, de hardware o previa del proyecto)
1. 📱 Arquitectura del Sistema
•	Frontend (Nativo Kotlin): Para un proyecto de esta magnitud, el desarrollo nativo es obligatorio. Frameworks como Flutter o React Native introducen puentes (bridges) que penalizan el rendimiento al manejar buffers de memoria masivos, como ráfagas RAW de 25 MB por fotograma. La interfaz debe implementar un patrón MVVM apoyado en Corrutinas y aislar el tráfico de la cámara en un HandlerThread dedicado en segundo plano para evadir bloqueos de la UI (errores ANR).
•	Backend (Microservicios opcionales): La arquitectura será de filosofía Offline-First (procesamiento local en el borde). El backend se reservará exclusivamente para tareas asíncronas no bloqueantes, como la sincronización de catálogos de estrellas (ej. Hipparcos o Gaia para Plate Solving) o el almacenamiento en la nube de archivos FITS generados.
•	Procesamiento Local (GPU / Vulkan): El pilar de la arquitectura es procesar los datos On-Device. Para evitar la saturación de RAM de la máquina virtual de Android (Garbage Collector), se debe usar memoria no administrada fuera del montículo (Off-Heap) a través del NDK. Los datos capturados en el ImageReader se pasan a C++ mediante DirectByteBuffer (Transferencia Zero-Copy). Posteriormente, las matemáticas por píxel se derivan a la GPU programando Compute Shaders en Vulkan utilizando precisión media (FP16) para duplicar el rendimiento y ahorrar batería.
•	Pipeline de Procesamiento: Camera2 API (RAW_SENSOR) → DirectByteBuffer (JNI) → Calibración Radiométrica (C++) → Alineación de Asterismos (OpenCV/CPU) → Apilamiento y Sigma-Clipping (Vulkan/GPU) → Compresión y Metadatos (DngCreator).
2. 📸 Módulo de Captura de Imagen
•	Uso de API (Camera2): Se debe utilizar estrictamente Camera2. Las librerías de alto nivel como CameraX abstraen demasiado y ocultan capacidades de ráfagas a alta velocidad o RAW puro.
•	Detección de Nivel de Hardware: La aplicación debe consultar CameraCharacteristics y validar que el dispositivo retorne INFO_SUPPORTED_HARDWARE_LEVEL_FULL o LEVEL_3. Esto garantiza control manual por fotograma y salida de buffers RAW.
•	Captura RAW (DNG) y Bloqueo 3A: Es obligatorio configurar el CaptureRequest.CONTROL_MODE en CONTROL_MODE_OFF. Esto desactiva el enfoque, exposición y balance de blancos automáticos. Los fotones capturados en formato ImageFormat.RAW_SENSOR se empaquetan al final usando DngCreator, el cual incrusta perfiles de ruido y matrices de color vitales para el procesamiento científico.
•	Estrategia para Astrofotografía Móvil: Los sensores móviles suelen limitar la exposición máxima a fracciones de segundo (ej. 0.8s) por restricciones térmicas. La estrategia consiste en aplicar la Regla de los 500 y simular una exposición prolongada mediante una ráfaga (burst) de fotogramas de exposición media (ej. 20 fotos de 15s) usando CameraCaptureSession.captureBurst.
3. 🧪 Procesamiento de Imagen
•	Calibración Radiométrica: Antes de fusionar, cada fotograma crudo (Light Frame) debe limpiarse de anomalías. Esto se logra aplicando la ecuación: Radiancia = (RAW - Bias) / (FlatField * Ganancia) - Dark.
o	Dark Frame Subtraction: Fotoramas tomados con el obturador tapado para aislar y restar el ruido térmico y píxeles calientes ("hot pixels").
o	Flat Field Correction: Cuadros de luz uniforme para corregir el oscurecimiento de las esquinas (viñeteado) y manchas de polvo.
•	Alineación Estelar (Registration): Debido a la rotación terrestre, el cielo se mueve entre tomas. Se deben detectar las estrellas calculando su "Centro de Gravedad Ponderado por Intensidad" para lograr precisión sub-píxel. Luego, se usan árboles kD para emparejar asterismos (triángulos de estrellas) y, mediante RANSAC, calcular la matriz de homografía que alinea todos los cuadros con el de referencia.
•	Image Stacking y Noise Reduction: Una vez alineados, se fusionan los fotogramas. Para rechazar aviones o satélites (Starlink), se utiliza Sigma-Clipping estadístico, descartando píxeles atípicos antes de promediar el resto, aumentando la relación Señal-Ruido proporcionalmente a N .
4. 📊 Métricas y Validación
•	SNR (Signal-to-Noise Ratio): Se calcula en tiempo real comparando la intensidad media de la señal estelar contra la varianza del fondo del cielo oscuro. Dictamina si la ráfaga requiere más exposición.
•	Histograma de Luminancia: Crucial para evitar que el núcleo de las estrellas se sature (clipping) o que el fondo se pierda en el ruido de lectura. Se analiza para lograr exponer "hacia la derecha" sin quemar luces.
•	Nitidez (Varianza del Laplaciano): Se aplica un filtro Laplaciano sobre la previsualización. Su varianza alcanza un valor escalar máximo de forma exacta cuando las estrellas ocupan la menor cantidad de píxeles, logrando el enfoque al infinito perfecto.
•	FWHM (Full Width at Half Maximum): Una métrica de grado científico que evalúa la calidad atmosférica (seeing) ajustando una curva Gaussiana sobre el perfil de luz de una estrella.
5. 🎨 Generación de Efectos
•	Star Trails: Se logra cambiando el algoritmo de compilación a Intensidad Máxima (Maximum Stacking). En lugar de promediar, retiene solo el píxel más brillante a través del tiempo, creando arcos continuos por la rotación de la Tierra.
•	Deep Sky Enhancement: Las nebulosas requieren un estiramiento no lineal del histograma (ej. función asinh o logarítmica) para revelar estructuras tenues sin quemar las estrellas brillantes que las rodean.
•	Superposición Inteligente de Frames (HDR Nocturno): Integrar modelos ligeros de segmentación semántica en la NPU para aislar el paisaje del cielo estrellado. Permite fusionar una exposición larga del suelo con la ráfaga apilada del cielo sin comprometer la nitidez.
6. ⚙️ Optimización Según Hardware
•	Detección y Adaptación Dinámica: La API lee los límites estáticos mediante SENSOR_INFO_EXPOSURE_TIME_RANGE e SENSOR_INFO_SENSITIVITY_RANGE. Si la ganancia requerida supera el límite analógico, se compensa algorítmicamente y se bloquea a su ISO Nativo para no introducir ruido de cuantización digital.
•	On-Device ML: Utilización de TensorFlow Lite (o NNAPI en Android) ejecutándose en el silicio dedicado (NPU) para tareas como la reducción de gradientes de contaminación lumínica o la mejora algorítmica de ráfagas.
7. 🧩 Stack Tecnológico Recomendado
•	Core y UI: Kotlin + Corrutinas + Android SDK (Camera2 API).
•	Visión Computacional y Motor Matemático: C++17 a través del Android NDK. OpenCV para la detección de características e interpolaciones, y la librería Eigen para álgebra lineal (matrices de homografía).
•	Aceleración por Hardware: API Vulkan utilizando Compute Shaders precompilados en SPIR-V para procesar el apilamiento de la memoria RAW paralelizada.
•	Inteligencia Artificial: TensorFlow Lite para la segmentación del paisaje y remoción de polución lumínica.
8. 🚀 MVP (Producto Mínimo Viable)
•	Funcionalidades Esenciales (Hito 1 y 2): Un motor manual absoluto de RAW sobre Camera2, sistema de ráfagas continuo, un algoritmo básico de alineación C++ (Astroalign) promediando 30 cuadros en memoria Off-Heap y visualización del enfoque con Varianza Laplaciana.
•	Retos Técnicos Principales: El cuello de botella en transferencia de memoria desde el ImageReader al NDK sin agotar la RAM, la limitación agresiva de exposición impuesta por el driver del fabricante, y la calibración térmica sin refrigeración en el dispositivo móvil.
•	Roadmap de Desarrollo:
1.	Core de Captura RAW y métricas en vivo.
2.	Calibración y Motor de Alineación (C++).
3.	GPU Stacking (Vulkan).
4.	Efectos IA e Interfaz de Usuario.
9. 🔬 Enfoque Innovador
•	IA para Polución Lumínica: Entrenar un modelo para estimar el gradiente de luz artificial en cielos de ciudad y aplicar una sustracción adaptativa, permitiendo fotografiar nebulosas en cielos clase Bortle 8/9.
•	Simulación Previa (Predictive View): Fusionar el giroscopio, el magnetómetro (AHRS) y las efemérides astronómicas en un "Star Chart" (AR). El usuario verá en vivo dónde aparecerá el núcleo de la Vía Láctea o cómo se compondrá la foto antes de iniciar una exposición de 10 minutos.
•	Recomendaciones Automáticas: Implementar un medidor de calidad del cielo (SQM virtual) calibrando el sensor para medir mag/arcsec². Basado en esta métrica, la IA sugiere el tiempo total de integración óptimo.
10. 💻 Ejemplos Técnicos
A. Configuración de Captura Multi-frame (Camera2 / Kotlin):
// Desactivar algoritmo 3A y configurar ráfaga manual
val captureBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_MANUAL).apply {
    set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_OFF)
    set(CaptureRequest.SENSOR_EXPOSURE_TIME, 15_000_000_000L) // 15 segundos en nanosegundos
    set(CaptureRequest.SENSOR_SENSITIVITY, 1600) // ISO Manual
    set(CaptureRequest.LENS_FOCUS_DISTANCE, 0.0f) // Enfoque al infinito
    addTarget(rawImageReader.surface)
}

val burstList = List(30) { captureBuilder.build() } // Ráfaga de 30 fotos
captureSession.captureBurst(burstList, captureCallback, backgroundHandler)
B. Evaluación de Nitidez mediante Varianza del Laplaciano (C++ / OpenCV):
// Código JNI (C++) para calcular el enfoque perfecto
extern "C" JNIEXPORT jdouble JNICALL
Java_com_astro_app_NativeProcess_laplacianVariance(JNIEnv *env, jobject, jobject buffer, jint width, jint height) {
    void* pixels = env->GetDirectBufferAddress(buffer);
    cv::Mat img(height, width, CV_8UC1, pixels); // Usar canal de luminancia Y
    
    cv::Mat laplacian;
    cv::Laplacian(img, laplacian, CV_64F); // Calcular segunda derivada espacial
    
    cv::Scalar mean, stddev;
    cv::meanStdDev(laplacian, mean, stddev);
    
    double variance = stddev.val * stddev.val; // Varianza = stddev^2
    return variance; // Mayor varianza = estrellas más nítidas/enfocadas
}
C. Algoritmo Básico de Stacking (Promedio Píxel a Píxel en C++):
// Pseudocódigo de Stacking matemático Off-Heap (C++)
void stackImages(std::vector<cv::Mat>& alignedFrames, cv::Mat& outputBuffer) {
    int totalFrames = alignedFrames.size();
    cv::Mat accumulator = cv::Mat::zeros(alignedFrames.size(), CV_32FC3); // Float 32 para evitar desbordamiento
    
    for (const auto& frame : alignedFrames) {
        cv::Mat floatFrame;
        frame.convertTo(floatFrame, CV_32FC3); // Promover a 32-bit linear
        cv::add(accumulator, floatFrame, accumulator);
    }
    
    accumulator = accumulator / totalFrames; // Promedio aritmético para mejorar SNR
    accumulator.convertTo(outputBuffer, CV_16UC3); // Retornar RAW a 16-bit para DngCreator
}
Informe de Arquitectura y Especificaciones Técnicas: App de Astrofotografía Móvil Computacional
Como Arquitecto de Sistemas y Especialista en Fotografía Computacional, he consolidado todo nuestro trabajo y análisis técnico en este informe general. Este documento detalla la visión, la arquitectura profunda, el flujo de datos y las características de nuestra aplicación, diseñada para transformar un dispositivo móvil estándar en un instrumento de observación astronómica de grado científico y creativo
.

--------------------------------------------------------------------------------
1. Arquitectura del Sistema (Full-Stack Móvil)
   El sistema ha sido diseñado con una filosofía de procesamiento en el borde (On-Device), eludiendo el sobrecoste de la máquina virtual de Android (ART) para manejar ráfagas masivas de datos fotónicos sin agotar la memoria ni congelar el dispositivo
   .
   Gestión de Concurrencia (Anti-ANR): La interfaz de usuario (UI) se construye en Kotlin utilizando corrutinas (MVVM). Para evitar errores de "Aplicación no Responde" (ANR), todo el pesado tráfico de la cámara se aísla en un HandlerThread de fondo dedicado
   .
   Memoria "Off-Heap" y Zero-Copy: El procesamiento de decenas de archivos RAW masivos agotaría rápidamente el montículo (Heap) de Java
   . Utilizamos la Interfaz Nativa de Java (JNI) con DirectByteBuffer para pasar los punteros de los píxeles directamente a C++ (NDK) sin realizar copias de memoria redundantes
   .
   Aceleración por GPU: Todo el apilamiento y filtrado matemático se paralela utilizando Compute Shaders en Vulkan
   .
2. Módulo de Captura (Hardware & Camera2 API)
   Rechazamos las abstracciones de alto nivel y utilizamos estrictamente la Camera2 API para tomar el control absoluto del sensor
   .
   Validación de Hardware: La aplicación consulta el CameraCharacteristics para asegurar que el dispositivo reporta un nivel de soporte FULL o LEVEL_3, garantizando la capacidad de capturar ráfagas en formato RAW y realizar control manual
   .
   Bloqueo 3A y Control Físico: Se desactiva el procesamiento del fabricante enviando el CaptureRequest con CONTROL_MODE_OFF
   . La app dicta manualmente el SENSOR_EXPOSURE_TIME y el ISO óptimo nativo
   .
   Empaquetado Científico (DNG): Solicitamos el formato RAW_SENSOR y utilizamos la clase DngCreator
   . Esto incrusta metadatos vitales para la astrofísica en la cabecera del archivo, como la matriz de calibración de color, los niveles de negro (SENSOR_DYNAMIC_BLACK_LEVEL) y el perfil de ruido del sensor (SENSOR_NOISE_PROFILE)
   .
3. Motor de Procesamiento y Visión Computacional (C++ y OpenCV)
   Los datos lineales RAW se procesan en C++ antes de convertirse en una imagen visible
   .
   Calibración Radiométrica: Restamos el ruido térmico y electrónico (Dark Frames y Bias) y dividimos por el Flat Field para eliminar viñeteo y manchas del sensor, utilizando instrucciones SIMD (NEON) de la CPU
   .
   Alineación Estelar (Registration): Debido a la rotación de la Tierra, las estrellas se mueven. Usamos OpenCV para calcular el Centro de Gravedad Ponderado por Intensidad (IWC) de cada estrella (precisión sub-píxel)
   . Luego construimos asterismos (triángulos) y los emparejamos usando árboles kD
   . Finalmente, calculamos una matriz de homografía utilizando RANSAC para alinear perfectamente todos los fotogramas
   .
4. Compilación y Apilamiento (Stacking en Vulkan GPU)
   Para fusionar las imágenes en milisegundos, inyectamos la matriz de homografía en la GPU del teléfono.
   Precisión Media (FP16): Los shaders de Vulkan se programan en float16_t (mediump), lo cual duplica el rendimiento en GPUs móviles (como Adreno o Mali) y evita el ahogamiento térmico del teléfono
   .
   Rechazo Estadístico (Sigma-Clipping): El algoritmo descarta matemáticamente los píxeles anómalos (satélites de Starlink, aviones) midiendo su desviación estándar, antes de promediar las imágenes para aumentar drásticamente la Relación Señal-Ruido (SNR)
   .
5. Metrología Óptica y Validación en Tiempo Real
   La aplicación no solo captura, sino que evalúa científicamente la calidad de los datos mostrándola al usuario en tiempo real
   .
   Asistente de Enfoque Perfecto: Evalúa la Varianza Laplaciana de la escena. El número alcanza su pico máximo exacto cuando las estrellas ocupan la menor cantidad de píxeles posibles (enfoque al infinito perfecto)
   .
   Evaluación del Seeing Atmosférico: Cálculo de FWHM (Full Width at Half Maximum) ajustando una curva gaussiana a las estrellas para evaluar su nitidez
   .
   Medidor de Calidad del Cielo (SQM): Utiliza los datos fotométricos para estimar la oscuridad del cielo en mag/arcsec
   2
   y deducir la escala de Bortle, informando si la contaminación lumínica permite la captura
   .
   Monitor de SNR y Luminancia: Histograma en vivo para exponer correctamente sin quemar (saturar) los núcleos estelares
   .
6. Interfaz de Usuario (UX) y Modos Creativos
   El diseño de la UI está pensado estrictamente para entornos nocturnos y la comodidad del astrofotógrafo.
   Red Light Mode (Modo de Luz Roja): Una capa obligatoria que tiñe la pantalla de rojo puro para no destruir la visión escotópica (adaptación a la oscuridad) del usuario
   .
   Composición Predictiva (Realidad Aumentada): Fusión de giroscopio, magnetómetro y efemérides astronómicas en un "Star Chart" (AR) sobre la previsualización, permitiendo encuadrar galaxias o la Vía Láctea antes de que aparezcan en la foto de larga exposición
   .
   Efectos Únicos:
   Star Trails: Utiliza compilación de "Intensidad Máxima" para dejar únicamente el trazo luminoso de la rotación terrestre
   .
   Deep Sky Enhancement: Algoritmos de estiramiento no lineal (asinh) para revelar nebulosas ocultas sin saturar las estrellas
   .
   HDR Nocturno (Foreground Blending): Modelos ligeros de Machine Learning on-device (CoreML / TFLite) para segmentar el suelo y enmascararlo, permitiendo fusionar el cielo apilado con el paisaje terrestre con gran nitidez
   .
7. Plan de Desarrollo (Roadmap / MVP)
   Fase 1 (Core): Establecimiento del flujo Camera2 API, validación de hardware, captura RAW DngCreator asíncrona mediante HandlerThread
   .
   Fase 2 (Motor C++): Implementación JNI Zero-Copy, calibración de campos (Flats/Darks), cálculo de homografías RANSAC con OpenCV y métricas de enfoque (Varianza Laplaciana)
   .
   Fase 3 (GPU & UI): Integración de Vulkan Compute Shaders (FP16) para Sigma-Clipping, desarrollo de la UI en Red Light Mode y AR
   .
   Fase 4 (IA & Pulido): Redes neuronales ligeras para eliminación de gradientes de contaminación lumínica (Bortle map) y segmentación de paisajes
   .
   Este informe establece los pilares de un producto de calidad profesional (tipo Google Pixel Computational Photography), listos para guiar la implementación paso a paso de cada bloque funcional
   .