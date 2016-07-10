package eu.hgross.blaubotcam.video;

import android.content.Context;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.hardware.Camera;
import android.hardware.Camera.Parameters;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Observable;

import eu.hgross.blaubot.util.Log;

/**
 * Given a SurfaceView and a Camera object, this class provides some methods to start a simple MJPEG server serving the camera's preview image.
 * 
 * To integrate the preview into your app, you can use getSurfaceView() and add it to your layout.
 *
 * @author Henning Gross {@literal (mail.to@henning-gross.de)}
 */
public class CameraReader extends Observable implements Camera.PreviewCallback, SurfaceHolder.Callback {
    private static final String LOG_TAG = "CameraReader";
    private static final int DEFAULT_FPS = 15;
    private static final int DEFAULT_JPEG_QUALITY = 30;

    private final SurfaceView surfaceView;
    private byte[] lastPreviewJpeg;
    private Camera camera;
    private Parameters cameraParameters;

    /**
     * The jpeg quality to use
     */
    private int jpegQuality;
    /**
     * The camera needs some time to stop/start preview. Some android devices have concurrency problems with fast start() stop() calls so we give them this period of time.
     */
    private static final long CAMERA_SLEEP_PERIOD = 550; // ms;
    private boolean showingPreview = false;
    private boolean flashLightOn = false;
    private long lastRenderedFrameTime = 0;

    /**
     * @param context the android context to be used
     */
    public CameraReader(Context context) {
        setMaxFps(DEFAULT_FPS);
        setJpegQuality(DEFAULT_JPEG_QUALITY);
        this.surfaceView = new SurfaceView(context);
        SurfaceHolder holder = this.surfaceView.getHolder();
        holder.setSizeFromLayout();
        holder.addCallback(this);
    }

    public void acquireCamera() {
        Log.d(LOG_TAG, "Acquiring camera");
        try {
            this.camera = Camera.open();
        } catch (RuntimeException e) {
            Log.w(LOG_TAG, "Catched a runtime exception acquiring a back facing camera!", e);
        }
        if (this.camera == null) { // no backfacing camera - try if we can find a front facing camera
            Log.d(LOG_TAG, "Could not find a back facing camera - trying to get another ...");
            try {
                this.camera = Camera.open(0);
            } catch (RuntimeException e) {
                Log.w(LOG_TAG, "Catched a runtime exception acquiring a camera!", e);
            }
        }
        if (this.camera != null) {
            Log.d(LOG_TAG, "Got a camera ...");
            this.camera.lock();
        } else {
            Log.w(LOG_TAG, "Could not acquire camera");
        }
    }

    public void releaseCamera() {
        Log.d(LOG_TAG, "Releasing camera ...");
        if (this.camera != null) {
            this.camera.release();
        }
        this.camera = null;
    }

    /**
     * The used surface view to show the image.
     *
     * @return
     */
    public SurfaceView getSurfaceView() {
        return surfaceView;
    }

    /**
     * Turns the LED flash on. Note that if this (re)starts the previewing!
     */
    public synchronized void turnFlashlightOn() {
        if (flashLightOn) {
            return;
        }
        flashLightOn = true;
        if (showingPreview) {
            Log.d(LOG_TAG, "Restarting preview to turn on flashlight.");
            // restart preview
            stopPreview();
            try {
                Thread.sleep(CAMERA_SLEEP_PERIOD); // to overcome another android bug -.-
            } catch (InterruptedException e) {
            }
            startPreview();
        } else {
            startPreview();
            try {
                Thread.sleep(CAMERA_SLEEP_PERIOD); // to overcome another android bug -.-
            } catch (InterruptedException e) {
            }
            stopPreview();
        }
        Log.d(LOG_TAG, "Flashlight is now turned on.");
    }

    /**
     * Turns of the flashlight LED.
     */
    public synchronized void turnFlashlightOff() {
        if (!flashLightOn) {
            return;
        }
        flashLightOn = false;
        if (showingPreview) {
            Log.d(LOG_TAG, "Restarting preview to turn of flashlight.");
            // restart preview
            stopPreview();
            try {
                Thread.sleep(CAMERA_SLEEP_PERIOD); // to overcome another android bug -.-
            } catch (InterruptedException e) {
            }
            startPreview();
        } else {
            startPreview();
            try {
                Thread.sleep(CAMERA_SLEEP_PERIOD); // to overcome another android bug -.-
            } catch (InterruptedException e) {
            }
            stopPreview();
        }
        Log.d(LOG_TAG, "Flashlight is now turned off.");
    }

    private final ByteArrayOutputStream jpgOut = new ByteArrayOutputStream();
    private boolean processing = false;

