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

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.WritableByteChannel;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;

public class AstroCameraView extends FrameLayout implements TextureView.SurfaceTextureListener {

    private static final String TAG = "AstroCamera";
    private TextureView mTextureView;
    private String mCameraId;
    
    // Concurrency Lock
    private final Object mCameraStateLock = new Object();

    // Camera Resources (Guarded by mCameraStateLock)
    private CameraDevice mCameraDevice;
    private CameraCaptureSession mCaptureSession;
    private CaptureRequest.Builder mPreviewRequestBuilder;
    private ImageReader mImageReader;

    private HandlerThread mBackgroundThread;
    private Handler mBackgroundHandler;

    // Valores manuales
    private int mIso = 800;
    private long mExposureNs = 100000000L; // 0.1s default
    
    // Debounce Runnable
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

    private void scheduleUpdatePreview() {
        if (mBackgroundHandler != null) {
            mBackgroundHandler.removeCallbacks(mUpdatePreviewTask);
            mBackgroundHandler.postDelayed(mUpdatePreviewTask, 50); // 50ms debounce
        }
    }

    public void takePicture() {
        synchronized (mCameraStateLock) {
            if (mCameraDevice == null || mCaptureSession == null) return;
            try {
                // Crear Request de Captura
                final CaptureRequest.Builder captureBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
                captureBuilder.addTarget(mImageReader.getSurface());

                // Aplicar configuraciones manuales
                captureBuilder.set(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_OFF);
                captureBuilder.set(CaptureRequest.CONTROL_AF_MODE, CameraMetadata.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
                captureBuilder.set(CaptureRequest.SENSOR_SENSITIVITY, mIso);
                captureBuilder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, mExposureNs);
                captureBuilder.set(CaptureRequest.SENSOR_FRAME_DURATION, mExposureNs);
                captureBuilder.set(CaptureRequest.JPEG_ORIENTATION, 90);

                CameraCaptureSession.CaptureCallback captureCallback = new CameraCaptureSession.CaptureCallback() {
                    @Override
                    public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
                        Log.d(TAG, "Captura completada en sensor");
                        // Reiniciar preview después de capturar
                        scheduleUpdatePreview();
                    }
                };

                mCaptureSession.stopRepeating(); 
                mCaptureSession.capture(captureBuilder.build(), captureCallback, mBackgroundHandler);

            } catch (CameraAccessException e) {
                Log.e(TAG, "Error al tomar foto: " + e.getMessage());
            }
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
            float bestPixelSize = 0;
            Size largestJpegSize = new Size(1920, 1080);

            for (String cameraId : manager.getCameraIdList()) {
                CameraCharacteristics chars = manager.getCameraCharacteristics(cameraId);
                
                Integer facing = chars.get(CameraCharacteristics.LENS_FACING);
                if (facing == null || facing != CameraCharacteristics.LENS_FACING_BACK) continue;

                int[] caps = chars.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES);
                boolean hasManual = false;
                for (int cap : caps) {
                    if (cap == CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR) {
                        hasManual = true;
                        break;
                    }
                }
                if (!hasManual) continue;

                android.util.Range<Long> exposureRange = chars.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE);
                long maxExp = (exposureRange != null) ? exposureRange.getUpper() : 0;
                
