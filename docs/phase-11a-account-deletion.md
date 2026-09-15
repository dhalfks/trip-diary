# 11-A: 회원 탈퇴

## API

`DELETE /api/v1/users/me`

- 기존 Bearer Access JWT 필수. 본문/사용자 ID 입력 없이 JWT subject의 본인만 삭제한다.
- 정상 결과: `204 No Content`. 계정과 DB 데이터 삭제는 완료되었으며 S3 객체는 삭제를 시도한 상태다. 일부 S3 실패가 있어도 204이며 운영자 후속 처리가 필요하다.
- 인증 없음/잘못된 JWT/이미 삭제된 사용자: 기존 `401 UNAUTHORIZED` 형식.
- DB 삭제 실패: 기존 오류 응답, DB 전체 롤백, S3 미호출. 계정이 유지되므로 다시 시도할 수 있다.
- 새로운 테이블, 마이그레이션, 라이브러리 및 인프라는 추가하지 않는다.

## 처리 순서와 트랜잭션

1. 짧은 DB 트랜잭션에서 본인 User 행을 잠그고 활성 여부를 확인한다.
2. 본인 Trip → TripDay → DiaryEntry 행을 순서대로 잠근다. PostgreSQL FK의 부모 행 잠금과 함께 새 하위 레코드 삽입이 이미지 스냅샷 뒤로 끼어드는 것을 막는다. 이미 인증을 통과한 요청과 충돌하면 DB 트랜잭션이 대기/실패할 수 있으며 실패 시 재시도한다.
3. 본인의 **PENDING/COMPLETED 전체 Image**에서 이미지 ID, StorageType, storage_key만 확보한다.
4. 복구용 대상 목록을 로그에 남긴 뒤 User를 물리 삭제하고 DB FK cascade를 커밋한다.
5. **DB 트랜잭션 밖에서** 확보한 객체 키별로 기존 ObjectStorageService.delete를 호출한다. 한 객체 실패가 다음 객체 삭제를 중단하지 않는다.
6. 실패한 객체에는 재처리 로그를, 전체 처리에는 성공/실패 건수를 남긴다.

DB와 S3는 원자적 트랜잭션이 아니다. 이번 구현은 DB를 먼저 커밋한다. 이렇게 하면 네트워크 장애 동안 DB 잠금을 유지하지 않고, DB 실패 시 원본 사진만 먼저 삭제되는 일을 피하며, 탈퇴 계정의 인증을 즉시 차단할 수 있다. 그 대신 S3 실패/프로세스 종료 후에는 아래 운영 재처리가 필요하다. 자동 재시도 작업이나 영속 큐는 이번 범위에 추가하지 않는다.

## DB 삭제 대상

현재 DB에는 다른 사용자와 여행을 공동 소유하는 공유 구조가 없다. 소유권은 Trip.user_id를 기준으로 한다. 생성 다이어리는 User 직접 FK가 아니라 Trip을 통해 연결된다.

| 테이블 | 삭제되는 정보/관계 |
| --- | --- |
| users | 이메일, 비밀번호 해시, 닉네임, 상태/역할, 생성/수정 시각 |
| refresh_tokens | 해당 사용자의 모든 세션 토큰 해시, 교체/취소/만료 정보 (User FK cascade) |
| trips | 제목, 여행 기간, 시간대 (User FK cascade) |
| trip_days | 여행 날짜 (Trip FK cascade) |
| places | 이름, 주소, 좌표 (Trip FK cascade) |
| itineraries | 일정 제목, 메모, 시간, 장소 연결 (TripDay FK cascade) |
| diary_entries | 제목, 본문, 일정/장소 연결 (TripDay FK cascade) |
| images | 원본 파일명, 키, MIME, 크기, 저장소/업로드 상태 (DiaryEntry FK cascade) |
| travel_diaries | 제목, 템플릿, 대표 이미지, 상태 (Trip FK cascade) |
| diary_pages | 페이지 순서/종류/레이아웃, JSON의 텍스트와 이미지 참조 (TravelDiary FK cascade) |

