# Phase 11-C release checklist

Checked on 2026-09-15 against the current Spring Boot, PostgreSQL, Private S3, and Expo SDK 57 project.

## Release status

| # | Check | Status | Evidence / action |
|---:|---|---|---|
| 1 | Backend environment variables | [완료] | Safe placeholders cover DB, JWT, AWS/S3, Kakao, rate limits, cleanup, and optional Sentry. |
| 2 | Mobile environment variables | [완료] | API, policy/terms, and optional Sentry variables are documented without values. |
| 3 | AWS S3 | [운영 환경에서 필요] | Keep Block Public Access enabled and verify the production bucket/prefix and short signed URL TTL. |
| 4 | IAM | [운영 환경에서 필요] | Use a runtime role or scoped credentials limited to the required bucket prefix/actions. |
| 5 | S3 CORS | [운영 환경에서 필요] | Permit only expected origins, PUT/GET, and required content headers. |
| 6 | S3 Lifecycle | [확인 필요] | Apply expiry only to a proven temporary prefix; never broadly expire completed objects. |
| 7 | Database | [운영 환경에서 필요] | Provision PostgreSQL, restrict network access, and run Flyway through V10 before traffic. |
| 8 | DB backup | [완료] | Local Docker custom-format `pg_dump` succeeded. Schedule encrypted off-host production backups. |
| 9 | DB restore | [완료] | Local restore into a unique temporary DB matched core/Flyway row counts. Schedule restore drills. |
| 10 | JWT secrets | [운영 환경에서 필요] | Generate different strong access/refresh secrets and establish rotation. |
| 11 | Rate limit | [완료] | Per-API environment-controlled limits and common 429 responses work for one server. |
| 12 | Image cleanup | [확인 필요] | Disabled by default; review stale PENDING rows and settings before enabling. |
| 13 | Sentry | [운영 환경에서 필요] | Environment placeholders and safe policy are ready; create projects and verify SDK/source maps in release builds. |
| 14 | Privacy policy | [운영 환경에서 필요] | Publish reviewed legal text and set `EXPO_PUBLIC_PRIVACY_POLICY_URL`. |
| 15 | Terms of service | [운영 환경에서 필요] | Publish reviewed legal text and set `EXPO_PUBLIC_TERMS_URL`. |
| 16 | Android permissions | [완료] | Photo picker purpose is configured; camera/microphone permissions are disabled. Verify generated manifest in EAS build. |
| 17 | iOS permissions | [완료] | Korean photo-library purpose text is configured. Verify generated Info.plist in EAS build. |
| 18 | App icon | [확인 필요] | Current icon/adaptive/monochrome files are Expo starter artwork; replace with approved brand assets. |
| 19 | Splash | [확인 필요] | Current splash is Expo starter artwork; replace it and confirm safe-area rendering. |
| 20 | Physical-device tests | [운영 환경에서 필요] | Run the full journey on current Android and iOS devices. |
| 21 | Network-failure tests | [운영 환경에서 필요] | Verify offline, slow, interrupted PUT, retry, and 429 behavior on devices. |
| 22 | JWT expiry/refresh tests | [완료] | Automated access refresh, deleted-account, and late-refresh regression tests pass. |
| 23 | Image upload/delete tests | [완료] | Backend and mobile metadata, direct PUT, retry, private GET, ownership, and delete tests pass. |
| 24 | Travel diary create/preview tests | [완료] | Template generation, ordering, cover, private images, empty data, navigation, and delete tests pass. |
| 25 | Account deletion tests | [완료] | Confirmation, S3 failure policy, owned-data cascade, token invalidation, and cross-user preservation pass. |

## Sentry decision and safe configuration

No Sentry dependency was added in this phase. The Expo integration installs native code, modifies Metro/app configuration, and needs an organization, project, DSN, source-map token, and a release build to verify symbolication. Those release inputs are not present, so an end-to-end verified minimal integration cannot be completed safely in this repository alone.

When the release project is ready:

1. Upgrade Expo packages to the versions required by `npx expo install --check` and make a clean EAS development build.
2. Create separate Sentry projects for Spring Boot and React Native. Set backend `SENTRY_DSN`; set mobile `EXPO_PUBLIC_SENTRY_DSN`. Store `SENTRY_AUTH_TOKEN` only as a sensitive EAS build variable, never as `EXPO_PUBLIC_*` or in Git.
3. Follow Expo's React Native Sentry wizard, then verify source-map upload in a release build. Add the Spring Boot starter only after pinning a version compatible with the project's Spring Boot release.
4. Disable default PII transmission, request body capture, session replay, and network request/response bodies. Do not attach email, name, JWTs, refresh tokens, AWS credentials, presigned URLs, image bytes, or raw request bodies. Use the existing `traceId`, route template, status, and stable error code as diagnostic context.
5. Capture unhandled server/runtime exceptions. Explicitly capture S3/DB failures and mobile API/upload failures only after sanitizing URLs and headers. Set low production trace sampling and retention appropriate to the privacy policy.

