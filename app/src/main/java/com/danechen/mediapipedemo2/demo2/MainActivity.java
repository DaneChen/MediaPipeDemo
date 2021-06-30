package com.danechen.mediapipedemo2.demo2;

import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.SurfaceTexture;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.appcompat.app.AppCompatActivity;

import com.danechen.mediapipedemo2.MyFrameProcessor;
import com.danechen.mediapipedemo2.R;
import com.google.mediapipe.components.CameraHelper;
import com.google.mediapipe.components.PermissionHelper;
import com.google.mediapipe.formats.proto.LandmarkProto.NormalizedLandmark;
import com.google.mediapipe.formats.proto.LandmarkProto.NormalizedLandmarkList;
import com.google.mediapipe.framework.AndroidAssetUtil;
import com.google.mediapipe.framework.AndroidPacketGetter;
import com.google.mediapipe.glutil.EglManager;


/** Main activity of MediaPipe example apps. */
public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";

    private static final String BINARY_GRAPH_NAME = "handtrackinggpu.binarypb";
    private static final String INPUT_VIDEO_STREAM_NAME = "input_video";
    private static final String OUTPUT_VIDEO_STREAM_NAME = "input_video_cpu";
    private static final String OUTPUT_HAND_PRESENCE_STREAM_NAME = "hand_presence";
    private static final String OUTPUT_LANDMARKS_STREAM_NAME = "hand_landmarks";
    private static final CameraHelper.CameraFacing CAMERA_FACING = CameraHelper.CameraFacing.FRONT;


    private static final boolean FLIP_FRAMES_VERTICALLY = true;

    static {
        // Load all native libraries needed by the app.
        System.loadLibrary("mediapipe_jni");
        System.loadLibrary("opencv_java3");
    }

    // {@link SurfaceTexture} where the camera-preview frames can be accessed.
    private SurfaceTexture previewFrameTexture;
    // {@link SurfaceView} that displays the camera-preview frames processed by a MediaPipe graph.
    private SurfaceView previewDisplayView;

    // Creates and manages an {@link EGLContext}.
    private EglManager eglManager;
    // Sends camera-preview frames into a MediaPipe graph for processing, and displays the processed
    // frames onto a {@link Surface}.
    private MyFrameProcessor processor;
    // Converts the GL_TEXTURE_EXTERNAL_OES texture from Android camera into a regular texture to be
    // consumed by {@link FrameProcessor} and the underlying MediaPipe graph.
    private BitmapConverter converter;

    // Handles camera access via the {@link CameraX} Jetpack support library.
    //private CameraXPreviewHelper cameraHelper;
    BmpProducer bitmapProducer;
    // ApplicationInfo for retrieving metadata defined in the manifest.
    private ApplicationInfo applicationInfo;

    Handler handler = new Handler();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        previewDisplayView = new SurfaceView(this);
        setupPreviewDisplayView();

        try {
            applicationInfo =
                    getPackageManager().getApplicationInfo(getPackageName(), PackageManager.GET_META_DATA);
        } catch (PackageManager.NameNotFoundException e) {
            Log.e(TAG, "Cannot find application info: " + e);
        }
        // Initialize asset manager so that MediaPipe native libraries can access the app assets, e.g.,
        // binary graphs.
        AndroidAssetUtil.initializeNativeAssetManager(this);

        eglManager = new EglManager(null);
        processor =
                new MyFrameProcessor(
                        this,
                        eglManager.getNativeContext(),
                        applicationInfo.metaData.getString("binaryGraphName"),
                        applicationInfo.metaData.getString("inputVideoStreamName"),
                        //null);
                        applicationInfo.metaData.getString("outputVideoStreamName"));
        processor.getVideoSurfaceOutput().setFlipY(FLIP_FRAMES_VERTICALLY);
       processor.addPacketCallback(
               "throttled_input_video_gpu",
                (packet) -> {

                    Bitmap w = AndroidPacketGetter.getBitmapFromRgb(packet);
                    updateImg(w);
                });

/*
        processor.addPacketCallback(
                OUTPUT_LANDMARKS_STREAM_NAME,
                (packet) -> {
                    byte[] landmarksRaw = PacketGetter.getProtoBytes(packet);
                    try {
                        NormalizedLandmarkList landmarks = NormalizedLandmarkList.parseFrom(landmarksRaw);
                        if (landmarks == null) {
                            Log.d(TAG, "[TS:" + packet.getTimestamp() + "] No hand landmarks.");
                            return;
                        }
                        // Note: If hand_presence is false, these landmarks are useless.
                        Log.d(
                                TAG,
                                "[TS:"
                                        + packet.getTimestamp()
                                        + "] #Landmarks for hand: "
                                        + landmarks.getLandmarkCount());
                        Log.d(TAG, getLandmarksDebugString(landmarks));
                    } catch (Exception e) {
                        Log.e(TAG, "Couldn't Exception received - " + e);
                        return;
                    }
                });*/

        PermissionHelper.checkAndRequestCameraPermissions(this);
    }

    private void updateImg(Bitmap bitmap){
        //Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
        handler.post(new Runnable() {
            @Override
            public void run() {
                ImageView imageView = findViewById(R.id.handler_back_img);
                imageView.setImageBitmap(bitmap);
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        converter = new BitmapConverter(eglManager.getContext());
        //converter.setFlipY(FLIP_FRAMES_VERTICALLY);
        converter.setConsumer(processor);
        startProducer();
    }

    @Override
    protected void onPause() {
        super.onPause();
        converter.close();
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        PermissionHelper.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    private void setupPreviewDisplayView() {
        previewDisplayView.setVisibility(View.GONE);
        ViewGroup viewGroup = findViewById(R.id.preview_display_layout);
        viewGroup.addView(previewDisplayView);

        previewDisplayView
                .getHolder()
                .addCallback(
                        new SurfaceHolder.Callback() {
                            @Override
                            public void surfaceCreated(SurfaceHolder holder) {
                                processor.getVideoSurfaceOutput().setSurface(holder.getSurface());
                            }

                            @Override
                            public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
                                bitmapProducer.setCustomFrameAvailableListner(converter);
                            }

                            @Override
                            public void surfaceDestroyed(SurfaceHolder holder) {
                                processor.getVideoSurfaceOutput().setSurface(null);
                            }
                        });
    }



    private void startProducer(){
        bitmapProducer = new BmpProducer(this);
        bitmapProducer.setCustomFrameAvailableListner(converter);
        //previewDisplayView.setVisibility(View.VISIBLE);
    }

    private static String getLandmarksDebugString(NormalizedLandmarkList landmarks) {
        int landmarkIndex = 0;
        String landmarksString = "";
        for (NormalizedLandmark landmark : landmarks.getLandmarkList()) {
            landmarksString +=
                    "\t\tLandmark["
                            + landmarkIndex
                            + "]: ("
                            + landmark.getX()
                            + ", "
                            + landmark.getY()
                            + ", "
                            + landmark.getZ()
                            + ")\n";
            ++landmarkIndex;
        }
        return landmarksString;
    }
}