Flyway 이력은 사용자 데이터가 아니므로 유지한다. 다른 사용자의 레코드와 S3 키는 조회·삭제 대상에 포함하지 않는다. 버킷 전체 목록 삭제/날짜별 대량 삭제는 하지 않는다.

## S3 실패 및 재처리

ObjectStorageService의 기존 정책을 재사용한다. 없는 객체 삭제는 성공으로 취급하며, 권한/네트워크/저장소 종류 불일치는 실패로 기록한다. 사진 바이너리, 공개 URL, Presigned URL 또는 AWS credentials를 로그에 남기지 않는다.

로그 이벤트:

- `ACCOUNT_DELETION_PREPARED`: operation UUID, user UUID, 이미지 수.
- `ACCOUNT_DELETION_OBJECT`: operation, image UUID, StorageType, `keyBase64`. DB 커밋 전에 남기는 대상 목록으로, 도중 프로세스 종료도 조사할 수 있다.
- `ACCOUNT_DELETION_DB_COMMITTED`: DB 커밋 성공.
- `ACCOUNT_DELETION_OBJECT_DELETED`: 해당 객체 삭제 호출 성공.
- `ACCOUNT_DELETION_S3_RETRY`: 재처리할 객체 정보. 예외 메시지/스택은 출력하지 않는다.
- `ACCOUNT_DELETION_FINISHED`: 전체 객체 수와 retryRequired.

`keyBase64`는 로그 줄바꿈 삽입 방지용 인코딩이며 **암호화/익명화가 아니다**. UUID와 키도 제한된 운영자만 접근할 수 있는 연결 가능 정보다. 운영 로그 저장소에 수집·접근제어·보존/파기 정책을 설정해야 한다. 로컬 콘솔 로그만으로는 장애 후 복구를 보장할 수 없다.

운영자의 수동 재처리:

1. `S3_RETRY` 또는 `PREPARED` 이후 완료되지 않은 operation을 찾는다.
2. `PREPARED.user`의 User가 실제 DB에서 삭제되었는지 확인한다. DB 롤백/사용자 존재 시 S3를 삭제하지 않는다. 커밋 직후 프로세스 종료로 `DB_COMMITTED`가 누락될 수 있으므로 DB를 기준으로 확인한다.
3. 같은 operation의 `OBJECT` 목록에서 키를 Base64 UTF-8 디코딩한다. 현재 환경의 bucket/region 설정 및 당시 배포 환경을 대조하고, DB에 해당 storage_type/storage_key가 남아 있지 않은지 확인한다. 소유권이 불명확한 키는 삭제하지 않는다.
4. **확인한 정확한 키만** 해당 Private S3 버킷에서 삭제하고, 객체 부재를 확인한다. 실패 목록이 아닌 전체 버킷/prefix를 일괄 삭제하지 않는다.
5. 재처리 결과를 운영 기록에 남기고, 잔여 객체가 없는 것을 확인한 후 정해진 보존 정책에 따라 대상 로그를 파기한다.

중요한 운영 한계:

- 이미 발급한 Presigned GET/PUT URL은 JWT 삭제만으로 취소되지 않는다. 기본 TTL은 5분, 현재 설정의 최대 TTL은 15분이다. 탈퇴 전 업로드를 중단하고, 발급된 URL 만료와 진행 중 전송 종료 이후 **이 operation의 전체 키**를 다시 확인해야 한다. 삭제 후에도 이미 발급된 PUT으로 같은 키가 다시 생길 수 있다. 이번 단계에서는 업로드 정리 스케줄러를 추가하지 않는다.
- 버전 관리가 활성화된 버킷의 기존 deleteObject는 삭제 마커를 만들 수 있다. 이전 버전/복제본/Object Lock의 실제 제거는 운영자가 버킷 정책과 권한을 확인해 별도로 처리해야 한다. 이번 구현은 기존 저장소 동작을 변경하지 않는다.
- 과거 DB 메타데이터가 먼저 삭제되어 사용자와 연결할 수 없는 고아 객체는 이번 계정별 조회로 찾을 수 없다. 별도 운영 조사 대상이며 임의로 타인 객체를 추정 삭제하지 않는다.

