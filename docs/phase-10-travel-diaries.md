# 10단계: 규칙 기반 여행 다이어리

기존 Trip/TripDay/DiaryEntry/완료된 Image를 읽어 생성 시점의 텍스트와 페이지 구성을 저장한다. AI, 외부 생성 API, 새 라이브러리는 사용하지 않는다. 원본 여행 기록을 뜻하는 `diary` 패키지와 구분해 생성 결과는 Backend `book`, Mobile `features/books`에 둔다.

## 저장 구조

Flyway `V9__travel_diaries.sql`을 기존 마이그레이션 다음에 적용한다.

- `travel_diaries`: UUID `id`, `trip_id`, `title`, `template_type`, nullable `cover_image_id`, `status`, `created_at`, `updated_at`. Trip 삭제 시 cascade, 대표 Image 삭제 시 FK는 NULL.
- `diary_pages`: UUID `id`, `diary_id`, 1부터 시작하는 `page_order`, `page_type`, `layout_type`, JSONB `content`, `created_at`, `updated_at`. `(diary_id, page_order)` unique. 생성 결과 삭제 시 페이지 cascade.
- 상태는 동기 생성이 트랜잭션으로 완료된 `READY`만 저장한다. 생성 실패 시 결과/페이지 전체를 롤백한다.
- 페이지 JSON 예: `{"schemaVersion":1,"title":"첫날","text":"여행 기록","date":"2026-09-12","imageIds":["이미지 UUID"],"layout":"PHOTO_TEXT"}`. 공개 URL이나 Presigned URL을 DB에 저장하지 않는다.

## 생성 규칙과 템플릿

1. 표지는 항상 첫 페이지다. 기본 제목은 `여행 제목 + 여행 다이어리`, 표지에는 여행 기간을 표시한다.
2. 기록은 날짜 → 기록 createdAt → UUID 문자열 순서다. 각 기록의 완료된 사진은 createdAt → UUID 문자열 순서다. 동일 입력과 템플릿의 페이지 내용/순서는 동일하며, 새 결과의 UUID/생성 시각은 달라진다.
3. 명시한 대표 이미지는 같은 여행의 완료된 이미지인지 검증한다. 미지정 시 위 기록/사진 순서의 첫 사진, 사진이 없으면 대표 이미지 없이 생성한다.
4. CLASSIC은 기록 텍스트 페이지 다음 사진을 2장씩 배치한다. PHOTO는 첫 텍스트 페이지에 첫 사진을 함께 배치하고 나머지 사진은 1장씩 크게 배치한다.
5. 긴 본문은 Unicode 코드 포인트 1,200개 단위로 손실 없이 나눈다. 기록의 모든 텍스트 페이지 다음 나머지 사진 페이지를 배치하고 다음 기록/날짜로 넘어간다.
6. 공백/NULL 본문은 텍스트 페이지를 만들지 않는다. 사진만 있으면 사진 페이지를 만든다. 사진이 없어도 텍스트를 유지한다. 기록/사진 모두 없는 여행은 표지만 생성한다.
7. 페이지 종류는 `COVER`, `ENTRY`, `PHOTOS`, 레이아웃은 `COVER`, `TEXT`, `PHOTO_TEXT`, `PHOTO_GRID`다. `DiaryTemplate`은 JPA와 독립적인 입력 스냅샷/페이지 계획을 사용한다.

## API

모든 API는 기존 JWT 인증 및 Trip 소유권 검사를 적용한다. 타인/없는 여행은 기존 정책대로 `TRIP_NOT_FOUND`로 응답한다.

| Method | Path | 결과 |
| --- | --- | --- |
| POST | `/api/v1/trips/{tripId}/diaries` | 201, 생성된 다이어리 요약 |
| GET | `/api/v1/trips/{tripId}/diaries` | 200, 최신 생성순 요약 배열 |
| GET | `/api/v1/trips/{tripId}/diaries/{diaryId}` | 200, `{diary, pages, images}` 미리보기 |
| DELETE | `/api/v1/trips/{tripId}/diaries/{diaryId}` | 204, 결과와 페이지 삭제 |

