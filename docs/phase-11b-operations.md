# 11-B 운영 보호와 이미지 정리

## Rate Limiting

별도 라이브러리나 Redis 없이 단일 Spring Boot 프로세스의 고정 시간창 카운터를 사용한다. 회원가입과 로그인은 Servlet의 실제 remote address 기준이며, `X-Forwarded-For` 같은 사용자 입력 헤더를 신뢰하지 않는다. 인증 API는 검증된 JWT subject인 User UUID 기준이다. API 종류별 카운터는 독립적이다.

| 그룹 | 대상 | 기본 제한 |
| --- | --- | --- |
| signup | `POST /api/v1/auth/signup` | IP당 10회/10분 |
| login | `POST /api/v1/auth/login` | IP당 30회/1분 |
| upload-url | `POST .../images/upload-url` | 사용자당 60회/1분 |
| complete | `POST .../images/{imageId}/complete` | 사용자당 120회/1분 |
| diary | `POST /api/v1/trips/{tripId}/diaries` | 사용자당 10회/1분 |
| account | `DELETE /api/v1/users/me` | 사용자당 5회/10분 |

설정:

```text
RATE_LIMIT_ENABLED=true
RATE_LIMIT_MAX_KEYS=20000
RATE_LIMIT_SIGNUP_LIMIT=10
RATE_LIMIT_SIGNUP_WINDOW=10m
RATE_LIMIT_LOGIN_LIMIT=30
RATE_LIMIT_LOGIN_WINDOW=1m
RATE_LIMIT_UPLOAD_URL_LIMIT=60
RATE_LIMIT_UPLOAD_URL_WINDOW=1m
RATE_LIMIT_COMPLETE_LIMIT=120
RATE_LIMIT_COMPLETE_WINDOW=1m
RATE_LIMIT_DIARY_LIMIT=10
RATE_LIMIT_DIARY_WINDOW=1m
RATE_LIMIT_ACCOUNT_LIMIT=5
RATE_LIMIT_ACCOUNT_WINDOW=10m
```

초과 시 기존 ApiErrorResponse 형식으로 `429 Too Many Requests`, code `RATE_LIMIT_EXCEEDED`, `Retry-After` 초 단위 헤더와 `Cache-Control: no-store`를 반환한다. CORS에서도 `Retry-After`를 노출한다. 모바일은 429를 `요청이 너무 많습니다. 잠시 후 다시 시도해 주세요.`로 표시하며 자동 재시도나 JWT refresh를 하지 않는다. 이미지 큐는 제한을 받은 뒤 다음 사진을 연속 전송하지 않고, PUT까지 끝난 항목은 checkpoint를 유지해 재시도 때 complete만 호출한다.

카운터는 매분 만료 항목을 제거한다. 최대 identity/policy 키는 기본 20,000개다. 상한에 도달하면 새 키를 fail-closed로 429 처리하여 활성 카운터를 퇴출해 제한을 우회하거나 메모리가 계속 증가하지 않게 한다. 이 경우 `Retry-After`는 보수적으로 60초다. 재시작 시 카운터는 초기화되며 서버가 여러 대이면 인스턴스마다 별도 제한이다. 다중 서버 운영 전에는 신뢰 가능한 프록시 IP 처리와 Redis/API Gateway 같은 공유 제한기로 교체해야 한다.

## PENDING 이미지 Cleanup

흐름은 upload-url 발급 시 DB `PENDING` 생성 → 클라이언트가 Private S3로 직접 PUT → complete에서 S3 HEAD로 크기/MIME 확인 → `COMPLETED`다. Cleanup은 `created_at < 현재 - pending-age`인 PENDING ID만 오래된 순서로 한 batch 조회한다.

각 후보는 별도 DB 트랜잭션에서 행 잠금을 얻고 상태와 나이를 다시 확인한다. complete API도 같은 이미지 행 잠금을 사용하므로 동시에 실행될 때 둘 중 하나가 끝난 다음 상태를 확인한다. 여전히 오래된 PENDING이면 기존 이미지 삭제 정책과 같이 S3 key 삭제 성공 후 DB metadata를 삭제한다. S3에 객체가 없어도 기존 Storage 구현이 성공으로 처리하므로 PUT하지 않은 요청도 정리된다. PUT 후 complete를 빠뜨린 객체도 같은 방식으로 정리된다.

S3 삭제 실패 또는 storage type 불일치 시 해당 DB 행을 유지하고 `IMAGE_S3_DELETE_FAILED`, `IMAGE_CLEANUP_FAILED`를 남긴다. 다음 주기에 다시 시도할 수 있다. 한 건은 다음 건을 막지 않는다. DB 삭제가 실패했는데 S3가 이미 삭제된 경우에도 metadata가 유지되고 다음 실행의 idempotent delete로 정리된다. `COMPLETED`와 최근 PENDING은 자동 삭제하지 않는다.

