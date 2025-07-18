var capacitorVideoRecorder = (function (exports, core) {
    'use strict';

    // 错误代码常量
    const VideoRecorderErrorCodes = {
        PERMISSION_DENIED: 'PERMISSION_DENIED',
        RECORDING_FAILED: 'RECORDING_FAILED',
        INVALID_OPTIONS: 'INVALID_OPTIONS',
        ALREADY_RECORDING: 'ALREADY_RECORDING',
        NOT_RECORDING: 'NOT_RECORDING',
        FILE_NOT_FOUND: 'FILE_NOT_FOUND',
        STORAGE_ERROR: 'STORAGE_ERROR',
        CAMERA_ERROR: 'CAMERA_ERROR',
        MICROPHONE_ERROR: 'MICROPHONE_ERROR',
        CAPTURE_CANCELLED: 'CAPTURE_CANCELLED',
    };

    const VideoRecorder = core.registerPlugin('CipaceVideoRecorder');
    // 为了兼容性，也导出一个 MediaCapture 别名
    const MediaCapture = VideoRecorder;

    exports.MediaCapture = MediaCapture;
    exports.VideoRecorder = VideoRecorder;
    exports.VideoRecorderErrorCodes = VideoRecorderErrorCodes;

    return exports;

})({}, capacitorExports);
//# sourceMappingURL=plugin.js.map