    @Override
    public void onPreviewFrame(byte[] data, Camera camera) {
        if (processing) {
            return;
        }
        if (this.camera == null) {
            // do nothing if the camera is gone (overcomes some concurrency issues on poorly implemented android cameras)
            return;
        }
        long now = System.currentTimeMillis();
        long diff = now - lastRenderedFrameTime;
        if (diff < maxFpsPeriod) {
            return;
        }

        try {
            cameraParameters = camera.getParameters();
            int imageFormat = cameraParameters.getPreviewFormat();
            if (imageFormat == ImageFormat.NV21) {
                processing = true;
                int previewSizeWidth = cameraParameters.getPreviewSize().width;
                int previewSizeHeight = cameraParameters.getPreviewSize().height;
                Rect rect = new Rect(0, 0, previewSizeWidth, previewSizeHeight);
                YuvImage img = new YuvImage(data, ImageFormat.NV21, previewSizeWidth, previewSizeHeight, null);
                img.compressToJpeg(rect, jpegQuality, jpgOut);
                lastPreviewJpeg = jpgOut.toByteArray();
                jpgOut.reset();
                lastRenderedFrameTime = now;
                setChanged();
                notifyObservers(lastPreviewJpeg);
                processing = false;
            }
        } catch (RuntimeException e) {
            Log.e(LOG_TAG, "Something went wrong capturing a previeFrame", e);
        }
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
    }

    /**
     * Toggles the video stream on and off.
     */
    public synchronized void toggleVideoStream() {
        if (showingPreview) {
            stopPreview();
        } else {
            startPreview();
        }
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
    }


    /**
     * @return true if the camera is currently fetching preview images, false otherwise
     */
    public boolean isShowingPreview() {
        return showingPreview;
    }

    /**
     * onResume
     */
    public synchronized void startPreview() {
        if (showingPreview)
            return;
        acquireCamera();
        if (camera == null) {
            Log.d(LOG_TAG, "No acquired camera found. Not starting preview.");
            return;
        }
        Parameters params = null;
        try {
            params = camera.getParameters();
        } catch (RuntimeException e) {
            Log.e(LOG_TAG, "Stream not started! Failed to get camera parameters - this happens occasionally if the headlights are toggled to fast! Seems to be an android issue. Message: " + e.getMessage(), e);
            return;
        }
        // List<Size> sizes = params.getSupportedPreviewSizes();
        // Size size = sizes.get(1);
        // params.setPictureSize(size.width, size.height);
        // params.setPreviewSize(size.width, size.height);
        params.setJpegQuality(jpegQuality);
        boolean flashModeSupported = params.getSupportedFlashModes() == null ? false : params.getSupportedFlashModes().contains(Parameters.FLASH_MODE_TORCH);
        if (flashModeSupported) {
            params.setFlashMode(flashLightOn ? Parameters.FLASH_MODE_TORCH : Parameters.FLASH_MODE_OFF);
        } else {
            Log.w(LOG_TAG, "Flashlight not supported by device.");
        }

        // find the highest maxFps range
        int[] range = null;
        for (int[] ints : params.getSupportedPreviewFpsRange()) {
            if (range == null || ints[0] > range[0])
                range = ints;
        }
        params.setPreviewFpsRange(range[0], range[1]);

        try {
            try {
                camera.setPreviewDisplay(surfaceView.getHolder());
            } catch (IOException e) {
                Log.e(LOG_TAG, "IO Exception setting previewDisplay for camera", e);
            }
            camera.setParameters(params);
            camera.setPreviewCallback(this);
            camera.startPreview();
            this.showingPreview = true;
        } catch (RuntimeException e) {
            Log.w(LOG_TAG, "Something went wrong starting the camera preview - catching the exception and going back to disabled state.", e);
            if (camera != null) {
                camera.setPreviewCallback(null);
                stopPreview();
            }
        }
    }

    /**
     * Stop the camera's preview.
     */
    public synchronized void stopPreview() {
        if (camera == null)
            return;
        try {
            camera.setPreviewDisplay(null);
        } catch (IOException e) {
            Log.e(LOG_TAG, "IO Exception setting previewDisplay for camera", e);
        }
        camera.setPreviewCallback(null);
        camera.stopPreview();
        this.showingPreview = false;
        releaseCamera();
    }

    /**
     * Retrieves the flashlight state.
     *
     * @return true, if the flashlight is set to on
     */
    public boolean isFlashLightOn() {
        return flashLightOn;
    }


    /**
     * the configured fps limit
     */
    private long maxFps;
    /**
     * min delay between pictures to maintain the max fps
     */
    private long maxFpsPeriod;

    /**
     * The configured frames per seconds
     *
     * @return
     */
    public long getMAxFps() {
        return maxFps;
    }

    /**
     * sets the max frames per second
     *
     * @param fps
     */
    public void setMaxFps(long fps) {
        this.maxFps = fps;
        this.maxFpsPeriod = 1000 / fps;
    }

    /**
     * the jpeg quality
     *
     * @return [1, 100]
     */
    public int getJpegQuality() {
        return jpegQuality;
    }

    /**
     * Sets the jpeg quality
     *
     * @param jpegQuality the jpeg quality in percent, higher is better [1,100]
     */
    public void setJpegQuality(int jpegQuality) {
        if (jpegQuality < 1 || jpegQuality > 100) {
            throw new IllegalArgumentException("JPEG quality has to be in [1,100]");
        }
        this.jpegQuality = jpegQuality;
    }
}
