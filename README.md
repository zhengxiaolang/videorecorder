# @cipace/capacitor-video-recorder

🎥 **A FREE Capacitor 7+ alternative to @ionic-enterprise/media-capture**

A comprehensive Capacitor plugin for video and audio recording on iOS, Android, and Web platforms. This plugin provides all the essential features of @ionic-enterprise/media-capture without the enterprise licensing requirements.

> ✨ **Fully compatible with Capacitor 7.0 and above** - Supports all Capacitor 7.x, 8.x, 9.x versions with dynamic version compatibility for future releases.

## 🚀 Features

### Media Capture Compatibility
- ✅ **captureVideo()** - Compatible with @ionic-enterprise/media-capture API
- ✅ **captureAudio()** - Compatible with @ionic-enterprise/media-capture API  
- ✅ **getSupportedVideoModes()** - Get supported video formats and resolutions
- ✅ **getSupportedAudioModes()** - Get supported audio formats
- ✅ **MediaFile interface** - Full compatibility with existing media-capture code

### Advanced Features (Beyond media-capture)
- ✅ **Real-time recording control** - Start, stop, pause, resume
- ✅ **Multiple quality levels** - Low, Medium, High, Highest (4K)
- ✅ **Camera selection** - Front and back camera support
- ✅ **Permission management** - Built-in permission handling
- ✅ **Gallery integration** - Optional save to device gallery
- ✅ **Thumbnail generation** - Automatic video thumbnail creation
- ✅ **Recording status** - Real-time status and duration tracking
- ✅ **File management** - Delete recordings with cleanup

### Platform Support
- ✅ **iOS 14.0+** - Using AVFoundation (Capacitor 7 compatible)
- ✅ **Android API 23+** - Using Camera2 API and MediaRecorder (Capacitor 7 compatible)
- ✅ **Web** - Using MediaRecorder API (for development/testing)
- ✅ **Capacitor 7.0+** - Full support for the latest Capacitor version

## 📦 Installation

```bash
npm install @cipace/capacitor-video-recorder
npx cap sync
```

### iOS Setup (iOS 14.0+)

Add permissions to `ios/App/App/Info.plist`:

```xml
<key>NSCameraUsageDescription</key>
<string>This app needs camera access to record videos</string>
<key>NSMicrophoneUsageDescription</key>
<string>This app needs microphone access to record audio</string>
<key>NSPhotoLibraryAddUsageDescription</key>
<string>This app needs photo library access to save videos</string>
```

### Android Setup (API 23+)

Permissions are automatically added to your manifest:

```xml
<uses-permission android:name="android.permission.CAMERA" />
<uses-permission android:name="android.permission.RECORD_AUDIO" />
<uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" />
<uses-permission android:name="android.permission.READ_MEDIA_VIDEO" />
```

## 🔄 Migration from @ionic-enterprise/media-capture

This plugin is designed as a **drop-in replacement**. Simply change your import:

```typescript
// Before (Enterprise)
import { MediaCapture, MediaFile, CaptureVideoOptions } from '@ionic-enterprise/media-capture/ngx';

// After (Free)
import { MediaCapture, MediaFile, CaptureVideoOptions } from '@cipace/capacitor-video-recorder';
// or
import { VideoRecorder as MediaCapture } from '@cipace/capacitor-video-recorder';
```

Your existing code will work without changes!

## 📖 Usage Examples

### Basic Video Capture (media-capture compatible)

```typescript
import { MediaCapture, CaptureVideoOptions } from '@cipace/capacitor-video-recorder';

const mediaCapture = new MediaCapture();

// Capture video (same API as @ionic-enterprise/media-capture)
const options: CaptureVideoOptions = {
  limit: 1,
  duration: 30, // 30 seconds max
  quality: 50   // 0-100 quality scale
};

try {
  const result = await mediaCapture.captureVideo(options);
  const videoFile = result.files[0];
  
  console.log('Video captured:', {
    name: videoFile.name,
    path: videoFile.fullPath,
    size: videoFile.size,
    type: videoFile.type
  });
  
  // Get additional format data
  const formatData = await videoFile.getFormatData();
  console.log('Format info:', formatData);
  
} catch (error) {
  console.error('Capture failed:', error);
}
```

### Basic Audio Capture

