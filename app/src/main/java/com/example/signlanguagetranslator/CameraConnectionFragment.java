/*
 * Copyright 2019 The TensorFlow Authors. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.signlanguagetranslator;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Point;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.ImageReader;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.LayoutInflater;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

// CORRECTION: Imports pour les fragments et annotations androidx
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment; // CORRECTION: L'import clé !

import com.example.signlanguagetranslator.customView.AutoFitTextureView;
import com.example.signlanguagetranslator.env.Loger;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * Camera Connection Fragment that captures images from camera.
 *
 * CORRECTION: Hériter de androidx.fragment.app.Fragment
 */
public class CameraConnectionFragment extends Fragment {
  private static final Loger LOGGER = new Loger();

  private static final SparseIntArray ORIENTATIONS = new SparseIntArray();

  static {
    ORIENTATIONS.append(Surface.ROTATION_0, 90);
    ORIENTATIONS.append(Surface.ROTATION_90, 0);
    ORIENTATIONS.append(Surface.ROTATION_180, 270);
    ORIENTATIONS.append(Surface.ROTATION_270, 180);
  }

  private final Semaphore cameraOpenCloseLock = new Semaphore(1);
  private ImageReader previewReader;
  private String cameraId;
  private AutoFitTextureView textureView;
  private CameraCaptureSession captureSession;
  private CameraDevice cameraDevice;
  private Size previewSize;
  private HandlerThread backgroundThread;
  private Handler backgroundHandler;
  private final ConnectionCallback cameraConnectionCallback;
  private final ImageReader.OnImageAvailableListener imageListener;
  private final Size inputSize;
  private final int layout;

  private final TextureView.SurfaceTextureListener surfaceTextureListener =
          new TextureView.SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(@NonNull final SurfaceTexture texture, final int width, final int height) {
              openCamera(width, height);
            }

            @Override
            public void onSurfaceTextureSizeChanged(@NonNull final SurfaceTexture texture, final int width, final int height) {
              configureTransform(width, height);
            }

            @Override
            public boolean onSurfaceTextureDestroyed(@NonNull final SurfaceTexture texture) {
              return true;
            }