Official setup references: <https://docs.expo.dev/guides/using-sentry/> and <https://docs.sentry.io/platforms/java/guides/spring-boot/>.

## PostgreSQL backup and restore

Use a custom-format dump because it supports validation and selective restore. The examples avoid putting passwords on the command line; supply `PGPASSWORD` through the process environment or use the container's configured PostgreSQL user.

Docker Compose backup from the project root:

```powershell
docker compose exec -T db pg_dump -U trip_diary -d trip_diary -Fc > trip_diary.dump
```

Restore rehearsal into a **new, empty, uniquely named** database:

```powershell
docker compose exec -T db createdb -U trip_diary trip_diary_restore_YYYYMMDD
cmd /c "docker compose exec -T db pg_restore -U trip_diary -d trip_diary_restore_YYYYMMDD --exit-on-error < trip_diary.dump"
docker compose exec -T db psql -U trip_diary -d trip_diary_restore_YYYYMMDD -c "SELECT installed_rank, version, success FROM flyway_schema_history ORDER BY installed_rank;"
```

For a plain SQL backup, use `pg_dump -Fp` and restore with `psql -v ON_ERROR_STOP=1 -f backup.sql`. Before production use, record PostgreSQL versions, encryption, retention, off-host storage, access control, checksums, RPO/RTO, and a scheduled restore drill. Never test restore over the live database. The local rehearsal restored users, trips, images, travel diaries, diary pages, and Flyway history with matching row counts; the temporary database and dump were removed afterward.

## Performance review

- Trip lists already use pagination. Diary entries are scoped to one trip day and images to one entry, so the existing bounded UI requests remain appropriate.
- Generated diary history is capped at five per trip. A diary page count is capped at 500, detail loading fetches its pages together, referenced images are fetched in one `IN` query, and only completed images receive short-lived GET URLs.
- Image selection is capped at ten per picker action. Upload stays direct to S3 and does not buffer image bytes in Spring Boot.
- `V10__index_pending_image_cleanup.sql` adds `(status, created_at, id)` for the scheduled stale-PENDING scan. Existing FK/order indexes cover entry image order and trip diary history.
- Presigned GET creation remains one signer operation per displayed image. Monitor page/image counts and signing latency before introducing batching or caching; do not persist signed or public URLs.

## Security review

- Authentication: protected routes use JWT authentication, refresh checks an active user/token record, and account deletion invalidates persisted refresh tokens and removes the user.
- Authorization: trip, day, entry, image, and generated diary operations resolve ownership through their parent trip. Cross-user and missing-resource cases use the common error response.
- Files: the client sends metadata only; the server validates type/size/name, generates UUID storage keys, and never accepts a caller-selected S3 key. S3 remains private and clients receive short-lived signed PUT/GET URLs.
- Abuse and cleanup: rate limits protect auth, signing/completion, diary generation, and deletion. Cleanup targets only old `PENDING` rows, preserves `COMPLETED`, and retains DB metadata if S3 deletion fails.
- Logging: request bodies, authorization headers, tokens, credentials, image bytes, and full presigned URLs are excluded. Logs contain trace ID, route template, method, status, duration, error code, and limited internal IDs.
- Repository scan: review tracked files before every release for private keys, AWS key patterns, populated secrets, tokens, and DSNs. Treat any finding as compromised and rotate it before release.
- `npm audit --omit=dev` reports 14 moderate findings in Expo CLI/config transitive packages. Its automated fixes propose incompatible major downgrades (for example Expo 46), while Expo Doctor passes 21/21 on the current SDK 57 set. Do not apply `--force`; track upstream SDK fixes and rerun the audit before release.

## Mobile accessibility and state review

Primary settings, account-deletion, image selection/upload/retry/delete, and diary preview controls expose button roles or labels. Progress, success, and failure use visible text and live regions; destructive operations have confirmation and disabled/loading states. Empty image/diary states and 429 retry guidance remain visible text instead of color-only signals. Verify screen-reader order, dynamic text, contrast, switch control, and minimum touch targets on physical Android/iOS devices because automated TypeScript/unit checks cannot prove native accessibility behavior.

## S3 operational checks

Keep bucket Block Public Access enabled. Limit application IAM permissions to the configured bucket prefix and required object actions. Configure CORS only for expected app/web origins, `PUT`/`GET` methods, and required content headers. Keep signed URL TTL short. Review old `PENDING` rows before enabling cleanup. Prefer lifecycle expiration for a dedicated temporary-upload prefix only after confirming key layout; do not apply a broad lifecycle deletion rule to completed objects. Reconcile DB keys against an inventory/report and require human review before deleting orphans.
