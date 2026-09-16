#!/usr/bin/env bash
set -Eeuo pipefail

if [[ $# -ne 2 ]]; then
  echo "usage: deploy-staging.sh <ghcr-image-with-40-char-sha-tag> <incoming-env-file>" >&2
  exit 2
fi

image_ref="$1"
incoming_env="$2"
app_dir="${TRIP_DIARY_STAGING_DIR:-$HOME/trip-diary-staging}"
container_name="trip-diary-backend-staging"
previous_name="${container_name}-previous"
runtime_env="$app_dir/backend.env"
host_port="${TRIP_DIARY_STAGING_PORT:-8080}"
health_url="http://127.0.0.1:${host_port}/actuator/health"

if [[ ! "$image_ref" =~ ^ghcr\.io/[a-z0-9._/-]+:[0-9a-f]{40}$ ]]; then
  echo "Refusing image without an immutable commit SHA tag" >&2
  exit 2
fi
if [[ ! -f "$incoming_env" ]]; then
  echo "Incoming staging environment file is missing" >&2
  exit 2
fi

mkdir -p "$app_dir"
install -m 600 "$incoming_env" "$runtime_env"
rm -f "$incoming_env"

docker pull "$image_ref"
docker rm -f "$previous_name" >/dev/null 2>&1 || true

had_previous=false
if docker inspect "$container_name" >/dev/null 2>&1; then
  had_previous=true
  docker stop "$container_name" >/dev/null
  docker rename "$container_name" "$previous_name"
fi

restore_previous() {
  docker rm -f "$container_name" >/dev/null 2>&1 || true
  if [[ "$had_previous" == true ]]; then
    docker rename "$previous_name" "$container_name"
    docker start "$container_name" >/dev/null
  fi
}
trap restore_previous ERR

docker run -d \
  --name "$container_name" \
  --restart unless-stopped \
  --network trip-diary-staging \
  --env-file "$runtime_env" \
  --label "com.tripdiary.commit=${image_ref##*:}" \
  -p "127.0.0.1:${host_port}:8080" \
  "$image_ref" >/dev/null

healthy=false
for _ in $(seq 1 30); do
  if curl --fail --silent --show-error "$health_url" | grep -q '"status":"UP"'; then
    healthy=true
    break
  fi
  sleep 2
done

if [[ "$healthy" != true ]]; then
  echo "Staging health check failed; restoring the previous container" >&2
  false
fi

trap - ERR
docker rm -f "$previous_name" >/dev/null 2>&1 || true
echo "Staging deployment healthy: ${image_ref##*:}"
