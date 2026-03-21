package com.cameraestellar;

import android.content.ContentValues;
import android.content.Context;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.DngCreator;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.net.Uri;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.provider.MediaStore;
import android.util.Log;
import android.util.Size;
import android.view.Surface;
import android.view.TextureView;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.ReactContext;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.uimanager.events.RCTEventEmitter;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.WritableByteChannel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class AstroCameraView extends FrameLayout implements TextureView.SurfaceTextureListener {

    private static final String TAG = "AstroCamera";
    private TextureView mTextureView;
    private String mCameraId;
    
    private final Object mCameraStateLock = new Object();

    private CameraDevice mCameraDevice;
    private CameraCaptureSession mCaptureSession;
    private CaptureRequest.Builder mPreviewRequestBuilder;
    private ImageReader mJpegReader;
    private ImageReader mRawReader;
    private CameraCharacteristics mCameraChars;
    private android.util.Range<Integer> mIsoRange;
    private android.util.Range<Long> mExposureRange;
    
    // Manejo de condición de carrera para RAW (DNG)
    private final Map<Long, Image> mPendingRawImages = new ConcurrentHashMap<>();
    private final Map<Long, TotalCaptureResult> mPendingCaptureResults = new ConcurrentHashMap<>();

    private HandlerThread mBackgroundThread;
    private Handler mBackgroundHandler;

    // Valores manuales
    private int mIso = 800;
    private long mExposureNs = 100000000L; 
    private float mFocusDistance = 0.0f; // 0.0 = Infinito (Default para Astro)
    
    // Tarea de actualización con Debounce
    private final Runnable mUpdatePreviewTask = new Runnable() {
        @Override
        public void run() {
            updatePreview();
        }
    };

    public AstroCameraView(@NonNull Context context) {
        super(context);
        init();
    }

    private void init() {
        mTextureView = new TextureView(getContext());
        mTextureView.setSurfaceTextureListener(this);
        addView(mTextureView, new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, 
            FrameLayout.LayoutParams.MATCH_PARENT
        ));
    }

    public void setIso(int iso) {
        this.mIso = iso;
        scheduleUpdatePreview();
    }

    public void setExposure(double seconds) {
        this.mExposureNs = (long) (seconds * 1_000_000_000.0);
        scheduleUpdatePreview();
    }

    public void setFocusDistance(float distance) {
        this.mFocusDistance = distance;
        scheduleUpdatePreview();
    }

    private void scheduleUpdatePreview() {
        if (mBackgroundHandler != null) {
            mBackgroundHandler.removeCallbacks(mUpdatePreviewTask);
            mBackgroundHandler.postDelayed(mUpdatePreviewTask, 50);
        }
    }

    private int getClampedIso(int iso) {
        if (mIsoRange == null) {
            Log.w(TAG, "Rango ISO no disponible, usando valor por defecto.");
            return iso;
        }
        return Math.max(mIsoRange.getLower(), Math.min(iso, mIsoRange.getUpper()));
    }

    private long getClampedExposure(long exposureNs) {
        if (mExposureRange == null) {
            Log.w(TAG, "Rango Exposición no disponible, usando valor por defecto.");
            return exposureNs;
        }
        // MODIFICACIÓN: No limitamos el tope superior (Math.min eliminado).
        // Intentamos enviar el valor solicitado aunque exceda lo que el driver dice soportar.
        // Solo mantenemos el límite inferior para evitar valores negativos o cero.
        return Math.max(mExposureRange.getLower(), exposureNs);
    }

        // Variable de ráfaga
        private int mBurstCount = 1;
    
        public void setBurstCount(int count) {
            this.mBurstCount = Math.max(1, count);
        }
    
        public void takePicture() {
            synchronized (mCameraStateLock) {
                if (mCameraDevice == null || mCaptureSession == null) {
                    Log.e(TAG, "Cámara no lista para capturar.");
                    return;
                }
                try {
                    Log.d(TAG, "Iniciando captura. Burst Count: " + mBurstCount);
                    
                    // 1. Preparar Builder Base - PREFERIR TEMPLATE_MANUAL (6)
                    // TEMPLATE_MANUAL ofrece mejor control sobre ganancia y exposición y desactiva post-proceso agresivo.
                    int templateType = CameraDevice.TEMPLATE_MANUAL;
                    // Fallback a STILL_CAPTURE si MANUAL no está soportado (raro en dispositivos Camera2 decentes)
                    // Verificamos capabilities previamente idealmente, pero try-catch capturará si falla la creación.
                    
                    final CaptureRequest.Builder captureBuilder = mCameraDevice.createCaptureRequest(templateType);
                    captureBuilder.addTarget(mJpegReader.getSurface());
                    if (mRawReader != null) captureBuilder.addTarget(mRawReader.getSurface());
    
                    // 2. Parámetros Manuales (Límite Hardware 0.15s)
                    int clampedIso = getClampedIso(mIso);
                    long clampedExposure = getClampedExposure(mExposureNs);
                    
                    Log.w(TAG, "=== CAPTURA DATOS ===");
                    Log.w(TAG, "Cámara ID Actual: " + mCameraId);
                    Log.w(TAG, "Solicitado ISO: " + mIso + " -> Clamped: " + clampedIso);
                    Log.w(TAG, "Solicitado Exp: " + (mExposureNs/1e9) + "s -> Clamped: " + (clampedExposure/1e9) + "s");
                    Log.w(TAG, "Rango Exp Hardware: " + (mExposureRange.getLower()/1e9) + "s - " + (mExposureRange.getUpper()/1e9) + "s");
    
                    // Forzar modo manual
                    captureBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_OFF);
                    // CONTROL_CAPTURE_INTENT_MANUAL es redundante con TEMPLATE_MANUAL pero asegura la intención
                    captureBuilder.set(CaptureRequest.CONTROL_CAPTURE_INTENT, CameraMetadata.CONTROL_CAPTURE_INTENT_MANUAL);
                    captureBuilder.set(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_OFF);
                    captureBuilder.set(CaptureRequest.CONTROL_AF_MODE, CameraMetadata.CONTROL_AF_MODE_OFF);
                    captureBuilder.set(CaptureRequest.CONTROL_AWB_MODE, CameraMetadata.CONTROL_AWB_MODE_OFF);
                    
                    // Desactivar cualquier "Scene Mode" o "Effect Mode"
                    captureBuilder.set(CaptureRequest.CONTROL_SCENE_MODE, CameraMetadata.CONTROL_SCENE_MODE_DISABLED);
                    captureBuilder.set(CaptureRequest.CONTROL_EFFECT_MODE, CameraMetadata.CONTROL_EFFECT_MODE_OFF);
                    
                    // Hotfix para algunos Samsung/Xiaomi: Desactivar ZSL (Zero Shutter Lag) si está activo implícitamente
                    // captureBuilder.set(CaptureRequest.CONTROL_ENABLE_ZSL, false); // Requiere API 26+
                    
                    captureBuilder.set(CaptureRequest.SENSOR_SENSITIVITY, clampedIso);
                    captureBuilder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, clampedExposure);
                    captureBuilder.set(CaptureRequest.SENSOR_FRAME_DURATION, clampedExposure + 5_000_000L); // +5ms overhead
                    captureBuilder.set(CaptureRequest.LENS_FOCUS_DISTANCE, mFocusDistance);
                    captureBuilder.set(CaptureRequest.JPEG_ORIENTATION, 90);
    
                    // 3. Crear lista de solicitudes para la ráfaga
                    List<CaptureRequest> burstRequests = new ArrayList<>();
                    for (int i = 0; i < mBurstCount; i++) {
                        captureBuilder.setTag(i); // Marcar índice
                        burstRequests.add(captureBuilder.build());
                    }
    
                    CameraCaptureSession.CaptureCallback captureCallback = new CameraCaptureSession.CaptureCallback() {
                        @Override
                        public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
                            Integer index = (Integer) request.getTag();
                            int idx = (index != null) ? index : 0;
                            
                            Log.d(TAG, "Captura " + (idx + 1) + "/" + mBurstCount + " completada.");
                            handleCaptureResult(result);
                            
                            // Si es la última foto, finalizar
                            if (idx == mBurstCount - 1) {
                                scheduleUpdatePreview();
                                WritableMap params = Arguments.createMap();
                                params.putBoolean("success", true);
                                sendEvent("topCaptureEnded", params);
                            }
                        }
                        
                        @Override
                        public void onCaptureFailed(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull android.hardware.camera2.CaptureFailure failure) {
                            Log.e(TAG, "Fallo en captura: " + failure.getReason());
                            Integer index = (Integer) request.getTag();
                            
                            // Reportar error solo si es la última para no spamear eventos, o si es fatal
                            if (index != null && index == mBurstCount - 1) {
                                scheduleUpdatePreview();
                                WritableMap params = Arguments.createMap();
                                params.putBoolean("success", false);
                                params.putString("error", "Error hardware: " + failure.getReason());
                                sendEvent("topCaptureEnded", params);
                            }
                        }
                        
                        @Override
                        public void onCaptureStarted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, long timestamp, long frameNumber) {
                            Integer index = (Integer) request.getTag();
                            if (index != null && index == 0) {
                                sendEvent("topCaptureStarted", null);
                            }
                        }
                    };
    
                    // 4. Ejecutar
                    mCaptureSession.stopRepeating();
                    mCaptureSession.abortCaptures();
                    
                    if (mBurstCount > 1) {
                        mCaptureSession.captureBurst(burstRequests, captureCallback, mBackgroundHandler);
                    } else {
                        mCaptureSession.capture(burstRequests.get(0), captureCallback, mBackgroundHandler);
                    }
                    
                } catch (CameraAccessException e) {
                    Log.e(TAG, "Error: " + e.getMessage());
                }
            }
        }    
    private void handleCaptureResult(TotalCaptureResult result) {
        Long timestamp = result.get(CaptureResult.SENSOR_TIMESTAMP);
        if (timestamp == null) {
            Log.e(TAG, "Error: Timestamp nulo en resultado de captura.");
            return;
        }

        Image pendingImage = mPendingRawImages.remove(timestamp);
        if (pendingImage != null) {
            Log.d(TAG, "Sincronización exitosa (Result llegó último). Guardando RAW...");
            saveRawToGallery(pendingImage, result);
        } else {
            Log.d(TAG, "Resultado llegó primero. Esperando imagen RAW...");
            mPendingCaptureResults.put(timestamp, result);
        }
    }

    @Override
    public void onSurfaceTextureAvailable(@NonNull SurfaceTexture surface, int width, int height) {
        openCamera();
    }

    @Override
    public void onSurfaceTextureSizeChanged(@NonNull SurfaceTexture surface, int width, int height) {}

    @Override
    public boolean onSurfaceTextureDestroyed(@NonNull SurfaceTexture surface) {
        closeCamera();
        return true;
    }

    @Override
    public void onSurfaceTextureUpdated(@NonNull SurfaceTexture surface) {}

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        closeCamera();
    }

    private void openCamera() {
        CameraManager manager = (CameraManager) getContext().getSystemService(Context.CAMERA_SERVICE);
        try {
            String bestCameraId = null;
            long maxExposureRange = 0;
            
            Log.e(TAG, "========== INICIO AUDITORÍA PROFUNDA (CÁMARAS FÍSICAS) ==========");
            
            for (String cameraId : manager.getCameraIdList()) {
                CameraCharacteristics chars = manager.getCameraCharacteristics(cameraId);
                
                // Analizar Cámara Lógica/Principal
                printCameraSpecs(cameraId, chars, "LOGICAL/MAIN");
                
                // Revisar si tiene mejores specs que lo encontrado hasta ahora
                long logicMax = getMaxExposure(chars);
                if (logicMax > maxExposureRange && isBackFacing(chars)) {
                    maxExposureRange = logicMax;
                    bestCameraId = cameraId;
                    mCameraChars = chars;
                }

                // --- DEEP SCAN: CÁMARAS FÍSICAS INTERNAS (Android 9+) ---
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                    java.util.Set<String> physicalIds = chars.getPhysicalCameraIds();
                    if (physicalIds != null && !physicalIds.isEmpty()) {
                        Log.e(TAG, "   [ID: " + cameraId + "] Contiene " + physicalIds.size() + " cámaras físicas: " + physicalIds.toString());
                        
                        for (String physicalId : physicalIds) {
                            CameraCharacteristics physicalChars = manager.getCameraCharacteristics(physicalId);
                            printCameraSpecs(physicalId, physicalChars, "   >>> PHYSICAL (Sub-cámara de " + cameraId + ")");
                            
                            // ¿Esta cámara física oculta es mejor?
                            long physMax = getMaxExposure(physicalChars);
                            // Solo cambiamos a física si supera drásticamente a la lógica (ej. > 1 seg)
                            if (physMax > maxExposureRange && physMax > 1_000_000_000L) {
                                Log.e(TAG, "   !!! HALLAZGO: La cámara física " + physicalId + " está DESBLOQUEADA (" + (physMax/1e9) + "s) !!!");
                                maxExposureRange = physMax;
                                bestCameraId = physicalId; // ¡Usaremos la ID física directamente!
                                mCameraChars = physicalChars;
                            }
                        }
                    }
                }
            }
            Log.e(TAG, "========== FIN AUDITORÍA PROFUNDA ==========");
            
            if (bestCameraId == null) bestCameraId = "0";
            
            Log.i(TAG, "CÁMARA ELEGIDA FINAL: " + bestCameraId + " (Max Exp: " + (maxExposureRange/1e9) + "s)");

            // ... (Resto de la inicialización igual)
            
            // Recargar chars si cambiamos de ID
            if (!bestCameraId.equals(mCameraId)) {
                mCameraChars = manager.getCameraCharacteristics(bestCameraId);
            }
            mCameraId = bestCameraId;

            StreamConfigurationMap map = mCameraChars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            Size largestJpegSize = new Size(1920, 1080);
            Size largestRawSize = null;

            if (map != null) {
                largestJpegSize = Collections.max(Arrays.asList(map.getOutputSizes(ImageFormat.JPEG)), new CompareSizesByArea());
                
                // RAW Check
                int[] caps = mCameraChars.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES);
                boolean currentHasRaw = false;
                if (caps != null) {
                    for (int cap : caps) { if (cap == CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_RAW) currentHasRaw = true; }
                }
                
                if (currentHasRaw) {
                    Size[] rawSizes = map.getOutputSizes(ImageFormat.RAW_SENSOR);
                    if (rawSizes != null && rawSizes.length > 0) {
                        largestRawSize = Collections.max(Arrays.asList(rawSizes), new CompareSizesByArea());
                    }
                }
            }
            
            // Guardar rangos finales
            mExposureRange = mCameraChars.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE);
            mIsoRange = mCameraChars.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE);

            startBackgroundThread();

            mJpegReader = ImageReader.newInstance(largestJpegSize.getWidth(), largestJpegSize.getHeight(), ImageFormat.JPEG, 2);
            mJpegReader.setOnImageAvailableListener(mJpegImageListener, mBackgroundHandler);

            if (largestRawSize != null) {
                mRawReader = ImageReader.newInstance(largestRawSize.getWidth(), largestRawSize.getHeight(), ImageFormat.RAW_SENSOR, 2);
                mRawReader.setOnImageAvailableListener(mRawImageListener, mBackgroundHandler);
            }

            try {
                manager.openCamera(mCameraId, mStateCallback, mBackgroundHandler);
            } catch (SecurityException e) {
                Log.e(TAG, "Permiso denegado: " + e.getMessage());
            }

        } catch (CameraAccessException e) {
            Log.e(TAG, "Error accediendo a cámara: " + e.getMessage());
        }
    }
    
    private void printCameraSpecs(String id, CameraCharacteristics chars, String label) {
        Integer facing = chars.get(CameraCharacteristics.LENS_FACING);
        String facingStr = (facing != null && facing == CameraCharacteristics.LENS_FACING_FRONT) ? "FRONT" : "BACK";
        
        int[] caps = chars.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES);
        boolean hasManual = false;
        boolean hasRaw = false;
        if (caps != null) {
            for (int cap : caps) {
                if (cap == CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR) hasManual = true;
                if (cap == CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_RAW) hasRaw = true;
            }
        }
        
        long maxExp = getMaxExposure(chars);
        double maxSeconds = maxExp / 1_000_000_000.0;
        
        Log.e(TAG, label + " [ID: " + id + "] " + facingStr + " | Manual: " + hasManual + " | RAW: " + hasRaw + " | Max Exp: " + maxSeconds + "s");
    }
    
    private long getMaxExposure(CameraCharacteristics chars) {
        android.util.Range<Long> range = chars.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE);
        return (range != null) ? range.getUpper() : 0;
    }
    
    private boolean isBackFacing(CameraCharacteristics chars) {
        Integer facing = chars.get(CameraCharacteristics.LENS_FACING);
        return facing != null && facing == CameraCharacteristics.LENS_FACING_BACK;
    }

    private final ImageReader.OnImageAvailableListener mJpegImageListener = new ImageReader.OnImageAvailableListener() {
        @Override
        public void onImageAvailable(ImageReader reader) {
            Log.d(TAG, "JPEG disponible en buffer.");
            Image image = null;
            try {
                image = reader.acquireNextImage();
                if (image != null) saveJpegToGallery(image);
            } finally {
                if (image != null) image.close();
            }
        }
    };

    private final ImageReader.OnImageAvailableListener mRawImageListener = new ImageReader.OnImageAvailableListener() {
        @Override
        public void onImageAvailable(ImageReader reader) {
            Log.d(TAG, "RAW disponible en buffer.");
            Image image = reader.acquireNextImage(); 
            if (image == null) return;
            
            long timestamp = image.getTimestamp();
            TotalCaptureResult result = mPendingCaptureResults.remove(timestamp);
            
            if (result != null) {
                Log.d(TAG, "Sincronización exitosa (Imagen llegó última). Guardando RAW...");
                saveRawToGallery(image, result);
            } else {
                Log.d(TAG, "Imagen RAW llegó primero. Esperando metadatos...");
                mPendingRawImages.put(timestamp, image);
            }
        }
    };

    private void saveRawToGallery(Image image, TotalCaptureResult result) {
        try {
            if (mCameraChars == null) return;
            
            ContentValues values = new ContentValues();
            values.put(MediaStore.Images.Media.DISPLAY_NAME, "ASTRO_" + System.currentTimeMillis() + ".dng");
            values.put(MediaStore.Images.Media.MIME_TYPE, "image/x-adobe-dng");
                values.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_DCIM + "/AstroCamera");

            Uri uri = getContext().getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
            if (uri == null) {
                Log.e(TAG, "Error al crear URI para DNG");
                return;
            }

            try (OutputStream output = getContext().getContentResolver().openOutputStream(uri);
                 DngCreator dngCreator = new DngCreator(mCameraChars, result)) {
                
                dngCreator.writeImage(output, image);
                Log.d(TAG, "RAW (DNG) guardado: " + uri.toString());
                
            } catch (IOException e) {
                Log.e(TAG, "Error escritura RAW: " + e.getMessage());
            }
        } finally {
            image.close();
        }
    }

    private void saveJpegToGallery(Image image) {
        ByteBuffer buffer = image.getPlanes()[0].getBuffer();
        ContentValues values = new ContentValues();
        values.put(MediaStore.Images.Media.DISPLAY_NAME, "ASTRO_" + System.currentTimeMillis() + ".jpg");
        values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
            values.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_DCIM + "/AstroCamera");
        Uri uri = getContext().getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
        if (uri == null) return;
        try (OutputStream output = getContext().getContentResolver().openOutputStream(uri);
             WritableByteChannel channel = Channels.newChannel(output)) {
            channel.write(buffer);
            Log.d(TAG, "JPEG guardado: " + uri.toString());
        } catch (IOException e) {
            Log.e(TAG, "Error JPEG: " + e.getMessage());
        }
    }

    private final CameraDevice.StateCallback mStateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(@NonNull CameraDevice camera) {
            synchronized (mCameraStateLock) {
                mCameraDevice = camera;
                createCameraPreviewSession();
            }
        }
        @Override
        public void onDisconnected(@NonNull CameraDevice camera) {
            synchronized (mCameraStateLock) {
                camera.close();
                mCameraDevice = null;
            }
        }
        @Override
        public void onError(@NonNull CameraDevice camera, int error) {
            synchronized (mCameraStateLock) {
                camera.close();
                mCameraDevice = null;
                Log.e(TAG, "Camera Device Error: " + error);
            }
        }
    };

    private void createCameraPreviewSession() {
        try {
            SurfaceTexture texture = mTextureView.getSurfaceTexture();
            assert texture != null;
            texture.setDefaultBufferSize(1920, 1080);
            Surface surface = new Surface(texture);
            
            List<Surface> targets = new ArrayList<>();
            targets.add(surface);
            targets.add(mJpegReader.getSurface());
            if (mRawReader != null) targets.add(mRawReader.getSurface());

            mPreviewRequestBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            mPreviewRequestBuilder.addTarget(surface);

            mCameraDevice.createCaptureSession(targets,
                new CameraCaptureSession.StateCallback() {
                    @Override
                    public void onConfigured(@NonNull CameraCaptureSession session) {
                        synchronized (mCameraStateLock) {
                            if (mCameraDevice == null) return;
                            mCaptureSession = session;
                            updatePreview();
                        }
                    }
                    @Override
                    public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                         Log.e(TAG, "Fallo al configurar sesión de captura");
                    }
                }, mBackgroundHandler);
        } catch (CameraAccessException e) {
            Log.e(TAG, "Error sesión: " + e.getMessage());
        }
    }

    private void updatePreview() {
        synchronized (mCameraStateLock) {
            if (mCaptureSession == null) return;
            try {
                // Limitar la exposición de la vista previa para evitar lag (ej. máximo 1/15s)
                long MAX_PREVIEW_EXPOSURE_NS = 66_666_666L; 
                long previewExposure = Math.min(mExposureNs, MAX_PREVIEW_EXPOSURE_NS);
                
                int clampedIso = getClampedIso(mIso);
                long clampedPreviewExposure = getClampedExposure(previewExposure);

                mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_OFF);
                mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CameraMetadata.CONTROL_AF_MODE_OFF);
                mPreviewRequestBuilder.set(CaptureRequest.LENS_FOCUS_DISTANCE, mFocusDistance);
                
                // Desactivar estabilización en preview también
                mPreviewRequestBuilder.set(CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE, CameraMetadata.CONTROL_VIDEO_STABILIZATION_MODE_OFF);
                mPreviewRequestBuilder.set(CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE, CameraMetadata.LENS_OPTICAL_STABILIZATION_MODE_OFF);

                mPreviewRequestBuilder.set(CaptureRequest.SENSOR_SENSITIVITY, clampedIso);
                mPreviewRequestBuilder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, clampedPreviewExposure);
                mPreviewRequestBuilder.set(CaptureRequest.SENSOR_FRAME_DURATION, clampedPreviewExposure);
                mCaptureSession.setRepeatingRequest(mPreviewRequestBuilder.build(), null, mBackgroundHandler);
            } catch (CameraAccessException e) {
                Log.e(TAG, "Error en vista previa: " + e.getMessage());
            }
        }
    }

    private void closeCamera() {
        synchronized (mCameraStateLock) {
            if (mCaptureSession != null) { mCaptureSession.close(); mCaptureSession = null; }
            if (mCameraDevice != null) { mCameraDevice.close(); mCameraDevice = null; }
            if (mJpegReader != null) { mJpegReader.close(); mJpegReader = null; }
            if (mRawReader != null) { mRawReader.close(); mRawReader = null; }
            
            // Limpiar pendientes
            for (Image img : mPendingRawImages.values()) {
                img.close();
            }
            mPendingRawImages.clear();
            mPendingCaptureResults.clear();
        }
        stopBackgroundThread();
    }

    private void startBackgroundThread() {
        mBackgroundThread = new HandlerThread("CameraBackground");
        mBackgroundThread.start();
        mBackgroundHandler = new Handler(mBackgroundThread.getLooper());
    }

    private void sendEvent(String eventName, @Nullable WritableMap params) {
        ReactContext reactContext = (ReactContext) getContext();
        reactContext.getJSModule(RCTEventEmitter.class).receiveEvent(
            getId(),
            eventName,
            params
        );
    }

    private void stopBackgroundThread() {
        if (mBackgroundThread != null) {
            mBackgroundThread.quitSafely();
            try { mBackgroundThread.join(); mBackgroundThread = null; mBackgroundHandler = null; } catch (InterruptedException e) {}
        }
    }
    
    static class CompareSizesByArea implements Comparator<Size> {
        @Override public int compare(Size lhs, Size rhs) {
            return Long.signum((long) lhs.getWidth() * lhs.getHeight() - (long) rhs.getWidth() * rhs.getHeight());
        }
    }
}
