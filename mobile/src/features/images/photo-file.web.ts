import type { ImagePickerAsset } from 'expo-image-picker';
import { photoMetadata } from './photo-metadata';

export function preparePhoto(asset: ImagePickerAsset) {
  if (!asset.file) throw new Error('사진 파일을 읽을 수 없어요. 다시 선택해 주세요.');
  return { ...photoMetadata({ uri: asset.uri, fileName: asset.file.name || asset.fileName,
    mimeType: asset.file.type || asset.mimeType, fileSize: asset.file.size }), webFile: asset.file };
}
