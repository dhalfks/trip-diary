# Phase 12-C: Android EAS Release 준비

## 1. Expo/EAS 구조

모바일 앱은 Expo SDK 57의 Managed/CNG 구조를 유지한다. 정적 앱 정보는 `mobile/app.json`, 빌드 환경 검증은 `mobile/app.config.js`, EAS 빌드 프로필은 `mobile/eas.json`에서 관리한다. `android/`와 `ios/` 디렉터리 및 빌드 산출물은 Git에 저장하지 않는다.

## 2. eas.json

- `development`: 개발 환경 확인용 APK, internal distribution, EAS `development` 환경
- `preview`: 내부 테스트용 APK, internal distribution, EAS `preview` 환경
- `production`: Play Console 제출용 AAB, store distribution, EAS `production` 환경

각 프로필은 `EXPO_PUBLIC_APP_ENV`만 저장소에 공개 설정으로 둔다. API 주소는 EAS Environment에서 주입한다.

## 3. 프로필 차이

development는 로컬 API 확인용 내부 APK이며 localhost를 허용한다. 기존 Expo Go 개발 흐름을 유지하고 `expo-dev-client`는 추가하지 않았다. preview는 설치 가능한 APK로 staging 검증에 사용한다. production은 AAB를 생성하며 HTTPS production API만 허용한다.

## 4. Android application ID

Application ID는 기존 값 `com.tripdiary.app`을 유지한다. Play Console에 앱을 처음 등록한 뒤에는 이 값을 변경하면 기존 앱의 업데이트로 배포할 수 없다.

## 5. version/versionCode

사용자 표시 버전은 기존 `1.0.0`을 유지한다. 초기 `android.versionCode`는 `1`이다. EAS의 `appVersionSource: remote`와 production의 `autoIncrement: true`로 이후 production build의 versionCode를 원격에서 증가시킨다. major/minor/patch 버전은 출시 결정에 따라 `app.json`에서 명시적으로 변경한다.

## 6. 앱 아이콘

기존 `assets/images/icon.png`와 Android adaptive icon의 foreground/background/monochrome 이미지를 그대로 사용한다. Play Console 등록 전 실제 기기에서 원형·사각형 마스크와 단색 아이콘을 확인한다.

## 7. Splash

`expo-splash-screen` 플러그인이 기존 `assets/images/splash-icon.png`, 배경색 `#208AEF`, imageWidth `76`을 사용한다. 디자인은 변경하지 않았다.

## 8. Production API URL

EAS의 `production` 환경에 공개 변수 `EXPO_PUBLIC_API_BASE_URL=https://.../api/v1`을 등록한다. production 구성은 값 누락, 잘못된 URL, HTTP, localhost/loopback 주소를 거부한다. 실제 production 주소가 확정되지 않아 placeholder를 코드에 넣지 않았다.

로컬 개발은 `.env`를 사용하고, preview에는 staging HTTPS API 주소를 EAS `preview` 환경으로 주입한다.

## 9. EAS Environment

로그인 후 다음과 같이 공개 API 주소를 등록한다. 실제 주소로 치환해야 한다.

```bash
eas env:set --name EXPO_PUBLIC_API_BASE_URL --value https://staging.example.com/api/v1 --environment preview --visibility plaintext
eas env:set --name EXPO_PUBLIC_API_BASE_URL --value https://api.example.com/api/v1 --environment production --visibility plaintext
```

`EXPO_PUBLIC_` 값은 앱 번들에서 사용자가 읽을 수 있으므로 비밀값을 넣지 않는다. AWS key, JWT secret, DB password, Kakao server secret, signing credential은 모바일 환경변수로 등록하지 않는다.

## 10. Android signing

EAS managed credentials를 사용한다. 저장소에는 keystore가 없으며 새 credential을 이번 단계에서 생성하지 않았다. 최초 production build 때 프로젝트 소유자가 EAS 안내를 확인하고 생성 또는 기존 credential 사용을 직접 승인한다. 기존 credential이 있다면 삭제하거나 교체하지 않는다.

