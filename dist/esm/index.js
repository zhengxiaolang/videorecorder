import { registerPlugin } from '@capacitor/core';
const VideoRecorder = registerPlugin('CipaceVideoRecorder');
export * from './definitions';
export { VideoRecorder };
// 为了兼容性，也导出一个 MediaCapture 别名
export const MediaCapture = VideoRecorder;
