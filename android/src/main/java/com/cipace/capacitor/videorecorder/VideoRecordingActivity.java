package com.cipace.capacitor.videorecorder;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.hardware.Camera;
import android.media.CamcorderProfile;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Gravity;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.TextView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;


import java.io.File;
import java.io.IOException;
import java.util.List;

public class VideoRecordingActivity extends Activity implements SurfaceHolder.Callback {
    
    private static final String TAG = "VideoRecordingActivity";
    
    public static final String EXTRA_OPTIONS = "options";
    public static final String EXTRA_RESULT = "result";
    public static final String EXTRA_ERROR = "error";
    public static final String EXTRA_IS_CAPTURE_MODE = "is_capture_mode";
    
    private SurfaceView surfaceView;
    private SurfaceHolder surfaceHolder;
    private Camera camera;
    private MediaRecorder mediaRecorder;
    private Button recordButton;
    private Button stopButton;
    private Button cancelButton;
    private Button switchCameraButton;
    private TextView recordingLabel;
    private TextView durationLabel;

    private boolean isRecording = false;
    private boolean isPreviewMode = true;
    private int currentCameraId = Camera.CameraInfo.CAMERA_FACING_BACK;
    private VideoRecordingOptions options;
    private String outputFilePath;
    private long recordingStartTime;
    private Handler blinkHandler;
    private Runnable blinkRunnable;
    private Handler durationHandler;
    private Runnable durationRunnable;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 首先检查权限
        if (!hasRequiredPermissions()) {
            requestRequiredPermissions();
            return;
        }

