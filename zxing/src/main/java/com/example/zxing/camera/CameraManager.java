/*
 * Copyright (C) 2008 ZXing authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.zxing.camera;

import android.content.Context;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.graphics.Rect;
import android.hardware.Camera;
import android.hardware.Camera.Parameters;
import android.os.Build;
import android.os.Handler;
import android.view.SurfaceHolder;

import java.io.IOException;

/**
 这个对象包装摄像机服务对象，并期望它是唯一与之对话的对象
 实现封装了采取预览大小的图像所需的步骤，这些图像用于预览和解码。
 *
 */
public final class CameraManager {

  private static final String TAG = CameraManager.class.getSimpleName();

  private static final int MIN_FRAME_WIDTH = 240;
  private static final int MIN_FRAME_HEIGHT = 240;
  private static final int MAX_FRAME_WIDTH = 480;
  private static final int MAX_FRAME_HEIGHT = 360;

  private static CameraManager cameraManager;

  static final int SDK_INT; // Later we can use Build.VERSION.SDK_INT
  static {
    int sdkInt;
    try {
      sdkInt = Integer.parseInt(Build.VERSION.SDK);
    } catch (NumberFormatException nfe) {
      // Just to be safe
      sdkInt = 10000;
    }
    SDK_INT = sdkInt;
  }

  private final Context context;
  private final CameraConfigurationManager configManager;
  private Camera camera;
  private Rect framingRect;
  private Rect framingRectInPreview;
  private boolean initialized;
  private boolean previewing;
  private final boolean useOneShotPreviewCallback;
  /**
   * 预览帧在这里传递，我们将其传递给注册的处理程序。一定要
   * 清除处理程序，使其只接收一条消息。
   */
  private final PreviewCallback previewCallback;
  /** Autofocus callbacks arrive here, and are dispatched to the Handler which requested them. */
  private final AutoFocusCallback autoFocusCallback;

  /**
   * 使用调用活动的上下文初始化此静态对象。
   *
   * @param context The Activity which wants to use the camera.
   */
  public static void init(Context context) {
    if (cameraManager == null) {
      cameraManager = new CameraManager(context);
    }
  }

  /**
   * Gets the CameraManager singleton instance.
   *
   * @return A reference to the CameraManager singleton.
   */
  public static CameraManager get() {
    return cameraManager;
  }

  private CameraManager(Context context) {

    this.context = context;
    this.configManager = new CameraConfigurationManager(context);

    // Camera.setOneShotPreviewCallback() has a race condition in Cupcake, so we use the older
    // Camera.setPreviewCallback() on 1.5 and earlier. For Donut and later, we need to use
    // the more efficient one shot callback, as the older one can swamp the system and cause it
    // to run out of memory. We can't use SDK_INT because it was introduced in the Donut SDK.
    //useOneShotPreviewCallback = Integer.parseInt(Build.VERSION.SDK) > Build.VERSION_CODES.CUPCAKE;
    useOneShotPreviewCallback = Integer.parseInt(Build.VERSION.SDK) > 3; // 3 = Cupcake

    previewCallback = new PreviewCallback(configManager, useOneShotPreviewCallback);
    autoFocusCallback = new AutoFocusCallback();
  }

  /**
   * 打开摄像机驱动程序并初始化硬件参数。
   *
   * @param holder The surface object which the camera will draw preview frames into.
   * @throws IOException Indicates the camera driver failed to open.
   */
  public void openDriver(SurfaceHolder holder) throws IOException {
    if (camera == null) {
      camera = Camera.open();
      if (camera == null) {
        throw new IOException();
      }
      camera.setPreviewDisplay(holder);

      if (!initialized) {
        initialized = true;
        configManager.initFromCameraParameters(camera);
      }
      configManager.setDesiredCameraParameters(camera);

      FlashlightManager.enableFlashlight();
    }
  }

  /**
   * 如果相机驱动程序还在使用，关闭它。
   */
  public void closeDriver() {
    if (camera != null) {
      FlashlightManager.disableFlashlight();
      camera.release();
      camera = null;
    }
  }

