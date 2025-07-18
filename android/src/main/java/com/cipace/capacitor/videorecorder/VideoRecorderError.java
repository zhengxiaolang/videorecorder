package com.cipace.capacitor.videorecorder;

import com.getcapacitor.JSObject;
import java.io.Serializable;

public class VideoRecorderError implements Serializable {
    public final String code;
    public final String message;
    public final JSObject details;
    
    public VideoRecorderError(String code, String message) {
        this(code, message, null);
    }
    
    public VideoRecorderError(String code, String message, JSObject details) {
        this.code = code;
        this.message = message;
        this.details = details;
    }
    
    // Error codes constants
    public static final String PERMISSION_DENIED = "PERMISSION_DENIED";
    public static final String RECORDING_FAILED = "RECORDING_FAILED";
    public static final String INVALID_OPTIONS = "INVALID_OPTIONS";
    public static final String ALREADY_RECORDING = "ALREADY_RECORDING";
    public static final String NOT_RECORDING = "NOT_RECORDING";
    public static final String FILE_NOT_FOUND = "FILE_NOT_FOUND";
    public static final String STORAGE_ERROR = "STORAGE_ERROR";
    public static final String CAMERA_ERROR = "CAMERA_ERROR";
    public static final String MICROPHONE_ERROR = "MICROPHONE_ERROR";
    public static final String CAPTURE_CANCELLED = "CAPTURE_CANCELLED";
}