```typescript
import { MediaCapture, CaptureAudioOptions } from '@cipace/capacitor-video-recorder';

const mediaCapture = new MediaCapture();

const options: CaptureAudioOptions = {
  limit: 1,
  duration: 60 // 60 seconds max
};

try {
  const result = await mediaCapture.captureAudio(options);
  const audioFile = result.files[0];
  console.log('Audio captured:', audioFile.fullPath);
} catch (error) {
  console.error('Audio capture failed:', error);
}
```

### Advanced Video Recording

```typescript
import { VideoRecorder, StartRecordingOptions } from '@cipace/capacitor-video-recorder';

// Advanced recording with more control
const options: StartRecordingOptions = {
  quality: 'high',
  maxDuration: 300,
  camera: 'back',
  enableAudio: true,
  saveToGallery: true,
  fileNamePrefix: 'my_video'
};

try {
  // Start recording
  const startResult = await VideoRecorder.startRecording(options);
  console.log('Recording started:', startResult.recordingId);
  
  // Monitor status
  const status = await VideoRecorder.getRecordingStatus();
  console.log('Recording status:', status);
  
  // Stop recording
  const stopResult = await VideoRecorder.stopRecording();
  console.log('Video saved:', stopResult.videoPath);
  
} catch (error) {
  console.error('Recording failed:', error);
}
```

### Get Supported Modes

```typescript
import { VideoRecorder } from '@cipace/capacitor-video-recorder';

// Get supported video modes
const videoModes = await VideoRecorder.getSupportedVideoModes();
console.log('Supported video modes:', videoModes);

// Get supported audio modes  
const audioModes = await VideoRecorder.getSupportedAudioModes();
console.log('Supported audio modes:', audioModes);
```

## 🔧 API Reference

### Media Capture Compatible Methods

| Method | Description | Compatible |
|--------|-------------|------------|
| `captureVideo(options?)` | Capture video files | ✅ 100% |
| `captureAudio(options?)` | Capture audio files | ✅ 100% |
| `getSupportedVideoModes()` | Get video capabilities | ✅ 100% |
| `getSupportedAudioModes()` | Get audio capabilities | ✅ 100% |

### Advanced Methods (Bonus Features)

| Method | Description |
|--------|-------------|
| `startRecording(options)` | Start advanced recording |
| `stopRecording()` | Stop current recording |
| `pauseRecording()` | Pause recording (Android 24+) |
| `resumeRecording()` | Resume recording (Android 24+) |
| `getRecordingStatus()` | Get real-time status |
| `checkPermissions()` | Check current permissions |
| `requestPermissions()` | Request required permissions |
| `deleteRecording(options)` | Delete recorded files |

## 🆚 Comparison with @ionic-enterprise/media-capture

| Feature | @ionic-enterprise/media-capture | @cipace/capacitor-video-recorder |
|---------|--------------------------------|----------------------------------|
| **Price** | 💰 Enterprise License Required | 🆓 **FREE** |
| **Basic Capture** | ✅ | ✅ |
| **Audio Recording** | ✅ | ✅ |
| **Supported Modes** | ✅ | ✅ |
| **Real-time Control** | ❌ | ✅ **Bonus** |
| **Pause/Resume** | ❌ | ✅ **Bonus** |
| **Permission Management** | ❌ | ✅ **Bonus** |
| **Thumbnail Generation** | ❌ | ✅ **Bonus** |
| **Gallery Integration** | ❌ | ✅ **Bonus** |
| **Recording Status** | ❌ | ✅ **Bonus** |
| **Quality Control** | Basic | ✅ **Advanced** |
| **File Management** | ❌ | ✅ **Bonus** |

## 🛠️ Development

```bash
# Install dependencies
npm install

# Build the plugin
npm run build

# Run example
cd example && npm start
```

## 📄 License

MIT License - Use freely in commercial and open source projects.

## 🤝 Contributing

Contributions welcome! This is a community-driven alternative to expensive enterprise solutions.

## 💡 Why This Plugin?

@ionic-enterprise/media-capture is a great plugin, but it requires an expensive enterprise license. This free alternative provides:

- **Same API compatibility** - Drop-in replacement
- **More features** - Advanced recording controls
- **No licensing costs** - Use in any project
- **Community driven** - Open source development
- **Regular updates** - Maintained by the community

Perfect for startups, indie developers, and anyone who needs media capture without enterprise costs!

---

**Made with ❤️ by the CIPAce Team**