  /**
   * 要求相机硬件开始在屏幕上绘制预览帧。
   */
  public void startPreview() {
    if (camera != null && !previewing) {
      camera.startPreview();
      previewing = true;
    }
  }

  /**
   * 告诉相机停止绘制预览帧。
   */
  public void stopPreview() {
    if (camera != null && previewing) {
      if (!useOneShotPreviewCallback) {
        camera.setPreviewCallback(null);
      }
      camera.stopPreview();
      previewCallback.setHandler(null, 0);
      autoFocusCallback.setHandler(null, 0);
      previewing = false;
    }
  }

  /**
   * 一个预览帧将返回给提供的处理程序。数据将以byte[]的形式到达
   * 在消息。obj字段，宽度和高度编码为消息。分别为__arg1 message.arg2,
   *
   * @param handler 发送消息的处理程序。
   * @param message 要发送的消息的哪个字段。
   */
  public void requestPreviewFrame(Handler handler, int message) {
    if (camera != null && previewing) {
      previewCallback.setHandler(handler, message);
      if (useOneShotPreviewCallback) {
        camera.setOneShotPreviewCallback(previewCallback);
      } else {
        camera.setPreviewCallback(previewCallback);
      }
    }
  }

  /**
   * 要求相机硬件执行自动对焦。
   *
   * @param handler 自动聚焦完成时通知的处理程序。
   * @param message 要传递的消息。
   */
  public void requestAutoFocus(Handler handler, int message) {
    if (camera != null && previewing) {
      autoFocusCallback.setHandler(handler, message);
      //Log.d(TAG, "Requesting auto-focus callback");
      camera.autoFocus(autoFocusCallback);
    }
  }

  /**
   * 计算框架矩形，用户界面应该绘制，以显示用户在哪里放置条形码
   * 这个目标有助于对齐，并迫使用户将设备保持足够远的距离，以确保图像将在焦点。
   *
   * @return 在屏幕上以窗口坐标绘制的矩形。
   */
  public Rect getFramingRect() {
    Point screenResolution = configManager.getScreenResolution();
    if(screenResolution == null)
       return null;
    if (framingRect == null) {
      if (camera == null) {
        return null;
      }

      //修改之后
      int width = screenResolution.x * 7 / 10;
      int height = screenResolution.y * 7 / 10;

      if(height >= width) { //竖屏
        height  = width;
      } else { //黑屏
        width = height;
      }

      int leftOffset = (screenResolution.x - width) / 2;
      int topOffset = (screenResolution.y - height) / 2;
      framingRect = new Rect(leftOffset, topOffset, leftOffset + width, topOffset + height);

    }
    return framingRect;
  }
//  public Rect getFramingRect() {
//    Point screenResolution = configManager.getScreenResolution();
//    if (framingRect == null) {
//      if (camera == null) {
//        return null;
//      }
//      int width = screenResolution.x * 3 / 4;
//      if (width < MIN_FRAME_WIDTH) {
//        width = MIN_FRAME_WIDTH;
//      } else if (width > MAX_FRAME_WIDTH) {
//        width = MAX_FRAME_WIDTH;
//      }
//      int height = screenResolution.y * 3 / 4;
//      if (height < MIN_FRAME_HEIGHT) {
//        height = MIN_FRAME_HEIGHT;
//      } else if (height > MAX_FRAME_HEIGHT) {
//        height = MAX_FRAME_HEIGHT;
//      }
//      int leftOffset = (screenResolution.x - width) / 2;
//      int topOffset = (screenResolution.y - height) / 2;
//      framingRect = new Rect(leftOffset, topOffset, leftOffset + width, topOffset + height);
//      Log.d(TAG, "Calculated framing rect: " + framingRect);
//    }
//    return framingRect;
//  }

