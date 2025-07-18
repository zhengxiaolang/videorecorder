import Foundation
import AVFoundation
import Photos
import UIKit

// MARK: - Data Models

public enum VideoQuality: String, CaseIterable {
    case low = "low"
    case medium = "medium"
    case high = "high"
    case highest = "highest"
    
    var preset: AVCaptureSession.Preset {
        switch self {
        case .low:
            return .low
        case .medium:
            return .medium
        case .high:
            return .high
        case .highest:
            return .hd1920x1080
        }
    }
}

public enum CameraPosition: String {
    case front = "front"
    case back = "back"
    
    var devicePosition: AVCaptureDevice.Position {
        switch self {
        case .front:
            return .front
        case .back:
            return .back
        }
    }
}

public enum VideoOrientation: String {
    case portrait = "portrait"
    case landscape = "landscape"
}

public struct VideoRecordingOptions {
    let quality: VideoQuality
    let maxDuration: Double
    let fileNamePrefix: String
    let saveToGallery: Bool
    let camera: CameraPosition
    let orientation: VideoOrientation
    let enableAudio: Bool
}

public struct StartRecordingResult {
    let recordingId: String
    let startTime: Double
    let tempFilePath: String?
}

public struct StopRecordingResult {
    let recordingId: String
    let videoPath: String
    let fileSize: Int64
    let duration: Double
    let width: Int
    let height: Int
    let startTime: Double
    let endTime: Double
    let thumbnailPath: String?
    let mimeType: String
}

public struct RecordingStatus {
    let isRecording: Bool
    let isPaused: Bool
    let currentDuration: Double
    let recordingId: String?
}

public struct VideoRecorderError: Error {
    let code: String
    let message: String
    let details: [String: Any]?
    
    init(code: String, message: String, details: [String: Any]? = nil) {
        self.code = code
        self.message = message
        self.details = details
    }
}

// MARK: - Audio Recording Support

public struct AudioFile {
    let name: String
    let path: String
    let mimeType: String
    let size: Int64
    let lastModified: Date
}

public class AudioRecorder: NSObject {
    private var audioRecorder: AVAudioRecorder?
    private var recordingSession: AVAudioSession?
    private var outputURL: URL?
    
    public func startRecording(duration: Double, completion: @escaping (Result<[AudioFile], Error>) -> Void) {
        setupAudioSession { [weak self] success in
            if success {
                self?.beginRecording(duration: duration, completion: completion)
            } else {
                completion(.failure(VideoRecorderError(code: "MICROPHONE_ERROR", message: "Failed to setup audio session")))
            }
        }
    }
    
    private func setupAudioSession(completion: @escaping (Bool) -> Void) {
        recordingSession = AVAudioSession.sharedInstance()
        
        do {
            try recordingSession?.setCategory(.playAndRecord, mode: .default)
            try recordingSession?.setActive(true)
            
            recordingSession?.requestRecordPermission { allowed in
                DispatchQueue.main.async {
                    completion(allowed)
                }
            }
        } catch {
            completion(false)
        }
    }
    
    private func beginRecording(duration: Double, completion: @escaping (Result<[AudioFile], Error>) -> Void) {
        let documentsPath = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0]
        let fileName = "audio_\(Int(Date().timeIntervalSince1970)).m4a"
        outputURL = documentsPath.appendingPathComponent(fileName)
        
        guard let outputURL = outputURL else {
            completion(.failure(VideoRecorderError(code: "STORAGE_ERROR", message: "Failed to create output URL")))
            return
        }
        
        let settings = [
            AVFormatIDKey: Int(kAudioFormatMPEG4AAC),
            AVSampleRateKey: 44100,
            AVNumberOfChannelsKey: 2,
            AVEncoderAudioQualityKey: AVAudioQuality.high.rawValue
        ]
        
