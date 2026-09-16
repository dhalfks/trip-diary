# Phase 12-D: 최종 Production 출시 준비

점검일: 2026-09-16. 이 문서는 코드와 로컬 검증 결과를 기록한다. Production 서버, DB, S3, IAM, DNS, EAS, Play Console 계정에는 접근하지 않았으며 실제 배포·빌드·제출을 수행하지 않았다.

## 출시 상태표

| # | 항목 | 상태 | 확인 결과와 남은 작업 |
|---:|---|---|---|
| 1 | Production Backend | [완료] | `production` profile은 필수 환경변수 누락과 local/placeholder 값을 거부하고 Swagger를 끈다. 실제 서버 배포는 미실행이다. |
| 2 | Production DB | [미완료] | 별도 PostgreSQL, 네트워크 제한, 사용자 권한, 암호화, backup/restore 정책을 운영자가 준비하고 staging과 다른 자원인지 확인해야 한다. |
| 3 | Production S3 | [미완료] | 별도 Private bucket, Block Public Access, CORS, versioning/retention을 운영자가 확인해야 한다. staging bucket과 같은 이름을 사용하면 안 된다. |
| 4 | IAM | [사람 확인 필요] | production bucket/prefix의 필요한 object 작업만 허용하는지 검토한다. 이번 단계는 정책·credential을 생성하거나 변경하지 않았다. |
| 5 | JWT Secret | [사람 확인 필요] | access/refresh secret은 각각 32자 이상이며 서로 달라야 한다. production secret store에서만 관리하고 값을 출력하지 않는다. |
| 6 | HTTPS/DNS | [미완료] | production API DNS, 유효한 TLS 인증서와 HTTPS endpoint가 필요하다. |
| 7 | Health Check | [완료] | `/actuator/health`, HTTP 200, `status=UP`, `show-details=never` 구성이 있다. 실제 production 응답 확인은 미실행이다. |
| 8 | GitHub Actions | [사람 확인 필요] | CI와 staging SHA 배포·rollback 구조는 있다. production Environment, 승인 규칙과 서버가 없으므로 production 배포 job은 활성화하지 않았다. |
| 9 | EAS | [미완료] | CLI는 로그인되어 있지 않고 Expo project 연결도 확인할 수 없다. 계정 소유자가 로그인·연결해야 한다. |
| 10 | Android signing | [미완료] | EAS managed credential을 사용할 준비만 됐다. 기존 credential 확인 또는 최초 생성을 사람이 승인해야 한다. |
| 11 | AAB | [미완료] | production profile은 AAB와 원격 versionCode 증가로 설정됐다. 실제 cloud build 산출물은 없다. |
| 12 | Play Console | [미완료] | 앱 생성, 내부 테스트 트랙, store listing, 콘텐츠 등급과 앱 접근 설명이 필요하다. |
| 13 | Data Safety | [사람 확인 필요] | 아래 코드 기준 초안을 실제 운영·외부 서비스·보존 정책과 대조해 Console에 입력해야 한다. |
| 14 | Privacy Policy | [미완료] | production `EXPO_PUBLIC_PRIVACY_POLICY_URL`에 실제 공개 HTTPS URL을 등록해야 한다. 누락 시 production config가 실패한다. |
| 15 | Terms | [미완료] | production `EXPO_PUBLIC_TERMS_URL`에 실제 공개 HTTPS URL을 등록해야 한다. 누락 시 production config가 실패한다. |
| 16 | App screenshots | [미완료] | 실제 release 후보 기기에서 Play 규격으로 촬영해야 한다. |
| 17 | 실제 Android 기기 QA | [미완료] | 아래 체크리스트를 실제 production 후보 APK/AAB로 수행하지 않았다. |
| 18 | 장애 대응 | [사람 확인 필요] | 담당자, 알림 채널, 로그 접근, incident severity, S3/DB 장애 절차와 사용자 공지를 정해야 한다. |
| 19 | Rollback | [완료] | commit SHA Docker image 재배포, health 확인, DB migration 별도 판단 절차가 문서화됐다. production에서 drill은 미실행이다. |
| 20 | 출시 후 모니터링 | [사람 확인 필요] | health, 5xx/429, 인증 실패, S3 오류, cleanup 실패, DB 연결, 자원 사용량 경보와 보존 정책을 설정해야 한다. |

## Production Backend

