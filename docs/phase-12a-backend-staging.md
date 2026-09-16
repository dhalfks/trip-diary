# Phase 12-A: Backend staging

This phase prepares the existing Backend for staging. It does not create cloud resources, change IAM or bucket policy, deploy production, or modify production data.

## Profile separation

| Profile | Purpose | Configuration behavior |
|---|---|---|
| `local` | Developer machine and local Docker PostgreSQL | Default profile. Local DB and JWT fallback values remain available for compatibility. Local CORS origins are allowed. |
| `staging` | Integration and release-candidate verification | DB, AWS credentials, S3 bucket, JWT secrets, and CORS origins are required at startup. Flyway runs and Hibernate only validates. Swagger remains available for staging verification. |
| `production` | Production safety baseline | Same fail-fast requirements as staging. Swagger/OpenAPI are disabled. No local secret or local/open CORS origin is accepted. |
| `prod` | Compatibility alias | Expands to `production`; new deployments should use `production`. |

`application.yml` contains shared non-secret behavior. Profile-specific values live in `application-local.yml`, `application-staging.yml`, and `application-production.yml`. With no active profile Spring uses `local`, preserving the existing developer command.

## Required staging environment variables

Use a deployment secret store or process environment. Do not create or commit a populated staging `.env` file. `backend/staging.env.example` contains names and safe placeholders only.

| Variable | Required | Purpose |
|---|---:|---|
| `SPRING_PROFILES_ACTIVE=staging` | yes | Select staging safeguards. |
| `DATABASE_URL` | yes | JDBC URL for the staging-only PostgreSQL database. |
| `DATABASE_USERNAME` | yes | Staging DB user. |
| `DATABASE_PASSWORD` | yes | Staging DB password. |
| `AWS_ACCESS_KEY_ID` | yes | Scoped staging IAM credential for the current environment-variable provider. |
| `AWS_SECRET_ACCESS_KEY` | yes | Matching secret key. |
| `AWS_SESSION_TOKEN` | only for temporary credentials | Session token; never log it. |
| `AWS_REGION` | yes | Region of the staging bucket. |
| `S3_BUCKET` | yes | Private, staging-only image bucket. |
| `S3_PRESIGN_TTL` | no | Defaults to `5m`; validation permits 1 second through 15 minutes. |
| `JWT_ACCESS_SECRET` | yes | Staging-only signing key, at least 32 characters. |
| `JWT_REFRESH_SECRET` | yes | Different staging-only signing key, at least 32 characters. |
| `CORS_ALLOWED_ORIGIN_PATTERNS` | yes | Explicit HTTPS staging origins separated by commas. `*` and local origins are rejected. |
| `KAKAO_REST_API_KEY` | feature-dependent | Staging Kakao key. |
| `SENTRY_DSN` | optional | Staging monitoring project when Sentry is enabled later. |

Rate-limit and image-cleanup variables from the root `.env.example` remain supported. Cleanup stays disabled unless explicitly enabled after reviewing stale PENDING data.

## Staging PostgreSQL and Flyway

Create a dedicated staging database and least-privilege application user outside this repository. Point the three `DATABASE_*` variables only to that database. On startup Flyway applies every committed migration, then Hibernate `ddl-auto=validate` verifies the resulting schema. The application never creates or drops the staging database.

Back up staging before a migration rehearsal. Review Flyway history after deployment:

```sql
SELECT installed_rank, version, description, success
FROM flyway_schema_history
ORDER BY installed_rank;
```

Database restore and S3 restore are independent. PostgreSQL contains image metadata and S3 keys, while image bytes remain in S3. Restoring only the DB can create references to absent objects; restoring only S3 can create orphans. Account/image deletion after a backup may also be reversed in only one store, so reconcile both stores before exposing a restored environment.

## Staging S3 and IAM

Use a separate private bucket for staging. Never put a production bucket name in `S3_BUCKET` for a staging process. Keep Block Public Access enabled and use only short-lived presigned PUT/GET URLs. Confirm that CORS permits the required staging origins, PUT/GET methods, and signed headers without making objects public.

The staging IAM principal should be restricted to the staging bucket/prefix and the object actions currently used by the Backend. Review permissions manually; this phase does not change IAM policy or create credentials. Do not share credentials between staging and production. If the deployment platform later uses workload/IAM roles, change and test the application's credentials provider deliberately before removing the environment credential requirement.

## Fail-fast production safeguards

Staging and production fail application startup when a required deployment variable is blank. They also reject values containing known local/placeholder markers and reject wildcard, localhost, or loopback CORS origins. JWT property validation separately enforces minimum length and different access/refresh secrets. This prevents the checked-in local defaults from being inherited by deployment profiles.

Production and staging use distinct process environments. `AWS_SESSION_TOKEN`, Kakao, and Sentry are optional only when their corresponding credential type or feature does not require them. Secret values, request bodies, authorization headers, AWS credentials, and full presigned URLs remain excluded from application logs and health responses.

## Health check

The unauthenticated readiness endpoint remains:

```text
GET /actuator/health
```

Expected response is HTTP 200 with `{"status":"UP"}` (plus the configured health groups). `management.endpoint.health.show-details=never` prevents component details, DB connection data, credentials, and secrets from appearing. Use this endpoint for a staging load balancer health check.

## Running staging

Build and test first:

```powershell
cd backend
.\gradlew.bat clean test bootJar
```

Inject the required variables through the shell or deployment secret store, then use one of these commands:

```powershell
$env:SPRING_PROFILES_ACTIVE = 'staging'
.\gradlew.bat bootRun
```

```powershell
$env:SPRING_PROFILES_ACTIVE = 'staging'
java -jar .\build\libs\trip-diary-api-0.0.1-SNAPSHOT.jar
```

Verify without printing configuration:

```powershell
Invoke-RestMethod https://staging-api.example.com/actuator/health
```

Do not pass secrets as command-line arguments because process lists and shell history can expose them.

## Staging deployment checklist

- [ ] Provision a staging-only PostgreSQL database and user; configure backup and restore drills.
- [ ] Provision a staging-only private S3 bucket with Block Public Access.
- [ ] Review scoped IAM and S3 CORS; keep production resources inaccessible.
- [ ] Store all required variables in the hosting platform's secret/environment store.
- [ ] Use `SPRING_PROFILES_ACTIVE=staging`; verify startup fails in a rehearsal when one required value is omitted.
- [ ] Run the full Backend tests and review Flyway V1 through the latest migration.
- [ ] Start the artifact and verify `/actuator/health` returns UP without details.
- [ ] Point a staging Mobile build to the HTTPS staging API and test authentication, refresh, images, diaries, rate limiting, cleanup policy, and account deletion.
- [ ] Review logs for trace IDs and error codes and confirm no tokens, credentials, bodies, or signed URLs appear.
- [ ] Record owner, rollback procedure, monitoring, DNS/TLS, and retention settings outside the repository.

## Production cautions

Nothing in 12-A has been applied to production. Production still requires manually provisioned DB, backups, private S3, IAM, CORS, TLS/domain, runtime secrets, monitoring project, legal URLs, store accounts, and physical-device verification. Do not reuse staging data, buckets, IAM credentials, JWT secrets, Sentry project, or Kakao credentials. Do not run migration or restore commands against production until the backup and rollback plan has been reviewed.
