import Foundation
import Capacitor
import AVFoundation
import Photos
import UIKit

@objc(VideoRecorderPlugin)
public class VideoRecorderPlugin: CAPPlugin, CAPBridgedPlugin {
    public let identifier = "VideoRecorderPlugin"
    public let jsName = "CipaceVideoRecorder"
    public let pluginMethods: [CAPPluginMethod] = [
        // Media Capture 兼容方法
        CAPPluginMethod(name: "captureVideo", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "captureAudio", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "getSupportedVideoModes", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "getSupportedAudioModes", returnType: CAPPluginReturnPromise),
        
        // 高级录制方法
        CAPPluginMethod(name: "openRecordingInterface", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "stopRecording", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "pauseRecording", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "resumeRecording", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "checkPermissions", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "requestPermissions", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "getRecordingStatus", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "deleteRecording", returnType: CAPPluginReturnPromise),
        
        // 缩略图生成方法
        CAPPluginMethod(name: "generateThumbnail", returnType: CAPPluginReturnPromise)
    ]
    
    private var videoRecorder: VideoRecorder?
    private var currentCall: CAPPluginCall?
    private var recordingInterfaceCall: CAPPluginCall?
    
    // MARK: - Media Capture 兼容方法
    
    @objc func captureVideo(_ call: CAPPluginCall) {
        let duration = call.getDouble("duration") ?? 300.0
        let limit = call.getInt("limit") ?? 1
        let quality = call.getInt("quality") ?? 50

        // 转换为内部选项格式
        let options = VideoRecordingOptions(
            quality: mapQualityFromNumber(quality),
            maxDuration: duration,
            fileNamePrefix: "captured_video",
            saveToGallery: false,
            camera: .back,
            orientation: .portrait,
            enableAudio: true
        )

        videoRecorder = VideoRecorder()

        // 存储call以便在录制完成时使用
        self.currentCall = call

        videoRecorder?.openRecordingInterface(options: options) { [weak self] result in
            DispatchQueue.main.async {
                switch result {
                case .success(let recordingResult):
                    // 录制完成，直接返回结果
                    print("Recording completed with ID: \(recordingResult.recordingId)")

                    // 检查是否有来自captureVideo的待处理调用
                    if let captureCall = self?.currentCall {
                        // 这是captureVideo调用的结果，返回MediaCapture格式
                        let mediaFile = self?.createMediaFileDict(from: recordingResult)
                        captureCall.resolve(["files": [mediaFile].compactMap { $0 }])
                        self?.currentCall = nil
                    } else {
                        // 这是直接的openRecordingInterface调用，返回完整的录制结果
                        let response = self?.createStopRecordingResponse(from: recordingResult)
                        call.resolve(response ?? JSObject())
                    }
                    self?.videoRecorder = nil
                case .failure(let error):
                    // 检查是否有待处理的调用
                    if let captureCall = self?.currentCall {
                        captureCall.reject(error.code, error.message, nil, error.details)
                        self?.currentCall = nil
                    } else {
                        call.reject(error.code, error.message, nil, error.details)
                    }
                }
            }
        }
    }
    
    @objc func captureAudio(_ call: CAPPluginCall) {
        let duration = call.getDouble("duration") ?? 300.0
        let limit = call.getInt("limit") ?? 1
        
        // 使用系统的音频录制功能
        let audioRecorder = AudioRecorder()
        
        audioRecorder.startRecording(duration: duration) { result in
            DispatchQueue.main.async {
                switch result {
                case .success(let audioFiles):
                    let files = audioFiles.map { self.createMediaFileDict(from: $0) }
                    call.resolve(["files": files])
                case .failure(let error):
                    call.reject("RECORDING_FAILED", error.localizedDescription)
                }
            }
        }
    }
    
    @objc func getSupportedVideoModes(_ call: CAPPluginCall) {
        let supportedModes = [
            ["type": "video/mp4", "width": 640, "height": 480],
            ["type": "video/mp4", "width": 1280, "height": 720],
            ["type": "video/mp4", "width": 1920, "height": 1080],
            ["type": "video/mp4", "width": 3840, "height": 2160]
        ]
        call.resolve(["supportedModes": supportedModes])
    }
    
    @objc func getSupportedAudioModes(_ call: CAPPluginCall) {
        let supportedModes = [
            ["type": "audio/mp4", "width": 0, "height": 0],
            ["type": "audio/wav", "width": 0, "height": 0],
            ["type": "audio/aac", "width": 0, "height": 0]
        ]
        call.resolve(["supportedModes": supportedModes])
    }
    
    // MARK: - 高级录制方法
    
