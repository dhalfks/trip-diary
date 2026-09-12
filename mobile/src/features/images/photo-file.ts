import { File } from 'expo-file-system';
import type { ImagePickerAsset } from 'expo-image-picker';
import { photoMetadata } from './photo-metadata';

export function preparePhoto(asset: ImagePickerAsset) {
  const file = new File(asset.uri);
  if (!file.exists) throw new Error('사진 파일을 찾을 수 없어요. 다시 선택해 주세요.');
  return photoMetadata({ uri: asset.uri, fileName: asset.fileName, mimeType: file.type || asset.mimeType, fileSize: file.size });
}
