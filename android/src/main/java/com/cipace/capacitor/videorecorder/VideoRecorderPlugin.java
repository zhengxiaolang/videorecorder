package com.cipace.capacitor.videorecorder;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.PermissionState;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.ActivityCallback;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.annotation.Permission;
import com.getcapacitor.annotation.PermissionCallback;

@CapacitorPlugin(
    name = "VideoRecorder",
    permissions = {
        @Permission(
            strings = { Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO },
            alias = VideoRecorderPlugin.CAMERA_MICROPHONE
        ),
        @Permission(
            strings = { Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE },
            alias = VideoRecorderPlugin.STORAGE
        )
    }
)
public class VideoRecorderPlugin extends Plugin {
    
    static final String CAMERA_MICROPHONE = "camera_microphone";
    static final String STORAGE = "storage";
    
    private VideoRecorder videoRecorder;
    
    @Override
    public void load() {
        videoRecorder = new VideoRecorder(getContext());
    }
    
    // MARK: - Media Capture Compatible Methods
    
    @PluginMethod
    public void captureVideo(PluginCall call) {
        if (!hasRequiredPermissions()) {
            requestAllPermissions(call, "captureVideoWithPermissions");
            return;
        }

        captureVideoWithPermissions(call);
    }

    @PermissionCallback
    private void captureVideoWithPermissions(PluginCall call) {
        if (!hasRequiredPermissions()) {
            call.reject("PERMISSION_DENIED", "Camera, microphone, or storage permission denied");
            return;
        }

        VideoRecordingOptions options = createOptionsFromCall(call);

        // 使用 startActivityForResult 来等待录制完成，就像 openRecordingInterface 一样
        Intent intent = new Intent(getContext(), VideoRecordingActivity.class);
        intent.putExtra(VideoRecordingActivity.EXTRA_OPTIONS, options);
        intent.putExtra(VideoRecordingActivity.EXTRA_IS_CAPTURE_MODE, true); // 标记为 captureVideo 模式

        startActivityForResult(call, intent, "handleCaptureVideoResult");
    }

    @ActivityCallback
    private void handleCaptureVideoResult(PluginCall call, Intent data) {
        if (data == null) {
            call.reject("USER_CANCELLED", "Recording was cancelled");
            return;
        }

        VideoRecorderError error = (VideoRecorderError) data.getSerializableExtra(VideoRecordingActivity.EXTRA_ERROR);
        if (error != null) {
            call.reject(error.code, error.message, error.details);
            return;
        }

        VideoRecorder.StopRecordingResult result = (VideoRecorder.StopRecordingResult) data.getSerializableExtra(VideoRecordingActivity.EXTRA_RESULT);
        if (result != null) {
            // 转换为 MediaCapture 兼容的格式
            JSObject mediaFile = new JSObject();
            mediaFile.put("name", new File(result.videoPath).getName());
            mediaFile.put("fullPath", result.videoPath);
            mediaFile.put("type", result.mimeType);
            mediaFile.put("lastModifiedDate", result.endTime);
            mediaFile.put("size", result.fileSize);

            JSArray files = new JSArray();
            files.put(mediaFile);

            JSObject ret = new JSObject();
            ret.put("files", files);
            call.resolve(ret);
        } else {
            call.reject("UNKNOWN_ERROR", "Unknown error occurred during recording");
        }
    }
    
    @PluginMethod
    public void captureAudio(PluginCall call) {
        if (!hasRequiredPermissions()) {
            requestAllPermissions(call, "captureAudioWithPermissions");
            return;
        }
        
        captureAudioWithPermissions(call);
    }
    
    @PermissionCallback
    private void captureAudioWithPermissions(PluginCall call) {
        if (!hasRequiredPermissions()) {
            call.reject("PERMISSION_DENIED", "Microphone or storage permission denied");
            return;
        }
        
        VideoRecordingOptions options = createOptionsFromCall(call);
        
        videoRecorder.captureAudio(options, new VideoRecorder.RecordingCallback() {
            @Override
            public void onSuccess(Object result) {
                if (result instanceof VideoRecorder.CaptureAudioResult) {
                    VideoRecorder.CaptureAudioResult captureResult = (VideoRecorder.CaptureAudioResult) result;
                    JSObject ret = new JSObject();
                    ret.put("files", captureResult.files);
                    call.resolve(ret);
                }
            }
            
            @Override
            public void onError(VideoRecorderError error) {
                call.reject(error.code, error.message, error.details);
            }
        });
    }
    
    @PluginMethod
    public void getSupportedVideoModes(PluginCall call) {
        JSObject[] supportedModes = {
            createModeObject("video/mp4", 640, 480),
            createModeObject("video/mp4", 1280, 720),
            createModeObject("video/mp4", 1920, 1080),
            createModeObject("video/mp4", 3840, 2160)
        };
        call.resolve(new JSObject().put("modes", supportedModes));
    }
    