## JWT / Refresh Token

- 기존 `JwtUserAuthenticationConverter`가 요청마다 User 존재와 ACTIVE 상태를 조회한다. User 삭제 후 유효기간이 남은 Access JWT도 보호 API에서 401이다. 별도 blacklist/Redis가 필요 없다.
- 기존 refresh_tokens FK cascade로 모든 기기의 Refresh Token 레코드가 삭제된다. TokenService.rotate의 기존 저장 토큰 조회가 실패하여 `401 INVALID_REFRESH_TOKEN`이 된다.
- 이미 인증을 통과하여 실행 중인 요청의 과거 응답까지 회수하는 기능은 없다. 이후 요청은 차단되며 새 DB 쓰기는 FK/트랜잭션 제약을 받는다.

## Mobile

홈 → 설정 → 회원 탈퇴 → 삭제 범위/복구 불가 안내 → `모든 데이터를 삭제하고 탈퇴` → DELETE → 인증 정보 삭제 → 기존 Stack.Protected가 로그인 스택으로 전환한다. 확인 취소는 API를 호출하지 않는다. 요청 중 중복 클릭/취소를 막고 로딩을 표시하며, 실패 시 확인 화면에서 다시 시도할 수 있다.

성공 시 SecureStore(웹에서는 기존 localStorage)와 메모리 토큰/사용자 정보를 제거한다. 탈퇴 전에 시작된 Refresh 응답 및 사용자 조회가 뒤늦게 세션을 복구하지 않도록 검사한다. SecureStore 삭제 자체가 실패하더라도 메모리 세션은 해제되고 서버 토큰은 이미 차단되지만, 기기 저장소 정리는 별도 확인이 필요하다.

많은 사진/S3 장애로 요청이 기존 클라이언트 제한 시간(15초)을 넘을 수 있다. 이때 화면에는 실패로 보이더라도 DB 탈퇴는 이미 커밋되었을 수 있다. 재시도 시 삭제된 계정의 JWT/Refresh가 401을 반환하면 기존 인증 클라이언트가 세션을 정리하고 로그인 화면으로 이동한다. 서버는 S3 실패 목록을 계속 기록한다.

정책 링크는 실제 법률 문구 없이 다음 환경변수로 준비한다.

```text
EXPO_PUBLIC_PRIVACY_POLICY_URL=
EXPO_PUBLIC_TERMS_URL=
```

빈 값 또는 유효하지 않은 URL이면 `준비 중`을 표시한다. URL 설정 시 자격증명이 없는 HTTPS 주소만 열며, 링크 열기 실패는 안내한다. 실제 공개 문서가 준비되면 `.env`에 URL을 설정하고 Expo 앱을 재시작/재빌드한다. 이 값들은 공개 앱 설정이므로 비밀정보를 넣지 않는다.

## 개인정보와 로그

계정 탈퇴는 위 DB 행과 연결된 S3 원본을 대상으로 한다. 이미 저장된 운영 로그, 기기에 사용자가 따로 저장한 사진/선택 원본, 외부 시스템 사본을 자동으로 지우지 않는다. 현재 앱에서 받은 이미지의 임시 캐시나 파일 시스템 정리는 OS/앱 캐시 정책에 따르며, 원본 사진 앨범을 삭제하지 않는다.

로그 점검 및 최소 변경:

- 인증 요청 DTO는 이메일/닉네임/비밀번호를, JWT 설정 DTO는 signing secret을 문자열 출력에서 가린다. 기존 토큰 응답과 Refresh 요청의 토큰 마스킹은 유지한다.
- 공통 예외 로그는 exception class와 기존 trace ID만 남긴다. SQL 값/서명 URL/비밀을 포함할 수 있는 메시지 및 cause/stack을 출력하지 않는다.
- Spring Web/Security는 INFO, SQL·SDK는 WARN, Hibernate 바인딩/결과 추출/DB 오류 상세 로거는 OFF로 설정한다. DB 오류 메시지는 값 자체를 포함할 수 있어 공통 예외의 안전한 유형 로그로 진단한다.
- 운영자 배포 설정이나 프록시가 DEBUG/TRACE, HTTP body/header, query string을 별도 기록하지 않는지 확인해야 한다. 과거 로그에 이미 기록된 개인정보는 해당 저장소의 접근/보존/파기 정책으로 처리해야 한다.

