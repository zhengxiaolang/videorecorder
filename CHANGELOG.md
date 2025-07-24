# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.0.4] - 2025-01-24

### Fixed
- **Android**: Fixed permission handling logic for video recording
- **Android**: Simplified permission request flow, removed complex `_originalMethod` mechanism
- **Android**: Fixed Android 13+ storage permission compatibility issues
- **Android**: Ensured consistent permission string usage across request and validation
- **Android**: Fixed permission callback handling for all recording methods

### Changed
- **Android**: Refactored permission handling with dedicated callbacks for each method
- **Android**: Improved error messages for permission denials
- **Android**: Streamlined permission request methods for better maintainability

### Technical Details
- Replaced complex `handlePermissionResult` with method-specific callbacks
- Fixed `hasStoragePermission()` to use consistent permission strings
- Added proper Android 13+ `READ_MEDIA_VIDEO` permission support
- Simplified permission flow: `captureVideo` → `requestVideoPermissions` → `captureVideoPermissionCallback`

## [1.0.2] - 2025-01-24

### Fixed
- **Android**: Fixed missing `java.io.File` import causing compilation error
- **Android**: Fixed method visibility issue with `hasRequiredPermissions()` method
- **Android**: Updated to use standard Capacitor plugin dependency pattern
- **Android**: Improved Capacitor 7.0+ version compatibility

### Changed
- **Android**: Replaced custom `capacitorAndroidVersion` variable with standard Capacitor dependency resolution
- **Android**: Now uses `implementation project(':capacitor-android')` for development mode
- **Android**: Added support for `CAP_PLUGIN_PUBLISH` environment variable for publishing mode

### Technical Details
- Fixed `cannot find symbol: class File` compilation error
- Fixed `Cannot reduce the visibility of the inherited method` error
- Aligned with official Capacitor plugin patterns for better maintainability

## [1.0.1] - 2025-01-23

### Added
- Initial release with Capacitor 7+ support
- Video recording functionality for iOS and Android
- Audio recording capabilities
- Permission management
- File system integration

### Features
- Cross-platform video recording
- Configurable recording options
- Permission handling
- File management utilities
