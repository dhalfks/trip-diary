# Phase 12-B: Backend CI/CD to staging

This phase adds Backend CI and a staging-only Docker deployment path. It does not create AWS/server resources, deploy production, change production data, configure mobile store builds, or register any real secret.

## CI/CD structure

```text
Pull request touching Backend
  -> checkout -> JDK 17 -> Gradle test/bootJar -> secret-free Docker build

Push to main (or manual workflow dispatch)
  -> the same CI gate
  -> build GHCR image tagged with the full commit SHA
  -> GitHub Environment: staging
  -> create protected runtime env from Environment secrets
  -> verified SSH/SCP to the prepared staging server
  -> replace the Docker container
  -> internal and external /actuator/health checks
```

The workflow is `.github/workflows/backend-staging.yml`. Pull requests never receive a deployment job. Automatic deployment only runs for Backend/deployment changes pushed to `main`. `workflow_dispatch` can deploy the current SHA or redeploy an existing full SHA for a controlled rollback. There is no production job or production environment reference.

## GitHub Actions workflow

The `ci` job uses the Gradle Wrapper on a Linux runner and executes:

```bash
cd backend
chmod +x gradlew
./gradlew test bootJar
docker build --tag trip-diary-backend:ci .
```

Tests use `application-test.yml` with H2 and mocked storage behavior. They do not call a real S3 bucket or production DB. The deploy job cannot start unless CI and the Docker build pass.

## GitHub Environment

Create a GitHub Environment named exactly `staging`. Store staging values at Environment scope so future production values cannot be selected accidentally. Add required reviewers if the repository plan supports them. Do not create a `production` deployment workflow in this phase.

Required Environment secrets:

- `DATABASE_URL`
- `DATABASE_USERNAME`
- `DATABASE_PASSWORD`
- `AWS_ACCESS_KEY_ID`
- `AWS_SECRET_ACCESS_KEY`
- `AWS_REGION`
- `S3_BUCKET`
- `JWT_ACCESS_SECRET`
- `JWT_REFRESH_SECRET`
- `CORS_ALLOWED_ORIGIN_PATTERNS`
- `STAGING_SSH_HOST`
- `STAGING_SSH_USER`
- `STAGING_SSH_PRIVATE_KEY`
- `STAGING_SSH_KNOWN_HOSTS`

Optional Environment secrets:

- `AWS_SESSION_TOKEN` for temporary AWS credentials
- `KAKAO_REST_API_KEY`
- `SENTRY_DSN`
- `STAGING_SSH_PORT` (defaults to 22)

Environment variables, which are configuration rather than secrets:

- `STAGING_HEALTH_URL`: required public HTTPS URL ending in `/actuator/health`
- `S3_PRESIGN_TTL`: optional, defaults to `5m`
- `RATE_LIMIT_ENABLED`: optional, defaults to `true`
- `IMAGE_CLEANUP_ENABLED`: optional, defaults to `false`

The application property is named `CORS_ALLOWED_ORIGIN_PATTERNS`; do not register the similarly named `CORS_ALLOWED_ORIGINS`. Never print, echo, or `cat` secret values in a workflow. The workflow writes them using a non-verbose Python process to a mode-600 file, rejects missing/multiline values, copies it over verified SSH, installs it as mode 600 on the server, and removes the transfer files.

## Docker image and tag policy

`backend/Dockerfile` copies the already tested Spring Boot jar into a Java 17 runtime image and runs as numeric non-root user `10001`. Build arguments and image layers contain no runtime configuration or secret.

Images use this immutable form:

```text
ghcr.io/<owner>/<repository>/backend:<full-40-character-git-sha>
```

The deployment script refuses mutable tags such as `latest`. The running container has the same SHA as a label, so the deployed version is available with:

```bash
docker inspect trip-diary-backend-staging \
  --format '{{ index .Config.Labels "com.tripdiary.commit" }}'
```

Keep previous SHA images in GHCR according to an explicit retention policy. If the GHCR package is private, authenticate the staging server once with a read-only package token or equivalent server credential. Do not put that pull credential in the image.

## Staging server prerequisites

Prepare one Linux server manually with:

- Docker Engine and permission for the deployment SSH user to run Docker
- `curl`, Bash, outbound access to GHCR, staging PostgreSQL, and AWS S3 endpoints
- a reverse proxy/load balancer terminating HTTPS and forwarding to `127.0.0.1:8080`
- ownership of `$HOME/trip-diary-staging` by the deployment user
- a dedicated SSH key and a pinned `known_hosts` entry stored in GitHub Environment secrets
- GHCR pull authentication when the package is private
- firewall rules exposing only SSH and the HTTPS reverse proxy, not port 8080 directly

AWS, DB, DNS, TLS, server, and GHCR package visibility are not created or modified by the workflow. See `phase-12a-backend-staging.md` for staging DB/S3/IAM separation.

## Deployment and health checks

The deploy job builds and pushes the SHA image, creates the staging env file, and invokes `scripts/deploy-staging.sh` over SSH. The script validates the immutable image name, protects the runtime env with mode 600, pulls the image, preserves the current container, and starts:

```text
trip-diary-backend-staging
```

The container binds only to `127.0.0.1:8080`. It runs with `SPRING_PROFILES_ACTIVE=staging`; Flyway migrates the configured staging database and Hibernate validates it. The script polls the local `/actuator/health` for up to 60 seconds. GitHub then checks the external HTTPS `STAGING_HEALTH_URL`. A non-200 response, missing `status=UP`, timeout, startup error, or migration error fails the job.

Container logs retain the existing trace ID, route template, status, duration, and error-code policy. Do not enable Docker command tracing (`set -x`) or request-body/header logging. JWTs, Authorization headers, AWS credentials, DB passwords, presigned URLs, storage secrets/keys, and personal request bodies must remain absent. Inspect operational output with:

```bash
docker logs --since 15m trip-diary-backend-staging
```

Review values rather than copying logs into public issues.

## Rollback

If the new container fails its internal health check, the script removes it and restores the immediately previous container. The workflow remains failed, so an operator must investigate. This automatic container restoration does not reverse Flyway migrations.

For a manual image rollback:

1. Confirm the target full commit SHA and that its application version is compatible with the **current** staging schema.
2. In GitHub Actions choose `Backend CI and staging deployment` -> `Run workflow`.
3. Enter the existing 40-character SHA in `image_sha`.
4. Approve the `staging` Environment deployment if protection rules require it.
5. Confirm both health checks and the running container label.
6. Run a focused staging regression test.

Never rollback by editing a mutable tag. If a migration removed/renamed data or is incompatible with the old application, stop and use the reviewed DB restore/forward-fix plan instead of starting the old image.

## Flyway safety

Staging retains `spring.jpa.hibernate.ddl-auto=validate`; the workflow never uses `create`, `create-drop`, Flyway `clean`, database initialization, or data deletion. Back up staging before a risky migration. Prefer backward-compatible expand/migrate/contract changes so the immediately previous SHA can still run during a deployment rollback. Check `flyway_schema_history` after deployment and do not edit applied migration files.

## Failure response

| Failure | Response |
|---|---|
| Gradle test/build | Fix code or test configuration; no image is pushed and no deployment occurs. |
| Docker build/push | Check GHCR permissions, runner/network, and base-image availability. Do not expose registry tokens. |
| Missing secret | Add the named value to the `staging` Environment; the workflow reports only its name. |
| SSH host verification | Correct the pinned `known_hosts` value after independently verifying the server fingerprint. Never disable strict checking. |
| Container startup/Flyway | Inspect sanitized container logs and DB/Flyway state; the prior container is restored when possible. |
| Internal health failure | Verify port, profile, DB, S3 credential availability, and application logs. |
| External health failure | Check TLS, DNS, reverse proxy, firewall, and routing while the internally healthy container remains available. |
| Bad release after health passes | Manually deploy a schema-compatible prior SHA and run staging regression tests. |

## Mobile connection preparation

No Mobile API value is changed by 12-B. A future staging build can set:

```text
EXPO_PUBLIC_API_BASE_URL=https://staging-api.example.com/api/v1
```

Local and production values remain separate. Store builds and signing remain 12-C work.

## Before production deployment

Add a separately reviewed production workflow/environment only after staging proves image retention, protected approvals, backup/restore, migration compatibility, monitoring, TLS/DNS, capacity, incident response, rollback drills, and secret rotation. Production credentials and resources must never reuse the staging Environment. No production deployment path exists in this phase.
