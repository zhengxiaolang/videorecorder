# Cap Video Recorder

🎥 **A FREE Capacitor 7 plugin for video recording**

A simple and powerful Capacitor plugin for video and audio recording on iOS and Android platforms.

## ✨ Features

- 📹 Video recording with quality control
- 🎤 Audio recording support
- 📱 iOS 14.0+ and Android API 23+ support
- 🔄 Real-time recording control (start, stop, pause, resume)
- 📷 Front/back camera selection
- 💾 Save to device gallery
- 🔐 Built-in permission handling

## 📦 Installation

```bash
npm install cap-video-recorder
npx cap sync
```

### iOS Setup

Add permissions to `ios/App/App/Info.plist`:

```xml
<key>NSCameraUsageDescription</key>
<string>This app needs camera access to record videos</string>
<key>NSMicrophoneUsageDescription</key>
<string>This app needs microphone access to record audio</string>
<key>NSPhotoLibraryAddUsageDescription</key>
<string>This app needs photo library access to save videos</string>
```

### Android Setup

Permissions are automatically added to your manifest.

## 📖 Usage

### Basic Video Recording

```typescript
import { VideoRecorder } from 'cap-video-recorder';

// Start recording
const result = await VideoRecorder.startRecording({
  quality: 'high',
  maxDuration: 60,
  camera: 'back',
  enableAudio: true,
  saveToGallery: true
});

// Stop recording
const video = await VideoRecorder.stopRecording();
console.log('Video saved:', video.videoPath);
```

### Audio Recording

```typescript
import { VideoRecorder } from 'cap-video-recorder';

// Start audio recording
await VideoRecorder.startRecording({
  enableAudio: true,
  enableVideo: false,
  maxDuration: 120
});

// Stop recording
const audio = await VideoRecorder.stopRecording();
console.log('Audio saved:', audio.audioPath);
```

### Check Permissions

```typescript
import { VideoRecorder } from 'cap-video-recorder';

// Check permissions
const permissions = await VideoRecorder.checkPermissions();

// Request permissions if needed
if (permissions.camera !== 'granted') {
  await VideoRecorder.requestPermissions();
}
```

## 🔧 API Reference

### Main Methods

| Method | Description |
|--------|-------------|
| `startRecording(options)` | Start video/audio recording |
| `stopRecording()` | Stop current recording |
| `pauseRecording()` | Pause recording (Android 24+) |
| `resumeRecording()` | Resume recording (Android 24+) |
| `getRecordingStatus()` | Get current recording status |
| `checkPermissions()` | Check camera/microphone permissions |
| `requestPermissions()` | Request required permissions |

### Recording Options

```typescript
interface StartRecordingOptions {
  quality?: 'low' | 'medium' | 'high' | 'highest';
  maxDuration?: number; // seconds
  camera?: 'front' | 'back';
  enableAudio?: boolean;
  enableVideo?: boolean;
  saveToGallery?: boolean;
  fileNamePrefix?: string;
}
```

## 📄 License

MIT License

## 🤝 Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

## 📞 Support

If you have any questions or issues, please create an issue on GitHub.
