package com.cipace.capacitor.videorecorder;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

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
    name = "CipaceVideoRecorder",
    permissions = {
        @Permission(
            strings = { Manifest.permission.CAMERA },
            alias = VideoRecorderPlugin.CAMERA
        ),
        @Permission(
            strings = { Manifest.permission.RECORD_AUDIO },
            alias = VideoRecorderPlugin.MICROPHONE
        ),
        @Permission(
            strings = { Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE },
            alias = VideoRecorderPlugin.STORAGE
        ),
        @Permission(
            strings = { "android.permission.READ_MEDIA_VIDEO" },
            alias = VideoRecorderPlugin.MEDIA_VIDEO
        )
    }
)
public class VideoRecorderPlugin extends Plugin {
    
    static final String CAMERA = "camera";
    static final String MICROPHONE = "microphone";
    static final String STORAGE = "storage";
    static final String MEDIA_VIDEO = "mediaVideo";

    private VideoRecorder videoRecorder;
    @Override
    public void load() {
        videoRecorder = new VideoRecorder(getContext());
    }
    
    // MARK: - Media Capture Compatible Methods
    
    @PluginMethod
    public void captureVideo(PluginCall call) {
        // 简化：像 cordova-plugin-media-capture 一样，直接启动录制
        // 让 VideoRecordingActivity 自己处理权限请求
        VideoRecordingOptions options = createOptionsFromCall(call);
        Intent intent = new Intent(getContext(), VideoRecordingActivity.class);
        intent.putExtra(VideoRecordingActivity.EXTRA_OPTIONS, options);
        intent.putExtra(VideoRecordingActivity.EXTRA_IS_CAPTURE_MODE, true);
        startActivityForResult(call, intent, "handleCaptureVideoResult");
    }

    @PermissionCallback
    private void captureVideoWithPermissions(PluginCall call) {
        if (!hasFullPermissions()) {
            call.reject("PERMISSION_DENIED", "Camera, microphone and storage permissions are required for video recording");
            return;
        }

        VideoRecordingOptions options = createOptionsFromCall(call);

        Intent intent = new Intent(getContext(), VideoRecordingActivity.class);
        intent.putExtra(VideoRecordingActivity.EXTRA_OPTIONS, options);
        intent.putExtra(VideoRecordingActivity.EXTRA_IS_CAPTURE_MODE, true);

        startActivityForResult(call, intent, "handleCaptureVideoResult");
    }