실행 profile은 `SPRING_PROFILES_ACTIVE=production`이다. `backend/production.env.example`은 이름만 제공하며 runtime 파일이 아니다. 실제 값은 production secret store에 넣는다.

필수 변수는 `DATABASE_URL`, `DATABASE_USERNAME`, `DATABASE_PASSWORD`, `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY`, `AWS_REGION`, `S3_BUCKET`, `JWT_ACCESS_SECRET`, `JWT_REFRESH_SECRET`, `CORS_ALLOWED_ORIGIN_PATTERNS`이다. 임시 AWS credential이면 `AWS_SESSION_TOKEN`도 필요하다. `KAKAO_REST_API_KEY`, `SENTRY_DSN`은 기능 사용 여부에 따라 선택한다.

요구사항의 `CORS_ALLOWED_ORIGINS`에 대응하는 실제 프로젝트 변수명은 `CORS_ALLOWED_ORIGIN_PATTERNS`이다. production은 빈 값, `*` 단독 허용, localhost, `127.0.0.1`, `0.0.0.0`을 거부한다. 허용할 실제 HTTPS 웹 origin만 쉼표로 지정한다.

Hibernate는 `ddl-auto=validate`, Flyway는 `enabled=true`다. `create`, `create-drop`, `Flyway clean`, 자동 DB 초기화는 사용하지 않는다. Swagger와 API docs는 production에서 비활성화된다.

## Production DB, S3와 IAM

코드만으로 staging과 production 자원이 실제 분리됐는지 확인할 수 없다. 배포 전에 두 환경의 DB host/database name, S3 account/bucket, IAM principal을 값 자체를 로그에 남기지 않는 방식으로 교차 확인한다.

Production PostgreSQL은 staging과 별도 instance/database 및 최소 권한 application user를 사용한다. 암호화, 자동 backup, 보존 기간, off-site 사본, RPO/RTO와 격리된 restore drill을 정한다. 배포 전 `flyway_schema_history`를 확인하며 적용된 migration을 수정하거나 삭제하지 않는다.

Production S3는 별도 Private bucket과 Block Public Access를 유지한다. Mobile에는 AWS credential이 없으며 Backend가 짧은 presigned PUT/GET URL만 발급한다. IAM은 production bucket/prefix의 현재 애플리케이션에 필요한 object 조회·쓰기·삭제만 허용하는지 사람이 검토한다. 이 저장소는 IAM 정책이나 bucket 설정을 변경하지 않는다.

## Backend 배포와 Health Check

현재 GitHub Actions는 test → bootJar → secret 없는 Docker build → GHCR commit SHA image → staging SSH 배포 → 내부/외부 health check를 제공한다. Production 서버·GitHub Environment·승인 규칙이 없으므로 staging workflow를 production으로 복제하거나 실행하지 않았다.

Production을 준비할 때 기존 검증된 Docker image와 배포 스크립트 동작을 재사용하되 별도 GitHub `production` Environment, 필수 reviewer, production 전용 SSH/secret, HTTPS health URL을 설정하고 변경 내용을 review한다. staging secret과 서버 경로를 재사용하지 않는다.

배포 후 외부에서 `GET https://<production-api>/actuator/health`가 HTTP 200과 `{"status":"UP"}`만 반환하는지 확인한다. 응답과 로그에 DB password, AWS credential, JWT, authorization header, presigned URL, 내부 exception 상세가 없어야 한다.

## Mobile Production 설정

EAS `production` Environment에 다음 공개값을 등록한다.

- `EXPO_PUBLIC_API_BASE_URL`: 실제 production Backend의 HTTPS `/api/v1` 주소
- `EXPO_PUBLIC_PRIVACY_POLICY_URL`: 공개된 개인정보처리방침 HTTPS 주소
- `EXPO_PUBLIC_TERMS_URL`: 공개된 이용약관 HTTPS 주소
- `EXPO_PUBLIC_SENTRY_DSN`: 선택 사항이며 DSN을 사용하는 경우에만 등록

일반적인 문서의 `PRIVACY_POLICY_URL`, `TERMS_OF_SERVICE_URL`에 대응하는 실제 모바일 변수명은 위의 `EXPO_PUBLIC_...` 이름이다. 공개 번들 값이므로 여기에 secret을 넣지 않는다. Production config는 API가 없거나 HTTP/local 주소인 경우, 정책·약관 URL이 없거나 공개 HTTPS가 아닌 경우 실패한다.