POST 본문: `{"templateType":"CLASSIC","title":null,"coverImageId":null}`. templateType 필수, 제목은 선택이며 지정 시 공백 불가/최대 120자다. 대표 이미지는 선택이다.

요약: id, tripId, title, templateType, coverImageId, status, pageCount, createdAt, updatedAt. 페이지: id, pageOrder, pageType, layoutType, content, createdAt, updatedAt.

`images`는 페이지가 참조하는, 현재도 같은 여행에 속한 완료된 Image에 대해 기존 `ImageViewResponse` 형식의 Presigned GET URL을 반환한다. 기존 Storage 추상화 및 URL TTL 설정을 재사용하며 기본 유효기간은 5분이다. 상세 응답은 `Cache-Control: no-store`다. S3 호출 없이 텍스트 생성이 가능하고, 조회 시 URL 발급에 실패한 사진은 응답 이미지 배열에서 빠진다. 화면은 해당 자리에 안내/새로고침을 표시한다.

## 재생성/수정/삭제 정책

- 기존 결과를 덮어쓰지 않고 매번 독립적인 새 결과를 생성한다. 여행당 최대 5개 보관, 초과 시 409 `DIARY_LIMIT_REACHED`. Trip 행 잠금으로 동시 요청에도 상한을 적용한다. 기존 결과를 삭제하면 다시 생성할 수 있다.
- 모바일은 생성 중 중복 누르기를 막는다. 네트워크 응답 유실에 대한 idempotency key는 이번 MVP에 없다. 실패 후 목록을 새로고침하면 서버에서 이미 생성된 결과를 확인할 수 있다.
- 결과당 최대 500페이지, 초과 시 400 `DIARY_TOO_LARGE`로 부분 저장 없이 실패한다.
- 잘못된 대표 이미지: 400 `DIARY_COVER_INVALID`. 다른 여행의 결과 또는 없는 결과: 404 `DIARY_NOT_FOUND`.
- 텍스트/페이지 구성은 생성 시점 스냅샷이다. 원본 수정은 기존 결과를 바꾸지 않는다. 원본 사진 삭제 시 사진 파일을 보존하지 않으며, 기존 페이지의 이미지 ID는 빈 사진 안내로 표시된다.
- 생성 결과 삭제는 원본 DiaryEntry/Image/S3 객체를 삭제하지 않는다. 원본 여행 삭제 시 생성 결과/페이지는 DB FK로 정리된다.
- 이번 단계에는 편집 UI/API가 없다. 향후 페이지 순서/텍스트/레이아웃/대표 이미지 수정은 독립 페이지 ID, JSON 버전, updatedAt 구조를 확장해 처리할 수 있다.

## Mobile

여행 상세와 기존 기록 화면의 `여행 다이어리 만들기 · 미리보기` 버튼 → 생성/목록 화면 → 템플릿 및 선택적 제목/대표 사진 선택 → POST → Preview로 이동한다. 대표 사진 선택은 기존 날짜별 기록/이미지 조회 API를 사용하며 요청을 최대 4개씩 처리한다.

Preview는 한 번에 한 페이지를 종이 형태로 렌더링하고 이전/다음 및 현재/전체 페이지 수를 표시한다. 긴 페이지는 스크롤한다. 기존 인증/자동 토큰 갱신 클라이언트를 사용하며, 사진은 Private Presigned GET URL로만 표시하고 디스크 캐시를 사용하지 않는다. 만료 전/앱 복귀/수동 새로고침으로 URL을 다시 받는다. 원본 사진 누락 및 네트워크 오류에 대한 대체 표시가 있다. 결과 삭제는 목록에서 확인 후 실행하며 진행/실패/성공 상태를 표시한다.

## 검증

