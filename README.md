# MobilLax

여러 교통수단(지하철·버스·철도 등)을 아우르는 경로 탐색과 예매·결제를 한 곳에서 제공하는 통합 모빌리티 웹 서비스.

## 프로젝트 이력

- 2025-1학기 졸업 캡스톤디자인 3인 팀 프로젝트로 시작
  - 원본 저장소: https://github.com/Abbbs2ws/MobilLax (`map` 브랜치 최종본, 2025-06-09)
  - 원본 커밋 이력과 기여자 정보는 원본 저장소에서 확인할 수 있음
- 이 저장소는 위 결과물을 기준점으로 삼아 개인적으로 개선을 이어가는 프로젝트
  - 첫 커밋 = 팀 프로젝트 최종 코드 (하드코딩된 API 키를 환경 변수로 분리한 것 외에는 원본과 동일, 태그 `v1`)

## 기술 스택

Java 21 · Spring Boot 3.2 · Spring Security · Spring Data JPA · Thymeleaf · MySQL · Gradle
외부 API: Tmap(경로), Kakao 지도, 공공데이터포털(TAGO 등), PortOne(결제)

## 실행 준비

1. `.env.example`을 `.env`로 복사한 뒤 DB 정보와 API 키를 입력 (키는 모두 `.env` 에서만 관리)
2. `docker compose up -d --build` → http://localhost:8080

## 참고 API

- 도시철도(지하철): https://data.kric.go.kr/rips/M_01_02/intro.do
- 전철(KTX, ITX 등): https://www.data.go.kr/data/15098552/openapi.do
- 시외버스: https://www.data.go.kr/data/15098541/openapi.do
- 고속버스: https://www.data.go.kr/data/15098522/openapi.do
- 시내버스: https://www.data.go.kr/data/15098533/openapi.do