    @objc func openRecordingInterface(_ call: CAPPluginCall) {
        let quality = call.getString("quality") ?? "high"
        let maxDuration = call.getDouble("maxDuration") ?? 300.0
        let fileNamePrefix = call.getString("fileNamePrefix") ?? "video_recording"
        let saveToGallery = call.getBool("saveToGallery") ?? false
        let camera = call.getString("camera") ?? "back"
        let orientation = call.getString("orientation") ?? "portrait"
        let enableAudio = call.getBool("enableAudio") ?? true
        
        let options = VideoRecordingOptions(
            quality: VideoQuality(rawValue: quality) ?? .high,
            maxDuration: maxDuration,
            fileNamePrefix: fileNamePrefix,
            saveToGallery: saveToGallery,
            camera: CameraPosition(rawValue: camera) ?? .back,
            orientation: VideoOrientation(rawValue: orientation) ?? .portrait,
            enableAudio: enableAudio
        )
        
        videoRecorder = VideoRecorder()
        
        // 打开录制界面，等待录制完成后返回结果
        videoRecorder?.openRecordingInterface(options: options) { [weak self] result in
            DispatchQueue.main.async {
                switch result {
                case .success(let recordingResult):
                    // 录制完成，返回完整结果
                    let response = [
                        "recordingId": recordingResult.recordingId,
                        "videoPath": recordingResult.videoPath,
                        "fileSize": recordingResult.fileSize,
                        "duration": recordingResult.duration,
                        "width": recordingResult.width,
                        "height": recordingResult.height,
                        "startTime": recordingResult.startTime,
                        "endTime": recordingResult.endTime,
                        "thumbnailPath": recordingResult.thumbnailPath ?? "",
                        "mimeType": recordingResult.mimeType
                    ] as [String : Any]

                    call.resolve(response)
                    self?.videoRecorder = nil
                case .failure(let error):
                    // 录制失败或被取消
                    call.reject(error.code, error.message, nil, error.details)
                    self?.videoRecorder = nil
                }
            }
        }
    }
    
    @objc func stopRecording(_ call: CAPPluginCall) {
        guard let recorder = videoRecorder else {
            call.reject("NOT_RECORDING", "No active recording to stop")
            return
        }

        recorder.stopRecording { [weak self] result in
            DispatchQueue.main.async {
                switch result {
                case .success(let recordingResult):
                    // 检查是否有来自captureVideo的待处理调用
                    if let captureCall = self?.currentCall {
                        // 这是captureVideo调用的结果，返回MediaCapture格式
                        let mediaFile = self?.createMediaFileDict(from: recordingResult)
                        captureCall.resolve(["files": [mediaFile].compactMap { $0 }])
                        self?.currentCall = nil
                    } else {
                        // 这是直接的stopRecording调用
                        let response = self?.createStopRecordingResponse(from: recordingResult)
                        call.resolve(response ?? JSObject())
                    }
                    self?.videoRecorder = nil
                case .failure(let error):
                    // 检查是否有待处理的调用
                    if let captureCall = self?.currentCall {
                        captureCall.reject(error.code, error.message, nil, error.details)
                        self?.currentCall = nil
                    } else {
                        call.reject(error.code, error.message, nil, error.details)
                    }
                }
            }
        }
    }
    
    @objc func pauseRecording(_ call: CAPPluginCall) {
        guard let recorder = videoRecorder else {
            call.reject("NOT_RECORDING", "No active recording to pause")
            return
        }
        
        recorder.pauseRecording { result in
            DispatchQueue.main.async {
                switch result {
                case .success:
                    call.resolve()
                case .failure(let error):
                    call.reject(error.code, error.message, nil, error.details)
                }
            }
        }
    }
    
    @objc func resumeRecording(_ call: CAPPluginCall) {
        guard let recorder = videoRecorder else {
            call.reject("NOT_RECORDING", "No paused recording to resume")
            return
        }
        
        recorder.resumeRecording { result in
            DispatchQueue.main.async {
                switch result {
                case .success:
                    call.resolve()
                case .failure(let error):
                    call.reject(error.code, error.message, nil, error.details)
                }
            }
        }
    }
    
    @objc public override func checkPermissions(_ call: CAPPluginCall) {
        let cameraStatus = AVCaptureDevice.authorizationStatus(for: .video)
        let microphoneStatus = AVCaptureDevice.authorizationStatus(for: .audio)
        let photosStatus = PHPhotoLibrary.authorizationStatus()
        
        call.resolve([
            "camera": permissionStateString(from: cameraStatus),
            "microphone": permissionStateString(from: microphoneStatus),
            "storage": photosPermissionStateString(from: photosStatus)
        ])
    }
    
    @objc public override func requestPermissions(_ call: CAPPluginCall) {
        let group = DispatchGroup()
        var cameraGranted = false
        var microphoneGranted = false
        var photosGranted = false
        
        // 请求摄像头权限
        group.enter()
        AVCaptureDevice.requestAccess(for: .video) { granted in
            cameraGranted = granted
            group.leave()
        }
        
        // 请求麦克风权限
        group.enter()
        AVCaptureDevice.requestAccess(for: .audio) { granted in
            microphoneGranted = granted
            group.leave()
        }
        
        // 请求相册权限
        group.enter()
        PHPhotoLibrary.requestAuthorization { status in
            photosGranted = status == .authorized
            group.leave()
        }
        
        group.notify(queue: .main) {
            let cameraStatus = AVCaptureDevice.authorizationStatus(for: .video)
            let microphoneStatus = AVCaptureDevice.authorizationStatus(for: .audio)
            let photosStatus = PHPhotoLibrary.authorizationStatus()
            
            call.resolve([
                "camera": self.permissionStateString(from: cameraStatus),
                "microphone": self.permissionStateString(from: microphoneStatus),
                "storage": self.photosPermissionStateString(from: photosStatus)
            ])
        }
    }
    