        do {
            audioRecorder = try AVAudioRecorder(url: outputURL, settings: settings)
            audioRecorder?.delegate = self
            audioRecorder?.record(forDuration: duration)
            
            // 设置完成回调
            DispatchQueue.main.asyncAfter(deadline: .now() + duration) { [weak self] in
                self?.stopRecording(completion: completion)
            }
            
        } catch {
            completion(.failure(VideoRecorderError(code: "RECORDING_FAILED", message: "Failed to start audio recording: \(error.localizedDescription)")))
        }
    }
    
    private func stopRecording(completion: @escaping (Result<[AudioFile], Error>) -> Void) {
        audioRecorder?.stop()
        
        guard let outputURL = outputURL else {
            completion(.failure(VideoRecorderError(code: "STORAGE_ERROR", message: "Output URL not found")))
            return
        }
        
        do {
            let attributes = try FileManager.default.attributesOfItem(atPath: outputURL.path)
            let fileSize = attributes[.size] as? Int64 ?? 0
            
            let audioFile = AudioFile(
                name: outputURL.lastPathComponent,
                path: outputURL.path,
                mimeType: "audio/mp4",
                size: fileSize,
                lastModified: Date()
            )
            
            completion(.success([audioFile]))
        } catch {
            completion(.failure(VideoRecorderError(code: "STORAGE_ERROR", message: "Failed to get file info: \(error.localizedDescription)")))
        }
    }
}

extension AudioRecorder: AVAudioRecorderDelegate {
    public func audioRecorderDidFinishRecording(_ recorder: AVAudioRecorder, successfully flag: Bool) {
        // 录制完成的处理在 stopRecording 方法中
    }

    public func audioRecorderEncodeErrorDidOccur(_ recorder: AVAudioRecorder, error: Error?) {
        // 错误处理
        print("Audio recording error: \(error?.localizedDescription ?? "Unknown error")")
    }
}

// MARK: - VideoRecorder Class

public class VideoRecorder: NSObject {
    private var captureSession: AVCaptureSession?
    private var videoOutput: AVCaptureMovieFileOutput?
    private var videoInput: AVCaptureDeviceInput?
    private var audioInput: AVCaptureDeviceInput?
    private var previewLayer: AVCaptureVideoPreviewLayer?
    private var recordingViewController: VideoRecordingViewController?
    private var stopRecordingCallback: ((Result<StopRecordingResult, VideoRecorderError>) -> Void)?

    private var recordingId: String?
    private var startTime: Double = 0
    private var outputURL: URL?
    private var options: VideoRecordingOptions?
    private var maxDurationTimer: Timer?
    public override init() {
        super.init()
    }

    // MARK: - Public Methods

    public func setCompletionHandler(_ handler: @escaping (Result<StopRecordingResult, VideoRecorderError>) -> Void) {
        self.stopRecordingCallback = handler
    }

    public func openRecordingInterface(options: VideoRecordingOptions, completion: @escaping (Result<StopRecordingResult, VideoRecorderError>) -> Void) {
        self.options = options

        // 检查并请求权限
        requestPermissionsIfNeeded { [weak self] granted in
            guard granted else {
                completion(.failure(VideoRecorderError(code: "PERMISSION_DENIED", message: "Camera or microphone permission denied")))
                return
            }

            // 权限获取成功，设置录制会话
            self?.setupCaptureSession(options: options) { result in
                switch result {
                case .success:
                    self?.startRecordingSession(options: options, completion: completion)
                case .failure(let error):
                    completion(.failure(error))
                }
            }
        }
    }

    public func stopRecording(completion: @escaping (Result<StopRecordingResult, VideoRecorderError>) -> Void) {
        guard let videoOutput = videoOutput, videoOutput.isRecording else {
            completion(.failure(VideoRecorderError(code: "NOT_RECORDING", message: "No active recording to stop")))
            return
        }

        self.stopRecordingCallback = completion
        videoOutput.stopRecording()

        // 停止定时器
        maxDurationTimer?.invalidate()
        maxDurationTimer = nil
    }

    private func autoStopRecording() {
        guard let videoOutput = videoOutput, videoOutput.isRecording else {
            return
        }

        // 自动停止时不设置completion，让现有的completion处理结果
        videoOutput.stopRecording()

        // 停止定时器
        maxDurationTimer?.invalidate()
        maxDurationTimer = nil
    }

    public func pauseRecording(completion: @escaping (Result<Void, VideoRecorderError>) -> Void) {
        guard let videoOutput = videoOutput, videoOutput.isRecording else {
            completion(.failure(VideoRecorderError(code: "NOT_RECORDING", message: "No active recording to pause")))
            return
        }

        // iOS的AVCaptureMovieFileOutput不支持暂停，这里返回错误
        completion(.failure(VideoRecorderError(code: "NOT_SUPPORTED", message: "Pause/Resume is not supported on iOS")))
    }

    public func resumeRecording(completion: @escaping (Result<Void, VideoRecorderError>) -> Void) {
        // iOS的AVCaptureMovieFileOutput不支持恢复，这里返回错误
        completion(.failure(VideoRecorderError(code: "NOT_SUPPORTED", message: "Pause/Resume is not supported on iOS")))
    }