    @PluginMethod
    public void getSupportedAudioModes(PluginCall call) {
        JSObject[] supportedModes = {
            createModeObject("audio/mp4", 0, 0),
            createModeObject("audio/wav", 0, 0),
            createModeObject("audio/aac", 0, 0)
        };
        call.resolve(new JSObject().put("modes", supportedModes));
    }
    
    // MARK: - Advanced Recording Methods
    
    @PluginMethod
    public void openRecordingInterface(PluginCall call) {
        if (!hasRequiredPermissions()) {
            requestAllPermissions(call, "openRecordingInterfaceWithPermissions");
            return;
        }

        openRecordingInterfaceWithPermissions(call);
    }
    
    @PermissionCallback
    private void openRecordingInterfaceWithPermissions(PluginCall call) {
        Log.d("VideoRecorderPlugin", "🎬 启动录制界面...");

        if (!hasRequiredPermissions()) {
            Log.e("VideoRecorderPlugin", "🚫 权限不足，无法启动录制");
            call.reject("PERMISSION_DENIED", "Camera, microphone, or storage permission denied");
            return;
        }

        VideoRecordingOptions options = createOptionsFromCall(call);

        // Launch VideoRecordingActivity，等待录制完成
        Intent intent = new Intent(getContext(), VideoRecordingActivity.class);
        intent.putExtra(VideoRecordingActivity.EXTRA_OPTIONS, options);

        Log.d("VideoRecorderPlugin", "📱 启动 VideoRecordingActivity，等待用户操作...");
        startActivityForResult(call, intent, "handleRecordingResult");
    }

    @ActivityCallback
    private void handleRecordingResult(PluginCall call, Intent data) {
        Log.d("VideoRecorderPlugin", "🔄 handleRecordingResult 被调用");

        if (data == null) {
            Log.d("VideoRecorderPlugin", "❌ 用户取消录制，回调到前端");
            call.reject("USER_CANCELLED", "Recording was cancelled");
            return;
        }

        VideoRecorderError error = (VideoRecorderError) data.getSerializableExtra(VideoRecordingActivity.EXTRA_ERROR);
        if (error != null) {
            Log.e("VideoRecorderPlugin", "💥 录制错误: " + error.code + " - " + error.message);
            call.reject(error.code, error.message, error.details);
            return;
        }

        VideoRecorder.StopRecordingResult result = (VideoRecorder.StopRecordingResult) data.getSerializableExtra(VideoRecordingActivity.EXTRA_RESULT);
        if (result != null) {
            Log.d("VideoRecorderPlugin", "✅ 录制成功，准备回调结果到前端");
            Log.d("VideoRecorderPlugin", "📁 视频路径: " + result.videoPath);
            Log.d("VideoRecorderPlugin", "⏱️ 录制时长: " + result.duration + "秒");
            Log.d("VideoRecorderPlugin", "📊 文件大小: " + result.fileSize + "字节");

            // 返回录制完成的最终结果
            JSObject ret = new JSObject();
            ret.put("recordingId", result.recordingId);
            ret.put("videoPath", result.videoPath);
            ret.put("fileSize", result.fileSize);
            ret.put("duration", result.duration);
            ret.put("width", result.width);
            ret.put("height", result.height);
            ret.put("startTime", result.startTime);
            ret.put("endTime", result.endTime);
            ret.put("thumbnailPath", result.thumbnailPath);
            ret.put("mimeType", result.mimeType);

            Log.d("VideoRecorderPlugin", "🚀 调用 call.resolve() 回调到前端");
            call.resolve(ret);
        } else {
            Log.e("VideoRecorderPlugin", "❓ 未知错误：没有结果数据");
            call.reject("UNKNOWN_ERROR", "Unknown error occurred during recording");
        }
    }


    
    @PluginMethod
    public void stopRecording(PluginCall call) {
        videoRecorder.stopRecording(new VideoRecorder.RecordingCallback() {
            @Override
            public void onSuccess(Object result) {
                if (result instanceof VideoRecorder.StopRecordingResult) {
                    VideoRecorder.StopRecordingResult stopResult = (VideoRecorder.StopRecordingResult) result;
                    JSObject ret = new JSObject();
                    ret.put("recordingId", stopResult.recordingId);
                    ret.put("videoPath", stopResult.videoPath);
                    ret.put("fileSize", stopResult.fileSize);
                    ret.put("duration", stopResult.duration);
                    ret.put("width", stopResult.width);
                    ret.put("height", stopResult.height);
                    ret.put("startTime", stopResult.startTime);
                    ret.put("endTime", stopResult.endTime);
                    ret.put("thumbnailPath", stopResult.thumbnailPath);
                    ret.put("mimeType", stopResult.mimeType);
                    call.resolve(ret);
                }
            }
            
            @Override
            public void onError(VideoRecorderError error) {
                call.reject(error.code, error.message, error.details);
            }
        });
    }
    