## 11. AAB 생성

production 프로필의 `android.buildType`은 `app-bundle`이다. 생성된 `.aab` 파일은 Git에 커밋하지 않는다.

## 12. EAS Build 명령

```bash
cd mobile
npx eas-cli login
npx eas-cli init
npx eas-cli build:configure
npx eas-cli build --platform android --profile preview
npx eas-cli build --platform android --profile production
```

`init`은 Expo 프로젝트 연결 정보가 아직 없을 때 한 번만 실행한다. 계정, projectId, signing 선택을 확인한 뒤 진행한다.

## 13. Google Play Console 준비

Google Play Developer 계정, 앱 이름, application ID, 아이콘, 설명, 스크린샷, 카테고리, 콘텐츠 등급, 광고 여부, 앱 접근 권한 설명, 개인정보처리방침 URL, 이용약관 URL, Data Safety 답변이 필요하다. 이번 단계에서는 Play Console 앱을 생성하거나 AAB를 제출하지 않았다.

## 14. 내부 테스트 절차

preview APK로 로그인, 토큰 갱신, 여행 CRUD, 기록, 사진 선택·리사이즈·업로드·조회·삭제, 다이어리 생성·미리보기, 회원 탈퇴와 429 처리를 실제 Android 13 이상 기기에서 확인한다. production AAB는 Play Console 내부 테스트 트랙에 먼저 업로드하고 설치·업데이트를 검증한다.

## 15. 개인정보처리방침/이용약관

`EXPO_PUBLIC_PRIVACY_POLICY_URL`과 `EXPO_PUBLIC_TERMS_URL`은 공개 HTTPS URL로 EAS production 환경에 등록한다. 실제 법률 문구와 URL은 아직 준비되지 않았으며 출시 전에 확정해야 한다.

## 16. Data Safety 확인 데이터

계정 정보, 여행·일정·기록 텍스트, 사용자가 선택한 사진, 인증 토큰의 기기 저장, 오류 모니터링 사용 여부, 데이터 암호화와 삭제 요청 방식을 실제 구현과 대조해 답변한다. 사진은 Private S3에 저장되고 presigned URL로 전송되며 회원 탈퇴 삭제 정책은 11-A 문서를 기준으로 확인한다.

## 17. 출시 전 체크리스트

- production Backend와 HTTPS 도메인 준비
- EAS project 연결 및 production API 환경변수 등록
- 개인정보처리방침·이용약관 공개 URL 등록
- EAS managed Android signing credential 확인 및 안전한 복구 절차 보관
- production AAB 생성 후 Play 내부 테스트
- application ID, version, versionCode 확인
- 아이콘, Splash, 사진 권한과 Android 13+ Photo Picker 확인
- 전체 핵심 기능과 계정 삭제 실제 기기 검증
- Play Console Data Safety, 콘텐츠 등급, 앱 접근 권한 작성

## 18. 빌드 실패 시 확인

production API URL 검증 오류, EAS 로그인과 project 연결, production Environment 변수, Android signing credential, Node/npm/Expo SDK 호환성, Expo Doctor 결과, versionCode 중복, 네트워크 및 EAS 서비스 상태를 순서대로 확인한다. API URL이나 credential을 로그에 출력해 진단하지 않는다.

## 권한과 업데이트 설정

사진 선택은 `expo-image-picker`의 시스템 Photo Picker를 사용한다. 카메라와 마이크 권한은 플러그인에서 비활성화되어 있으며 불필요한 Android 권한을 별도로 추가하지 않았다. EAS Update는 이번 단계 범위가 아니며 projectId와 update URL을 임의로 만들지 않았으므로 `runtimeVersion`과 `updates.url`도 추가하지 않았다.

## 12-D로 넘기는 항목

Play Console 등록·내부 테스트 제출, 실제 production AAB 빌드와 서명 생성/확정, 스토어 설명·스크린샷·Data Safety·법률 URL 확정, production Backend 배포, 출시 후보의 실제 기기 회귀 테스트는 후속 단계에서 처리한다.
