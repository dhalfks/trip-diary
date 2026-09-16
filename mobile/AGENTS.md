# Expo HAS CHANGED

Read the exact versioned docs at https://docs.expo.dev/versions/v56.0.0/ before writing any code.

# Trip Diary - AGENTS.md

## 1. 프로젝트 목적

Trip Diary는 여행 계획과 여행 기록을 하나의 앱에서 관리하는 모바일 애플리케이션이다.

주요 기능:

- 회원가입 / 로그인
- 여행 생성 / 조회 / 수정 / 삭제
- 여행 기간 및 날짜 관리
- 일자별 여행 일정 관리
- 장소 및 일정 관리
- 여행 사진 관리
- 여행 다이어리 작성
- 향후 AI 기반 여행 계획 및 여행 기록 기능
- 향후 여행 공유 및 수익화 기능

이 프로젝트는 실제 출시를 목표로 개발한다.

---

# 2. 전체 프로젝트 구조

프로젝트는 Monorepo 구조를 사용한다.

```text
trip-diary/
├── backend/
│   └── Spring Boot API Server
│
├── mobile/
│   └── Expo + React Native Mobile App
│
├── docs/
│   ├── requirements.md
│   ├── architecture.md
│   ├── api.md
│   └── erd.md
│
├── compose.yaml
├── .env.example
├── AGENTS.md
└── README.md

## Expo / React Native 환경 기준

현재 모바일 프로젝트는 다음 버전을 기준으로 개발한다.

* Node.js: 24.21.0 LTS
* npm: 11.19.0
* Expo SDK: 57.0.21
* React: 19.2.3
* React Native: 0.86.3
* Expo Router: 57.0.20
* React Native Reanimated: 4.5.1
* React Native Worklets: 0.10.1
* React Native Gesture Handler: 2.32.0

### SDK 변경 금지 규칙

* Expo SDK 57을 기준으로 개발한다.
* Expo SDK 56으로 다운그레이드하지 않는다.
* beta/canary/nightly 버전을 임의로 사용하지 않는다.
* React 19.2.3을 임의로 변경하지 않는다.
* React Native 버전을 임의로 변경하지 않는다.
* Expo 패키지는 SDK 57 호환 버전을 우선 사용한다.
* 패키지 버전 충돌이 발생하면 임의로 다른 버전으로 변경하지 말고 먼저 원인을 분석한다.
* `npm audit fix --force`를 사용하지 않는다.
* Expo SDK와 관련된 의존성 변경은 가능한 한 `npx expo install`을 사용한다.

### Node.js

* 개발 기준 Node.js는 24.21.0 LTS이다.
* Node.js 버전을 임의로 변경하지 않는다.
* Node.js 20으로 되돌리지 않는다.
* 프로젝트의 package.json에 별도 요구사항이 없는 한 Node 버전 관련 설정을 새로 추가하지 않는다.

### Expo Router

현재 Expo Router의 기존 구조를 유지한다.

* Route Groups `(auth)`, `(app)` 유지
* 기존 Stack 구조 유지
* 기존 인증 라우팅 유지
* `Stack.Protected` 사용 구조 유지
* `expo-router/ui` 사용 구조 유지
* `expo-router/unstable-native-tabs`를 임의로 다른 navigation 방식으로 교체하지 않는다.

실험적 API인 `unstable-native-tabs`에서 문제가 발생할 경우 먼저 SDK 57 호환성 여부를 확인한 후 최소한의 변경만 수행한다.

### Reanimated / Worklets

현재 다음 조합을 기준으로 한다.

* Reanimated 4.5.1
* Worklets 0.10.1

다음 API를 사용하는 기존 코드를 임의로 제거하거나 다른 애니메이션 라이브러리로 교체하지 않는다.

* Keyframe
* FadeIn
* scheduleOnRN

SDK 업그레이드나 패키지 변경으로 오류가 발생한 경우 원인을 먼저 분석하고 최소한으로 수정한다.

### Native 프로젝트

현재 프로젝트는 Expo Managed/CNG 구조를 사용한다.

* `android/`와 `ios/`를 Git에 추가하지 않는다.
* 네이티브 프로젝트를 임의로 생성하지 않는다.
* `babel.config.js`, `metro.config.js`를 필요 없이 생성하지 않는다.
* Expo config plugin 설정을 임의로 삭제하지 않는다.

### 인증

다음 인증 구조를 유지한다.

* Spring Boot Backend
* JWT Access Token
* JWT Refresh Token
* SecureStore
* 자동 Refresh
* 인증 라우팅

인증 관련 코드를 수정할 때 기존 Backend API 계약을 임의로 변경하지 않는다.

특히 다음 SecureStore 동작을 유지한다.

* getItemAsync
* setItemAsync
* deleteItemAsync

### API / Backend

모바일 앱에서 사용하는 Backend API 계약을 임의로 변경하지 않는다.

Backend 코드를 수정해야 하는 상황이라면 모바일 코드만 수정해서 API 불일치를 숨기지 말고 먼저 원인을 확인한다.

API URL과 환경변수는 기존 프로젝트의 환경변수 구조를 따른다.

실제 API Key, JWT Secret, DB 비밀번호 등의 민감정보를 소스코드나 Git에 저장하지 않는다.

### 작업 원칙

Codex는 기존 코드를 존중하고 필요한 최소한의 변경만 수행한다.

다음 작업을 하기 전에 반드시 현재 코드를 확인한다.

* 패키지 추가/삭제
* 패키지 버전 변경
* navigation 구조 변경
* 인증 구조 변경
* API 구조 변경
* 환경설정 변경

기존 기능을 대체하기보다 현재 구조를 최대한 유지하면서 기능을 추가한다.

### 검증 원칙

코드 변경 후 가능한 경우 다음을 확인한다.

```bash
npx expo-doctor
npx tsc --noEmit
npm run check
```

실행 테스트가 필요한 경우:

```bash
npx expo start -c
```

Web과 Android에서 모두 문제가 없는지 확인한다.

실제 기기에서 확인이 필요한 기능은 번들 성공만으로 완료 처리하지 않는다.

특히 다음은 실제 기기 테스트를 우선한다.

* SecureStore
* 로그인/로그아웃
* 자동 토큰 갱신
* 앱 재시작 후 세션 복구
* Reanimated 애니메이션
* 네이티브 기능

### Git 작업

SDK 57 업그레이드는 다음 커밋으로 완료되었다.

```text
chore: upgrade expo sdk to 57
```

업그레이드 기준 커밋:

```text
8f5f619
```

SDK 57 업그레이드 이전 기준 커밋:

```text
90fa5d9
```

기존 작업을 임의로 reset, revert, rebase하지 않는다.

작업 전에 현재 Git 상태를 확인한다.

```bash
git status
```

사용자의 기존 변경사항을 임의로 삭제하거나 덮어쓰지 않는다.

### npm audit

현재 Expo SDK 57 환경에서 Expo Router 및 Expo CLI의 전이 의존성으로 moderate 수준의 npm audit 경고가 존재할 수 있다.

다음 명령을 임의로 실행하지 않는다.

```bash
npm audit fix --force
```

보안 경고가 발견되면 먼저 해당 패키지가 Expo SDK 57과 호환되는지 확인한다.

호환성을 깨뜨리는 강제 업그레이드는 사용자의 승인 없이 수행하지 않는다.

### 개발 목표

현재 Trip Diary의 우선 목표는 실제 출시 가능한 모바일 여행 다이어리 앱을 단계적으로 구현하는 것이다.

기능 구현 시:

1. 기존 Backend API 확인
2. 필요한 화면 구조 확인
3. 상태/데이터 흐름 설계
4. UI 구현
5. API 연동
6. loading / empty / error / retry 처리
7. TypeScript 검사
8. 실제 실행 확인

순서로 진행한다.

UI를 구현할 때 임의로 기능 범위를 확대하지 않는다.

## 현재 구현 완료 범위

### Phase 6 — 모바일 여행 관리

다음 기능이 구현되어 있으며 실제 Backend API와 연동하여 검증되었다.

* 여행 목록 조회
* 20개 단위 페이지네이션
* Pull-to-refresh
* 다음 페이지 로딩
* 여행 생성
* 여행 수정
* 여행 상세 조회
* 여행 삭제
* 여행 기간 및 타임존 validation
* 백엔드에서 생성된 여행 날짜 표시
* 여행 기간 변경 시 날짜 목록 재구성
* loading 상태
* empty 상태
* API error 상태
* retry UX
* 요청 중 중복 실행 방지

### 현재 모바일 여행 관련 주요 파일

* `src/app/(app)/home.tsx`
* `src/app/(app)/trips/new.tsx`
* `src/app/(app)/trips/[tripId].tsx`
* `src/app/(app)/trips/[tripId]/edit.tsx`
* `src/features/trips/trip-api.ts`
* `src/features/trips/types.ts`
* `src/features/trips/trip-form-screen.tsx`
* `src/features/trips/screen-state.tsx`

### Backend 연동 검증

다음 API 동작은 실제 Backend를 통해 검증되었다.

* 회원가입
* 로그인
* 여행 생성
* 여행 목록 조회
* 여행 상세 조회
* 여행 수정
* 여행 삭제

인증 및 JWT 토큰 갱신 로직은 Phase 6에서 변경하지 않았다.

Backend 코드 및 DB Migration도 Phase 6에서 변경하지 않았다.

### 현재 미구현 범위

여행 상세 화면의 날짜별 일정 관리 기능은 아직 구현하지 않는다.

현재 여행 상세에서 `DAY 1`, `DAY 2` 등의 여행 날짜를 선택할 수 있지만 날짜별 일정 데이터가 없는 경우 빈 상태를 표시한다.

다음 단계에서 일정 관리 기능을 구현할 때 기존 여행 CRUD 기능을 변경하지 않는다.

### Phase 6 완료 기준

자동 검증:

* ESLint: PASS
* TypeScript: PASS
* Expo Doctor: PASS
* Web production bundle: PASS
* Backend tests: PASS
* 회원가입: PASS
* 로그인: PASS
* 여행 CRUD: PASS

실제 Android 기기에서는 다음 항목을 최종 확인한다.

* 로그인/회원가입
* 여행 생성
* 여행 조회
* 여행 수정
* 여행 삭제
* 앱 재시작 후 세션 복구
* SecureStore
* Reanimated animation

실제 기기 테스트가 완료되기 전까지 Phase 6을 완전한 출시 검증으로 간주하지 않는다.
