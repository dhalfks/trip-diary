# Photo uploads, viewing and deletion (9-C / 9-D)

Open a trip's diary and choose **사진 추가** on a saved entry. For a new entry,
save it first, then choose **사진 추가** from the list. Choose up to ten photos per selection; each
must be 1 byte–10 MiB and JPEG, PNG, WebP, HEIC, or HEIF. Unsupported photos show an
error while valid photos continue through the sequential queue.

The picker uses the system photo selection UI, without camera/microphone access.
`expo-image-picker` is added at the SDK 57 compatible version; the already installed
`expo-file-system` becomes an explicit dependency for actual native file metadata.
Preview rendering uses the existing `expo-image` dependency. No AWS SDK is installed.

## Flow

1. Read the selected local file's actual size and type (browser `File` on web).
2. Use the existing JWT API client, including automatic token refresh, to POST only
   `originalFileName`, `contentType`, and `fileSize` to the entry's `images/upload-url`.
3. PUT the raw file to the returned HTTPS URL using XMLHttpRequest and signed
   headers. The transport supplies Host and Content-Length. It never sends the
   API token, cookies, base64, or multipart data. Progress reflects bytes sent.
4. After successful PUT, POST `images/{imageId}/complete`. 100% byte transfer is
   displayed separately from confirmed completion; success requires COMPLETED.

Individual or all unfinished photos can be retried. An unexpired ticket is reused;
PUT 412 is reconciled via completion. After PUT success, retries call only complete.
Expired/rejected attempted tickets are checked through complete before allocating
a replacement, so a lost PUT response does not silently duplicate an upload. When
HEAD is inconclusive (including backend 503), the checkpoint remains for retry;
the backend/IAM issue must be resolved before issuing a replacement safely.

The queue and signed URLs exist only in memory. Stop cancels the current PUT and
leaves unfinished items available for retry. Closing an unfinished queue requires
confirmation; selected files must be chosen again after closing/restarting. No
background upload or durable upload queue is implemented. Upload preview thumbnails
refer only to local selections; the diary's image gallery loads completed images
from the backend.

## Viewing and deletion (9-D)

Each saved diary card lists its completed photos using the authenticated image list
API. `expo-image` reads the returned presigned GET URL directly, without API bearer
headers or persistent image caching. URLs remain in component memory only. The
list refreshes after each successful upload completion, diary refresh, app foreground,
and shortly before URL expiry. Failed thumbnail loads offer an explicit retry
without an endless automatic error loop. Empty entries retain the photo-add button.

Photo deletion requires confirmation and uses the existing authenticated API client.
The image remains visible with a deleting label until the server confirms deletion.
Failures preserve it with error/retry controls. Image-specific 404 reconciles a lost
successful DELETE response, while parent/permission failures stay errors. Late GET
responses cannot restore locally deleted images, and duplicate delete presses are
blocked. No new dependencies or backend binary proxy are introduced for 9-D.

## Checks

- `npm run check`: ESLint and TypeScript.
- `npm run test:images`: Node 24 built-in test runner; no added testing library.
- `npx expo-doctor`: SDK/dependency/config checks.
- `npx expo export --platform android --platform web`: production bundles.

Tests cover metadata validation, progress events, raw upload bodies, retry checkpoints,
expiration, lost responses, conditional PUT, completion failure, and cancellation.
Gallery tests also cover empty/error lists, upload refresh, deletion confirmation
state, storage failure/retry, image-specific 404, access loss, duplicate requests,
out-of-order responses, unmount, and URL refresh timing.
They do not call AWS or a live backend.

## Physical device checks

- Use a backend reachable from the phone via existing `EXPO_PUBLIC_API_BASE_URL`.
  Provision S3 credentials only on the backend. Keep Block Public Access enabled.
- Use Expo Go compatible with SDK 57 or rebuild the development client after native
  dependency/plugin changes. No generated native project is checked in.
- Android/iOS: system picker cancel, limited access, permission errors, multiple
  selection, cloud-backed photos, HEIC/JPEG conversion, orientation, 10 MiB limit.
- Confirm file size/Content-Type match S3 HEAD, smooth progress, and successful
  completion on a real device. Verify airplane-mode failures and manual retries,
  expired URLs, interrupted PUT responses, completion-only failures and retries,
  token refresh, background/foreground transitions, and stop/close behavior.
- Web: browser CORS must be allowed by the private S3 bucket for the actual app
  origin, PUT, Content-Type and If-None-Match (plus any other returned signed
  headers). This change does not mutate S3 CORS or public-access settings.
- Confirm completed photos appear immediately on their diary card; close/reopen the
  screen and verify server-backed images, empty entries, expired GET URL renewal,
  app foreground refresh, loading failures, delete confirmation/cancel, delete
  progress/success, S3 failure retry, and a deletion whose response was lost.
- Backend IAM now needs `s3:DeleteObject` for its image prefix. Browser image fetches
  may also need GET in bucket CORS when using cross-origin image requests. HEIC/HEIF
  rendering depends on the device/browser decoder; unsupported formats show retry
  UI and can still be deleted.

References: [SDK 57 ImagePicker](https://docs.expo.dev/versions/v57.0.0/sdk/imagepicker/),
[React Native networking](https://reactnative.dev/docs/network).