기본 설정은 안전하게 비활성화되어 실제 운영 S3를 건드리지 않는다.

```text
IMAGE_CLEANUP_ENABLED=false
IMAGE_CLEANUP_PENDING_AGE=24h
IMAGE_CLEANUP_FIXED_DELAY=1h
IMAGE_CLEANUP_BATCH_SIZE=100
```

최소 pending-age는 1시간으로, 최대 Presigned URL TTL 15분보다 충분히 길다. 최소 주기는 1분, batch는 1~1,000이다. 활성화하면 첫 실행은 fixed-delay 이후 시작한다. 한 프로세스 안에서 중복 실행을 막으며, 한 번에 batch-size까지만 처리한다. 운영 활성화 전에 dry-run SQL로 대상 수·상태·최소/최대 시각을 점검하고, 테스트 전용 PENDING 객체로 IAM `s3:DeleteObject` 권한과 로그를 검증한다.

## S3 고아 객체 정책

DB에 없는 객체는 upload-url 생성 DB 트랜잭션이 실패한 뒤 PUT이 도착하거나, S3 삭제 뒤 DB 롤백/반대 순서의 수동 삭제, 회원 탈퇴 중 S3 실패, 프로세스 종료, 과거 수동 SQL 삭제, 이미 발급된 PUT URL의 늦은 사용으로 생길 수 있다.

이번 단계는 버킷 전체 scan/delete를 구현하거나 활성화하지 않는다. 안전한 수동 점검은 다음과 같다.

1. Private 버킷의 `images/` inventory 또는 read-only 목록을 별도 파일로 취득한다.
2. DB의 모든 `(storage_type, storage_key)`를 read-only로 내보낸다.
3. bucket, region, prefix를 확인하고 두 목록의 차이를 만든다. 업로드가 진행 중일 수 있으므로 최소 24시간 이상 된 객체만 후보로 둔다.
4. 회원 탈퇴 및 Cleanup 실패 로그, 배포/장애 시간, S3 Versioning/Object Lock/복제 상태를 대조한다.
5. 다른 사용자와 연결되지 않은 정확한 key임을 재확인하고 작은 batch로 삭제한 뒤 객체 부재와 앱 회귀를 확인한다. prefix나 날짜만으로 대량 삭제하지 않는다.

Lifecycle은 앱의 정상 완료 이미지와 고아 객체가 모두 같은 `images/` prefix를 사용하므로 현재 객체 전체 만료 규칙을 적용하면 안 된다. 현재 PUT은 단일 putObject이므로 multipart abort 규칙이 이 업로드 흐름의 PENDING 객체를 제거하지도 않는다. 향후 multipart를 도입할 경우 AWS의 `AbortIncompleteMultipartUpload` 규칙을 별도 검토한다. Versioning을 켠 버킷은 current expiration만으로 실제 바이트가 없어지지 않을 수 있어 noncurrent version/expired delete marker 정책과 보존 요구를 함께 검토한다. Lifecycle 적용 전 예상 대상과 비용, 복구·법적 보존 정책을 운영자가 승인해야 한다.

## 운영 로그

기존 traceId를 유지하며 실패 응답은 WARN으로 다음만 남긴다.

```text
HTTP_REQUEST method=POST path=/api/v1/auth/login status=429 durationMs=4 code=RATE_LIMIT_EXCEEDED
```

path는 Spring의 route template만 사용한다. raw URL, query string, request/response body, Authorization/Cookie header는 기록하지 않는다. 정상 요청은 DEBUG라 기본 INFO 운영 로그에는 쌓이지 않는다. 주요 에러는 GlobalExceptionHandler/SecurityConfig가 request attribute에 안전한 ErrorCode를 넣고, 필터가 status·duration과 함께 출력한다. 매핑 전 404 등은 `<unmapped>`와 `UNCLASSIFIED`로 남겨 raw path 노출을 피한다.

Cleanup은 실행 UUID, 후보/삭제/skip/실패 수와 Image UUID, 안전한 ErrorCode만 기록한다. S3 key, 원본 파일명, 예외 메시지/stack, Presigned URL은 기록하지 않는다. 회원 탈퇴의 기존 복구 로그는 11-A 정책대로 제한된 운영자 접근이 필요하다.

다음 값은 절대 로그에 남기지 않는다: password, Access/Refresh JWT, AWS Access Key/Secret/Session Token, Authorization header, 전체 Presigned URL, 요청 body, 이메일·닉네임·파일명 같은 불필요한 개인정보, 이미지 바이너리. Web/Security는 INFO, AWS SDK는 WARN, Hibernate SQL은 WARN이며 bind/extract/error 상세는 OFF다. 프록시·클라우드 로드밸런서·컨테이너 플랫폼에서 별도 access/body/header logging이 켜져 있지 않은지도 운영자가 확인해야 한다.

## 운영 전 수동 확인