## 실제 운영 전 추가 확인

DB cascade/잠금은 PostgreSQL 운영 버전에서 스테이징 검증하고, 로그 수집 중단·프로세스 종료·S3 권한 실패에 대한 재처리 절차를 시험한다. S3 버전 관리/복제/보존 설정, 로그 및 기존 백업의 보존·접근·파기 기준도 검토한다. 이번 단계에서 backup/restore, 자동 업로드 정리, rate limiting, Sentry, Redis, AI 기능은 구현하지 않는다.

실제 기기에서는 확인 취소, 삭제 중 상태, 성공 후 뒤로 가기/앱 재시작 시 로그인 유지, JWT 자동 갱신 중 탈퇴, 정책 링크 열기, 네트워크 실패와 재시도를 확인해야 한다. 실제 사용자 계정이 아닌 전용 테스트 계정으로 검증한다.

## 테스트와 변경 파일

Backend는 H2 PostgreSQL 모드, 실제 서명된 테스트 JWT, Mock ObjectStorage를 사용한다. 로컬 실제 PostgreSQL/S3 데이터 삭제는 실행하지 않는다. Mobile은 기존 Node 테스트와 설치된 TypeScript로 상태 모델 및 실제 API 클라이언트를 검증한다. UI 기기 테스트를 대체하지 않는다.

주요 명령: Backend `.\gradlew.bat test`; Mobile `npm test`, `npm run check`, `npx expo-doctor`, `npx expo export --platform android --platform web`.

2026-09-14 실행 결과:

- Backend 전체 95개 통과: 기존 85개 + 탈퇴 통합 8개 + 개인정보 로그 2개. DB 롤백 시 S3 미호출, S3 호출 시 DB 트랜잭션 미유지, 타인 데이터 보존도 확인했다.
- Mobile 전체 60개 통과: 기존 48개 + 계정/정책 링크/API 클라이언트 12개.
- ESLint, TypeScript, Android/Web production export 통과.
- Expo Doctor 20/21. 기존 Expo 관련 16개 패키지의 권장 패치 버전 불일치가 남아 있으며, 이번 단계에서 버전을 변경하지 않았다.
- 실제 PostgreSQL/S3 삭제 및 실제 기기 조작 테스트는 실행하지 않았다.

```text
backend/src/main/java/com/tripdiary/user/AccountDeletionService.java
backend/src/main/java/com/tripdiary/user/UserController.java
backend/src/main/java/com/tripdiary/user/UserRepository.java
backend/src/main/java/com/tripdiary/image/ImageRepository.java
backend/src/main/java/com/tripdiary/auth/AuthController.java
backend/src/main/java/com/tripdiary/auth/JwtProperties.java
backend/src/main/java/com/tripdiary/global/error/GlobalExceptionHandler.java
backend/src/main/resources/application.yml
backend/src/test/java/com/tripdiary/user/AccountDeletionIntegrationTest.java
backend/src/test/java/com/tripdiary/user/AccountPrivacyLoggingTest.java
mobile/src/features/account/account-model.ts
mobile/src/features/auth/auth-context.tsx
mobile/src/lib/api.ts
mobile/src/app/(app)/settings.tsx
mobile/src/app/(app)/home.tsx
mobile/src/app/(app)/_layout.tsx
mobile/.env.example
mobile/package.json
mobile/tests/account.test.mjs
docs/phase-11a-account-deletion.md
```

기존 `mobile/AGENTS.md`의 사용자 변경은 이번 구현에 포함하지 않는다.