    public func getRecordingStatus() -> RecordingStatus {
        let isRecording = videoOutput?.isRecording ?? false
        let currentDuration = isRecording ? (Date().timeIntervalSince1970 - startTime) : 0

        return RecordingStatus(
            isRecording: isRecording,
            isPaused: false, // iOS不支持暂停
            currentDuration: currentDuration,
            recordingId: recordingId
        )
    }

    public static func deleteRecording(videoPath: String, deleteThumbnail: Bool, completion: @escaping (Result<Void, VideoRecorderError>) -> Void) {
        let fileManager = FileManager.default

        // 删除视频文件
        if fileManager.fileExists(atPath: videoPath) {
            do {
                try fileManager.removeItem(atPath: videoPath)
            } catch {
                completion(.failure(VideoRecorderError(code: "STORAGE_ERROR", message: "Failed to delete video file: \(error.localizedDescription)")))
                return
            }
        }

        // 删除缩略图
        if deleteThumbnail {
            let thumbnailPath = videoPath.replacingOccurrences(of: ".mp4", with: "_thumbnail.jpg")
            if fileManager.fileExists(atPath: thumbnailPath) {
                try? fileManager.removeItem(atPath: thumbnailPath)
            }
        }

        completion(.success(()))
    }

    // MARK: - Private Methods

    private func checkPermissions() -> Bool {
        let cameraStatus = AVCaptureDevice.authorizationStatus(for: .video)
        let microphoneStatus = AVCaptureDevice.authorizationStatus(for: .audio)

        return cameraStatus == .authorized && microphoneStatus == .authorized
    }

    private func requestPermissionsIfNeeded(completion: @escaping (Bool) -> Void) {
        let cameraStatus = AVCaptureDevice.authorizationStatus(for: .video)
        let microphoneStatus = AVCaptureDevice.authorizationStatus(for: .audio)

        // 如果权限已经授予，直接返回成功
        if cameraStatus == .authorized && microphoneStatus == .authorized {
            completion(true)
            return
        }

        // 如果权限被明确拒绝，返回失败
        if cameraStatus == .denied || microphoneStatus == .denied {
            completion(false)
            return
        }

        let group = DispatchGroup()
        var cameraGranted = cameraStatus == .authorized
        var microphoneGranted = microphoneStatus == .authorized

        // 请求摄像头权限（如果需要）
        if cameraStatus != .authorized {
            group.enter()
            AVCaptureDevice.requestAccess(for: .video) { granted in
                cameraGranted = granted
                group.leave()
            }
        }

        // 请求麦克风权限（如果需要）
        if microphoneStatus != .authorized {
            group.enter()
            AVCaptureDevice.requestAccess(for: .audio) { granted in
                microphoneGranted = granted
                group.leave()
            }
        }

        // 等待所有权限请求完成
        group.notify(queue: .main) {
            completion(cameraGranted && microphoneGranted)
        }
    }

    private func setupCaptureSession(options: VideoRecordingOptions, completion: @escaping (Result<Void, VideoRecorderError>) -> Void) {
        DispatchQueue.global(qos: .userInitiated).async { [weak self] in
            guard let self = self else { return }

            let session = AVCaptureSession()
            session.sessionPreset = options.quality.preset

            do {
                // 设置视频输入
                guard let videoDevice = self.getCameraDevice(for: options.camera) else {
                    completion(.failure(VideoRecorderError(code: "CAMERA_ERROR", message: "Failed to get camera device")))
                    return
                }

                let videoInput = try AVCaptureDeviceInput(device: videoDevice)
                if session.canAddInput(videoInput) {
                    session.addInput(videoInput)
                    self.videoInput = videoInput
                } else {
                    completion(.failure(VideoRecorderError(code: "CAMERA_ERROR", message: "Cannot add video input")))
                    return
                }

                // 设置音频输入
                if options.enableAudio {
                    guard let audioDevice = AVCaptureDevice.default(for: .audio) else {
                        completion(.failure(VideoRecorderError(code: "MICROPHONE_ERROR", message: "Failed to get microphone device")))
                        return
                    }

                    let audioInput = try AVCaptureDeviceInput(device: audioDevice)
                    if session.canAddInput(audioInput) {
                        session.addInput(audioInput)
                        self.audioInput = audioInput
                    }
                }

                // 设置视频输出
                let videoOutput = AVCaptureMovieFileOutput()
                if session.canAddOutput(videoOutput) {
                    session.addOutput(videoOutput)
                    self.videoOutput = videoOutput
                } else {
                    completion(.failure(VideoRecorderError(code: "RECORDING_FAILED", message: "Cannot add video output")))
                    return
                }

                self.captureSession = session
                completion(.success(()))

            } catch {
                completion(.failure(VideoRecorderError(code: "RECORDING_FAILED", message: "Failed to setup capture session: \(error.localizedDescription)")))
            }
        }
    }

