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
        mainLayout.addView(surfaceView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        
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
        cancelParams.setMargins(100, 0, 0, 100);
        cancelButton.setOnClickListener(v -> cancelRecording());
        mainLayout.addView(cancelButton, cancelParams);

        // Record button - 圆形，中间，最大
        recordButton = createCircularButton("●", Color.parseColor("#F44336"));
        recordButton.setTextSize(32);
        FrameLayout.LayoutParams recordParams = new FrameLayout.LayoutParams(160, 160);
        recordParams.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        recordParams.setMargins(0, 0, 0, 80);
        recordButton.setOnClickListener(v -> startRecording());
        mainLayout.addView(recordButton, recordParams);

        // Stop button - 圆形，中间，与Record相同位置（初始隐藏）
        stopButton = createCircularButton("■", Color.parseColor("#F44336"));
        stopButton.setTextSize(28);
        FrameLayout.LayoutParams stopParams = new FrameLayout.LayoutParams(160, 160);
        stopParams.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        stopParams.setMargins(0, 0, 0, 80);
        stopButton.setOnClickListener(v -> stopRecording());
        stopButton.setVisibility(View.GONE);
        mainLayout.addView(stopButton, stopParams);

        // Switch camera button - 圆形，右侧
        switchCameraButton = createCircularButton("⟲", Color.parseColor("#99000000"));
        switchCameraButton.setTextSize(20);
        FrameLayout.LayoutParams switchParams = new FrameLayout.LayoutParams(140, 140);
        switchParams.gravity = Gravity.BOTTOM | Gravity.RIGHT;
        switchParams.setMargins(0, 0, 100, 100);
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
            
            // Set camera parameters
            List<Camera.Size> supportedSizes = parameters.getSupportedPreviewSizes();
            if (!supportedSizes.isEmpty()) {
                Camera.Size bestSize = supportedSizes.get(0);
                parameters.setPreviewSize(bestSize.width, bestSize.height);
            }
            
            camera.setParameters(parameters);
            camera.setDisplayOrientation(90);
            
            if (surfaceHolder != null) {
                camera.setPreviewDisplay(surfaceHolder);
                camera.startPreview();
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

        switch (quality.toLowerCase()) {
            case "low":
                return CamcorderProfile.get(CamcorderProfile.QUALITY_LOW);
            case "medium":
                return CamcorderProfile.get(CamcorderProfile.QUALITY_480P);
            case "highest":
                return CamcorderProfile.get(CamcorderProfile.QUALITY_1080P);
            case "high":
            default:
                return CamcorderProfile.get(CamcorderProfile.QUALITY_720P);
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
        // Handle surface changes if needed
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
}
