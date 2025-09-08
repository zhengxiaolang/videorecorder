export interface VideoRecorderPlugin {
    /**
     * 开始视频录制 - 类似于 media-capture 的 captureVideo
     * @param options 录制配置选项
     * @returns Promise<CaptureVideoResult>
     */
    captureVideo(options?: CaptureVideoOptions): Promise<CaptureVideoResult>;
    /**
     * 开始音频录制 - 类似于 media-capture 的 captureAudio
     * @param options 录制配置选项
     * @returns Promise<CaptureAudioResult>
     */
    captureAudio(options?: CaptureAudioOptions): Promise<CaptureAudioResult>;
    /**
     * 打开视频录制界面 - 扩展功能
     * 注意：此方法会打开原生录制界面，等待用户完成录制操作后才返回最终结果
     * @param options 录制配置选项
     * @returns Promise<StopRecordingResult> 录制完成后的结果
     */
    openRecordingInterface(options: StartRecordingOptions): Promise<StopRecordingResult>;
    /**
     * 停止当前录制
     * @returns Promise<StopRecordingResult>
     */
    stopRecording(): Promise<StopRecordingResult>;
    /**
     * 暂停视频录制
     * @returns Promise<void>
     */
    pauseRecording(): Promise<void>;
    /**
     * 恢复视频录制
     * @returns Promise<void>
     */
    resumeRecording(): Promise<void>;
    /**
     * 检查录制权限
     * @returns Promise<PermissionStatus>
     */
    checkPermissions(): Promise<PermissionStatus>;
    /**
     * 请求录制权限
     * @returns Promise<PermissionStatus>
     */
    requestPermissions(): Promise<PermissionStatus>;
    /**
     * 获取当前录制状态
     * @returns Promise<RecordingStatus>
     */
    getRecordingStatus(): Promise<RecordingStatus>;
    /**
     * 获取支持的视频模式 - 类似于 media-capture 的 supportedVideoModes
     * @returns Promise<ConfigurationData[]>
     */
    getSupportedVideoModes(): Promise<ConfigurationData[]>;
    /**
     * 获取支持的音频模式 - 类似于 media-capture 的 supportedAudioModes
     * @returns Promise<ConfigurationData[]>
     */
    getSupportedAudioModes(): Promise<ConfigurationData[]>;
    /**
     * 删除录制的文件
     * @param options 删除选项
     * @returns Promise<void>
     */
    deleteRecording(options: DeleteRecordingOptions): Promise<void>;
    /**
     * 生成视频缩略图
     * @param options 缩略图生成选项
     * @returns Promise<ThumbnailResult>
     */
    generateThumbnail(options: GenerateThumbnailOptions): Promise<ThumbnailResult>;
}
export interface CaptureVideoOptions {
    /**
     * 最大录制时长（秒）
     */
    duration?: number;
    /**
     * 最大录制文件数量
     */
    limit?: number;
    /**
     * 视频质量 (0-100)
     */
    quality?: number;
}
export interface CaptureAudioOptions {
    /**
     * 最大录制时长（秒）
     */
    duration?: number;
    /**
     * 最大录制文件数量
     */
    limit?: number;
}
export interface CaptureVideoResult {
    /**
     * 录制的媒体文件数组
     */
    files: MediaFile[];
}
export interface CaptureAudioResult {
    /**
     * 录制的媒体文件数组
     */
    files: MediaFile[];
}
export interface MediaFile {
    /**
     * 文件名（不包含路径）
     */
    name: string;
    /**
     * 文件完整路径
     */
    fullPath: string;
    /**
     * 文件类型 (MIME type)
     */
    type: string;
    /**
     * 文件大小（字节）
     */
    size: number;
    /**
     * 最后修改日期
     */
    lastModifiedDate: Date;
    /**
     * 获取格式数据
     */
    getFormatData(): Promise<MediaFileData>;
}
export interface MediaFileData {
    /**
     * 编解码器信息
     */
    codecs: string;
    /**
     * 比特率
     */
    bitrate: number;
    /**
     * 视频/音频时长（秒）
     */
    duration: number;
    /**
     * 视频宽度（音频为0）
     */
    width: number;
    /**
     * 视频高度（音频为0）
     */
    height: number;
}
export interface ConfigurationData {
    /**
     * 媒体类型
     */
    type: string;
    /**
     * 视频宽度
     */
    width: number;
    /**
     * 视频高度
     */
    height: number;
}
export interface StartRecordingOptions {
    /**
     * 视频质量设置
     * @default 'high'
     */
    quality?: 'low' | 'medium' | 'high' | 'highest';
    /**
     * 最大录制时长（秒）
     * @default 300 (5分钟)
     */
    maxDuration?: number;
    /**
     * 文件名前缀
     * @default 'video_recording'
     */
    fileNamePrefix?: string;
    /**
     * 是否保存到相册
     * @default false
     */
    saveToGallery?: boolean;
    /**
     * 摄像头方向
     * @default 'back'
     */
    camera?: 'front' | 'back';
    /**
     * 视频方向
     * @default 'portrait'
     */
    orientation?: 'portrait' | 'landscape';
    /**
     * 是否启用音频录制
     * @default true
     */
    enableAudio?: boolean;
    /**
     * 自定义视频尺寸
     */
    customSize?: {
        width: number;
        height: number;
    };
}
export interface StartRecordingResult {
    /**
     * 录制会话ID
     */
    recordingId: string;
    /**
     * 录制开始时间戳
     */
    startTime: number;
    /**
     * 临时文件路径
     */
    tempFilePath?: string;
}
export interface StopRecordingResult {
    /**
     * 录制会话ID
     */
    recordingId: string;
    /**
     * 视频文件路径
     */
    videoPath: string;
    /**
     * 视频文件大小（字节）
     */
    fileSize: number;
    /**
     * 录制时长（秒）
     */
    duration: number;
    /**
     * 视频宽度
     */
    width: number;
    /**
     * 视频高度
     */
    height: number;
    /**
     * 录制开始时间戳
     */
    startTime: number;
    /**
     * 录制结束时间戳
     */
    endTime: number;
    /**
     * 视频缩略图路径（可选）
     */
    thumbnailPath?: string;
    /**
     * MIME类型
     */
    mimeType: string;
    /**
     * 转换为 MediaFile 格式
     */
    toMediaFile(): MediaFile;
}
export interface PermissionStatus {
    /**
     * 摄像头权限状态
     */
    camera: PermissionState;
    /**
     * 麦克风权限状态
     */
    microphone: PermissionState;
    /**
     * 存储权限状态
     */
    storage: PermissionState;
}
export type PermissionState = 'prompt' | 'prompt-with-rationale' | 'granted' | 'denied';
export interface RecordingStatus {
    /**
     * 是否正在录制
     */
    isRecording: boolean;
    /**
     * 是否暂停
     */
    isPaused: boolean;
    /**
     * 当前录制时长（秒）
     */
    currentDuration: number;
    /**
     * 录制会话ID
     */
    recordingId?: string;
}
export interface DeleteRecordingOptions {
    /**
     * 要删除的视频文件路径
     */
    videoPath: string;
    /**
     * 是否同时删除缩略图
     * @default true
     */
    deleteThumbnail?: boolean;
}
export interface GenerateThumbnailOptions {
    /**
     * 视频文件路径
     */
    videoPath: string;
    /**
     * 生成缩略图的时间点（秒）
     * @default 1.0
     */
    timeAt?: number;
    /**
     * 缩略图质量 (0.0 - 1.0)
     * @default 0.8
     */
    quality?: number;
}
export interface ThumbnailResult {
    /**
     * 生成的缩略图文件路径
     */
    thumbnailPath: string;
    /**
     * 原始视频文件路径
     */
    videoPath: string;
    /**
     * 生成缩略图的时间点（秒）
     */
    timeAt: number;
    /**
     * 缩略图质量
     */
    quality: number;
}
export interface CaptureError {
    /**
     * 错误代码
     */
    code: string;
    /**
     * 错误消息
     */
    message: string;
    /**
     * 详细错误信息
     */
    details?: any;
}
export declare const VideoRecorderErrorCodes: {
    readonly PERMISSION_DENIED: "PERMISSION_DENIED";
    readonly RECORDING_FAILED: "RECORDING_FAILED";
    readonly INVALID_OPTIONS: "INVALID_OPTIONS";
    readonly ALREADY_RECORDING: "ALREADY_RECORDING";
    readonly NOT_RECORDING: "NOT_RECORDING";
    readonly FILE_NOT_FOUND: "FILE_NOT_FOUND";
    readonly STORAGE_ERROR: "STORAGE_ERROR";
    readonly CAMERA_ERROR: "CAMERA_ERROR";
    readonly MICROPHONE_ERROR: "MICROPHONE_ERROR";
    readonly CAPTURE_CANCELLED: "CAPTURE_CANCELLED";
    readonly THUMBNAIL_GENERATION_FAILED: "THUMBNAIL_GENERATION_FAILED";
};