    private func getCameraDevice(for position: CameraPosition) -> AVCaptureDevice? {
        let deviceTypes: [AVCaptureDevice.DeviceType] = [
            .builtInWideAngleCamera,
            .builtInTelephotoCamera,
            .builtInUltraWideCamera
        ]

        let discoverySession = AVCaptureDevice.DiscoverySession(
            deviceTypes: deviceTypes,
            mediaType: .video,
            position: position.devicePosition
        )

        return discoverySession.devices.first
    }

    private func startRecordingSession(options: VideoRecordingOptions, completion: @escaping (Result<StopRecordingResult, VideoRecorderError>) -> Void) {
        // 保存completion，等待录制完成时调用
        self.stopRecordingCallback = completion
        guard let captureSession = captureSession,
              let videoOutput = videoOutput else {
            completion(.failure(VideoRecorderError(code: "RECORDING_FAILED", message: "Capture session not properly configured")))
            return
        }

        // 生成录制ID和文件路径
        recordingId = generateRecordingId()
        startTime = Date().timeIntervalSince1970

        let fileName = "\(options.fileNamePrefix)_\(Int(startTime)).mp4"
        let documentsPath = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0]
        outputURL = documentsPath.appendingPathComponent(fileName)

        guard let outputURL = outputURL else {
            completion(.failure(VideoRecorderError(code: "STORAGE_ERROR", message: "Failed to create output URL")))
            return
        }

        // 启动捕获会话
        DispatchQueue.global(qos: .userInitiated).async {
            captureSession.startRunning()

            DispatchQueue.main.async { [weak self] in
                guard let self = self else { return }

                // 创建并显示录制界面（只显示预览，不自动开始录制）
                // completion会在录制完成时通过stopRecordingCallback调用
                self.showRecordingInterface(captureSession: captureSession, options: options, outputURL: outputURL)
            }
        }
    }

    private func showRecordingInterface(captureSession: AVCaptureSession, options: VideoRecordingOptions, outputURL: URL) {
        guard let rootViewController = UIApplication.shared.windows.first?.rootViewController else {
            stopRecordingCallback?(.failure(VideoRecorderError(code: "UI_ERROR", message: "Unable to find root view controller")))
            return
        }

        let recordingVC = VideoRecordingViewController()
        recordingVC.setupWithCaptureSession(captureSession, options: options)
        recordingVC.onRecordingStarted = { [weak self] in
            // 用户点击开始录制 - 不回调到前端，只在原生界面开始录制
            guard let self = self, let videoOutput = self.videoOutput else { return }

            // 开始实际录制
            videoOutput.startRecording(to: outputURL, recordingDelegate: self)

            // 设置最大录制时长定时器
            if options.maxDuration > 0 {
                self.maxDurationTimer = Timer.scheduledTimer(withTimeInterval: options.maxDuration, repeats: false) { _ in
                    self.autoStopRecording()
                }
            }

            // 注意：这里不调用任何回调，让用户在原生界面继续操作
            print("📹 录制已开始，等待用户点击停止或取消...")
        }
        recordingVC.onRecordingStopped = { [weak self] in
            // 用户点击停止录制 - 需要回调到前端
            guard let self = self else { return }
            print("🛑 用户点击停止录制，准备回调到前端...")

            // 直接停止录制，不覆盖现有的 stopRecordingCallback
            guard let videoOutput = self.videoOutput, videoOutput.isRecording else {
                print("⚠️ 没有正在进行的录制")
                return
            }

            // 直接调用 stopRecording，让 AVCaptureFileOutputRecordingDelegate 处理回调
            videoOutput.stopRecording()

            // 停止定时器
            self.maxDurationTimer?.invalidate()
            self.maxDurationTimer = nil

            print("🛑 停止录制命令已发送，等待 delegate 回调...")
        }
        recordingVC.onCancelled = { [weak self] in
            // 用户点击取消 - 需要回调到前端
            print("❌ 用户点击取消录制，回调到前端...")
            self?.stopRecordingCallback?(.failure(VideoRecorderError(code: "USER_CANCELLED", message: "User cancelled recording")))
            self?.stopRecordingCallback = nil
        }
        recordingVC.onRecordingFinished = { [weak self] in
            self?.recordingViewController = nil
        }

        recordingVC.modalPresentationStyle = .fullScreen
        self.recordingViewController = recordingVC

        rootViewController.present(recordingVC, animated: true)
    }

    private func generateRecordingId() -> String {
        return "recording_\(Int(Date().timeIntervalSince1970))_\(UUID().uuidString.prefix(8))"
    }

    private func generateThumbnail(from videoURL: URL) -> String? {
        let asset = AVAsset(url: videoURL)
        let imageGenerator = AVAssetImageGenerator(asset: asset)
        imageGenerator.appliesPreferredTrackTransform = true

        let time = CMTime(seconds: 1, preferredTimescale: 60)

        do {
            let cgImage = try imageGenerator.copyCGImage(at: time, actualTime: nil)
            let image = UIImage(cgImage: cgImage)

            let thumbnailFileName = videoURL.lastPathComponent.replacingOccurrences(of: ".mp4", with: "_thumbnail.jpg")
            let thumbnailURL = videoURL.deletingLastPathComponent().appendingPathComponent(thumbnailFileName)

            if let imageData = image.jpegData(compressionQuality: 0.8) {
                try imageData.write(to: thumbnailURL)
                return thumbnailURL.path
            }
        } catch {
            print("Failed to generate thumbnail: \(error)")
        }

        return nil
    }

    private func saveToPhotoLibrary(videoURL: URL, completion: @escaping (Bool) -> Void) {
        PHPhotoLibrary.requestAuthorization { status in
            guard status == .authorized else {
                completion(false)
                return
            }

            PHPhotoLibrary.shared().performChanges({
                PHAssetChangeRequest.creationRequestForAssetFromVideo(atFileURL: videoURL)
            }) { success, error in
                completion(success)
            }
        }
    }

    private func getVideoInfo(from url: URL) -> (width: Int, height: Int, duration: Double) {
        let asset = AVAsset(url: url)
        let duration = CMTimeGetSeconds(asset.duration)

        guard let videoTrack = asset.tracks(withMediaType: .video).first else {
            return (0, 0, duration)
        }

        let size = videoTrack.naturalSize.applying(videoTrack.preferredTransform)
        let width = Int(abs(size.width))
        let height = Int(abs(size.height))

        return (width, height, duration)
    }
}

