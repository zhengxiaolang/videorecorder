import { registerPlugin } from '@capacitor/core';

import type { VideoRecorderPlugin } from './definitions';

const VideoRecorder = registerPlugin<VideoRecorderPlugin>('CipaceVideoRecorder');

export * from './definitions';
export { VideoRecorder };

// 为了兼容性，也导出一个 MediaCapture 别名
export const MediaCapture = VideoRecorder;