  /**
   * 像{@link #getFramingRect}但是坐标是在预览帧中，
   * not UI / screen.
   */
  public Rect getFramingRectInPreview() {
    if (framingRectInPreview == null) {
      Rect rect = new Rect(getFramingRect());
      Point cameraResolution = configManager.getCameraResolution();
      Point screenResolution = configManager.getScreenResolution();
      //modify here
//      rect.left = rect.left * cameraResolution.x / screenResolution.x;
//      rect.right = rect.right * cameraResolution.x / screenResolution.x;
//      rect.top = rect.top * cameraResolution.y / screenResolution.y;
//      rect.bottom = rect.bottom * cameraResolution.y / screenResolution.y;
      rect.left = rect.left * cameraResolution.y / screenResolution.x;
      rect.right = rect.right * cameraResolution.y / screenResolution.x;
      rect.top = rect.top * cameraResolution.x / screenResolution.y;
      rect.bottom = rect.bottom * cameraResolution.x / screenResolution.y;
      framingRectInPreview = rect;
    }
    return framingRectInPreview;
  }

  /**
   * Converts the result points from still resolution coordinates to screen coordinates.
   *
   * @param points The points returned by the Reader subclass through Result.getResultPoints().
   * @return An array of Points scaled to the size of the framing rect and offset appropriately
   *         so they can be drawn in screen coordinates.
   */
  /*
  public Point[] convertResultPoints(ResultPoint[] points) {
    Rect frame = getFramingRectInPreview();
    int count = points.length;
    Point[] output = new Point[count];
    for (int x = 0; x < count; x++) {
      output[x] = new Point();
      output[x].x = frame.left + (int) (points[x].getX() + 0.5f);
      output[x].y = frame.top + (int) (points[x].getY() + 0.5f);
    }
    return output;
  }
   */

  /**
   * A factory method to build the appropriate LuminanceSource object based on the format
   * of the preview buffers, as described by Camera.Parameters.
   *
   * @param data A preview frame.
   * @param width The width of the image.
   * @param height The height of the image.
   * @return A PlanarYUVLuminanceSource instance.
   */
  public PlanarYUVLuminanceSource buildLuminanceSource(byte[] data, int width, int height) {
    Rect rect = getFramingRectInPreview();
    int previewFormat = configManager.getPreviewFormat();
    String previewFormatString = configManager.getPreviewFormatString();
    switch (previewFormat) {
      // This is the standard Android format which all devices are REQUIRED to support.
      // In theory, it's the only one we should ever care about.
      case PixelFormat.YCbCr_420_SP:
      // This format has never been seen in the wild, but is compatible as we only care
      // about the Y channel, so allow it.
      case PixelFormat.YCbCr_422_SP:
        return new PlanarYUVLuminanceSource(data, width, height, rect.left, rect.top,
            rect.width(), rect.height());
      default:
        // The Samsung Moment incorrectly uses this variant instead of the 'sp' version.
        // Fortunately, it too has all the Y data up front, so we can read it.
        if ("yuv420p".equals(previewFormatString)) {
          return new PlanarYUVLuminanceSource(data, width, height, rect.left, rect.top,
            rect.width(), rect.height());
        }
    }
    throw new IllegalArgumentException("Unsupported picture format: " +
        previewFormat + '/' + previewFormatString);
  }

	public Context getContext() {
		return context;
	}

  public  void flashHandler() {
      //camera.startPreview(); 
      Parameters parameters = camera.getParameters();
      // 判断闪光灯当前状态來修改
      if (Parameters.FLASH_MODE_OFF.equals(parameters.getFlashMode())) {
        turnOn(parameters); 
      } else if (Parameters.FLASH_MODE_TORCH.equals(parameters.getFlashMode())) { 
        turnOff(parameters); 
      }

    }  
    //开启闪光灯
    private void turnOn(Parameters parameters) {
      parameters.setFlashMode(Parameters.FLASH_MODE_TORCH); 
      camera.setParameters(parameters); 
    } 
    //关闭闪光灯
    private void turnOff(Parameters parameters) {
      parameters.setFlashMode(Parameters.FLASH_MODE_OFF); 
      camera.setParameters(parameters); 
    }
	

}