                android.util.SizeF sensorSize = chars.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE);
                android.graphics.Rect activeArray = chars.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE);
                float pixelSize = (sensorSize != null && activeArray != null) ? (sensorSize.getWidth() / activeArray.width()) : 0;

                if (maxExp > maxExposureRange || (maxExp == maxExposureRange && pixelSize > bestPixelSize)) {
                    maxExposureRange = maxExp;
                    bestPixelSize = pixelSize;
                    bestCameraId = cameraId;

                    StreamConfigurationMap map = chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                    if (map != null) {
                        largestJpegSize = Collections.max(
                            Arrays.asList(map.getOutputSizes(ImageFormat.JPEG)),
                            new CompareSizesByArea()
                        );
                    }
                }
            }
            
            mCameraId = bestCameraId;
            if (mCameraId == null) return;

            Log.i(TAG, "Lente seleccionado: " + mCameraId + " | Max Exp: " + maxExposureRange);

            mImageReader = ImageReader.newInstance(largestJpegSize.getWidth(), largestJpegSize.getHeight(), ImageFormat.JPEG, 2);
            mImageReader.setOnImageAvailableListener(mOnImageAvailableListener, mBackgroundHandler);

            startBackgroundThread();
            try {
                manager.openCamera(mCameraId, mStateCallback, mBackgroundHandler);
            } catch (SecurityException e) {
                Log.e(TAG, "Permiso denegado: " + e.getMessage());
            }

        } catch (CameraAccessException e) {
            Log.e(TAG, "Error accediendo a cámara: " + e.getMessage());
        }
    }

    private final ImageReader.OnImageAvailableListener mOnImageAvailableListener = new ImageReader.OnImageAvailableListener() {
        @Override
        public void onImageAvailable(ImageReader reader) {
            Image image = null;
            try {
                image = reader.acquireNextImage();
                if (image != null) {
                    saveImageToGallery(image);
                }
            } catch (Exception e) {
                 Log.e(TAG, "Error capturando imagen: " + e.getMessage());
            } finally {
                if (image != null) image.close();
            }
        }
    };

    private void saveImageToGallery(Image image) {
        ByteBuffer buffer = image.getPlanes()[0].getBuffer();
        
        ContentValues values = new ContentValues();
        values.put(MediaStore.Images.Media.DISPLAY_NAME, "ASTRO_" + System.currentTimeMillis() + ".jpg");
        values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
        values.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/AstroCamera");

        Uri uri = getContext().getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
        if (uri == null) return;

        try (OutputStream output = getContext().getContentResolver().openOutputStream(uri);
             WritableByteChannel channel = Channels.newChannel(output)) {
            
            channel.write(buffer);
            Log.d(TAG, "Imagen guardada eficientemente: " + uri.toString());
            
        } catch (IOException e) {
            Log.e(TAG, "Error guardando imagen: " + e.getMessage());
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
            }
            Log.e(TAG, "Error de cámara: " + error);
        }
    };

    private void createCameraPreviewSession() {
        try {
            SurfaceTexture texture = mTextureView.getSurfaceTexture();
            assert texture != null;
            texture.setDefaultBufferSize(1920, 1080);
            Surface surface = new Surface(texture);
            Surface readerSurface = mImageReader.getSurface();

            mPreviewRequestBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            mPreviewRequestBuilder.addTarget(surface);

            mCameraDevice.createCaptureSession(Arrays.asList(surface, readerSurface),
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
                         Log.e(TAG, "Fallo configuración de sesión");
                    }
                }, mBackgroundHandler);

        } catch (CameraAccessException e) {
            Log.e(TAG, "Error creando sesión: " + e.getMessage());
        }
    }

    private void updatePreview() {
        synchronized (mCameraStateLock) {
            if (mCaptureSession == null) return;
            try {
                long MAX_PREVIEW_EXPOSURE_NS = 66_666_666L; 
                long previewExposure = Math.min(mExposureNs, MAX_PREVIEW_EXPOSURE_NS);

                mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_OFF);
                mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CameraMetadata.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
                mPreviewRequestBuilder.set(CaptureRequest.SENSOR_SENSITIVITY, mIso);
                mPreviewRequestBuilder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, previewExposure);
                mPreviewRequestBuilder.set(CaptureRequest.SENSOR_FRAME_DURATION, previewExposure);

                mCaptureSession.setRepeatingRequest(mPreviewRequestBuilder.build(), null, mBackgroundHandler);

            } catch (CameraAccessException e) {
                Log.e(TAG, "Error actualizando preview: " + e.getMessage());
            } catch (IllegalStateException e) {
                 Log.e(TAG, "Sesión cerrada prematuramente: " + e.getMessage());
            }
        }
    }

    private void closeCamera() {
        synchronized (mCameraStateLock) {
            if (mCaptureSession != null) {
                mCaptureSession.close();
                mCaptureSession = null;
            }
            if (mCameraDevice != null) {
                mCameraDevice.close();
                mCameraDevice = null;
            }
            if (mImageReader != null) {
                mImageReader.close();
                mImageReader = null;
            }
        }
        stopBackgroundThread();
    }

    private void startBackgroundThread() {
        mBackgroundThread = new HandlerThread("CameraBackground");
        mBackgroundThread.start();
        mBackgroundHandler = new Handler(mBackgroundThread.getLooper());
    }

    private void stopBackgroundThread() {
        if (mBackgroundThread != null) {
            mBackgroundThread.quitSafely();
            try {
                mBackgroundThread.join();
                mBackgroundThread = null;
                mBackgroundHandler = null;
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
    
    static class CompareSizesByArea implements Comparator<Size> {
        @Override
        public int compare(Size lhs, Size rhs) {
            return Long.signum((long) lhs.getWidth() * lhs.getHeight() -
                    (long) rhs.getWidth() * rhs.getHeight());
        }
    }
}