## EAS, signing과 AAB

현재 `eas whoami` 결과는 `Not logged in`이었다. 계정 소유자가 `mobile` 디렉터리에서 다음을 수행한다.

```bash
npx eas-cli login
npx eas-cli init
npx eas-cli config --platform android --profile production
npx eas-cli credentials --platform android
npx eas-cli build --platform android --profile production
```

`init`은 기존 Expo project가 연결되지 않은 경우에만 실행하고 기존 project가 있으면 그 project를 선택한다. `credentials`에서는 기존 Android signing이 있으면 그대로 유지한다. 없다면 계정 소유자가 EAS managed credential 생성을 명시적으로 승인한다. keystore를 Git에 넣거나 기존 key를 삭제·교체하지 않는다. 성공한 production build 산출물은 `.aab`이며 Git에 커밋하지 않는다.

## Google Play Console 준비

- App name: `Trip Diary`
- Application ID: `com.tripdiary.app`
- Version: `1.0.0`
- 초기 local versionCode: `1`; EAS remote auto-increment 사용

짧은 설명, 전체 설명, 카테고리, 연락처, 실제 아이콘/feature graphic/휴대전화 스크린샷, 개인정보처리방침 URL, 이용약관 URL, 콘텐츠 등급, 광고 여부, 앱 접근 지침은 아직 확정하지 않았다. 추측값을 넣지 않는다. AAB는 공개 출시 전에 내부 테스트 트랙에서 설치·로그인·업데이트를 검증한다.

## Data Safety 코드 기준 초안

| 데이터 | 사용 목적 | 저장 위치 | 삭제 가능 여부 |
|---|---|---|---|
| 이메일, 닉네임, password hash | 계정 생성·인증·화면 표시 | PostgreSQL `users`; 원문 비밀번호는 저장하지 않음 | 회원 탈퇴 시 사용자 레코드 삭제 |
| refresh token hash, 발급·만료·폐기 상태 | 로그인 유지와 token rotation | PostgreSQL refresh token 테이블 | 로그아웃/회전/회원 탈퇴 정책에 따라 폐기·삭제 |
| 여행 제목, 날짜, timezone | 여행 관리 | PostgreSQL Trip/TripDay | 여행 삭제 또는 회원 탈퇴 시 삭제 |
| 일정 제목·메모, 장소명·주소 | 일정과 장소 관리 | PostgreSQL Itinerary/Place | 여행 삭제 또는 회원 탈퇴 시 삭제 |
| 기록 제목·본문 | 여행 기록과 다이어리 생성 | PostgreSQL DiaryEntry 및 생성된 DiaryPage JSON | 여행/다이어리 삭제 또는 회원 탈퇴 시 삭제 |
| 사진 원본, 파일명·MIME·크기·storage key | 사진 업로드·표시·다이어리 구성 | 원본은 Private S3, metadata는 PostgreSQL | 사진/여행/계정 삭제 흐름에서 삭제 시도; 실패는 운영 재처리 필요 |
| 생성 다이어리 제목·template·페이지 layout JSON | 규칙 기반 다이어리 미리보기 | PostgreSQL TravelDiary/DiaryPage | 다이어리/여행/계정 삭제 시 삭제 |
| traceId, method, path, status, 처리시간, 제한된 오류 코드 | 장애 분석·운영 보안 | 애플리케이션 로그/운영 로그 수집기 | 실제 retention과 삭제 절차는 운영자가 확정해야 함 |
| IP 또는 인증 사용자 식별값의 메모리 rate-limit key | 남용 방지 | 단일 Backend process memory, 만료 후 정리 | 만료 시 자동 제거; 서버 재시작 시 소멸 |

Kakao 장소 검색과 선택적 Sentry 사용 시 외부 처리 범위, production 로그 수집기, backup 보존·삭제, S3 versioning/replication은 코드만으로 확인할 수 없다. 실제 운영 설정과 각 제공자의 Data Safety 요구사항을 사람이 검토한다. JWT 원문, AWS credential, DB password, presigned URL 전체와 사진 바이너리는 운영 로그 대상이 아니다.

회원 탈퇴는 계정과 소유 DB 데이터를 삭제하고 연관 S3 객체 삭제를 시도한다. DB와 S3는 원자적이지 않으므로 S3 실패 로그와 11-A의 수동 재처리 절차가 필요하다. backup과 운영 로그의 보존·파기 정책은 별도로 고지해야 한다.

