# Capacitor 7 Compatibility Guide

## 🎉 Full Capacitor 7 Support

This plugin has been updated to fully support **Capacitor 7.0+** with all the latest improvements and requirements.

## 📋 Requirements

### Minimum Versions
- **Capacitor**: 7.0.0+
- **iOS**: 14.0+ (updated from 13.0)
- **Android**: API 23+ (updated from API 22)
- **Node.js**: 16.0+
- **TypeScript**: 4.7+

### Dependencies
```json
{
  "peerDependencies": {
    "@capacitor/core": "^7.0.0"
  },
  "devDependencies": {
    "@capacitor/android": "^7.0.0",
    "@capacitor/core": "^7.0.0",
    "@capacitor/ios": "^7.0.0"
  }
}
```

## 🔄 Migration from Capacitor 6

If you're upgrading from Capacitor 6, follow these steps:

### 1. Update Capacitor
```bash
npm install @capacitor/core@^7.0.0
npm install @capacitor/cli@^7.0.0
npm install @capacitor/android@^7.0.0
npm install @capacitor/ios@^7.0.0
```

### 2. Update Plugin
```bash
npm install @cipace/capacitor-video-recorder@latest
npx cap sync
```

### 3. iOS Changes
- **Minimum iOS version**: Now requires iOS 14.0+
- **Xcode**: Requires Xcode 14.0+
- **Swift**: Uses Swift 5.7+

### 4. Android Changes
- **Minimum API level**: Now requires API 23+
- **Compile SDK**: Updated to API 35
- **Target SDK**: Updated to API 35
- **Gradle**: Compatible with latest Android Gradle Plugin

## 🆕 Capacitor 7 Improvements

### Enhanced Performance
- Faster plugin initialization
- Improved memory management
- Better error handling

### Modern APIs
- Updated to use latest iOS and Android APIs
- Better permission handling
- Improved file system access

### Developer Experience
- Better TypeScript support
- Enhanced debugging capabilities
- Improved build times

## 🔧 Configuration Updates

### iOS Configuration (ios/App/App/Info.plist)
```xml
<!-- Required permissions (same as before) -->
<key>NSCameraUsageDescription</key>
<string>This app needs camera access to record videos</string>
<key>NSMicrophoneUsageDescription</key>
<string>This app needs microphone access to record audio</string>
<key>NSPhotoLibraryAddUsageDescription</key>
<string>This app needs photo library access to save videos</string>

<!-- New: Enhanced privacy descriptions -->
<key>NSCameraUsageDescription</key>
<string>This app uses the camera to record high-quality videos for your content creation needs.</string>
```

### Android Configuration
The plugin automatically handles all required permissions:

```xml
<!-- Core permissions -->
<uses-permission android:name="android.permission.CAMERA" />
<uses-permission android:name="android.permission.RECORD_AUDIO" />

<!-- Storage permissions (API level dependent) -->
<uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" />
<uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" />
<uses-permission android:name="android.permission.READ_MEDIA_VIDEO" />
<uses-permission android:name="android.permission.READ_MEDIA_AUDIO" />
```

## 🚀 New Features in Capacitor 7 Version

### Enhanced Permission Handling
```typescript
// More granular permission checking
const permissions = await VideoRecorder.checkPermissions();
console.log('Camera:', permissions.camera);
console.log('Microphone:', permissions.microphone);
console.log('Storage:', permissions.storage);
```

### Improved Error Messages
```typescript
try {
  await VideoRecorder.startRecording(options);
} catch (error) {
  // More detailed error information
  console.log('Error code:', error.code);
  console.log('Error message:', error.message);
  console.log('Platform details:', error.details);
}
```

### Better File Management
```typescript
// Enhanced file operations
const result = await VideoRecorder.stopRecording();
console.log('File path:', result.videoPath);
console.log('File size:', result.fileSize);
console.log('Duration:', result.duration);
console.log('Resolution:', `${result.width}x${result.height}`);
```

## 🔍 Testing with Capacitor 7

### Development Setup
```bash
# Create new Capacitor 7 project
npm create @capacitor/app my-video-app
cd my-video-app

# Install the plugin
npm install @cipace/capacitor-video-recorder

# Add platforms
npx cap add ios
npx cap add android

# Sync and run
npx cap sync
npx cap run ios
npx cap run android
```

### Web Testing
```bash
# Test in browser (development only)
npm run dev
# or
npx cap serve
```

## 🐛 Troubleshooting

### Common Issues

#### iOS Build Errors
```bash
# Clean and rebuild
cd ios
rm -rf Pods Podfile.lock
pod install
cd ..
npx cap sync ios
```

#### Android Build Errors
```bash
# Clean Android build
cd android
./gradlew clean
cd ..
npx cap sync android
```

#### Permission Issues
```typescript
// Always check permissions before recording
const permissions = await VideoRecorder.checkPermissions();
if (permissions.camera !== 'granted') {
  await VideoRecorder.requestPermissions();
}
```

## 📚 Resources

- [Capacitor 7 Migration Guide](https://capacitorjs.com/docs/updating/7-0)
- [Plugin API Documentation](./README.md)
- [Example Implementation](./example/migration-example.ts)

## 🎯 Compatibility Matrix

| Capacitor Version | Plugin Version | iOS Min | Android Min | Status |
|-------------------|----------------|---------|-------------|---------|
| 7.0+ | 1.0+ | iOS 14.0+ | API 23+ | ✅ Supported |
| 6.0+ | - | - | - | ❌ Not Supported |
| 5.0+ | - | - | - | ❌ Not Supported |

## 🔮 Future Updates

This plugin will continue to support the latest Capacitor versions and platform requirements. We're committed to:

- Following Capacitor's release cycle
- Supporting latest iOS and Android APIs
- Maintaining backward compatibility where possible
- Providing migration guides for major updates

---

**Ready to use with Capacitor 7! 🚀**