// MARK: - AVCaptureFileOutputRecordingDelegate

extension VideoRecorder: AVCaptureFileOutputRecordingDelegate {
    public func fileOutput(_ output: AVCaptureFileOutput, didFinishRecordingTo outputFileURL: URL, from connections: [AVCaptureConnection], error: Error?) {

        // 停止捕获会话
        captureSession?.stopRunning()

        // 关闭录制界面
        DispatchQueue.main.async { [weak self] in
            self?.recordingViewController?.dismiss(animated: true) {
                self?.recordingViewController = nil
            }
        }

        if let error = error {
            stopRecordingCallback?(.failure(VideoRecorderError(code: "RECORDING_FAILED", message: "Recording failed: \(error.localizedDescription)")))
            stopRecordingCallback = nil // 清理回调
            return
        }

        guard let recordingId = recordingId else {
            stopRecordingCallback?(.failure(VideoRecorderError(code: "RECORDING_FAILED", message: "Recording ID not found")))
            stopRecordingCallback = nil // 清理回调
            return
        }

        let endTime = Date().timeIntervalSince1970
        let videoInfo = getVideoInfo(from: outputFileURL)

        // 获取文件大小
        let fileSize: Int64
        do {
            let attributes = try FileManager.default.attributesOfItem(atPath: outputFileURL.path)
            fileSize = attributes[.size] as? Int64 ?? 0
        } catch {
            fileSize = 0
        }

        // 生成缩略图
        let thumbnailPath = generateThumbnail(from: outputFileURL)

        let result = StopRecordingResult(
            recordingId: recordingId,
            videoPath: outputFileURL.path,
            fileSize: fileSize,
            duration: videoInfo.duration,
            width: videoInfo.width,
            height: videoInfo.height,
            startTime: startTime,
            endTime: endTime,
            thumbnailPath: thumbnailPath,
            mimeType: "video/mp4"
        )

        // 如果需要保存到相册
        if let options = options, options.saveToGallery {
            saveToPhotoLibrary(videoURL: outputFileURL) { [weak self] success in
                if !success {
                    print("Failed to save video to photo library")
                }
                self?.stopRecordingCallback?(.success(result))
                self?.stopRecordingCallback = nil // 清理回调
            }
        } else {
            stopRecordingCallback?(.success(result))
            stopRecordingCallback = nil // 清理回调
        }
    }
}

