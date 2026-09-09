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