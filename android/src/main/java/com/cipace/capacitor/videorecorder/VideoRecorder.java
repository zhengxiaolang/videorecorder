package com.cipace.capacitor.videorecorder;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.media.MediaRecorder;
import android.os.Environment;
import com.getcapacitor.JSObject;
import java.io.File;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class VideoRecorder {
    
    private Context context;
    private MediaRecorder mediaRecorder;
    private String recordingId;
    private long startTime;
    private boolean isRecording = false;
    private boolean isPaused = false;
    private File outputFile;
    private VideoRecordingOptions currentOptions;
    
    public VideoRecorder(Context context) {
        this.context = context;
    }
    
    // Data classes for results
    public static class StartRecordingResult {
        public final String recordingId;
        public final long startTime;
        public final String tempFilePath;
        
        public StartRecordingResult(String recordingId, long startTime, String tempFilePath) {
            this.recordingId = recordingId;
            this.startTime = startTime;
            this.tempFilePath = tempFilePath;
        }
    }
    
    public static class StopRecordingResult implements Serializable {
        public final String recordingId;
        public final String videoPath;
        public final long fileSize;
        public final double duration;
        public final int width;
        public final int height;
        public final long startTime;
        public final long endTime;
        public final String thumbnailPath;
        public final String mimeType;
        
        public StopRecordingResult(String recordingId, String videoPath, long fileSize, 
                                 double duration, int width, int height, long startTime, 
                                 long endTime, String thumbnailPath, String mimeType) {
            this.recordingId = recordingId;
            this.videoPath = videoPath;
            this.fileSize = fileSize;
            this.duration = duration;
            this.width = width;
            this.height = height;
            this.startTime = startTime;
            this.endTime = endTime;
            this.thumbnailPath = thumbnailPath;
            this.mimeType = mimeType;
        }
    }
    
    public static class RecordingStatus {
        public final boolean isRecording;
        public final boolean isPaused;
        public final double currentDuration;
        public final String recordingId;
        
        public RecordingStatus(boolean isRecording, boolean isPaused, double currentDuration, String recordingId) {
            this.isRecording = isRecording;
            this.isPaused = isPaused;
            this.currentDuration = currentDuration;
            this.recordingId = recordingId;
        }
    }
    
    // Media Capture compatible results
    public static class CaptureVideoResult {
        public final List<JSObject> files;
        
        public CaptureVideoResult(List<JSObject> files) {
            this.files = files;
        }
    }
    
    public static class CaptureAudioResult {
        public final List<JSObject> files;
        
        public CaptureAudioResult(List<JSObject> files) {
            this.files = files;
        }
    }
    
    public static class ThumbnailResult {
        public final String thumbnailPath;
        
        public ThumbnailResult(String thumbnailPath) {
            this.thumbnailPath = thumbnailPath;
        }
    }
    
    public interface RecordingCallback {
        void onSuccess(Object result);
        void onError(VideoRecorderError error);
    }
    
    // Media Capture compatible methods
    // Note: captureVideo is now handled directly by VideoRecorderPlugin using startActivityForResult
    // This ensures proper callback when user stops recording
    
    public void captureAudio(VideoRecordingOptions options, RecordingCallback callback) {
        // Simplified audio capture implementation
        callback.onSuccess(new CaptureAudioResult(new ArrayList<>()));
    }
    
    // Advanced recording methods
    public void startRecording(VideoRecordingOptions options, RecordingCallback callback) {
        if (isRecording) {
            callback.onError(new VideoRecorderError(VideoRecorderError.ALREADY_RECORDING, "Recording is already in progress"));
            return;
        }

        try {
            this.currentOptions = options;
            this.recordingId = generateRecordingId();
            this.startTime = System.currentTimeMillis();

            // Launch VideoRecordingActivity
            Intent intent = new Intent(context, VideoRecordingActivity.class);
            intent.putExtra(VideoRecordingActivity.EXTRA_OPTIONS, options);

            if (context instanceof Activity) {
                ((Activity) context).startActivityForResult(intent, 1001);
            } else {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intent);
            }

            // Return immediately with recording started result
            StartRecordingResult result = new StartRecordingResult(
                recordingId,
                startTime,
                null // tempFilePath will be set when recording actually starts
            );

            callback.onSuccess(result);

        } catch (Exception e) {
            callback.onError(new VideoRecorderError(VideoRecorderError.RECORDING_FAILED, "Failed to start recording: " + e.getMessage()));
        }
    }
    
    public void stopRecording(RecordingCallback callback) {
        if (!isRecording) {
            callback.onError(new VideoRecorderError(VideoRecorderError.NOT_RECORDING, "No active recording to stop"));
            return;
        }
        
        try {
            mediaRecorder.stop();
            mediaRecorder.release();
            mediaRecorder = null;
            
            long endTime = System.currentTimeMillis();
            double duration = (endTime - startTime) / 1000.0;
            
            StopRecordingResult result = new StopRecordingResult(
                recordingId,
                outputFile.getAbsolutePath(),
                outputFile.length(),
                duration,
                1920, // Default width
                1080, // Default height
                startTime,
                endTime,
                null, // No thumbnail for now
                "video/mp4"
            );
            
            isRecording = false;
            isPaused = false;
            
            callback.onSuccess(result);
            
        } catch (Exception e) {
            callback.onError(new VideoRecorderError(VideoRecorderError.RECORDING_FAILED, "Failed to stop recording: " + e.getMessage()));
        }
    }
    
    public void pauseRecording(RecordingCallback callback) {
        if (!isRecording) {
            callback.onError(new VideoRecorderError(VideoRecorderError.NOT_RECORDING, "No active recording to pause"));
            return;
        }
        
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                mediaRecorder.pause();
                isPaused = true;
                callback.onSuccess(null);
            } else {
                callback.onError(new VideoRecorderError("NOT_SUPPORTED", "Pause/Resume is not supported on Android API < 24"));
            }
        } catch (Exception e) {
            callback.onError(new VideoRecorderError(VideoRecorderError.RECORDING_FAILED, "Failed to pause recording: " + e.getMessage()));
        }
    }
    
    public void resumeRecording(RecordingCallback callback) {
        if (!isRecording || !isPaused) {
            callback.onError(new VideoRecorderError(VideoRecorderError.NOT_RECORDING, "No paused recording to resume"));
            return;
        }
        
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                mediaRecorder.resume();
                isPaused = false;
                callback.onSuccess(null);
            } else {
                callback.onError(new VideoRecorderError("NOT_SUPPORTED", "Pause/Resume is not supported on Android API < 24"));
            }
        } catch (Exception e) {
            callback.onError(new VideoRecorderError(VideoRecorderError.RECORDING_FAILED, "Failed to resume recording: " + e.getMessage()));
        }
    }
    
    public RecordingStatus getRecordingStatus() {
        double currentDuration = isRecording ? (System.currentTimeMillis() - startTime) / 1000.0 : 0;
        return new RecordingStatus(isRecording, isPaused, currentDuration, recordingId);
    }
    
    public static void deleteRecording(String videoPath, boolean deleteThumbnail, RecordingCallback callback) {
        try {
            File videoFile = new File(videoPath);
            if (videoFile.exists()) {
                if (!videoFile.delete()) {
                    callback.onError(new VideoRecorderError(VideoRecorderError.STORAGE_ERROR, "Failed to delete video file"));
                    return;
                }
            }
            
            if (deleteThumbnail) {
                String thumbnailPath = videoPath.replace(".mp4", "_thumbnail.jpg");
                File thumbnailFile = new File(thumbnailPath);
                if (thumbnailFile.exists()) {
                    thumbnailFile.delete();
                }
            }
            
            callback.onSuccess(null);
        } catch (Exception e) {
            callback.onError(new VideoRecorderError(VideoRecorderError.STORAGE_ERROR, "Failed to delete recording: " + e.getMessage()));
        }
    }
    
    public static void generateThumbnail(String videoPath, double timeAt, double quality, RecordingCallback callback) {
        try {
            // 使用MediaMetadataRetriever生成缩略图
            android.media.MediaMetadataRetriever retriever = new android.media.MediaMetadataRetriever();
            retriever.setDataSource(videoPath);
            
            // 获取视频时长
            String durationStr = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION);
            long duration = Long.parseLong(durationStr);
            
            // 确保时间点不超过视频时长
            long actualTimeAt = Math.min(Math.max((long)(timeAt * 1000), 0), duration - 100); // 转换为毫秒
            
            // 生成缩略图
            android.graphics.Bitmap bitmap = retriever.getFrameAtTime(actualTimeAt * 1000, android.media.MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
            retriever.release();
            
            if (bitmap == null) {
                callback.onError(new VideoRecorderError("THUMBNAIL_GENERATION_FAILED", "Failed to extract frame from video"));
                return;
            }
            
            // 生成缩略图文件路径
            File videoFile = new File(videoPath);
            String fileName = videoFile.getName().replace(".mp4", "_thumbnail_" + (int)timeAt + "s.jpg");
            File thumbnailFile = new File(videoFile.getParent(), fileName);
            
            // 保存缩略图
            java.io.FileOutputStream out = new java.io.FileOutputStream(thumbnailFile);
            bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, (int)(quality * 100), out);
            out.flush();
            out.close();
            bitmap.recycle();
            
            callback.onSuccess(new ThumbnailResult(thumbnailFile.getAbsolutePath()));
            
        } catch (Exception e) {
            callback.onError(new VideoRecorderError("THUMBNAIL_GENERATION_FAILED", "Failed to generate thumbnail: " + e.getMessage()));
        }
    }
    
    // Private helper methods
    private String generateRecordingId() {
        return "recording_" + System.currentTimeMillis() + "_" + UUID.randomUUID().toString().substring(0, 8);
    }
    
    private File createOutputFile(String prefix) {
        File mediaStorageDir = new File(context.getExternalFilesDir(Environment.DIRECTORY_MOVIES), "VideoRecorder");
        
        if (!mediaStorageDir.exists()) {
            if (!mediaStorageDir.mkdirs()) {
                throw new RuntimeException("Failed to create directory");
            }
        }
        
        String fileName = prefix + "_" + System.currentTimeMillis() + ".mp4";
        return new File(mediaStorageDir, fileName);
    }
    
    private void setupMediaRecorder(VideoRecordingOptions options) throws Exception {
        mediaRecorder = new MediaRecorder();
        
        // Simplified setup
        if (options.enableAudio) {
            mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        }
        mediaRecorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);
        mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        mediaRecorder.setOutputFile(outputFile.getAbsolutePath());
        mediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
        
        if (options.enableAudio) {
            mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
        }
        
        // Set video size based on quality
        setVideoQuality(options.quality);
        
        if (options.maxDuration > 0) {
            mediaRecorder.setMaxDuration((int) (options.maxDuration * 1000));
        }
        
        mediaRecorder.prepare();
    }
    
    private void setVideoQuality(String quality) {
        switch (quality.toLowerCase()) {
            case "low":
                mediaRecorder.setVideoSize(640, 480);
                mediaRecorder.setVideoEncodingBitRate(1000000);
                break;
            case "medium":
                mediaRecorder.setVideoSize(1280, 720);
                mediaRecorder.setVideoEncodingBitRate(5000000);
                break;
            case "high":
                mediaRecorder.setVideoSize(1920, 1080);
                mediaRecorder.setVideoEncodingBitRate(10000000);
                break;
            case "highest":
                mediaRecorder.setVideoSize(3840, 2160);
                mediaRecorder.setVideoEncodingBitRate(20000000);
                break;
            default:
                mediaRecorder.setVideoSize(1920, 1080);
                mediaRecorder.setVideoEncodingBitRate(10000000);
        }
        
        mediaRecorder.setVideoFrameRate(30);
    }
}