// MARK: - VideoRecordingViewController

class VideoRecordingViewController: UIViewController {
    private var captureSession: AVCaptureSession?
    private var previewLayer: AVCaptureVideoPreviewLayer?
    private var recordingLabel: UILabel!
    private var recordButton: UIButton!
    private var stopButton: UIButton!
    private var cancelButton: UIButton!
    private var switchCameraButton: UIButton!
    private var durationLabel: UILabel!
    private var isRecording = false
    private var currentCameraPosition: CameraPosition = .back
    private var options: VideoRecordingOptions?
    private var recordingTimer: Timer?
    private var recordingStartTime: Date?

    var onRecordingStarted: (() -> Void)?
    var onRecordingFinished: (() -> Void)?
    var onRecordingStopped: (() -> Void)?
    var onCancelled: (() -> Void)?

    override func viewDidLoad() {
        super.viewDidLoad()
        setupUI()
    }

    override func viewDidAppear(_ animated: Bool) {
        super.viewDidAppear(animated)
        // 界面显示后只显示预览，不自动开始录制
    }

    override func viewWillDisappear(_ animated: Bool) {
        super.viewWillDisappear(animated)
        onRecordingFinished?()
    }

    func setupWithCaptureSession(_ session: AVCaptureSession, options: VideoRecordingOptions) {
        self.captureSession = session
        self.options = options
        self.currentCameraPosition = options.camera
        setupPreviewLayer()
    }