    @PluginMethod
    public void pauseRecording(PluginCall call) {
        videoRecorder.pauseRecording(new VideoRecorder.RecordingCallback() {
            @Override
            public void onSuccess(Object result) {
                call.resolve();
            }
            
            @Override
            public void onError(VideoRecorderError error) {
                call.reject(error.code, error.message, error.details);
            }
        });
    }
    
    @PluginMethod
    public void resumeRecording(PluginCall call) {
        videoRecorder.resumeRecording(new VideoRecorder.RecordingCallback() {
            @Override
            public void onSuccess(Object result) {
                call.resolve();
            }
            
            @Override
            public void onError(VideoRecorderError error) {
                call.reject(error.code, error.message, error.details);
            }
        });
    }
    
    @PluginMethod
    public void checkPermissions(PluginCall call) {
        JSObject ret = new JSObject();
        ret.put("camera", getPermissionState(Manifest.permission.CAMERA));
        ret.put("microphone", getPermissionState(Manifest.permission.RECORD_AUDIO));
        ret.put("storage", getStoragePermissionState());
        call.resolve(ret);
    }
    
    @PluginMethod
    public void requestPermissions(PluginCall call) {
        requestAllPermissions(call, "checkPermissions");
    }
    
    @PluginMethod
    public void getRecordingStatus(PluginCall call) {
        VideoRecorder.RecordingStatus status = videoRecorder.getRecordingStatus();
        JSObject ret = new JSObject();
        ret.put("isRecording", status.isRecording);
        ret.put("isPaused", status.isPaused);
        ret.put("currentDuration", status.currentDuration);
        ret.put("recordingId", status.recordingId);
        call.resolve(ret);
    }
    
    @PluginMethod
    public void deleteRecording(PluginCall call) {
        String videoPath = call.getString("videoPath");
        boolean deleteThumbnail = call.getBoolean("deleteThumbnail", true);
        
        if (videoPath == null) {
            call.reject("INVALID_OPTIONS", "videoPath is required");
            return;
        }
        
        VideoRecorder.deleteRecording(videoPath, deleteThumbnail, new VideoRecorder.RecordingCallback() {
            @Override
            public void onSuccess(Object result) {
                call.resolve();
            }
            
            @Override
            public void onError(VideoRecorderError error) {
                call.reject(error.code, error.message, error.details);
            }
        });
    }

    // MARK: - Helper Methods

    private VideoRecordingOptions createOptionsFromCall(PluginCall call) {
        VideoRecordingOptions options = new VideoRecordingOptions();
        options.quality = call.getString("quality", "high");
        options.maxDuration = call.getDouble("maxDuration", 300.0);
        options.fileNamePrefix = call.getString("fileNamePrefix", "video_recording");
        options.saveToGallery = call.getBoolean("saveToGallery", false);
        options.camera = call.getString("camera", "back");
        options.orientation = call.getString("orientation", "portrait");
        options.enableAudio = call.getBoolean("enableAudio", true);

        // Media Capture compatibility
        Double duration = call.getDouble("duration");
        if (duration != null) {
            options.maxDuration = duration;
        }

        Integer qualityNumber = call.getInt("quality");
        if (qualityNumber != null) {
            options.quality = mapQualityFromNumber(qualityNumber);
        }

        return options;
    }

    private String mapQualityFromNumber(int quality) {
        if (quality <= 25) return "low";
        if (quality <= 50) return "medium";
        if (quality <= 75) return "high";
        return "highest";
    }

    private JSObject createModeObject(String type, int width, int height) {
        JSObject mode = new JSObject();
        mode.put("type", type);
        mode.put("width", width);
        mode.put("height", height);
        return mode;
    }

    private boolean hasRequiredPermissions() {
        return getPermissionState(Manifest.permission.CAMERA) == PermissionState.GRANTED &&
               getPermissionState(Manifest.permission.RECORD_AUDIO) == PermissionState.GRANTED &&
               hasStoragePermission();
    }

    private boolean hasStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return getPermissionState(Manifest.permission.READ_MEDIA_VIDEO) == PermissionState.GRANTED;
        } else {
            return getPermissionState(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PermissionState.GRANTED;
        }
    }

    private String getStoragePermissionState() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return getPermissionState(Manifest.permission.READ_MEDIA_VIDEO).toString().toLowerCase();
        } else {
            return getPermissionState(Manifest.permission.WRITE_EXTERNAL_STORAGE).toString().toLowerCase();
        }
    }
}
