import { ImageManipulator, SaveFormat } from 'expo-image-manipulator';
import type { ImagePickerAsset } from 'expo-image-picker';
import { resizeDimensions, UPLOAD_JPEG_QUALITY } from './photo-resize-policy';

/** Creates a normalized cache file for upload and leaves the device's original photo untouched. */
export async function resizePhotoForUpload(asset: ImagePickerAsset): Promise<ImagePickerAsset> {
  const dimensions = resizeDimensions(asset.width, asset.height);
  if (!dimensions) return asset;

  const context = ImageManipulator.manipulate(asset.uri);
  context.resize(dimensions);
  const rendered = await context.renderAsync();
  const result = await rendered.saveAsync({ compress: UPLOAD_JPEG_QUALITY, format: SaveFormat.JPEG });
  const originalName = asset.fileName || asset.uri.split('?')[0].split('/').pop() || 'photo.jpg';
  return {
    ...asset,
    uri: result.uri,
    width: result.width,
    height: result.height,
    fileName: `${originalName.replace(/\.[^.]*$/, '') || 'photo'}.jpg`,
    mimeType: 'image/jpeg',
    fileSize: undefined,
  };
}