    private func setupUI() {
        view.backgroundColor = .black

        // 录制状态标签（初始隐藏）
        recordingLabel = UILabel()
        recordingLabel.text = "● REC"
        recordingLabel.textColor = .red
        recordingLabel.font = UIFont.boldSystemFont(ofSize: 18)
        recordingLabel.translatesAutoresizingMaskIntoConstraints = false
        recordingLabel.isHidden = true
        view.addSubview(recordingLabel)

        // 录制时长标签（初始隐藏）
        durationLabel = UILabel()
        durationLabel.text = "00:00"
        durationLabel.textColor = .white
        durationLabel.font = UIFont.monospacedDigitSystemFont(ofSize: 20, weight: .medium)
        durationLabel.backgroundColor = UIColor.black.withAlphaComponent(0.6)
        durationLabel.layer.cornerRadius = 8
        durationLabel.layer.masksToBounds = true
        durationLabel.textAlignment = .center
        durationLabel.translatesAutoresizingMaskIntoConstraints = false
        durationLabel.isHidden = true
        view.addSubview(durationLabel)

        // 取消按钮 - 圆形，左侧
        cancelButton = UIButton(type: .system)
        cancelButton.setTitle("✕", for: .normal)
        cancelButton.setTitleColor(.white, for: .normal)
        cancelButton.backgroundColor = UIColor.systemGray.withAlphaComponent(0.8)
        cancelButton.layer.cornerRadius = 35 // 圆形
        cancelButton.titleLabel?.font = UIFont.systemFont(ofSize: 24, weight: .medium)
        cancelButton.layer.shadowColor = UIColor.black.cgColor
        cancelButton.layer.shadowOffset = CGSize(width: 0, height: 2)
        cancelButton.layer.shadowOpacity = 0.3
        cancelButton.layer.shadowRadius = 4
        cancelButton.translatesAutoresizingMaskIntoConstraints = false
        cancelButton.addTarget(self, action: #selector(cancelButtonTapped), for: .touchUpInside)
        view.addSubview(cancelButton)

        // 开始录制按钮 - 圆形，中间，最大
        recordButton = UIButton(type: .system)
        recordButton.setTitle("●", for: .normal)
        recordButton.setTitleColor(.white, for: .normal)
        recordButton.backgroundColor = UIColor.systemRed
        recordButton.layer.cornerRadius = 40 // 最大的圆形
        recordButton.titleLabel?.font = UIFont.systemFont(ofSize: 32, weight: .bold)
        recordButton.layer.shadowColor = UIColor.black.cgColor
        recordButton.layer.shadowOffset = CGSize(width: 0, height: 3)
        recordButton.layer.shadowOpacity = 0.4
        recordButton.layer.shadowRadius = 6
        recordButton.translatesAutoresizingMaskIntoConstraints = false
        recordButton.addTarget(self, action: #selector(recordButtonTapped), for: .touchUpInside)
        view.addSubview(recordButton)

        // 停止录制按钮 - 圆形，中间，与Record相同位置（初始隐藏）
        stopButton = UIButton(type: .system)
        stopButton.setTitle("■", for: .normal)
        stopButton.setTitleColor(.white, for: .normal)
        stopButton.backgroundColor = UIColor.systemRed
        stopButton.layer.cornerRadius = 40 // 与Record相同大小
        stopButton.titleLabel?.font = UIFont.systemFont(ofSize: 28, weight: .bold)
        stopButton.layer.shadowColor = UIColor.black.cgColor
        stopButton.layer.shadowOffset = CGSize(width: 0, height: 3)
        stopButton.layer.shadowOpacity = 0.4
        stopButton.layer.shadowRadius = 6
        stopButton.translatesAutoresizingMaskIntoConstraints = false
        stopButton.addTarget(self, action: #selector(stopButtonTapped), for: .touchUpInside)
        stopButton.isHidden = true
        view.addSubview(stopButton)

        // 切换摄像头按钮 - 圆形，右侧
        switchCameraButton = UIButton(type: .system)

        // 创建摄像头切换图标
        let config = UIImage.SymbolConfiguration(pointSize: 20, weight: .medium)
        if let cameraImage = UIImage(systemName: "camera.rotate", withConfiguration: config) {
            // 使用系统图标
            switchCameraButton.setImage(cameraImage, for: .normal)
            switchCameraButton.tintColor = .white
        } else {
            // 备用方案：使用文字图标
            switchCameraButton.setTitle("⟲", for: .normal)
            switchCameraButton.titleLabel?.font = UIFont.systemFont(ofSize: 20, weight: .medium)
            switchCameraButton.setTitleColor(.white, for: .normal)
        }

        switchCameraButton.backgroundColor = UIColor.black.withAlphaComponent(0.7)
        switchCameraButton.layer.cornerRadius = 35 // 圆形，与Cancel相同大小
        switchCameraButton.layer.shadowColor = UIColor.black.cgColor
        switchCameraButton.layer.shadowOffset = CGSize(width: 0, height: 2)
        switchCameraButton.layer.shadowOpacity = 0.3
        switchCameraButton.layer.shadowRadius = 4

        switchCameraButton.translatesAutoresizingMaskIntoConstraints = false
        switchCameraButton.addTarget(self, action: #selector(switchCameraButtonTapped), for: .touchUpInside)
        view.addSubview(switchCameraButton)

        setupConstraints()
    }

    private func setupConstraints() {
        NSLayoutConstraint.activate([
            // 录制状态标签 - 顶部左侧，避开刘海
            recordingLabel.topAnchor.constraint(equalTo: view.safeAreaLayoutGuide.topAnchor, constant: 15),
            recordingLabel.leadingAnchor.constraint(equalTo: view.safeAreaLayoutGuide.leadingAnchor, constant: 20),

            // 录制时长标签 - 顶部中央，避开刘海
            durationLabel.topAnchor.constraint(equalTo: view.safeAreaLayoutGuide.topAnchor, constant: 15),
            durationLabel.centerXAnchor.constraint(equalTo: view.centerXAnchor),
            durationLabel.widthAnchor.constraint(equalToConstant: 100),
            durationLabel.heightAnchor.constraint(equalToConstant: 36),

            // 底部按钮行 - 所有按钮都在底部同一行
            // 取消按钮 - 左侧
            cancelButton.bottomAnchor.constraint(equalTo: view.safeAreaLayoutGuide.bottomAnchor, constant: -50),
            cancelButton.leadingAnchor.constraint(equalTo: view.leadingAnchor, constant: 50),
            cancelButton.widthAnchor.constraint(equalToConstant: 70),
            cancelButton.heightAnchor.constraint(equalToConstant: 70),

            // 开始录制按钮 - 中间
            recordButton.bottomAnchor.constraint(equalTo: view.safeAreaLayoutGuide.bottomAnchor, constant: -40),
            recordButton.centerXAnchor.constraint(equalTo: view.centerXAnchor),
            recordButton.widthAnchor.constraint(equalToConstant: 80),
            recordButton.heightAnchor.constraint(equalToConstant: 80),

            // 停止录制按钮 - 与Record按钮相同位置
            stopButton.bottomAnchor.constraint(equalTo: view.safeAreaLayoutGuide.bottomAnchor, constant: -40),
            stopButton.centerXAnchor.constraint(equalTo: view.centerXAnchor),
            stopButton.widthAnchor.constraint(equalToConstant: 80),
            stopButton.heightAnchor.constraint(equalToConstant: 80),

            // 切换摄像头按钮 - 右侧
            switchCameraButton.bottomAnchor.constraint(equalTo: view.safeAreaLayoutGuide.bottomAnchor, constant: -50),
            switchCameraButton.trailingAnchor.constraint(equalTo: view.trailingAnchor, constant: -50),
            switchCameraButton.widthAnchor.constraint(equalToConstant: 70),
            switchCameraButton.heightAnchor.constraint(equalToConstant: 70)
        ])
    }

    private func setupPreviewLayer() {
        guard let captureSession = captureSession else { return }

        previewLayer = AVCaptureVideoPreviewLayer(session: captureSession)
        previewLayer?.videoGravity = .resizeAspectFill
        previewLayer?.frame = view.bounds

        if let previewLayer = previewLayer {
            view.layer.insertSublayer(previewLayer, at: 0)
        }
    }

    private func startBlinkingAnimation() {
        UIView.animate(withDuration: 0.5, delay: 0, options: [.repeat, .autoreverse], animations: {
            self.recordingLabel.alpha = 0.3
        })
    }

    private func startDurationTimer() {
        recordingTimer = Timer.scheduledTimer(withTimeInterval: 1.0, repeats: true) { [weak self] _ in
            self?.updateDurationLabel()
        }
    }

    private func stopDurationTimer() {
        recordingTimer?.invalidate()
        recordingTimer = nil
    }

    private func updateDurationLabel() {
        guard let startTime = recordingStartTime else { return }

        let elapsed = Date().timeIntervalSince(startTime)
        let minutes = Int(elapsed) / 60
        let seconds = Int(elapsed) % 60

        DispatchQueue.main.async { [weak self] in
            self?.durationLabel.text = String(format: "%02d:%02d", minutes, seconds)
        }
    }

    @objc private func recordButtonTapped() {
        isRecording = true
        recordingStartTime = Date()

        // 隐藏预览状态的按钮
        recordButton.isHidden = true
        cancelButton.isHidden = true
        switchCameraButton.isHidden = true

        // 显示录制状态的按钮和指示器
        stopButton.isHidden = false
        recordingLabel.isHidden = false
        durationLabel.isHidden = false

        startBlinkingAnimation()
        startDurationTimer()
        onRecordingStarted?()
    }

    @objc private func stopButtonTapped() {
        isRecording = false

        // 停止计时器和动画
        stopDurationTimer()
        recordingLabel.layer.removeAllAnimations()

        onRecordingStopped?()
    }

    @objc private func cancelButtonTapped() {
        dismiss(animated: true)
        onCancelled?()
    }

    @objc private func switchCameraButtonTapped() {
        guard !isRecording, let captureSession = captureSession else { return }

        // 切换摄像头位置
        currentCameraPosition = (currentCameraPosition == .back) ? .front : .back

        // 停止当前会话
        captureSession.stopRunning()

        // 移除当前视频输入
        if let currentVideoInput = captureSession.inputs.first(where: { $0 is AVCaptureDeviceInput }) as? AVCaptureDeviceInput {
            captureSession.removeInput(currentVideoInput)
        }

        // 添加新的视频输入
        do {
            guard let newVideoDevice = getCameraDevice(for: currentCameraPosition) else { return }
            let newVideoInput = try AVCaptureDeviceInput(device: newVideoDevice)

            if captureSession.canAddInput(newVideoInput) {
                captureSession.addInput(newVideoInput)
            }
        } catch {
            print("Error switching camera: \(error)")
        }

        // 重新启动会话
        captureSession.startRunning()
    }

    private func getCameraDevice(for position: CameraPosition) -> AVCaptureDevice? {
        let deviceTypes: [AVCaptureDevice.DeviceType] = [
            .builtInWideAngleCamera,
            .builtInTelephotoCamera,
            .builtInUltraWideCamera
        ]

        let discoverySession = AVCaptureDevice.DiscoverySession(
            deviceTypes: deviceTypes,
            mediaType: .video,
            position: position.devicePosition
        )

        return discoverySession.devices.first
    }

    override func viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()
        previewLayer?.frame = view.bounds
    }
}
