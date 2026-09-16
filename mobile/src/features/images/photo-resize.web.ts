import type { ImagePickerAsset } from 'expo-image-picker';

// Preserve the browser File object used by the direct PUT transport.
export async function resizePhotoForUpload(asset: ImagePickerAsset) {
  return asset;
}