    @ActivityCallback
    private void handleCaptureVideoResult(PluginCall call, androidx.activity.result.ActivityResult result) {
        if (result.getResultCode() != Activity.RESULT_OK || result.getData() == null) {
            call.reject("USER_CANCELLED", "Recording was cancelled");
            return;
        }

        Intent data = result.getData();

        VideoRecorderError error = (VideoRecorderError) data.getSerializableExtra(VideoRecordingActivity.EXTRA_ERROR);
        if (error != null) {
            call.reject(error.code, error.message, error.details);
            return;
        }

        VideoRecorder.StopRecordingResult recordingResult = (VideoRecorder.StopRecordingResult) data.getSerializableExtra(VideoRecordingActivity.EXTRA_RESULT);
        if (recordingResult != null) {
            JSObject mediaFile = new JSObject();
            mediaFile.put("name", new File(recordingResult.videoPath).getName());
            mediaFile.put("fullPath", recordingResult.videoPath);
            mediaFile.put("type", recordingResult.mimeType);
            mediaFile.put("lastModifiedDate", recordingResult.endTime);
            mediaFile.put("size", recordingResult.fileSize);

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
        if (getPermissionState(Manifest.permission.RECORD_AUDIO) != PermissionState.GRANTED) {
            requestPermissionForAlias(MICROPHONE, call, "captureAudioWithPermissions");
            return;
        }

        captureAudioWithPermissions(call);
    }
    
    @PermissionCallback
    private void captureAudioWithPermissions(PluginCall call) {
        if (getPermissionState(Manifest.permission.RECORD_AUDIO) != PermissionState.GRANTED) {
            call.reject("PERMISSION_DENIED", "Microphone permission denied");
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
        if (getPermissionState(Manifest.permission.CAMERA) != PermissionState.GRANTED) {
            requestPermissionForAlias(CAMERA, call, "openRecordingInterfaceWithPermissions");
            return;
        }

        openRecordingInterfaceWithPermissions(call);
    }
    
    @PermissionCallback
    private void openRecordingInterfaceWithPermissions(PluginCall call) {
        if (getPermissionState(Manifest.permission.CAMERA) != PermissionState.GRANTED) {
            call.reject("PERMISSION_DENIED", "Camera permission denied");
            return;
        }

        VideoRecordingOptions options = createOptionsFromCall(call);

        Intent intent = new Intent(getContext(), VideoRecordingActivity.class);
        intent.putExtra(VideoRecordingActivity.EXTRA_OPTIONS, options);

        startActivityForResult(call, intent, "handleRecordingResult");
    }

    @ActivityCallback
    private void handleRecordingResult(PluginCall call, Intent data) {
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

            call.resolve(ret);
        } else {
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
        // 简化：直接返回当前权限状态
        checkPermissions(call);
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
    
    @PluginMethod
    public void generateThumbnail(PluginCall call) {
        String videoPath = call.getString("videoPath");
        double timeAt = call.getDouble("timeAt", 1.0); // 默认在第1秒生成缩略图
        double quality = call.getDouble("quality", 0.8); // 默认压缩质量0.8
        
        if (videoPath == null) {
            call.reject("INVALID_OPTIONS", "videoPath is required");
            return;
        }
        
        // 处理路径兼容性：支持 file:// 开头的路径
        String actualVideoPath;
        if (videoPath.startsWith("file://")) {
            actualVideoPath = videoPath.substring(7); // 移除 "file://" 前缀
        } else {
            actualVideoPath = videoPath;
        }
        
        // 验证视频文件是否存在
        java.io.File videoFile = new java.io.File(actualVideoPath);
        if (!videoFile.exists()) {
            call.reject("FILE_NOT_FOUND", "Video file not found at path: " + actualVideoPath);
            return;
        }
        
        VideoRecorder.generateThumbnail(actualVideoPath, timeAt, quality, new VideoRecorder.RecordingCallback() {
            @Override
            public void onSuccess(Object result) {
                if (result instanceof VideoRecorder.ThumbnailResult) {
                    VideoRecorder.ThumbnailResult thumbnailResult = (VideoRecorder.ThumbnailResult) result;
                    JSObject ret = new JSObject();
                    ret.put("thumbnailPath", thumbnailResult.thumbnailPath);
                    ret.put("videoPath", actualVideoPath);
                    ret.put("timeAt", timeAt);
                    ret.put("quality", quality);
                    call.resolve(ret);
                }
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

    @Override
    public boolean hasRequiredPermissions() {
        return hasVideoPermissions();
    }

    private boolean hasVideoPermissions() {
        return getPermissionState(Manifest.permission.CAMERA) == PermissionState.GRANTED &&
               hasStoragePermission();
    }

    private boolean hasAudioPermissions() {
        return getPermissionState(Manifest.permission.RECORD_AUDIO) == PermissionState.GRANTED &&
               hasStoragePermission();
    }

    private boolean hasFullPermissions() {
        return getPermissionState(Manifest.permission.CAMERA) == PermissionState.GRANTED &&
               getPermissionState(Manifest.permission.RECORD_AUDIO) == PermissionState.GRANTED &&
               hasStoragePermission();
    }





    private boolean hasStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+ 使用新的媒体权限
            PermissionState videoPermission = getPermissionState("android.permission.READ_MEDIA_VIDEO");
            android.util.Log.d("VideoRecorder", "Android 13+ 视频权限状态: " + videoPermission);
            return videoPermission == PermissionState.GRANTED;
        } else {
            // Android 12 及以下使用传统存储权限
            PermissionState writePermission = getPermissionState(Manifest.permission.WRITE_EXTERNAL_STORAGE);
            PermissionState readPermission = getPermissionState(Manifest.permission.READ_EXTERNAL_STORAGE);
            android.util.Log.d("VideoRecorder", "传统存储权限 - Write: " + writePermission + ", Read: " + readPermission);
            return writePermission == PermissionState.GRANTED && readPermission == PermissionState.GRANTED;
        }
    }

    private String getStoragePermissionState() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return getPermissionState("android.permission.READ_MEDIA_VIDEO").toString().toLowerCase();
        } else {
            return getPermissionState(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PermissionState.GRANTED &&
                   getPermissionState(Manifest.permission.READ_EXTERNAL_STORAGE) == PermissionState.GRANTED
                   ? "granted" : "denied";
        }
    }


}
