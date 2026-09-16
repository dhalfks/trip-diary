export const MAX_UPLOAD_DIMENSION = 2048;
export const UPLOAD_JPEG_QUALITY = 0.85;

export function resizeDimensions(width: number, height: number, maxDimension = MAX_UPLOAD_DIMENSION) {
  if (!Number.isFinite(width) || !Number.isFinite(height) || width <= 0 || height <= 0 || Math.max(width, height) <= maxDimension) {
    return undefined;
  }
  return width >= height ? { width: maxDimension, height: null } : { width: null, height: maxDimension };
}
