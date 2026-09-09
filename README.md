# Trip Diary

Trip Diary is a mobile travel journal project with an Expo client and a Spring Boot REST API.

## Prerequisites

- Java 17 or later
- Node.js 20.19 or later
- Docker Desktop or another Docker Compose-compatible runtime

## Repository layout

- `backend`: Spring Boot, Spring Data JPA, PostgreSQL
- `mobile`: React Native, Expo, TypeScript, Expo Router
- `compose.yaml`: local PostgreSQL

## Run locally

1. Copy `.env.example` to `.env` and change the local password if needed.
2. Start PostgreSQL with `docker compose up -d postgres`.
3. Start the API with `cd backend` and `gradlew.bat bootRun` on Windows (`./gradlew bootRun` on macOS/Linux).
4. Install and start the app with `cd mobile`, `npm install`, and `npm start`.

The API is available at `http://localhost:8080`, and its public health endpoint is
`GET http://localhost:8080/api/v1/health`.

In non-production profiles, OpenAPI JSON is available at `http://localhost:8080/api-docs`
and Swagger UI at `http://localhost:8080/swagger-ui.html`. Both are disabled by the
`prod` profile.

## Authentication endpoints

- `POST /api/v1/auth/signup`
- `POST /api/v1/auth/login`
- `POST /api/v1/auth/refresh`
- `POST /api/v1/auth/logout`
- `GET /api/v1/users/me`
- `PATCH /api/v1/users/me`

Access tokens expire after 15 minutes. Refresh tokens expire after 30 days, are
rotated whenever used, and are stored in the database only as SHA-256 hashes.
Production must provide different secrets of at least 32 characters through
`JWT_ACCESS_SECRET` and `JWT_REFRESH_SECRET`.

## Trip endpoints

- `POST /api/v1/trips`
- `GET /api/v1/trips?page=0&size=20`
- `GET /api/v1/trips/{tripId}`
- `PUT /api/v1/trips/{tripId}`
- `DELETE /api/v1/trips/{tripId}`

Trip dates are inclusive and automatically create one `trip_days` row per date.
Trips are limited to 366 days and require an IANA timezone such as `Asia/Seoul`.

## Verify

```text
cd backend
./gradlew test

cd ../mobile
npm run check
```

Do not commit `.env` files or production credentials.

## Mobile authentication

The Expo app stores access and refresh tokens in `expo-secure-store` on iOS and Android.
It restores the session at startup and performs one synchronized refresh when an API request
returns `401`. Copy `mobile/.env.example` to `mobile/.env` and choose the API host for the
target device before starting Expo.