            @Override
            public void onSurfaceTextureUpdated(@NonNull final SurfaceTexture texture) {
            }
          };

  private final CameraDevice.StateCallback stateCallback =
          new CameraDevice.StateCallback() {
            @Override
            public void onOpened(@NonNull final CameraDevice cd) {
              cameraOpenCloseLock.release();
              cameraDevice = cd;
              createCameraPreviewSession();
            }

            @Override
            public void onDisconnected(@NonNull final CameraDevice cd) {
              cameraOpenCloseLock.release();
              cd.close();
              cameraDevice = null;
            }

            @Override
            public void onError(@NonNull final CameraDevice cd, final int error) {
              cameraOpenCloseLock.release();
              cd.close();
              cameraDevice = null;
              // CORRECTION: Utiliser requireActivity() au lieu de getActivity()
              if (isAdded() && requireActivity() != null) {
                requireActivity().finish();
              }
            }
          };

  private CaptureRequest.Builder previewRequestBuilder;
  private CaptureRequest previewRequest;

  public CameraConnectionFragment(
          final ConnectionCallback connectionCallback,
          final ImageReader.OnImageAvailableListener imageListener,
          final int layout,
          final Size inputSize) {
    this.cameraConnectionCallback = connectionCallback;
    this.imageListener = imageListener;
    this.layout = layout;
    this.inputSize = inputSize;
  }

  public static CameraConnectionFragment newInstance(
          final ConnectionCallback connectionCallback,
          final ImageReader.OnImageAvailableListener imageListener,
          final int layout,
          final Size inputSize) {
    return new CameraConnectionFragment(connectionCallback, imageListener, layout, inputSize);
  }

  @Override
  public View onCreateView(
          final LayoutInflater inflater, final ViewGroup container, final Bundle savedInstanceState) {
    return inflater.inflate(layout, container, false);
  }

  // CORRECTION: Utiliser onViewCreated au lieu de onActivityCreated
  @Override
  public void onViewCreated(@NonNull final View view, final Bundle savedInstanceState) {
    textureView = view.findViewById(R.id.texture);
  }

  @Override
  public void onResume() {
    super.onResume();
    startBackgroundThread();

    if (textureView.isAvailable()) {
      openCamera(textureView.getWidth(), textureView.getHeight());
    } else {
      textureView.setSurfaceTextureListener(surfaceTextureListener);
    }
  }

  @Override
  public void onPause() {
    closeCamera();
    stopBackgroundThread();
    super.onPause();
  }

  public void setCamera(String cameraId) {
    this.cameraId = cameraId;
  }

  private void startBackgroundThread() {
    backgroundThread = new HandlerThread("CameraBackground");
    backgroundThread.start();
    backgroundHandler = new Handler(backgroundThread.getLooper());
  }

  private void stopBackgroundThread() {
    if (backgroundThread != null) {
      backgroundThread.quitSafely();
      try {
        backgroundThread.join();
        backgroundThread = null;
        backgroundHandler = null;
      } catch (final InterruptedException e) {
        LOGGER.e(e, "Exception!");
      }
    }
  }

  private void openCamera(final int width, final int height) {
    // CORRECTION: Utiliser requireActivity() pour obtenir le contexte
    final Context context = requireActivity();
    final CameraManager manager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
    setUpCameraOutputs(width, height);
    configureTransform(width, height);
    try {
      if (!cameraOpenCloseLock.tryAcquire(2500, TimeUnit.MILLISECONDS)) {
        throw new RuntimeException("Time out waiting to lock camera opening.");
      }
      if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
        // La permission doit déjà être accordée à ce stade
        return;
      }
      manager.openCamera(cameraId, stateCallback, backgroundHandler);
    } catch (final CameraAccessException e) {
      LOGGER.e(e, "Exception!");
    } catch (final InterruptedException e) {
      throw new RuntimeException("Interrupted while trying to lock camera opening.", e);
    }
  }

  private void closeCamera() {
    try {
      cameraOpenCloseLock.acquire();
      if (null != captureSession) {
        captureSession.close();
        captureSession = null;
      }
      if (null != cameraDevice) {
        cameraDevice.close();
        cameraDevice = null;
      }
      if (null != previewReader) {
        previewReader.close();
        previewReader = null;
      }
    } catch (final InterruptedException e) {
      throw new RuntimeException("Interrupted while trying to lock camera closing.", e);
    } finally {
      cameraOpenCloseLock.release();
    }
  }

  // Dans CameraConnectionFragment.java
  private void setUpCameraOutputs(final int width, final int height) {    final Context context = requireActivity();
    final CameraManager manager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
    try {
      final CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);
      final StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);

      // On prend la plus grande résolution YUV possible comme référence pour le ratio d'aspect.
      final Size largest = Collections.max(
              Arrays.asList(map.getOutputSizes(ImageFormat.YUV_420_888)),
              new CompareSizesByArea());

      int displayRotation = requireActivity().getWindowManager().getDefaultDisplay().getRotation();
      int sensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
      boolean swappedDimensions = false;
      switch (displayRotation) {
        case Surface.ROTATION_0:
        case Surface.ROTATION_180:
          if (sensorOrientation == 90 || sensorOrientation == 270) {
            swappedDimensions = true;
          }
          break;
        case Surface.ROTATION_90:
        case Surface.ROTATION_270:
          if (sensorOrientation == 0 || sensorOrientation == 180) {
            swappedDimensions = true;
          }
          break;
        default:
          LOGGER.e("Display rotation is invalid: %d", displayRotation);
      }

      Point displaySize = new Point();
      requireActivity().getWindowManager().getDefaultDisplay().getSize(displaySize);
      int rotatedPreviewWidth = width;
      int rotatedPreviewHeight = height;
      int maxPreviewWidth = displaySize.x;
      int maxPreviewHeight = displaySize.y;

      if (swappedDimensions) {
        rotatedPreviewWidth = height;
        rotatedPreviewHeight = width;
        maxPreviewWidth = displaySize.y;
        maxPreviewHeight = displaySize.x;
      }

      // Limiter la résolution maximale pour ne pas surcharger le téléphone
      if (maxPreviewWidth > 1920) maxPreviewWidth = 1920;
      if (maxPreviewHeight > 1080) maxPreviewHeight = 1080;

      // La ligne la plus importante : on choisit la taille optimale.
      previewSize = chooseOptimalSize(map.getOutputSizes(SurfaceTexture.class),
              rotatedPreviewWidth, rotatedPreviewHeight, maxPreviewWidth, maxPreviewHeight, largest);

      // On ajuste le ratio d'aspect de la vue pour qu'elle corresponde à l'image.
      if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
        textureView.setAspectRatio(previewSize.getWidth(), previewSize.getHeight());
      } else {
        textureView.setAspectRatio(previewSize.getHeight(), previewSize.getWidth());
      }

      cameraConnectionCallback.onPreviewSizeChosen(previewSize, sensorOrientation);
    } catch (final CameraAccessException | NullPointerException e) {
      LOGGER.e(e, "Failed to set up camera outputs.");
    }
  }



  private void createCameraPreviewSession() {
    try {
      final SurfaceTexture texture = textureView.getSurfaceTexture();
      assert texture != null;
      texture.setDefaultBufferSize(previewSize.getWidth(), previewSize.getHeight());
      final Surface surface = new Surface(texture);

      previewRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
      previewRequestBuilder.addTarget(surface);

      previewReader = ImageReader.newInstance(
              previewSize.getWidth(), previewSize.getHeight(), ImageFormat.YUV_420_888, 2);
      previewReader.setOnImageAvailableListener(imageListener, backgroundHandler);
      previewRequestBuilder.addTarget(previewReader.getSurface());

      cameraDevice.createCaptureSession(
              Arrays.asList(surface, previewReader.getSurface()),
              new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
                  if (null == cameraDevice) return;
                  captureSession = cameraCaptureSession;
                  try {
                    previewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE,
                            CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
                    previewRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE,
                            CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);
                    previewRequest = previewRequestBuilder.build();
                    captureSession.setRepeatingRequest(
                            previewRequest, null, backgroundHandler);
                  } catch (final CameraAccessException e) {
                    LOGGER.e(e, "Exception!");
                  }
                }

                @Override
                public void onConfigureFailed(
                        @NonNull CameraCaptureSession cameraCaptureSession) {
                  Toast.makeText(requireActivity(), "Failed", Toast.LENGTH_SHORT).show();
                }
              },
              null);
    } catch (final CameraAccessException e) {
      LOGGER.e(e, "Exception!");
    }
  }
  private void configureTransform(final int viewWidth, final int viewHeight) {
    if (null == textureView || null == previewSize || null == requireActivity()) {
      return;
    }
    final int rotation = requireActivity().getWindowManager().getDefaultDisplay().getRotation();
    final Matrix matrix = new Matrix();
    final RectF viewRect = new RectF(0, 0, viewWidth, viewHeight);
    final RectF bufferRect = new RectF(0, 0, previewSize.getHeight(), previewSize.getWidth());
    final float centerX = viewRect.centerX();
    final float centerY = viewRect.centerY();

    if (Surface.ROTATION_90 == rotation || Surface.ROTATION_270 == rotation) {
      bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY());
      matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL);
      final float scale =
              Math.max(
                      (float) viewHeight / previewSize.getHeight(),
                      (float) viewWidth / previewSize.getWidth());
      matrix.postScale(scale, scale, centerX, centerY);
      matrix.postRotate(90 * (rotation - 2), centerX, centerY);
    } else if (Surface.ROTATION_180 == rotation) {
      matrix.postRotate(180, centerX, centerY);
    }

    // Cette partie est cruciale pour le mode portrait (ROTATION_0)
    // Elle s'assure que le ratio est correct.
    textureView.setTransform(matrix);
  }




  protected static Size chooseOptimalSize(
          final Size[] choices,
          final int textureViewWidth,
          final int textureViewHeight,
          final int maxWidth,
          final int maxHeight,
          final Size aspectRatio) {

    final List<Size> bigEnough = new ArrayList<>();
    final List<Size> notBigEnough = new ArrayList<>();
    final int w = aspectRatio.getWidth();
    final int h = aspectRatio.getHeight();
    for (final Size option : choices) {
      if (option.getWidth() <= maxWidth
              && option.getHeight() <= maxHeight
              && option.getHeight() == option.getWidth() * h / w) {
        if (option.getWidth() >= textureViewWidth && option.getHeight() >= textureViewHeight) {
          bigEnough.add(option);
        } else {
          notBigEnough.add(option);
        }
      }
    }

    if (bigEnough.size() > 0) {
      return Collections.min(bigEnough, new CompareSizesByArea());
    } else if (notBigEnough.size() > 0) {
      return Collections.max(notBigEnough, new CompareSizesByArea());
    } else {
      LOGGER.e("Couldn't find any suitable preview size");
      return choices[0];
    }
  }

  /**
   * The camera preview size will be chosen to be the smallest frame by pixel size capable of
   * containing a DESIRED_SIZE x DESIRED_SIZE square.
   */
  public interface ConnectionCallback {
    void onPreviewSizeChosen(Size size, int cameraRotation);
  }

  /** Compares two {@code Size}s based on their areas. */
  static class CompareSizesByArea implements Comparator<Size> {
    @Override
    public int compare(final Size lhs, final Size rhs) {
      // We cast here to ensure the multiplications won't overflow
      return Long.signum(
              (long) lhs.getWidth() * lhs.getHeight() - (long) rhs.getWidth() * rhs.getHeight());
    }
  }
}