- 실제 트래픽에 맞춰 그룹별 limit/window와 `RATE_LIMIT_MAX_KEYS`를 조정하고 429율을 관찰한다.
- 프록시 뒤 실제 client IP를 신뢰할 수 있게 전달/검증하는 인프라 정책을 마련한다. 현재 앱은 spoof 가능한 forwarded header를 사용하지 않는다.
- 단일 서버를 여러 대로 확장하기 전에 분산 Rate Limit으로 교체한다.
- Cleanup은 기본 OFF다. 대상 SQL, 테스트 객체, 삭제 IAM 권한, 장애/재시도 로그를 스테이징에서 검증한 뒤 ON으로 바꾼다.
- S3 Inventory와 DB key 대조 절차를 만들고 수동 삭제는 승인된 작은 batch로 실행한다.
- Lifecycle은 현재 `images/`의 정상 사진을 만료시키지 않도록 유지한다. multipart, Versioning, Object Lock, 복제를 사용할 때만 각각의 정책을 별도 검토한다.
- 로그 수집기의 접근권한, 보존기간, 마스킹 및 429/Cleanup 실패 알림을 설정한다. Sentry/새 인프라/자동 orphan scanner는 11-B 범위에 포함하지 않았다.

## 검증

Backend는 H2 PostgreSQL mode와 Mock Storage로 Rate Limit, 설정 override, 메모리 상한/만료/동시성, API 그룹 분리, 429 응답, 오래된·최근·COMPLETED 이미지, PUT 유무, S3/DB 실패, 반복 실행, batch, complete 경쟁, 운영 로그와 secret 비노출을 검사한다. 실제 운영 DB/S3 삭제는 수행하지 않는다.

Mobile은 기존 API error 처리와 업로드 checkpoint를 이용해 429 문구, 자동 refresh/retry 없음, binary 미전송, PUT 완료 후 complete-only retry를 검사한다. 새 라이브러리는 없다.

2026-09-15 실행 결과:

- 11-B Backend 신규 테스트 19개 통과: Rate Limiter 단위 5개, API 통합 5개, PENDING Cleanup 통합 7개, 운영 로그 2개.
- Backend 전체 114개 통과(11-A 포함), 실패/오류 0개.
- Mobile 전체 63개 통과. 11-B에서 upload-url/complete 429와 공통 API 429 처리 테스트 3개를 추가했다.
- Mobile ESLint와 TypeScript 검사, Android/Web production export 통과.
- 추가 라이브러리, DB migration, Redis 또는 외부 인프라는 없다.
- 실제 운영 PostgreSQL/S3 데이터 삭제나 버킷 전체 스캔은 실행하지 않았다. Cleanup 기본값은 OFF다.

11-B 신규 파일:

```text
backend/src/main/java/com/tripdiary/operations/RateLimitProperties.java
backend/src/main/java/com/tripdiary/operations/InMemoryRateLimiter.java
backend/src/main/java/com/tripdiary/operations/RateLimitExceededException.java
backend/src/main/java/com/tripdiary/operations/RateLimitInterceptor.java
backend/src/main/java/com/tripdiary/operations/OperationsConfig.java
backend/src/main/java/com/tripdiary/image/ImageCleanupProperties.java
backend/src/main/java/com/tripdiary/image/ImageCleanupService.java
backend/src/main/java/com/tripdiary/image/ImageCleanupScheduler.java
backend/src/test/java/com/tripdiary/operations/InMemoryRateLimiterTest.java
backend/src/test/java/com/tripdiary/operations/RateLimitIntegrationTest.java
backend/src/test/java/com/tripdiary/operations/OperationLoggingTest.java
backend/src/test/java/com/tripdiary/image/ImageCleanupIntegrationTest.java
docs/phase-11b-operations.md
```

11-B 수정 파일:

```text
backend/src/main/java/com/tripdiary/auth/SecurityConfig.java
backend/src/main/java/com/tripdiary/global/error/ErrorCode.java
backend/src/main/java/com/tripdiary/global/error/GlobalExceptionHandler.java
backend/src/main/java/com/tripdiary/global/web/RequestTraceIdFilter.java
backend/src/main/java/com/tripdiary/image/ImageRepository.java
backend/src/main/java/com/tripdiary/image/ImageService.java
backend/src/main/java/com/tripdiary/image/ImageUploadService.java
backend/src/main/resources/application.yml
backend/src/test/java/com/tripdiary/health/HealthControllerTest.java
backend/src/test/resources/application-test.yml
mobile/src/features/images/image-upload-modal.tsx
mobile/src/features/images/types.ts
mobile/src/features/images/upload-flow.ts
mobile/src/lib/api.ts
mobile/tests/account.test.mjs
mobile/tests/image-upload.test.mjs
```

작업 트리에는 이전 11-A 파일과 사용자의 기존 `mobile/AGENTS.md` 변경도 남아 있다. 위 목록은 11-B 범위만 구분한 것이다.