        // Set fullscreen
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);

        // Get options from intent
        options = (VideoRecordingOptions) getIntent().getSerializableExtra(EXTRA_OPTIONS);
        if (options == null) {
            finishWithError("INVALID_OPTIONS", "Recording options not provided");
            return;
        }
        
        setupUI();
        setupCamera();
    }
    
    private void setupUI() {
        FrameLayout mainLayout = new FrameLayout(this);
        mainLayout.setBackgroundColor(Color.BLACK);
        
        // Surface view for camera preview
        surfaceView = new SurfaceView(this);
        surfaceHolder = surfaceView.getHolder();
        surfaceHolder.addCallback(this);
        surfaceHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);

        // 初始设置为全屏，稍后会根据预览尺寸调整
        FrameLayout.LayoutParams surfaceParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        surfaceParams.gravity = Gravity.CENTER;
        mainLayout.addView(surfaceView, surfaceParams);
        
        // Recording label (initially hidden) - 避开刘海屏幕
        recordingLabel = new TextView(this);
        recordingLabel.setText("● REC");
        recordingLabel.setTextColor(Color.RED);
        recordingLabel.setTextSize(18);
        recordingLabel.setVisibility(View.GONE);
        FrameLayout.LayoutParams labelParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        labelParams.gravity = Gravity.TOP | Gravity.LEFT;
        labelParams.setMargins(60, 80, 0, 0); // 增加顶部边距避开刘海
        mainLayout.addView(recordingLabel, labelParams);

        // Duration label (initially hidden) - 顶部中央显示录制时长
        durationLabel = new TextView(this);
        durationLabel.setText("00:00");
        durationLabel.setTextColor(Color.WHITE);
        durationLabel.setTextSize(20);
        durationLabel.setTypeface(android.graphics.Typeface.MONOSPACE);
        durationLabel.setGravity(Gravity.CENTER);
        durationLabel.setVisibility(View.GONE);

        // 设置背景
        GradientDrawable durationBg = new GradientDrawable();
        durationBg.setColor(Color.parseColor("#99000000"));
        durationBg.setCornerRadius(20);
        durationLabel.setBackground(durationBg);
        durationLabel.setPadding(30, 15, 30, 15);

        FrameLayout.LayoutParams durationParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        durationParams.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        durationParams.setMargins(0, 80, 0, 0); // 避开刘海屏幕
        mainLayout.addView(durationLabel, durationParams);
        
        // 底部按钮行 - 从左到右：取消、录制、切换摄像头

        // Cancel button - 圆形，左侧
        cancelButton = createCircularButton("✕", Color.parseColor("#757575"));
        FrameLayout.LayoutParams cancelParams = new FrameLayout.LayoutParams(140, 140);
        cancelParams.gravity = Gravity.BOTTOM | Gravity.LEFT;
        cancelParams.setMargins(100, 0, 0, 150); // 初始设置更大的底部边距
        cancelButton.setOnClickListener(v -> cancelRecording());
        mainLayout.addView(cancelButton, cancelParams);

        // Record button - 圆形，中间，最大
        recordButton = createCircularButton("●", Color.parseColor("#F44336"));
        recordButton.setTextSize(32);
        FrameLayout.LayoutParams recordParams = new FrameLayout.LayoutParams(160, 160);
        recordParams.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        recordParams.setMargins(0, 0, 0, 150); // 初始设置更大的底部边距
        recordButton.setOnClickListener(v -> startRecording());
        mainLayout.addView(recordButton, recordParams);

        // Stop button - 圆形，中间，与Record相同位置（初始隐藏）
        stopButton = createCircularButton("■", Color.parseColor("#F44336"));
        stopButton.setTextSize(28);
        FrameLayout.LayoutParams stopParams = new FrameLayout.LayoutParams(160, 160);
        stopParams.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        stopParams.setMargins(0, 0, 0, 150); // 初始设置更大的底部边距
        stopButton.setOnClickListener(v -> stopRecording());
        stopButton.setVisibility(View.GONE);
        mainLayout.addView(stopButton, stopParams);

        // Switch camera button - 圆形，右侧
        switchCameraButton = createCircularButton("⟲", Color.parseColor("#99000000"));
        switchCameraButton.setTextSize(20);
        FrameLayout.LayoutParams switchParams = new FrameLayout.LayoutParams(140, 140);
        switchParams.gravity = Gravity.BOTTOM | Gravity.RIGHT;
        switchParams.setMargins(0, 0, 100, 150); // 初始设置更大的底部边距
        switchCameraButton.setOnClickListener(v -> switchCamera());
        mainLayout.addView(switchCameraButton, switchParams);
        
        setContentView(mainLayout);
    }
    
    private Button createCircularButton(String text, int backgroundColor) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextColor(Color.WHITE);
        button.setTextSize(20);
        button.setTypeface(null, android.graphics.Typeface.BOLD);

        // 创建圆形背景
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(backgroundColor);
        drawable.setShape(GradientDrawable.OVAL); // 设置为圆形

        // 添加阴影效果
        button.setElevation(12);
        button.setBackground(drawable);

        // 确保按钮内容居中
        button.setGravity(Gravity.CENTER);

        return button;
    }
    
    private void setupCamera() {
        // Set initial camera based on options
        if ("front".equals(options.camera)) {
            currentCameraId = Camera.CameraInfo.CAMERA_FACING_FRONT;
        }
        
        openCamera();
    }
    
    private void openCamera() {
        try {
            if (camera != null) {
                camera.release();
            }
            
            camera = Camera.open(currentCameraId);
            Camera.Parameters parameters = camera.getParameters();
            
            // Set camera parameters with optimal preview size
            Camera.Size optimalPreviewSize = getOptimalPreviewSize(parameters);
            if (optimalPreviewSize != null) {
                parameters.setPreviewSize(optimalPreviewSize.width, optimalPreviewSize.height);
            }

            // 设置对焦模式以获得更清晰的画面
            List<String> focusModes = parameters.getSupportedFocusModes();
            if (focusModes.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO)) {
                parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO);
            } else if (focusModes.contains(Camera.Parameters.FOCUS_MODE_AUTO)) {
                parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);
            }

            // 启用视频稳定（如果支持）
            if (parameters.isVideoStabilizationSupported()) {
                parameters.setVideoStabilization(true);
            }

            // 设置场景模式为视频录制
            List<String> sceneModes = parameters.getSupportedSceneModes();
            if (sceneModes != null && sceneModes.contains(Camera.Parameters.SCENE_MODE_AUTO)) {
                parameters.setSceneMode(Camera.Parameters.SCENE_MODE_AUTO);
            }

            camera.setParameters(parameters);
            camera.setDisplayOrientation(90);

            if (surfaceHolder != null) {
                camera.setPreviewDisplay(surfaceHolder);
                camera.startPreview();

                // 调整 SurfaceView 尺寸以保持正确的宽高比
                adjustSurfaceViewSize(optimalPreviewSize);
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error opening camera", e);
            finishWithError("CAMERA_ERROR", "Failed to open camera: " + e.getMessage());
        }
    }
    
    private void switchCamera() {
        if (isRecording) return; // Don't switch during recording
        
        currentCameraId = (currentCameraId == Camera.CameraInfo.CAMERA_FACING_BACK) 
                ? Camera.CameraInfo.CAMERA_FACING_FRONT 
                : Camera.CameraInfo.CAMERA_FACING_BACK;
        
        openCamera();
    }
    
    private void startRecording() {
        try {
            // Prepare MediaRecorder
            mediaRecorder = new MediaRecorder();
            camera.unlock();
            mediaRecorder.setCamera(camera);

            mediaRecorder.setAudioSource(MediaRecorder.AudioSource.CAMCORDER);
            mediaRecorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);

            // Set output format and encoders
            mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            mediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);

            // Set quality based on options
            CamcorderProfile profile = getQualityProfile();
            mediaRecorder.setVideoSize(profile.videoFrameWidth, profile.videoFrameHeight);
            mediaRecorder.setVideoFrameRate(profile.videoFrameRate);
            mediaRecorder.setVideoEncodingBitRate(profile.videoBitRate);
            mediaRecorder.setAudioEncodingBitRate(profile.audioBitRate);
            mediaRecorder.setAudioSamplingRate(profile.audioSampleRate);

            // Set output file
            File outputDir = new File(getExternalFilesDir(null), "videos");
            if (!outputDir.exists()) {
                outputDir.mkdirs();
            }

            String fileName = options.fileNamePrefix + "_" + System.currentTimeMillis() + ".mp4";
            File outputFile = new File(outputDir, fileName);
            outputFilePath = outputFile.getAbsolutePath();
            mediaRecorder.setOutputFile(outputFilePath);

            // Set max duration if specified
            if (options.maxDuration > 0) {
                mediaRecorder.setMaxDuration((int) (options.maxDuration * 1000));
                mediaRecorder.setOnInfoListener((mr, what, extra) -> {
                    if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED) {
                        stopRecording();
                    }
                });
            }

            mediaRecorder.setPreviewDisplay(surfaceHolder.getSurface());
            mediaRecorder.prepare();
            mediaRecorder.start();

            // Update UI - 隐藏预览状态的按钮，显示录制状态的按钮
            isRecording = true;
            isPreviewMode = false;
            recordingStartTime = System.currentTimeMillis();

            // 隐藏预览状态的按钮
            recordButton.setVisibility(View.GONE);
            cancelButton.setVisibility(View.GONE);
            switchCameraButton.setVisibility(View.GONE);

            // 显示录制状态的按钮和指示器
            stopButton.setVisibility(View.VISIBLE);
            recordingLabel.setVisibility(View.VISIBLE);
            durationLabel.setVisibility(View.VISIBLE);

            startBlinkingAnimation();
            startDurationTimer();

            // 注意：这里不回调到前端，让用户在原生界面继续操作
            Log.d(TAG, "📹 录制已开始，等待用户点击停止或取消...");

        } catch (Exception e) {
            Log.e(TAG, "Error starting recording", e);
            finishWithError("RECORDING_FAILED", "Failed to start recording: " + e.getMessage());
        }
    }

    private void stopRecording() {
        try {
            Log.d(TAG, "🛑 用户点击停止录制，准备回调到前端...");

            // 确保总是有回调，即使录制状态异常
            if (mediaRecorder != null && isRecording) {
                Log.d(TAG, "📹 正常停止录制流程...");

                mediaRecorder.stop();
                mediaRecorder.release();
                mediaRecorder = null;

                camera.lock();
                isRecording = false;

                stopBlinkingAnimation();
                stopDurationTimer();

                // Create result
                long endTime = System.currentTimeMillis();
                double duration = (endTime - recordingStartTime) / 1000.0;

                File file = new File(outputFilePath);
                long fileSize = file.length();

                VideoRecorder.StopRecordingResult result = new VideoRecorder.StopRecordingResult(
                    "recording_" + recordingStartTime,
                    outputFilePath,
                    fileSize,
                    duration,
                    1920, // Default width - should get from actual recording
                    1080, // Default height - should get from actual recording
                    recordingStartTime,
                    endTime,
                    null, // No thumbnail for now
                    "video/mp4"
                );

                Intent resultIntent = new Intent();
                resultIntent.putExtra(EXTRA_RESULT, result);
                setResult(RESULT_OK, resultIntent);
                Log.d(TAG, "✅ 录制结果已设置，准备关闭Activity...");
                finish();
            } else {
                Log.w(TAG, "⚠️ 录制状态异常，但仍然回调取消状态到前端");
                Log.w(TAG, "mediaRecorder: " + (mediaRecorder != null ? "not null" : "null"));
                Log.w(TAG, "isRecording: " + isRecording);

                // 即使状态异常，也要确保有回调
                setResult(RESULT_CANCELED);
                finish();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error stopping recording", e);
            finishWithError("RECORDING_FAILED", "Failed to stop recording: " + e.getMessage());
        }
    }

    private CamcorderProfile getQualityProfile() {
        String quality = options.quality != null ? options.quality : "high";
        int cameraId = currentCameraId;

        switch (quality.toLowerCase()) {
            case "low":
                if (CamcorderProfile.hasProfile(cameraId, CamcorderProfile.QUALITY_480P)) {
                    return CamcorderProfile.get(cameraId, CamcorderProfile.QUALITY_480P);
                }
                return CamcorderProfile.get(cameraId, CamcorderProfile.QUALITY_LOW);

            case "medium":
                if (CamcorderProfile.hasProfile(cameraId, CamcorderProfile.QUALITY_720P)) {
                    return CamcorderProfile.get(cameraId, CamcorderProfile.QUALITY_720P);
                }
                return CamcorderProfile.get(cameraId, CamcorderProfile.QUALITY_480P);

            case "highest":
                // 尝试最高质量：4K -> 1080P -> 720P
                if (CamcorderProfile.hasProfile(cameraId, CamcorderProfile.QUALITY_2160P)) {
                    CamcorderProfile profile = CamcorderProfile.get(cameraId, CamcorderProfile.QUALITY_2160P);
                    // 提高比特率以获得更好质量
                    profile.videoBitRate = Math.max(profile.videoBitRate, 20000000); // 20Mbps
                    return profile;
                }
                if (CamcorderProfile.hasProfile(cameraId, CamcorderProfile.QUALITY_1080P)) {
                    CamcorderProfile profile = CamcorderProfile.get(cameraId, CamcorderProfile.QUALITY_1080P);
                    // 提高比特率以获得更好质量
                    profile.videoBitRate = Math.max(profile.videoBitRate, 12000000); // 12Mbps
                    return profile;
                }
                return CamcorderProfile.get(cameraId, CamcorderProfile.QUALITY_720P);

            case "high":
            default:
                // 默认使用 1080P，如果不支持则降级到 720P
                if (CamcorderProfile.hasProfile(cameraId, CamcorderProfile.QUALITY_1080P)) {
                    CamcorderProfile profile = CamcorderProfile.get(cameraId, CamcorderProfile.QUALITY_1080P);
                    // 适中的比特率
                    profile.videoBitRate = Math.max(profile.videoBitRate, 8000000); // 8Mbps
                    return profile;
                }
                return CamcorderProfile.get(cameraId, CamcorderProfile.QUALITY_720P);
        }
    }

    private void startBlinkingAnimation() {
        blinkHandler = new Handler(Looper.getMainLooper());
        blinkRunnable = new Runnable() {
            boolean visible = true;
            @Override
            public void run() {
                if (isRecording) {
                    recordingLabel.setVisibility(visible ? View.VISIBLE : View.INVISIBLE);
                    visible = !visible;
                    blinkHandler.postDelayed(this, 500);
                }
            }
        };
        blinkHandler.post(blinkRunnable);
    }

    private void stopBlinkingAnimation() {
        if (blinkHandler != null && blinkRunnable != null) {
            blinkHandler.removeCallbacks(blinkRunnable);
            recordingLabel.setVisibility(View.GONE);
        }
    }

    private void startDurationTimer() {
        durationHandler = new Handler(Looper.getMainLooper());
        durationRunnable = new Runnable() {
            @Override
            public void run() {
                if (isRecording) {
                    updateDurationLabel();
                    durationHandler.postDelayed(this, 1000); // 每秒更新一次
                }
            }
        };
        durationHandler.post(durationRunnable);
    }

    private void stopDurationTimer() {
        if (durationHandler != null && durationRunnable != null) {
            durationHandler.removeCallbacks(durationRunnable);
            durationLabel.setVisibility(View.GONE);
        }
    }

    private void updateDurationLabel() {
        if (recordingStartTime > 0) {
            long elapsed = (System.currentTimeMillis() - recordingStartTime) / 1000;
            int minutes = (int) (elapsed / 60);
            int seconds = (int) (elapsed % 60);

            String timeText = String.format("%02d:%02d", minutes, seconds);
            durationLabel.setText(timeText);
        }
    }
    
    private void cancelRecording() {
        Log.d(TAG, "❌ 用户点击取消录制，回调到前端...");

        // 如果正在录制，先停止录制
        if (isRecording && mediaRecorder != null) {
            try {
                mediaRecorder.stop();
                mediaRecorder.release();
                mediaRecorder = null;
                camera.lock();

                // 删除录制的文件
                if (outputFilePath != null) {
                    File file = new File(outputFilePath);
                    if (file.exists()) {
                        file.delete();
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error stopping recording during cancel", e);
            }
        }

        setResult(RESULT_CANCELED);
        finish();
    }
    
    private void finishWithError(String code, String message) {
        Intent result = new Intent();
        result.putExtra(EXTRA_ERROR, new VideoRecorderError(code, message, null));
        setResult(RESULT_FIRST_USER, result);
        finish();
    }
    
    // SurfaceHolder.Callback methods
    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        if (camera != null) {
            try {
                camera.setPreviewDisplay(holder);
                camera.startPreview();
            } catch (IOException e) {
                Log.e(TAG, "Error setting camera preview", e);
            }
        }
    }
    
    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        // Surface 尺寸变化时，延迟调整按钮位置
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            if (surfaceView != null) {
                FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) surfaceView.getLayoutParams();
                if (params != null) {
                    adjustButtonPositions(params.height);
                }
            }
        }, 100); // 延迟 100ms 确保布局完成
    }
    
    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        if (camera != null) {
            camera.stopPreview();
        }
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (camera != null) {
            camera.release();
            camera = null;
        }
        if (mediaRecorder != null) {
            mediaRecorder.release();
            mediaRecorder = null;
        }
        if (blinkHandler != null && blinkRunnable != null) {
            blinkHandler.removeCallbacks(blinkRunnable);
        }
        if (durationHandler != null && durationRunnable != null) {
            durationHandler.removeCallbacks(durationRunnable);
        }
    }

    // 权限处理方法
    private boolean hasRequiredPermissions() {
        return checkSelfPermission(android.Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED &&
               checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestRequiredPermissions() {
        String[] permissions = {
            android.Manifest.permission.CAMERA,
            android.Manifest.permission.RECORD_AUDIO
        };
        requestPermissions(permissions, 1001);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == 1001) {
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }

            if (allGranted) {
                // 权限授予后重新创建 Activity
                recreate();
            } else {
                finishWithError("PERMISSION_DENIED", "Camera and microphone permissions are required");
            }
        }
    }

    // 获取最优预览尺寸，避免画面变形
    private Camera.Size getOptimalPreviewSize(Camera.Parameters parameters) {
        List<Camera.Size> supportedPreviewSizes = parameters.getSupportedPreviewSizes();
        List<Camera.Size> supportedVideoSizes = parameters.getSupportedVideoSizes();

        if (supportedPreviewSizes == null || supportedPreviewSizes.isEmpty()) {
            return null;
        }

        // 获取录制质量对应的尺寸
        CamcorderProfile profile = getQualityProfile();
        int targetWidth = profile.videoFrameWidth;
        int targetHeight = profile.videoFrameHeight;
        double targetRatio = (double) targetWidth / targetHeight;

        // 获取屏幕尺寸作为参考
        android.util.DisplayMetrics displayMetrics = new android.util.DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
        int screenWidth = displayMetrics.widthPixels;
        int screenHeight = displayMetrics.heightPixels;

        Camera.Size optimalSize = null;
        double minDiff = Double.MAX_VALUE;

        // 首先尝试找到与录制尺寸比例匹配的预览尺寸
        for (Camera.Size size : supportedPreviewSizes) {
            double ratio = (double) size.width / size.height;
            double ratioDiff = Math.abs(ratio - targetRatio);

            // 如果比例接近且尺寸合适
            if (ratioDiff < 0.1) {
                double sizeDiff = Math.abs(size.height - targetHeight);
                if (sizeDiff < minDiff) {
                    optimalSize = size;
                    minDiff = sizeDiff;
                }
            }
        }

        // 如果没找到合适的比例，选择最接近录制尺寸的
        if (optimalSize == null) {
            minDiff = Double.MAX_VALUE;
            for (Camera.Size size : supportedPreviewSizes) {
                double sizeDiff = Math.abs(size.width - targetWidth) + Math.abs(size.height - targetHeight);
                if (sizeDiff < minDiff) {
                    optimalSize = size;
                    minDiff = sizeDiff;
                }
            }
        }

        // 确保选择的尺寸不会太大（避免性能问题）
        if (optimalSize != null && optimalSize.width * optimalSize.height > 1920 * 1080) {
            for (Camera.Size size : supportedPreviewSizes) {
                if (size.width <= 1920 && size.height <= 1080) {
                    double ratio = (double) size.width / size.height;
                    if (Math.abs(ratio - targetRatio) < 0.1) {
                        optimalSize = size;
                        break;
                    }
                }
            }
        }

        return optimalSize != null ? optimalSize : supportedPreviewSizes.get(0);
    }

    // 调整 SurfaceView 尺寸以避免画面变形
    private void adjustSurfaceViewSize(Camera.Size previewSize) {
        if (previewSize == null || surfaceView == null) {
            return;
        }

        // 获取屏幕尺寸
        android.util.DisplayMetrics displayMetrics = new android.util.DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
        int screenWidth = displayMetrics.widthPixels;
        int screenHeight = displayMetrics.heightPixels;

        // 由于相机旋转了90度，需要交换宽高
        int previewWidth = previewSize.height;  // 旋转后的宽度
        int previewHeight = previewSize.width;  // 旋转后的高度

        // 计算预览的宽高比
        double previewRatio = (double) previewWidth / previewHeight;
        double screenRatio = (double) screenWidth / screenHeight;

        int surfaceWidth, surfaceHeight;

        if (previewRatio > screenRatio) {
            // 预览更宽，以屏幕宽度为准
            surfaceWidth = screenWidth;
            surfaceHeight = (int) (screenWidth / previewRatio);
        } else {
            // 预览更高，以屏幕高度为准
            surfaceHeight = screenHeight;
            surfaceWidth = (int) (screenHeight * previewRatio);
        }

        // 更新 SurfaceView 的布局参数
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(surfaceWidth, surfaceHeight);
        params.gravity = Gravity.CENTER;

        runOnUiThread(() -> {
            surfaceView.setLayoutParams(params);
            // 调整 SurfaceView 尺寸后，重新定位按钮
            adjustButtonPositions(surfaceHeight);
        });
    }

    // 根据 SurfaceView 的实际高度调整按钮位置
    private void adjustButtonPositions(int surfaceHeight) {
        // 获取屏幕高度
        android.util.DisplayMetrics displayMetrics = new android.util.DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
        int screenHeight = displayMetrics.heightPixels;

        // 计算 SurfaceView 底部到屏幕底部的距离
        int surfaceBottomMargin = (screenHeight - surfaceHeight) / 2;

        // 确保按钮在 SurfaceView 区域内，距离底部 80dp
        int buttonBottomMargin = Math.max(80, surfaceBottomMargin + 80);

        // 更新录制按钮位置
        if (recordButton != null) {
            FrameLayout.LayoutParams recordParams = (FrameLayout.LayoutParams) recordButton.getLayoutParams();
            recordParams.setMargins(0, 0, 0, buttonBottomMargin);
            recordButton.setLayoutParams(recordParams);
        }

        // 更新停止按钮位置
        if (stopButton != null) {
            FrameLayout.LayoutParams stopParams = (FrameLayout.LayoutParams) stopButton.getLayoutParams();
            stopParams.setMargins(0, 0, 0, buttonBottomMargin);
            stopButton.setLayoutParams(stopParams);
        }

        // 更新取消按钮位置（左下角）
        if (cancelButton != null) {
            FrameLayout.LayoutParams cancelParams = (FrameLayout.LayoutParams) cancelButton.getLayoutParams();
            cancelParams.setMargins(100, 0, 0, buttonBottomMargin);
            cancelButton.setLayoutParams(cancelParams);
        }

        // 更新切换相机按钮位置（右下角）
        if (switchCameraButton != null) {
            FrameLayout.LayoutParams switchParams = (FrameLayout.LayoutParams) switchCameraButton.getLayoutParams();
            switchParams.setMargins(0, 0, 100, buttonBottomMargin);
            switchCameraButton.setLayoutParams(switchParams);
        }
    }
}
