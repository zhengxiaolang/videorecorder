package com.cipace.capacitor.videorecorder;

import java.io.Serializable;

public class VideoRecordingOptions implements Serializable {
    public String quality = "high";
    public double maxDuration = 300.0;
    public String fileNamePrefix = "video_recording";
    public boolean saveToGallery = false;
    public String camera = "back";
    public String orientation = "portrait";
    public boolean enableAudio = true;
    
    public enum VideoQuality {
        LOW("low"),
        MEDIUM("medium"),
        HIGH("high"),
        HIGHEST("highest");
        
        private final String value;
        
        VideoQuality(String value) {
            this.value = value;
        }
        
        public String getValue() {
            return value;
        }
        
        public static VideoQuality fromString(String value) {
            for (VideoQuality quality : VideoQuality.values()) {
                if (quality.value.equals(value)) {
                    return quality;
                }
            }
            return HIGH; // default
        }
    }
    
    public enum CameraPosition {
        FRONT("front"),
        BACK("back");
        
        private final String value;
        
        CameraPosition(String value) {
            this.value = value;
        }
        
        public String getValue() {
            return value;
        }
        
        public static CameraPosition fromString(String value) {
            for (CameraPosition position : CameraPosition.values()) {
                if (position.value.equals(value)) {
                    return position;
                }
            }
            return BACK; // default
        }
    }
    
    public enum VideoOrientation {
        PORTRAIT("portrait"),
        LANDSCAPE("landscape");
        
        private final String value;
        
        VideoOrientation(String value) {
            this.value = value;
        }
        
        public String getValue() {
            return value;
        }
        
        public static VideoOrientation fromString(String value) {
            for (VideoOrientation orientation : VideoOrientation.values()) {
                if (orientation.value.equals(value)) {
                    return orientation;
                }
            }
            return PORTRAIT; // default
        }
    }
}
