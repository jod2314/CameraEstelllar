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
    private TotalCaptureResult mLastCaptureResult;

    private HandlerThread mBackgroundThread;
    private Handler mBackgroundHandler;

    // Valores manuales
    private int mIso = 800;
    private long mExposureNs = 100000000L; 
    private float mFocusDistance = 0.0f; // 0.0 = Infinito (Default para Astro)
    
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

    public void takePicture() {
        synchronized (mCameraStateLock) {
            if (mCameraDevice == null || mCaptureSession == null) return;
            try {
                final CaptureRequest.Builder captureBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
                captureBuilder.addTarget(mJpegReader.getSurface());
                
                if (mRawReader != null) {
                    captureBuilder.addTarget(mRawReader.getSurface());
                }

                // Configuración Manual Completa (Exposición + Enfoque)
                captureBuilder.set(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_OFF);
                captureBuilder.set(CaptureRequest.SENSOR_SENSITIVITY, mIso);
                captureBuilder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, mExposureNs);
                captureBuilder.set(CaptureRequest.SENSOR_FRAME_DURATION, mExposureNs);
                
                // Enfoque Manual
                captureBuilder.set(CaptureRequest.CONTROL_AF_MODE, CameraMetadata.CONTROL_AF_MODE_OFF);
                captureBuilder.set(CaptureRequest.LENS_FOCUS_DISTANCE, mFocusDistance);

                captureBuilder.set(CaptureRequest.JPEG_ORIENTATION, 90);

                CameraCaptureSession.CaptureCallback captureCallback = new CameraCaptureSession.CaptureCallback() {
                    @Override
                    public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
                        Log.d(TAG, "Captura completada en sensor");
                        synchronized (mCameraStateLock) {
                            mLastCaptureResult = result;
                        }
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
            Size largestRawSize = null;

            for (String cameraId : manager.getCameraIdList()) {
                CameraCharacteristics chars = manager.getCameraCharacteristics(cameraId);
                
                Integer facing = chars.get(CameraCharacteristics.LENS_FACING);
                if (facing == null || facing != CameraCharacteristics.LENS_FACING_BACK) continue;

                int[] caps = chars.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES);
                boolean hasManual = false;
                boolean hasRaw = false;
                for (int cap : caps) {
                    if (cap == CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR) hasManual = true;
                    if (cap == CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_RAW) hasRaw = true;
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
                    mCameraChars = chars;

                    StreamConfigurationMap map = chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                    if (map != null) {
                        largestJpegSize = Collections.max(Arrays.asList(map.getOutputSizes(ImageFormat.JPEG)), new CompareSizesByArea());
                        if (hasRaw) {
                            largestRawSize = Collections.max(Arrays.asList(map.getOutputSizes(ImageFormat.RAW_SENSOR)), new CompareSizesByArea());
                        }
                    }
                }
            }
            
            mCameraId = bestCameraId;
            if (mCameraId == null) return;

            Log.i(TAG, "Lente Astro Seleccionado: " + mCameraId + " (RAW: " + (largestRawSize != null) + ")");

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

    private final ImageReader.OnImageAvailableListener mJpegImageListener = new ImageReader.OnImageAvailableListener() {
        @Override
        public void onImageAvailable(ImageReader reader) {
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
            Image image = null;
            try {
                image = reader.acquireLatestImage();
                if (image != null) {
                    saveRawToGallery(image);
                }
            } finally {
                if (image != null) image.close();
            }
        }
    };

    private void saveRawToGallery(Image image) {
        TotalCaptureResult captureResult;
        synchronized (mCameraStateLock) {
            captureResult = mLastCaptureResult;
        }

        if (captureResult == null || mCameraChars == null) {
            Log.e(TAG, "No se puede crear DNG: Falta metadatos de captura");
            return;
        }

        ContentValues values = new ContentValues();
        values.put(MediaStore.Images.Media.DISPLAY_NAME, "ASTRO_" + System.currentTimeMillis() + ".dng");
        values.put(MediaStore.Images.Media.MIME_TYPE, "image/x-adobe-dng");
        values.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/AstroCamera");

        Uri uri = getContext().getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
        if (uri == null) return;

        try (OutputStream output = getContext().getContentResolver().openOutputStream(uri);
             DngCreator dngCreator = new DngCreator(mCameraChars, captureResult)) {
            
            dngCreator.writeImage(output, image);
            Log.d(TAG, "RAW (DNG) guardado: " + uri.toString());
            
        } catch (IOException e) {
            Log.e(TAG, "Error RAW: " + e.getMessage());
        }
    }

    // Actualización de takePicture para manejar DNG Creator
    private void saveJpegToGallery(Image image) {
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
                    public void onConfigureFailed(@NonNull CameraCaptureSession session) {}
                }, mBackgroundHandler);
        } catch (CameraAccessException e) {
            Log.e(TAG, "Error sesión: " + e.getMessage());
        }
    }

    private void updatePreview() {
        synchronized (mCameraStateLock) {
            if (mCaptureSession == null) return;
            try {
                long MAX_PREVIEW_EXPOSURE_NS = 66_666_666L; 
                long previewExposure = Math.min(mExposureNs, MAX_PREVIEW_EXPOSURE_NS);
                
                mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_OFF);
                // Enfoque Manual en Preview
                mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CameraMetadata.CONTROL_AF_MODE_OFF);
                mPreviewRequestBuilder.set(CaptureRequest.LENS_FOCUS_DISTANCE, mFocusDistance);
                
                mPreviewRequestBuilder.set(CaptureRequest.SENSOR_SENSITIVITY, mIso);
                mPreviewRequestBuilder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, previewExposure);
                mPreviewRequestBuilder.set(CaptureRequest.SENSOR_FRAME_DURATION, previewExposure);
                mCaptureSession.setRepeatingRequest(mPreviewRequestBuilder.build(), null, mBackgroundHandler);
            } catch (CameraAccessException e) {
                Log.e(TAG, "Error preview: " + e.getMessage());
            }
        }
    }

    private void closeCamera() {
        synchronized (mCameraStateLock) {
            if (mCaptureSession != null) { mCaptureSession.close(); mCaptureSession = null; }
            if (mCameraDevice != null) { mCameraDevice.close(); mCameraDevice = null; }
            if (mJpegReader != null) { mJpegReader.close(); mJpegReader = null; }
            if (mRawReader != null) { mRawReader.close(); mRawReader = null; }
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
            try { mBackgroundThread.join(); mBackgroundThread = null; mBackgroundHandler = null; } catch (InterruptedException e) {}
        }
    }
    
    static class CompareSizesByArea implements Comparator<Size> {
        @Override public int compare(Size lhs, Size rhs) {
            return Long.signum((long) lhs.getWidth() * lhs.getHeight() - (long) rhs.getWidth() * rhs.getHeight());
        }
    }
}
