package fyordan.androidgaze;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ImageFormat;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.YuvImage;
import android.hardware.Camera;
import android.os.Build;
import android.support.annotation.NonNull;
import android.os.Bundle;
import android.util.Log;
import android.util.SparseArray;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.vision.CameraSource;
import com.google.android.gms.vision.Detector;
import com.google.android.gms.vision.Frame;
import com.google.android.gms.vision.MultiProcessor;
import com.google.android.gms.vision.Tracker;
import com.google.android.gms.vision.face.Face;
import com.google.android.gms.vision.face.FaceDetector;
import com.google.android.gms.vision.face.Landmark;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;

public class MainActivity extends Activity {

    private static boolean DBG = BuildConfig.DEBUG; // provide normal log output only in debug version

    protected static FaceDetector faceDetector = null;
    protected static GazeDetector gazeDetector = null;
//    protected static Frame mFrame = null;
    protected static Bitmap mBitmap;
    protected static Bitmap mEyeBitmap;
    protected static Bitmap mBitmapGradientMag;
    protected static byte[] mFrameArray;
    protected static int[] mGrayData;
    protected CameraSource mCameraSource = null;
    protected CameraSourcePreview mPreview;
    protected GraphicOverlay mGraphicOverlay;

    protected int eyeRegionWidth = 80;
    protected int eyeRegionHeight = 60;
    protected int iris_pixel = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // go full screen
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        // and hide the window title.
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getPermissions();   // NOTE: can *not* assume we actually have permissions after this call
        this.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);

        setContentView(R.layout.activity_main);

        // Face Tracking stuff
        //mPreview = new Preview(this, DrawOnTop); // TODO(fyordan): Probably need to create a better preview
        mPreview = (CameraSourcePreview) findViewById(R.id.preview);
        mGraphicOverlay = (GraphicOverlay) findViewById(R.id.faceOverlay);
        faceDetector = new FaceDetector.Builder(getApplicationContext())
                .setTrackingEnabled(true)
                .setLandmarkType(FaceDetector.ALL_LANDMARKS)
                .build();
        gazeDetector = new GazeDetector(faceDetector);
        gazeDetector.setProcessor(
                new MultiProcessor.Builder<>(new GraphicFaceTrackerFactory()).build());

        mCameraSource = new CameraSource.Builder(getApplicationContext(), gazeDetector)
               // .setRequestedPreviewSize(640, 480)
                .setFacing(CameraSource.CAMERA_FACING_FRONT)
                .setRequestedFps(30.0f)
                .build();
    }

    @Override
    protected void onPause() {
        super.onPause();
        mPreview.stop();
    }

    // which means the CameraDevice has to be (re-)opened when the activity is (re-)started
    // (as long as we have permission to use the camera)

    @Override
    protected void onResume() {
        super.onResume();
        if (bCameraPermissionGranted && (mCameraSource != null) && (mPreview != null)) {
            try {
                mPreview.start(mCameraSource, mGraphicOverlay);
            } catch(IOException e) {
                // WHO CARES
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mCameraSource.release();
    }
/*
    //------- nested class DrawOnTop ---------------------------------------------------------------
    class DrawOnTop extends View {
        Bitmap mBitmap;
        byte[] mYUVData;
        int[] mRGBData;
        int mImageWidth, mImageHeight;
        int mTextsize = 40;        // controls size of text on screen
        int mLeading;              // spacing between text lines
        RectF barRect = new RectF();    // used in drawing histogram
        String TAG = "DrawOnTop";       // for logcat output

        public DrawOnTop(Context context) { // constructor
            super(context);

            mBitmap = null;    // will be set up later in Preview - PreviewCallback
            mYUVData = null;
            mRGBData = null;
            barRect = new RectF();    // moved here to reduce GC
            mLeading = mTextsize * 6 / 5;    // adjust line spacing

        }

        Paint makePaint(int color) {
            Paint mPaint = new Paint();
            mPaint.setStyle(Paint.Style.FILL);
            mPaint.setColor(color);
            mPaint.setTextSize(mTextsize);
            mPaint.setTypeface(Typeface.MONOSPACE);
            return mPaint;
        }

        @Override
        protected void onDraw(Canvas canvas) {
            String TAG = "onDraw";
            if (mBitmap == null) {    // sanity check
                Log.w(TAG, "mBitMap is null");
                super.onDraw(canvas);
                return;    // because not yet set up
            }

            // Convert image from YUV to RGB format:
            decodeYUV420SP(mRGBData, mYUVData, mImageWidth, mImageHeight);
            mBitmap.setPixels(mRGBData, 0, mImageWidth, 0, 0, mImageWidth, mImageHeight);

            SparseArray<Face> faces = faceDetector.detect(mFrame);
            double scale = Math.min(
                    canvas.getWidth()/mBitmap.getWidth(),
                    canvas.getHeight()/mBitmap.getHeight());
            for (int i = 0; i < faces.size(); ++i) {
                Log.w(TAG, "iterating through face " + i);
                for (Landmark landmark : faces.valueAt(i).getLandmarks()) {
                    int cx = (int) (landmark.getPosition().x * scale);
                    int cy = (int) (landmark.getPosition().y * scale);
                    Paint paint = new Paint();
                    paint.setColor(Color.GREEN);
                    paint.setStyle(Paint.Style.STROKE);
                    paint.setStrokeWidth(5);
                    canvas.drawCircle(cx, cy, 10, paint);
                }
            }
            //canvas.drawBitmap(mBitmap, src, src, mPaintBlack);
            super.onDraw(canvas);
        } // end onDraw method

        protected int calculateEyeCenter(byte[] grayData, double gradientThreshold) {
            // TODO(fyordan): Shouldn't use mImageWidth and mImageHeight, but grayData dimensions.
            // Calculate gradients.
            // Ignore edges of image to not deal with boundaries.
            double[][] gradients = new double[(mImageWidth-2)*(mImageHeight-2)][2];
            int k = 0;
            for(int i=1; i < mImageWidth-1; i++) {
                for (int j=1; j < mImageHeight-1; j++) {
                    int n = j*mImageWidth + i;
                    //double x = i - mImageWidth/2;
                    //double y = mImageHeight/2 - j;
                    gradients[k][0] = (grayData[n+1] - grayData[n]);
                    gradients[k][1] = (grayData[n + mImageWidth] - grayData[n]);
                    double mag = Math.sqrt(Math.pow(gradients[k][0],2) + Math.pow(gradients[k][1],2));
                    if (mag > gradientThreshold) {
                        gradients[k][0] /= mag;
                        gradients[k][1] /= mag;
                    } else {
                        gradients[k][0] = 0;
                        gradients[k][1] = 0;
                    }
                }
            }
            // For all potential centers
            int c_n = gradients.length/2;
            double max_c = 0;
            for (int i=1; i < mImageWidth-1; i++) {
                for (int j=1; j < mImageHeight; j++) {
                    int n = j*mImageWidth + i;
                    double sumC = 0;
                    for (k = 0; k < gradients.length; k++) {
                        if (!(gradients[k][0]==0 || gradients[k][1]==0)) continue;
                        int k_i = k%(mImageWidth-2)+1;
                        int k_j = k/(mImageWidth-2)+1;
                        double d_i = k_i - i;
                        double d_j = k_j - j;
                        double mag = Math.sqrt(Math.pow(d_i,2) + Math.pow(d_j,2));
                        d_i /= mag;
                        d_j /= mag;
                        sumC += Math.pow(d_i*gradients[k][0] + d_j*gradients[k][1], 2);
                    }
                    // TODO(fyordan): w_c should be the value in a gaussian filtered graydata
                    // sumC *= grayData[n];
                    if (sumC > max_c) {
                        c_n = n;
                        max_c = sumC;
                    }
                }
            }
            return c_n;
        }

        public void decodeYUV420SP(int[] rgb, byte[] yuv420sp, int width, int height) { // convert image in YUV420SP format to RGB format
            final int frameSize = width * height;

            for (int j = 0, pix = 0; j < height; j++) {
                int uvp = frameSize + (j >> 1) * width;    // index to start of u and v data for this row
                int u = 0, v = 0;
                for (int i = 0; i < width; i++, pix++) {
                    int y = (0xFF & ((int) yuv420sp[pix])) - 16;
                    if (y < 0) y = 0;
                    if ((i & 1) == 0) { // even row & column (u & v are at quarter resolution of y)
                        v = (0xFF & yuv420sp[uvp++]) - 128;
                        u = (0xFF & yuv420sp[uvp++]) - 128;
                    }

                    int y1192 = 1192 * y;
                    int r = (y1192 + 1634 * v);
                    int g = (y1192 - 833 * v - 400 * u);
                    int b = (y1192 + 2066 * u);

                    if (r < 0) r = 0;
                    else if (r > 0x3FFFF) r = 0x3FFFF;
                    if (g < 0) g = 0;
                    else if (g > 0x3FFFF) g = 0x3FFFF;
                    if (b < 0) b = 0;
                    else if (b > 0x3FFFF) b = 0x3FFFF;

                    rgb[pix] = 0xFF000000 | ((r << 6) & 0xFF0000) | ((g >> 2) & 0xFF00) | ((b >> 10) & 0xFF);
                }
            }
        }

        public void decodeYUV420SPGrayscale(int[] rgb, byte[] yuv420sp, int width, int height) { // extract grey RGB format image --- not used currently
            final int frameSize = width * height;

            // This is much simpler since we can ignore the u and v components
            for (int pix = 0; pix < frameSize; pix++) {
                int y = (0xFF & ((int) yuv420sp[pix])) - 16;
                if (y < 0) y = 0;
                if (y > 0xFF) y = 0xFF;
                rgb[pix] = 0xFF000000 | (y << 16) | (y << 8) | y;
            }
        }
    }

    // -------- nested class Preview --------------------------------------------------------------

    class Preview extends SurfaceView implements SurfaceHolder.Callback {    // deal with preview that will be shown on screen
        SurfaceHolder mHolder;
        DrawOnTop mDrawOnTop;
        boolean mFinished;
        String TAG = "PreView";    // tag for LogCat

        public Preview (Context context, DrawOnTop drawOnTop) { // constructor
            super(context);

            mDrawOnTop = drawOnTop;
            mFinished = false;

            // Install a SurfaceHolder.Callback so we get notified when the
            // underlying surface is created and destroyed.
            mHolder = getHolder();
            mHolder.addCallback(this);
            //  Following is deprecated setting, but required on Android versions prior to 3.0:
            //  mHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
        }

        public void surfaceCreated (SurfaceHolder holder) {
            String TAG = "surfaceCreated";
            Camera.PreviewCallback mPreviewCallback;
            if (mCamera == null) {    // sanity check
                Log.e(TAG, "ERROR: camera not open");
                System.exit(0);
            }
            Camera.CameraInfo info = new android.hardware.Camera.CameraInfo();
            android.hardware.Camera.getCameraInfo(mCam, info);
            // show some potentially useful information in log file
            switch (info.facing) {    // see which camera we are using
                case Camera.CameraInfo.CAMERA_FACING_BACK:
                    Log.i(TAG, "Camera " + mCam + " facing back");
                    break;
                case Camera.CameraInfo.CAMERA_FACING_FRONT:
                    Log.i(TAG, "Camera " + mCam + " facing front");
                    break;
            }
            if (DBG) Log.i(TAG, "Camera " + mCam + " orientation " + info.orientation);

            mPreviewCallback = new Camera.PreviewCallback() {
                public void onPreviewFrame(byte[] data, Camera camera) { // callback
                    String TAG = "onPreviewFrame";
                    if ((mDrawOnTop == null) || mFinished) return;
                    if (mDrawOnTop.mBitmap == null)  // need to initialize the drawOnTop companion?
                        setupArrays(data, camera);
                    // Pass YUV image data to draw-on-top companion
                    System.arraycopy(data, 0, mDrawOnTop.mYUVData, 0, data.length);
                    mDrawOnTop.invalidate();
                }
            };

            try {
                mCamera.setPreviewDisplay(holder);
                // Preview callback will be used whenever new viewfinder frame is available
                mCamera.setPreviewCallback(mPreviewCallback);
            } catch (IOException e) {
                Log.e(TAG, "ERROR: surfaceCreated - IOException " + e);
                mCamera.release();
                mCamera = null;
            }
        }

        public void surfaceDestroyed (SurfaceHolder holder) {
            String TAG = "surfaceDestroyed";
            // Surface will be destroyed when we return, so stop the preview.
            mFinished = true;
            if (mCamera != null) {    // not expected
                Log.e(TAG, "ERROR: camera still open");
                mCamera.setPreviewCallback(null);
                mCamera.stopPreview();
                mCamera.release();
                mCamera = null;
            }
        }

        public void surfaceChanged (SurfaceHolder holder, int format, int w, int h) {
            String TAG = "surfaceChanged";
            //	Now that the size is known, set up the camera parameters and begin the preview.
            if (mCamera == null) {    // sanity check
                Log.e(TAG, "ERROR: camera not open");
                System.exit(0);
            }
            if (DBG) Log.v(TAG, "Given parameters h " + h + " w " + w);
            if (DBG) Log.v(TAG, "What we are asking for h " + mCameraHeight + " w " + mCameraWidth);
            if (h != mCameraHeight || w != mCameraWidth)
                Log.w(TAG, "Mismatch in image size " + " " + h + " x " + w + " vs " + mCameraHeight + " x " + mCameraWidth);
            // this will be sorted out with a setParamaters() on mCamera
            Camera.Parameters parameters = mCamera.getParameters();
            parameters.setPreviewSize(mCameraWidth, mCameraHeight);
            // check whether following is within PreviewFpsRange ?
            parameters.setPreviewFrameRate(15);    // deprecated
            // parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);
            try {
                mCamera.setParameters(parameters);
            } catch (Exception e) {
                Log.e(TAG, "ERROR: setParameters exception " + e);
                System.exit(0);
            }
            mCamera.startPreview();
        }

        private void setupArrays (byte[] data, Camera camera) {
            String TAG = "setupArrays";
            if (DBG) Log.i(TAG, "Setting up arrays");
            Camera.Parameters params = camera.getParameters();
            mDrawOnTop.mImageHeight = params.getPreviewSize().height;
            mDrawOnTop.mImageWidth = params.getPreviewSize().width;
            if (DBG)
                Log.i(TAG, "height " + mDrawOnTop.mImageHeight + " width " + mDrawOnTop.mImageWidth);
            mDrawOnTop.mBitmap = Bitmap.createBitmap(mDrawOnTop.mImageWidth,
                    mDrawOnTop.mImageHeight, Bitmap.Config.RGB_565);

            mFrame = new Frame.Builder().setBitmap(mDrawOnTop.mBitmap).build();

            mDrawOnTop.mRGBData = new int[mDrawOnTop.mImageWidth * mDrawOnTop.mImageHeight];
            if (DBG)
                Log.i(TAG, "data length " + data.length); // should be width*height*3/2 for YUV format
            mDrawOnTop.mYUVData = new byte[data.length];
            int dataLengthExpected = mDrawOnTop.mImageWidth * mDrawOnTop.mImageHeight * 3 / 2;
            if (data.length != dataLengthExpected)
                Log.e(TAG, "ERROR: data length mismatch " + data.length + " vs " + dataLengthExpected);
        }

    }

    */

    // Used to load the 'native-lib' library on application startup.
    static {
        System.loadLibrary("native-lib");
    }

//////////////////////////////////////////////////////////////////////////////////////////////////////

    // For Android 6.0 (API Level 25)  permission requests

    private static final int REQ_PERMISSION_THISAPP = 0; // unique code for permissions request
    private static boolean bUseCameraFlag = true;               // we want to use the camera
    private static boolean bCameraPermissionGranted = false;   // have CAMERA permission

    private void getPermissions() {
        String TAG = "getPermissions";
        if (DBG) Log.v(TAG, "in getPermissions()");
        if (Build.VERSION.SDK_INT >= 23) {            // need to ask at runtime as of Android 6.0
            String sPermissions[] = new String[2];    // space for possible permission strings
            int nPermissions = 0;    // count of permissions to be asked for
            if (bUseCameraFlag) {    // protection level: dangerous
                if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
                    bCameraPermissionGranted = true;
                else sPermissions[nPermissions++] = Manifest.permission.CAMERA;
            }
            if (nPermissions > 0) {
                if (DBG) Log.d(TAG, "Need to ask for " + nPermissions + " permissions");
                if (nPermissions < sPermissions.length)
                    sPermissions = Arrays.copyOf(sPermissions, nPermissions);
                if (DBG) {
                    for (String sPermission : sPermissions)
                        Log.w(TAG, sPermission);    // debugging only
                }
                requestPermissions(sPermissions, REQ_PERMISSION_THISAPP);    // start the process
            }
        } else {    // in earlier API, permission is dealt with at install time, not run time
            if (bUseCameraFlag) bCameraPermissionGranted = true;
        }
    }

    //	Note: onRequestPermissionsResult happens *after* user has interacted with the permissions request
    //  So, annoyingly, have to now (re-)do things that didn't happen in onCreate() because permissions were not there yet.

    @Override
    // overrides method in android.app.Activity
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        String TAG = "onRequestPermitResult";
        if (DBG) Log.w(TAG, "in onRequestPermissionsResult(...) (" + requestCode + ")");
        if (requestCode != REQ_PERMISSION_THISAPP) {    // check that this is a response to our request
            Log.e(TAG, "Unexpected requestCode " + requestCode);    // can this happen?
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
            return;
        }
        int n = grantResults.length;
        if (DBG) Log.w(TAG, "requestCode=" + requestCode + " for " + n + " permissions");
        for (int i = 0; i < n; i++) {
            if (DBG) Log.w(TAG, "permission " + permissions[i] + " " + grantResults[i]);
            switch (permissions[i]) {
                case Manifest.permission.CAMERA:
                    if (grantResults[i] == PackageManager.PERMISSION_GRANTED) {
                        if (DBG) Log.w(TAG, "CAMERA Permission granted (" + i + ")");
                        bCameraPermissionGranted = true;
                        // redo the setup in onResume(...) ?
                    } else {
                        bUseCameraFlag = false;
                        String str = "You must grant CAMERA permission to use the camera!";
                        Log.e(TAG, str);
                    }
                    break;
            }
        }
    }

    //==============================================================================================
    // Graphic Face Tracker (Stolen from google)
    //==============================================================================================

    /**
     * Factory for creating a face tracker to be associated with a new face.  The multiprocessor
     * uses this factory to create face trackers as needed -- one for each individual.
     */
    private class GraphicFaceTrackerFactory implements MultiProcessor.Factory<Face> {
        @Override
        public Tracker<Face> create(Face face) {
            return new GraphicFaceTracker(mGraphicOverlay);
        }
    }

    /**
     * Face tracker for each detected individual. This maintains a face graphic within the app's
     * associated face overlay.
     */
    private class GraphicFaceTracker extends Tracker<Face> {
        private GraphicOverlay mOverlay;
        private FaceGraphic mFaceGraphic;

        GraphicFaceTracker(GraphicOverlay overlay) {
            mOverlay = overlay;
            mFaceGraphic = new FaceGraphic(overlay);
        }


        /**
         * Update the position/characteristics of the face within the overlay.
         */
        @Override
        public void onUpdate(FaceDetector.Detections<Face> detectionResults, Face face) {
            mOverlay.add(mFaceGraphic);
            mFaceGraphic.updateFace(face);
        }

        /**
         * Hide the graphic when the corresponding face was not detected.  This can happen for
         * intermediate frames temporarily (e.g., if the face was momentarily blocked from
         * view).
         */
        @Override
        public void onMissing(FaceDetector.Detections<Face> detectionResults) {
            mOverlay.remove(mFaceGraphic);
        }

        /**
         * Called when the face is assumed to be gone for good. Remove the graphic annotation from
         * the overlay.
         */
        @Override
        public void onDone() {
            mOverlay.remove(mFaceGraphic);
        }
    }

    /**
     * Graphic instance for rendering face position, orientation, and landmarks within an associated
     * graphic overlay view.
     */
    class FaceGraphic extends GraphicOverlay.Graphic {
        private static final float ID_TEXT_SIZE = 40.0f;
        private static final float BOX_STROKE_WIDTH = 5.0f;

        private final int COLOR_CHOICES[] = {
                Color.BLUE,
                Color.CYAN,
                Color.GREEN,
                Color.MAGENTA,
                Color.RED,
                Color.WHITE,
                Color.YELLOW
        };
        private int mCurrentColorIndex = 0;

        private Paint mFacePositionPaint;
        private Paint mIdPaint;
        private Paint mBoxPaint;

        private volatile Face mFace;

        FaceGraphic(GraphicOverlay overlay) {
            super(overlay);

            mCurrentColorIndex = (mCurrentColorIndex + 1) % COLOR_CHOICES.length;
            final int selectedColor = COLOR_CHOICES[mCurrentColorIndex];

            mFacePositionPaint = new Paint();
            mFacePositionPaint.setColor(selectedColor);

            mIdPaint = new Paint();
            mIdPaint.setColor(selectedColor);
            mIdPaint.setTextSize(ID_TEXT_SIZE);

            mBoxPaint = new Paint();
            mBoxPaint.setColor(selectedColor);
            mBoxPaint.setStyle(Paint.Style.STROKE);
            mBoxPaint.setStrokeWidth(BOX_STROKE_WIDTH);
        }


        /**
         * Updates the face instance from the detection of the most recent frame.  Invalidates the
         * relevant portions of the overlay to trigger a redraw.
         */
        void updateFace(Face face) {
            mFace = face;
            postInvalidate();
        }

        /**
         * Draws the face annotations for position on the supplied canvas.
         */
        @Override
        public void draw(Canvas canvas) {
            Face face = mFace;
            if (face == null) {
                return;
            }


            // Draws a circle at the position of the detected face, with the face's track id below.
            float x = translateX(face.getPosition().x + face.getWidth() / 2);
            float y = translateY(face.getPosition().y + face.getHeight() / 2);

            // Draws a bounding box around the face.
            float xOffset = scaleX(face.getWidth() / 2.0f);
            float yOffset = scaleY(face.getHeight() / 2.0f);
            float left = x - xOffset;
            float top = y - yOffset;
            float right = x + xOffset;
            float bottom = y + yOffset;
            canvas.drawRect(left, top, right, bottom, mBoxPaint);
//            canvas.drawCircle(x,y,5,mBoxPaint);

//            canvas.drawBitmap(mBitmap, left, top, null);

            for (Landmark landmark : face.getLandmarks()) {
                int landmark_type = landmark.getType();
//                if (landmark_type == Landmark.LEFT_EYE || landmark_type == Landmark.RIGHT_EYE) {
                if (landmark_type == Landmark.LEFT_EYE) {
                    int cx = (int) translateX(landmark.getPosition().x);
                    int cy = (int) translateY(landmark.getPosition().y);
                    Paint paint = new Paint();
                    paint.setColor(Color.GREEN);
                    paint.setStyle(Paint.Style.STROKE);
                    paint.setStrokeWidth(5);

                    // TODO(fyordan): These numbers are arbitray, probably should be proportional to face dimensions.
                    canvas.drawRect(cx-eyeRegionWidth/2, cy-eyeRegionHeight/2,
                            cx+eyeRegionWidth/2, cy+eyeRegionHeight/2, paint);
                    //canvas.drawCircle(cx, cy, 10, paint);

                    mEyeBitmap = Bitmap.createBitmap(mBitmap,
                            (int)landmark.getPosition().x-eyeRegionWidth/2,
                            (int)landmark.getPosition().y-eyeRegionHeight/2,
                            eyeRegionWidth, eyeRegionHeight);

                    iris_pixel = calculateEyeCenter(mEyeBitmap, 10.0, 30);
//                    if (mBitmapGradientMag != null)  canvas.drawBitmap(mBitmapGradientMag, 0, 0, paint);
                    //canvas.drawBitmap(eyeBitmap, 0, 0, paint);
                }
            }
            if (mEyeBitmap != null) {
                Bitmap resizedBitmap = Bitmap.createScaledBitmap(mEyeBitmap,
                        eyeRegionWidth*4,
                        eyeRegionHeight*4,
                        false);
                canvas.drawBitmap(resizedBitmap, 0, 0, mBoxPaint);
                int iris_x = iris_pixel%mEyeBitmap.getWidth()*4;
                int iris_y = iris_pixel/mEyeBitmap.getWidth()*4;
                canvas.drawCircle(iris_x, iris_y, 80, mBoxPaint);
            }
        }

        protected int calculateEyeCenter(Bitmap eyeMap, double gradientThreshold, int d_thresh) {
            // TODO(fyordan): Shouldn't use mImageWidth and mImageHeight, but grayData dimensions.
            // Calculate gradients.
            // Ignore edges of image to not deal with boundaries.
            Log.e("CalculateEyeCenter", "Well it entered");
            int imageWidth = eyeMap.getWidth();
            int imageHeight = eyeMap.getHeight();
            int grayData[] = new int[imageWidth*imageHeight];
            int mags[] = new int[(imageWidth-2)*(imageHeight-2)];
            Log.e("CalculateEyeCenter", "Size is : " + imageWidth*imageHeight);
            eyeMap.getPixels(grayData, 0, imageWidth, 0, 0, imageWidth, imageHeight);
            double[][] gradients = new double[(imageWidth-2)*(imageHeight-2)][2];
//            Pair<Point, Pair<float, float>> [] gradients =
//                    new Pair<Point, Pair<float,float>>[(imageWidth-2)*(imageHeight-2)];
            int k = 0;
            int magCount = 0;
            for(int i=1; i < imageWidth-1; i++) {
                for (int j=1; j < imageHeight-1; j++) {
                    int n = j*imageWidth + i;
                    //double x = i - imageWidth/2;
                    //double y = imageHeight/2 - j;
//                    gradients[k][0] = (grayData[n+1] - grayData[n]);
//                    gradients[k][1] = (grayData[n + imageWidth] - grayData[n]);
                    gradients[k][0] = (grayData[n+1] & 0xff) - (grayData[n] & 0xff);
                    gradients[k][1] = (grayData[n + imageWidth] & 0xff) - (grayData[n] & 0xff);
                    double mag = Math.sqrt(Math.pow(gradients[k][0],2) + Math.pow(gradients[k][1],2));
                    mags[k] = (int) mag;
                    if (mag > gradientThreshold) {
                        gradients[k][0] /= mag;
                        gradients[k][1] /= mag;
                        magCount++;
                    } else {
                        gradients[k][0] = 0;
                        gradients[k][1] = 0;
                    }
                    k++;
                }
            }
            //mBitmapGradientMag = BitmapFactory.decodeByteArray(mags, 0, mags.length);
//            mBitmapGradientMag.copyPixelsFromBuffer(ByteBuffer.wrap(mags));
            //mBitmapGradientMag = Bitmap.createBitmap(mags, imageWidth, imageHeight, Bitmap.Config.ARGB_8888);
            Log.e("CalculateEyeCenter", "mags above threshold: " + magCount);
            Log.e("CalculateEyeCenter", "Now we need to iterate through them all again");
            // For all potential centers
            int c_n = gradients.length/2;
            double max_c = 0;
            for (int i=1; i < imageWidth-1; i+=5) {
                for (int j=1; j < imageHeight-1; j+=5) {
                    int n = j*imageWidth + i;
                    int k_left = Math.max(0, i - d_thresh - 1);
                    int k_right= Math.min(imageWidth-2, i+d_thresh-1);
                    int k_top = Math.max(0, j - d_thresh-1);
                    int k_bottom = Math.min(imageHeight-2, j+d_thresh-1);
                    double sumC = 0;
                    for (int k_w = k_left; k < k_right; ++k_w) {
                        for (int k_h = k_top; k < k_bottom; ++k_h) {
                            k = k_w + k_h*(imageWidth-2);
//                    for (k = 0; k < gradients.length; k++) {
                            if (!(gradients[k][0] == 0 || gradients[k][1] == 0)) continue;
//                            int k_i = k % (imageWidth - 2) + 1;
//                            int k_j = k / (imageWidth - 2) + 1;
//                            double d_i = k_i - i;
//                            double d_j = k_j - j;
                            double d_i = k_w - i;
                            double d_j = k_h - j;
//                            if (Math.abs(d_i) > d_thresh || Math.abs(d_j) > d_thresh) continue;
                            double mag = Math.sqrt(Math.pow(d_i, 2) + Math.pow(d_j, 2));
                            if (mag > d_thresh) continue;
                            mag = mag == 0 ? 1 : mag;
                            d_i /= mag;
                            d_j /= mag;
                            sumC += Math.pow(d_i * gradients[k][0] + d_j * gradients[k][1], 2);
                        }
                    }
                    // TODO(fyordan): w_c should be the value in a gaussian filtered graydata
                    // sumC *= grayData[n];
                    if (sumC > max_c) {
                        c_n = n;
                        max_c = sumC;
                    }
                }
            }
            return c_n;
        }
    }

    // Convert from YUV color to gray - can ignore chromaticity for speed

    protected int[] decodeYUV420SPGrayscale (byte[] yuv420sp, int height, int width)
    {	// not used here ?
        final int frameSize = width * height;
        int[] gray = new int[frameSize];

        for (int pix = 0; pix < frameSize; pix++) {
            int pixVal = (int) yuv420sp[pix] & 0xff;
            // format for grey scale integer format data is 0xFFRRGGBB where RR = GG = BB
            gray[pix] = 0xff000000 | (pixVal << 16) | (pixVal << 8) | pixVal;
        }
        return gray;
    }

    class GazeDetector extends Detector<Face> {
        private Detector<Face> mDelegate;

        GazeDetector(Detector<Face> delegate) {
            mDelegate = delegate;
        }

        public SparseArray<Face> detect(Frame frame) {
            int w = frame.getMetadata().getWidth();
            int h = frame.getMetadata().getHeight();
            YuvImage yuvimage=new YuvImage(frame.getGrayscaleImageData().array(), ImageFormat.NV21, w, h, null);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            yuvimage.compressToJpeg(
                    new Rect(0, 0, w, h), 100, baos); // Where 100 is the quality of the generated jpeg
            mFrameArray = baos.toByteArray();
            mBitmap = BitmapFactory.decodeByteArray(mFrameArray, 0, mFrameArray.length);
//            mGrayData = decodeYUV420SPGrayscale(yuvimage.getYuvData(), yuvimage.getHeight(), yuvimage.getHeight());
//            mBitmap = Bitmap.createBitmap(yuvimage.getWidth(), yuvimage.getHeight(), Bitmap.Config.ARGB_8888);
            //mBitmap.setPixels(mGrayData, 0, yuvimage.getWidth(), 0, 0, yuvimage.getWidth(), yuvimage.getHeight());
//            mBitmap = Bitmap.createBitmap(mGrayData, yuvimage.getWidth(), yuvimage.getHeight(), Bitmap.Config.ARGB_8888);
//            mBitmap = Bitmap.createBitmap(mGrayData, w, h, Bitmap.Config.ARGB_8888);
            return mDelegate.detect(frame);
        }

        public boolean isOperational() {
            return mDelegate.isOperational();
        }

        public boolean setFocus(int id) {
            return mDelegate.setFocus(id);
        }
    }
}
