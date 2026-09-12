# Image upload, viewing and deletion (9-B / 9-D)

The API registers metadata, signs a PUT, and confirms the uploaded object. No API
accepts image bytes. Existing `ObjectStorageService` methods are unchanged;
`PresignedObjectStorageService` adds optional direct-upload and HEAD capabilities.

## Environment

Export variables into the **backend process** before running `gradlew.bat bootRun`.
The root `.env` is used by Docker Compose for PostgreSQL; Spring Boot does not load
it automatically. Never put credentials in source, application YAML, mobile code,
or committed files.

| Variable | Value |
| --- | --- |
| `AWS_ACCESS_KEY_ID` | Access key for IAM user `trip-diary-s3` |
| `AWS_SECRET_ACCESS_KEY` | Corresponding secret |
| `AWS_SESSION_TOKEN` | Only when using temporary credentials |
| `AWS_REGION` | `ap-northeast-2` (default) |
| `S3_BUCKET` | `trip-diary-images-2026-916912096488-ap-northeast-2-an` |
| `S3_PRESIGN_TTL` | `5m` (default; allowed 1 second–15 minutes) |

Credentials are resolved lazily from environment variables only. Without a bucket
or usable credentials the upload API returns `503 IMAGE_STORAGE_UNAVAILABLE`;
existing endpoints can still run. No real AWS calls run during application startup.

Keep bucket/account Block Public Access enabled and Object Ownership set to Bucket
owner enforced. This code does not change bucket policies, ACLs, or public access
settings and never supplies a public ACL. IAM needs `s3:PutObject` and
`s3:GetObject` (for HEAD) on this bucket's `images/*` objects for the 9-B flow.
`s3:DeleteObject` is required for the image delete API on the same `images/*` resource.
An absent object can produce S3 403
without ListBucket permission; it is treated as storage unavailable rather than
claiming the object is missing.

## APIs

Both endpoints require a bearer access token and ownership of the trip. All nested
trip/day/entry/image IDs are checked before storage access.

Base: `/api/v1/trips/{tripId}/days/{dayId}/entries/{entryId}/images`

1. `POST {base}/upload-url`

   Request: `{"originalFileName":"photo.jpg","contentType":"image/jpeg","fileSize":1234}`

   Returns 201 with `imageId`, `status: PENDING`, `method: PUT`, `uploadUrl`,
   `headers` (header name → array of values), and `expiresAt`. Response caching is
   disabled. Filename extensions must match JPEG, PNG, WebP, HEIC, or HEIF MIME
   types. Size must be 1 byte–10 MiB; paths and control characters are rejected.
   Keys are server-generated `images/{random UUID}` and never use supplied paths.

2. Send the raw file directly to `uploadUrl` using PUT and the returned signed
   headers. Do not send the API bearer token or multipart form data to S3. The body
   must have exactly the registered byte length. HTTP clients normally set Host and
   Content-Length themselves; the latter must match the returned value.

   The signed `If-None-Match: *` header prevents URL reuse from overwriting an
   already uploaded/confirmed object. A retry after a successful PUT can return
   S3 412; call the completion API to confirm the existing upload. If the URL
   expires before upload, register a new upload. Pending cleanup is deferred.
   Browser clients would need bucket CORS allowing their exact origin, PUT, and
   the signed request headers; no browser/mobile UI or CORS policy mutation is
   included here.

3. `POST {base}/{imageId}/complete` (no body)

   HEAD checks the stored key against the registered size and Content-Type before
   persisting `COMPLETED`. Returns 200 `{"imageId":"...","status":"COMPLETED"}`.
   Repeated completion is idempotent. Missing objects return 409
   `IMAGE_UPLOAD_NOT_READY`; metadata mismatch returns 409 `IMAGE_UPLOAD_MISMATCH`;
   storage/credentials failures return 503. All failures preserve `PENDING`, and
   signing failures roll back registration. HEAD checks metadata, not image bytes
   or their actual encoding.

V8 adds a constrained status column; pre-existing V7 metadata is `COMPLETED` for
backward compatibility (it is not retroactively verified in S3).

## Viewing and deletion (9-D)

- `GET {base}` returns only COMPLETED images, ordered by creation time then ID.
  Each object contains `id`, `diaryEntryId`, `originalFileName`, `contentType`,
  `fileSize`, `createdAt`, `downloadUrl`, and `expiresAt`. The GET URL uses the
  existing `S3_PRESIGN_TTL` (default 5 minutes) and is generated on each request.
  Neither URL nor expiration is stored in the database. The API response and signed
  S3 response both disable caching. An empty entry returns `[]` without S3 calls.
- `DELETE {base}/{imageId}` checks ownership and the entire trip/day/entry/image
  hierarchy before storage access. It locks the image row, deletes the S3 object,
  then deletes and flushes the DB row in a transaction. Success returns 204.
  Missing images return 404 `IMAGE_NOT_FOUND`; inaccessible parents use the existing
  `TRIP_DAY_NOT_FOUND` / `DIARY_ENTRY_NOT_FOUND` errors. Storage errors return 503
  `IMAGE_STORAGE_UNAVAILABLE` and preserve DB metadata.
- DeleteObject is already idempotent for absent objects; an explicit NoSuchKey is
  also accepted without a HEAD request. Missing buckets, access denied, and network
  errors are not treated as successful deletion.
- S3 and PostgreSQL do not share an atomic transaction. If S3 deletion succeeds
  but DB flush/commit fails, the row remains and DELETE can be retried safely.
  A repeated DELETE after complete success returns image-specific 404; mobile
  reconciles this as already deleted. Existing record/trip bulk deletion still
  uses its original metadata cascade; bulk S3 cleanup is outside these image APIs.
- These operations address the current key. Bucket versioning retains historical
  versions under normal DeleteObject semantics. Previously issued presigned URLs
  remain subject to their original expiry; deleting a row does not revoke a PUT
  URL. Pending-upload cleanup and revocation are not added here.

## Verification

`gradlew.bat test` uses H2 in PostgreSQL mode with Flyway migrations. API integration
tests mock storage; SDK tests use a real local presigner with fabricated credentials
and a mocked S3 client. They make no AWS uploads or other live AWS requests. The
9-D tests cover listing/empty entries, ownership and nested IDs, delete order,
missing objects/images, storage failures, DB rollback after S3 deletion, retry,
and the register → complete → list → delete flow.

AWS references: [Java presigning](https://docs.aws.amazon.com/sdk-for-java/latest/developer-guide/examples-s3-presign.html),
[conditional PUT](https://docs.aws.amazon.com/AmazonS3/latest/userguide/conditional-writes.html).