- Backend: `cd backend` 후 `.\gradlew.bat test`. 생성/JSON 저장·조회/템플릿/순서/대표 이미지/빈 데이터/긴 본문/권한/삭제/스냅샷/보관 상한과 동시 요청/Storage 실패를 검증한다. H2 PostgreSQL 모드와 mock Storage를 사용하며 실제 AWS 업로드는 실행하지 않는다.
- Mobile: `cd mobile` 후 `npm test`, `npm run check`, `npx expo-doctor`, `npx expo export --platform android --platform web`. 새 테스트는 화면이 사용하는 상태 저장소 및 페이지 표시 모델을 검증한다. 실제 React Native 기기 렌더링 테스트를 대체하지 않는다.
- 기기 확인: 두 템플릿의 긴 글/세로·가로 사진 표시, 대표 사진 선택, 이전/다음, 생성 후 뒤로 가기와 목록, 삭제 확인/재시도, 앱 복귀 및 URL 만료 후 사진 갱신, JWT 자동 갱신, 사진 없는 여행.

### 실행 결과 (2026-09-12)

- Backend 전체 85개 통과: 기존 69개 + 새 생성기 6개/통합 10개.
- Mobile 전체 48개 통과: 기존 이미지 33개 + 새 다이어리 15개.
- ESLint/TypeScript 통과. Android/Web production export 통과.
- Expo Doctor 20/21 통과. 기존 설치된 Expo 계열 16개 패키지가 현재 권장 패치 버전보다 낮다는 검사 1개 실패. 이 단계에서 의존성 버전 및 lockfile은 변경하지 않았다.
- 실제 AWS 업로드/기기 조작 테스트는 실행하지 않았다. DB 통합 테스트는 H2 PostgreSQL 모드 기준이다.

## 변경 파일 목록

Backend 신규:

```text
backend/src/main/java/com/tripdiary/book/DiaryPage.java
backend/src/main/java/com/tripdiary/book/DiaryStatus.java
backend/src/main/java/com/tripdiary/book/DiaryTemplate.java
backend/src/main/java/com/tripdiary/book/LayoutType.java
backend/src/main/java/com/tripdiary/book/PageType.java
backend/src/main/java/com/tripdiary/book/RuleBasedDiaryGenerator.java
backend/src/main/java/com/tripdiary/book/TemplateType.java
backend/src/main/java/com/tripdiary/book/TravelDiary.java
backend/src/main/java/com/tripdiary/book/TravelDiaryController.java
backend/src/main/java/com/tripdiary/book/TravelDiaryRepository.java
backend/src/main/java/com/tripdiary/book/TravelDiaryResponse.java
backend/src/main/java/com/tripdiary/book/TravelDiaryService.java
backend/src/main/resources/db/migration/V9__travel_diaries.sql
backend/src/test/java/com/tripdiary/book/RuleBasedDiaryGeneratorTest.java
backend/src/test/java/com/tripdiary/book/TravelDiaryIntegrationTest.java
```

Backend 수정:

```text
backend/src/main/java/com/tripdiary/diary/DiaryEntryRepository.java
backend/src/main/java/com/tripdiary/global/error/ErrorCode.java
backend/src/main/java/com/tripdiary/image/ImageRepository.java
backend/src/main/java/com/tripdiary/trip/TripRepository.java
```

Mobile 신규:

```text
mobile/src/features/books/types.ts
mobile/src/features/books/book-api.ts
mobile/src/features/books/book-model.ts
mobile/src/features/books/book-store.ts
mobile/src/features/books/diary-page-view.tsx
mobile/src/app/(app)/trips/[tripId]/diaries.tsx
mobile/src/app/(app)/trips/[tripId]/diaries/[diaryId].tsx
mobile/tests/book.test.mjs
```

Mobile 수정 및 문서 신규:

```text
mobile/package.json
mobile/src/app/(app)/_layout.tsx
mobile/src/app/(app)/trips/[tripId].tsx
mobile/src/app/(app)/trips/[tripId]/diary.tsx
docs/phase-10-travel-diaries.md
```
