# 视频缩略图生成功能使用说明

## 概述

为 VideoRecorder 插件新增了 `generateThumbnail` 方法，可以根据视频文件路径生成缩略图。

## 功能特性

- 支持 iOS 和 Android 平台
- 可指定生成缩略图的时间点
- 可调整缩略图质量
- 自动处理视频时长边界
- 返回缩略图文件路径

## 使用方法

### TypeScript 接口

```typescript
import { VideoRecorder } from '@capacitor/video-recorder';

// 生成缩略图 - 支持普通路径
const result = await VideoRecorder.generateThumbnail({
  videoPath: '/path/to/video.mp4',
  timeAt: 2.5,        // 可选，默认 1.0 秒
  quality: 0.8        // 可选，默认 0.8 (0.0-1.0)
});

// 生成缩略图 - 支持 file:// 协议路径
const result2 = await VideoRecorder.generateThumbnail({
  videoPath: 'file:///path/to/video.mp4',
  timeAt: 1.0,
  quality: 0.9
});

console.log('缩略图路径:', result.thumbnailPath);
console.log('原始视频路径:', result.videoPath);
console.log('生成时间点:', result.timeAt);
console.log('质量设置:', result.quality);
```

### 参数说明

#### GenerateThumbnailOptions

| 参数 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| videoPath | string | 是 | - | 视频文件的完整路径，支持普通路径和 `file://` 协议路径 |
| timeAt | number | 否 | 1.0 | 生成缩略图的时间点（秒） |
| quality | number | 否 | 0.8 | 缩略图质量，范围 0.0-1.0 |

#### ThumbnailResult

| 属性 | 类型 | 说明 |
|------|------|------|
| thumbnailPath | string | 生成的缩略图文件路径 |
| videoPath | string | 原始视频文件路径 |
| timeAt | number | 实际生成缩略图的时间点 |
| quality | number | 使用的质量设置 |

## 错误处理

```typescript
try {
  const result = await VideoRecorder.generateThumbnail({
    videoPath: '/path/to/video.mp4'
  });
} catch (error) {
  if (error.code === 'FILE_NOT_FOUND') {
    console.error('视频文件不存在');
  } else if (error.code === 'THUMBNAIL_GENERATION_FAILED') {
    console.error('缩略图生成失败:', error.message);
  }
}
```

## 错误代码

- `FILE_NOT_FOUND`: 视频文件不存在
- `THUMBNAIL_GENERATION_FAILED`: 缩略图生成失败
- `INVALID_OPTIONS`: 参数无效

## 平台差异

### iOS
- 使用 `AVAssetImageGenerator` 生成缩略图
- 支持精确的时间点定位
- 自动处理视频方向

### Android
- 使用 `MediaMetadataRetriever` 生成缩略图
- 支持多种视频格式
- 自动内存管理

## 注意事项

1. 确保视频文件路径正确且文件存在
2. **路径兼容性**：支持两种路径格式
   - 普通路径：`/path/to/video.mp4`
   - file:// 协议路径：`file:///path/to/video.mp4`
3. `timeAt` 参数会自动限制在视频时长范围内
4. 缩略图文件会保存在与视频文件相同的目录下
5. 缩略图文件名格式：`原文件名_thumbnail_时间点s.jpg`
6. 建议在后台线程调用此方法，避免阻塞UI

## 示例场景

### 为录制的视频生成缩略图

```typescript
// 录制完成后生成缩略图
const recordingResult = await VideoRecorder.openRecordingInterface({
  quality: 'high',
  maxDuration: 60
});

// 为录制的视频生成缩略图
const thumbnailResult = await VideoRecorder.generateThumbnail({
  videoPath: recordingResult.videoPath,
  timeAt: 1.0,
  quality: 0.8
});

// 在UI中显示缩略图
const thumbnailElement = document.getElementById('thumbnail');
thumbnailElement.src = `file://${thumbnailResult.thumbnailPath}`;
```

### 批量生成缩略图

```typescript
const videoFiles = ['/path/to/video1.mp4', '/path/to/video2.mp4'];

for (const videoPath of videoFiles) {
  try {
    const result = await VideoRecorder.generateThumbnail({
      videoPath,
      timeAt: 2.0,
      quality: 0.7
    });
    console.log(`缩略图已生成: ${result.thumbnailPath}`);
  } catch (error) {
    console.error(`生成缩略图失败 ${videoPath}:`, error);
  }
}
```

### 路径兼容性示例

```typescript
// 处理来自不同来源的路径
function generateThumbnailForVideo(videoPath: string) {
  // 无论传入的是普通路径还是 file:// 路径，都能正确处理
  return VideoRecorder.generateThumbnail({
    videoPath: videoPath, // 支持 '/path/to/video.mp4' 或 'file:///path/to/video.mp4'
    timeAt: 1.0,
    quality: 0.8
  });
}

// 使用示例
const result1 = await generateThumbnailForVideo('/storage/videos/recording.mp4');
const result2 = await generateThumbnailForVideo('file:///storage/videos/recording.mp4');
```