    @objc func getRecordingStatus(_ call: CAPPluginCall) {
        guard let recorder = videoRecorder else {
            call.resolve([
                "isRecording": false,
                "isPaused": false,
                "currentDuration": 0,
                "recordingId": ""
            ])
            return
        }
        
        let status = recorder.getRecordingStatus()
        call.resolve([
            "isRecording": status.isRecording,
            "isPaused": status.isPaused,
            "currentDuration": status.currentDuration,
            "recordingId": status.recordingId ?? ""
        ])
    }
    
    @objc func deleteRecording(_ call: CAPPluginCall) {
        guard let videoPath = call.getString("videoPath") else {
            call.reject("INVALID_OPTIONS", "videoPath is required")
            return
        }

        let deleteThumbnail = call.getBool("deleteThumbnail") ?? true

        VideoRecorder.deleteRecording(videoPath: videoPath, deleteThumbnail: deleteThumbnail) { result in
            DispatchQueue.main.async {
                switch result {
                case .success:
                    call.resolve()
                case .failure(let error):
                    call.reject(error.code, error.message, nil, error.details)
                }
            }
        }
    }
    
    @objc func generateThumbnail(_ call: CAPPluginCall) {
        guard let videoPath = call.getString("videoPath") else {
            call.reject("INVALID_OPTIONS", "videoPath is required")
            return
        }
        
        let timeAt = call.getDouble("timeAt") ?? 1.0 // 默认在第1秒生成缩略图
        let quality = call.getDouble("quality") ?? 0.8 // 默认压缩质量0.8
        
        // 处理路径兼容性：支持 file:// 开头的路径
        let actualVideoPath: String
        if videoPath.hasPrefix("file://") {
            actualVideoPath = String(videoPath.dropFirst(7)) // 移除 "file://" 前缀
        } else {
            actualVideoPath = videoPath
        }
        
        // 验证视频文件是否存在
        let videoURL = URL(fileURLWithPath: actualVideoPath)
        guard FileManager.default.fileExists(atPath: actualVideoPath) else {
            call.reject("FILE_NOT_FOUND", "Video file not found at path: \(actualVideoPath)")
            return
        }
        
        // 在后台线程生成缩略图
        DispatchQueue.global(qos: .userInitiated).async {
            let result = VideoRecorder.generateThumbnail(from: videoURL, timeAt: timeAt, quality: quality)
            
            DispatchQueue.main.async {
                switch result {
                case .success(let thumbnailPath):
                    call.resolve([
                        "thumbnailPath": thumbnailPath,
                        "videoPath": actualVideoPath,
                        "timeAt": timeAt,
                        "quality": quality
                    ])
                case .failure(let error):
                    call.reject(error.code, error.message, nil, error.details)
                }
            }
        }
    }



    // MARK: - Helper Methods

    private func createStopRecordingResponse(from result: StopRecordingResult) -> JSObject {
        return [
            "recordingId": result.recordingId,
            "videoPath": result.videoPath,
            "fileSize": NSNumber(value: result.fileSize),
            "duration": result.duration,
            "width": result.width,
            "height": result.height,
            "startTime": result.startTime,
            "endTime": result.endTime,
            "thumbnailPath": result.thumbnailPath ?? "",
            "mimeType": result.mimeType
        ]
    }

    private func createMediaFileDict(from result: StopRecordingResult) -> [String: Any] {
        return [
            "name": URL(fileURLWithPath: result.videoPath).lastPathComponent,
            "fullPath": result.videoPath,
            "type": result.mimeType,
            "size": result.fileSize,
            "lastModifiedDate": Date(timeIntervalSince1970: result.endTime / 1000).timeIntervalSince1970 * 1000
        ]
    }

    private func createMediaFileDict(from audioFile: AudioFile) -> [String: Any] {
        return [
            "name": audioFile.name,
            "fullPath": audioFile.path,
            "type": audioFile.mimeType,
            "size": audioFile.size,
            "lastModifiedDate": audioFile.lastModified.timeIntervalSince1970 * 1000
        ]
    }

    private func mapQualityFromNumber(_ quality: Int) -> VideoQuality {
        switch quality {
        case 0...25:
            return .low
        case 26...50:
            return .medium
        case 51...75:
            return .high
        default:
            return .highest
        }
    }

    private func permissionStateString(from status: AVAuthorizationStatus) -> String {
        switch status {
        case .authorized:
            return "granted"
        case .denied, .restricted:
            return "denied"
        case .notDetermined:
            return "prompt"
        @unknown default:
            return "prompt"
        }
    }

    private func photosPermissionStateString(from status: PHAuthorizationStatus) -> String {
        switch status {
        case .authorized:
            return "granted"
        case .denied, .restricted:
            return "denied"
        case .notDetermined:
            return "prompt"
        case .limited:
            return "granted"
        @unknown default:
            return "prompt"
        }
    }
}