## Android 권한 확인

앱 config는 `expo-image-picker`의 사진 선택 기능을 사용하고 `cameraPermission: false`, `microphonePermission: false`로 CAMERA와 RECORD_AUDIO를 차단한다. 위치, 연락처, 전화, SMS 권한을 직접 추가하지 않았다. 최종 AAB 생성 후 Play Console의 App Bundle Explorer 또는 분석된 manifest에서 이 상태를 다시 확인한다.

## 실제 Android 기기 QA

아래 항목은 모두 **미실행**이며 release 후보를 내부 테스트 트랙으로 설치해 사람이 확인한다.

- 인증: 회원가입, 로그인, 로그아웃, 앱 재실행, JWT 자동 갱신, 회원 탈퇴
- 여행: 여행 생성, 일정 생성, 장소 추가, 기록 작성·수정·삭제
- 이미지: 사진 권한, 1장/여러 장, 선택당 10장 제한, 진행률, 실패·재시도, HEIC 변환/리사이즈, 가로 목록과 개별 삭제
- 다이어리: CLASSIC/PHOTO, 대표 이미지, 이미지 없는 여행, 긴 글, Preview 이전/다음, 재생성, 삭제
- 네트워크: 업로드 중 끊기, 재연결, presigned URL 만료, 앱 background/foreground 복귀
- 기기: Android 13 이상 Photo Picker, 작은/큰 화면, 회전 제한, 저속 네트워크, 앱 업데이트 설치

## Rollback

1. 실행 중 컨테이너의 `com.tripdiary.commit` label과 배포 기록으로 현재 SHA를 확인한다.
2. 오류율, health, migration 상태와 영향 범위를 확인하고 필요하면 신규 트래픽을 중단한다.
3. GHCR에서 검증된 직전 40자리 commit SHA image를 선택한다. mutable tag를 사용하지 않는다.
4. 기존 배포 절차로 이전 SHA를 재배포한다.
5. 내부와 외부 `/actuator/health`가 모두 UP인지 확인한다.
6. secret을 제외한 로그와 핵심 API smoke test를 확인한다.
7. incident 기록에 원인, SHA, migration, 복구 시각을 남긴다.

컨테이너 rollback은 Flyway migration을 되돌리지 않는다. 이전 binary가 현재 schema와 호환되는지 먼저 확인한다. 적용된 migration 파일을 삭제·수정하거나 `Flyway clean`을 실행하지 않는다. 호환되지 않으면 검토된 forward fix 또는 검증된 backup을 새 격리 DB에 restore하는 절차를 사용한다.

## iOS 후속 작업

iOS Bundle Identifier는 현재 `com.tripdiary.app`이다. Apple Developer 계정과 App Store Connect 앱을 계정 소유자가 준비해야 한다. Distribution Certificate와 Provisioning Profile은 EAS managed credentials 또는 조직 정책에 따라 생성하되 기존 자격증명을 임의로 교체하지 않는다. EAS iOS production build, TestFlight, 실제 기기 QA, App Review 제출은 별도 단계이며 이번 작업에서는 실행하지 않았다.

## 출시 판정

코드와 로컬 build 설정은 검증 가능하지만 실제 production 인프라, 법률 URL, EAS project/signing, AAB, Play Console, 내부 테스트가 준비되지 않았다. 따라서 현재 상태는 **출시 불가·사람 확인 필요**다. 위 상태표의 [미완료] 항목을 끝내고 실제 production health와 Android 내부 테스트를 통과한 뒤 출시 여부를 다시 승인한다.

## 이번 단계 검증 결과

- Backend: 전체 121개 테스트 통과, 실패·오류·건너뜀 0, `bootJar` 성공
- Mobile: 전체 69개 테스트 통과, ESLint와 TypeScript 성공
- Expo Doctor: 21/21 통과
- Expo export: production 검증 변수를 프로세스에만 주입한 Android와 Web export 성공
- Secret/credential: 의심 패턴 0개, Git 추적 keystore/private key 0개
- Docker: 12-B에서 같은 Dockerfile build 성공. 이번 재검증은 Docker Desktop daemon이 실행되지 않아 미완료
- EAS: 로그인되지 않아 project/signing/config cloud 조회와 AAB build 미실행
- 실제 기기, production health, DB/S3/IAM, Play Console: 미실행·미확인
