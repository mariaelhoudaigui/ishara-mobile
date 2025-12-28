package com.example.signlanguagetranslator;

// CORRECTION: Import des classes 'androidx' modernes
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment; // CORRECTION: Utiliser androidx.fragment.app.Fragment

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Trace;
import android.util.Size;
import android.view.Surface;
import android.view.View;
import android.view.WindowManager;
import android.widget.CompoundButton;
import android.widget.Toast;

import com.example.signlanguagetranslator.env.ImageUtils;
import com.example.signlanguagetranslator.env.Loger;

import java.nio.ByteBuffer;

// CORRECTION: Implémentation de ActivityCompat.OnRequestPermissionsResultCallback
public abstract class CameraActivity extends AppCompatActivity
        implements ImageReader.OnImageAvailableListener,
        ActivityCompat.OnRequestPermissionsResultCallback, // Utiliser l'interface de compatibilité
        CompoundButton.OnCheckedChangeListener,
        View.OnClickListener {

    private static final Loger LOGGER = new Loger();
    private static final int PERMISSIONS_REQUEST = 1;
    private static final String PERMISSION_CAMERA = Manifest.permission.CAMERA;

    protected int previewWidth = 0;
    protected int previewHeight = 0;
    private Handler handler;
    private HandlerThread handlerThread;
    private boolean useCamera2API;
    private boolean isProcessingFrame = false;
    private byte[][] yuvBytes = new byte[3][];
    private int[] rgbBytes = null;
    private int yRowStride;
    private Runnable postInferenceCallback;
    private Runnable imageConverter;

    protected int[] getRgbBytes() {
        // This is a common implementation: it pre-allocates a byte array and fills it
        // on each camera frame.
        imageConverter.run();
        return rgbBytes;
    }
    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        LOGGER.d("onCreate " + this);
        // CORRECTION: Passer le savedInstanceState au super.onCreate() est crucial !
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_camera);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayShowTitleEnabled(false);
        }

        if (hasPermission()) {
            setFragment();
        } else {
            requestPermission();
        }
    }

    // --- Les callbacks pour Camera1 et Camera2 restent inchangés pour le moment ---
    // Note : L'implémentation de Camera1 (onPreviewFrame) est obsolète et peut échouer.
    // La logique de 'chooseCamera' tente de préférer Camera2, ce qui est bien.

    /** Callback for Camera2 API */
    @Override
    public void onImageAvailable(final ImageReader reader) {
        // We need wait until we have some size from onPreviewSizeChosen
        if (previewWidth == 0 || previewHeight == 0) {
            return;
        }
        if (rgbBytes == null) {
            rgbBytes = new int[previewWidth * previewHeight];
        }
        try {
            final Image image = reader.acquireLatestImage();

            if (image == null) {
                return;
            }

            if (isProcessingFrame) {
                image.close();
                return;
            }
            isProcessingFrame = true;
            Trace.beginSection("imageAvailable");
            final Image.Plane[] planes = image.getPlanes();
            fillBytes(planes, yuvBytes);
            yRowStride = planes[0].getRowStride();
            final int uvRowStride = planes[1].getRowStride();
            final int uvPixelStride = planes[1].getPixelStride();

            imageConverter =
                    () -> ImageUtils.convertYUV420ToARGB8888(
                            yuvBytes[0],
                            yuvBytes[1],
                            yuvBytes[2],
                            previewWidth,
                            previewHeight,
                            yRowStride,
                            uvRowStride,
                            uvPixelStride,
                            rgbBytes);

            postInferenceCallback =
                    () -> {
                        image.close();
                        isProcessingFrame = false;
                    };

            processImage();
        } catch (final Exception e) {
            LOGGER.e(e, "Exception!");
            Trace.endSection();
        }
    }


    // --- La gestion du cycle de vie (onResume, onPause...) reste la même ---
    @Override
    public synchronized void onResume() {
        LOGGER.d("onResume " + this);
        super.onResume();

        handlerThread = new HandlerThread("inference");
        handlerThread.start();
        handler = new Handler(handlerThread.getLooper());
    }

    @Override
    public synchronized void onPause() {
        LOGGER.d("onPause " + this);

        if (handlerThread != null) {
            handlerThread.quitSafely();
            try {
                handlerThread.join();
                handlerThread = null;
                handler = null;
            } catch (final InterruptedException e) {
                LOGGER.e(e, "Exception!", e);
            }
        }

        super.onPause();
    }


    protected synchronized void runInBackground(final Runnable r) {
        if (handler != null) {
            handler.post(r);
        }
    }

    // --- GESTION DES PERMISSIONS CORRIGÉE ---
    @Override
    public void onRequestPermissionsResult(
            final int requestCode, @NonNull final String[] permissions, @NonNull final int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults); // Important
        if (requestCode == PERMISSIONS_REQUEST) {
            // CORRECTION: Vérifier correctement si la permission a été accordée.
            // Le tableau ne contient qu'un seul résultat.
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                setFragment();
            } else {
                // L'utilisateur a refusé, on redemande.
                requestPermission();
            }
        }
    }

    private boolean hasPermission() {
        // CORRECTION: Utiliser ContextCompat pour une meilleure compatibilité
        return ContextCompat.checkSelfPermission(this, PERMISSION_CAMERA) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestPermission() {
        // CORRECTION: Utiliser ActivityCompat pour une meilleure compatibilité
        if (ActivityCompat.shouldShowRequestPermissionRationale(this, PERMISSION_CAMERA)) {
            Toast.makeText(
                            CameraActivity.this,
                            "Camera permission is required for this demo",
                            Toast.LENGTH_LONG)
                    .show();
        }
        ActivityCompat.requestPermissions(this, new String[]{PERMISSION_CAMERA}, PERMISSIONS_REQUEST);
    }


    // --- La logique de choix de la caméra reste la même ---
    private String chooseCamera() {
        final CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try {
            for (final String cameraId : manager.getCameraIdList()) {
                final CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);

                final Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
                if (facing != null && facing == CameraCharacteristics.LENS_FACING_FRONT) {
                    continue; // On ignore la caméra frontale
                }

                final StreamConfigurationMap map =
                        characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                if (map == null) {
                    continue;
                }

                useCamera2API = isHardwareLevelSupported(characteristics, CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_FULL);
                LOGGER.i("Camera API lv2?: %s", useCamera2API);
                return cameraId;
            }
        } catch (CameraAccessException e) {
            LOGGER.e(e, "Not allowed to access camera");
        }
        return null;
    }

    private boolean isHardwareLevelSupported(CameraCharacteristics characteristics, int requiredLevel) {
        int deviceLevel = characteristics.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL);
        return deviceLevel != CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY && requiredLevel <= deviceLevel;
    }


    protected void setFragment() {
        String cameraId = chooseCamera();
        if (cameraId == null) {
            Toast.makeText(this, "No camera found", Toast.LENGTH_LONG).show();
            // Gérer le cas où aucune caméra n'est trouvée
            return;
        }

        // CORRECTION: Utiliser le SupportFragmentManager pour les fragments androidx
        Fragment fragment;
        if (useCamera2API) {
            // NOTE : Ce fragment devra aussi être migré vers androidx.Fragment
            CameraConnectionFragment camera2Fragment =
                    CameraConnectionFragment.newInstance(
                            (size, rotation) -> {
                                previewHeight = size.getHeight();
                                previewWidth = size.getWidth();
                                CameraActivity.this.onPreviewSizeChosen(size, rotation);
                            },
                            this,
                            getLayoutId(),
                            getDesiredPreviewFrameSize());
            camera2Fragment.setCamera(cameraId);
            fragment = camera2Fragment;
        } else {
            // NOTE : Ce fragment devra aussi être migré vers androidx.Fragment
            //fragment = new LegacyCameraConnectionFragment(this, getLayoutId(), getDesiredPreviewFrameSize());
            // Pour l'instant, on force Camera2 car Camera1 n'est plus fiable.
            Toast.makeText(this, "Legacy Camera not supported, forcing Camera2.", Toast.LENGTH_LONG).show();
            // Vous pouvez gérer un fallback ici si nécessaire, mais c'est peu probable sur Android 15.
            return; // Arrêter si on ne peut pas utiliser Camera2
        }

        // CORRECTION: Utiliser getSupportFragmentManager
        getSupportFragmentManager().beginTransaction().replace(R.id.container, fragment).commit();
    }


    protected void fillBytes(final Image.Plane[] planes, final byte[][] yuvBytes) {
        for (int i = 0; i < planes.length; ++i) {
            final ByteBuffer buffer = planes[i].getBuffer();
            if (yuvBytes[i] == null || yuvBytes[i].length != buffer.capacity()) {
                yuvBytes[i] = new byte[buffer.capacity()];
            }
            buffer.get(yuvBytes[i]);
        }
    }


    protected void readyForNextImage() {
        if (postInferenceCallback != null) {
            postInferenceCallback.run();
        }
    }

    protected int getScreenOrientation() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return getDisplay().getRotation();
        } else {
            return getWindowManager().getDefaultDisplay().getRotation();
        }
    }

    // --- Les méthodes abstraites et autres restent les mêmes ---

    @Override
    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {}

    @Override
    public void onClick(View v) {}

    // Méthodes abstraites que la classe fille (DetectorActivity) doit implémenter
    protected abstract void processImage();
    protected abstract void onPreviewSizeChosen(final Size size, final int rotation);
    protected abstract int getLayoutId();
    protected abstract Size getDesiredPreviewFrameSize();
}
