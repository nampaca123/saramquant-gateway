# Gateway AWS 마이그레이션 진행 현황

- 스펙: `docs/superpowers/specs/2026-08-09-aws-migration-design.md`
- 계획: `docs/superpowers/plans/2026-08-09-aws-migration.md`
- 레이크 스키마·계약 기준: calc 스펙 §2·§8 (`saramquant-calc-server/docs/superpowers/specs/2026-08-09-aws-migration-design.md`)
- 브랜치: `feat/aws-migration`

## 태스크 체크리스트

- [x] T0 브랜치·상태문서·CLAUDE.md 태그
- [x] T1 Gradle 의존성 + S3 설정 골격
- [x] T2 S3KvStore
- [x] T3 DuckDB 실행기 + Glue 리졸버
- [x] T4 유저·인증 스토리지
- [x] T5 포트폴리오 스토리지
- [x] T6 LLM 스토리지
- [x] T7 감사로그 + Naver 제거
- [x] T8 시장 데이터 Lake DAO
- [x] T9 calc 계약 변경
- [x] T10 프로필 이미지 S3
- [x] T11 JPA 제거 + 로컬 검증
- [x] T12 Dockerfile + caddy
- [x] T13 Terraform
- [x] T14 deploy.yml + GH 변수
- [x] T15 가이드 문서
- [x] T16 최종 리뷰→배포→완주

## 배포 완주 결과 (T16, 2026-08-10)

- PR #1 머지 → main 배포 워크플로 성공 (arm64 이미지 빌드·push, terraform apply 전체 그린)
- **EIP: `54.116.55.252`** (instance i-0238baf595c44239f, ECS steady state, gateway 컨테이너 HEALTHY)
- CloudWatch `/saramquant/gateway`: 부팅 10.5초, ERROR 0건
- 배포 환경 검증 (SSM 경유 인스턴스 내부 curl + 외부 80포트):
  healthz 200 / sectors·stocks(실데이터)·freshness·home 200 / 인증헤더 누락 403 /
  send-verification 200(SES 실발송) / 미인증 signup 403 / http→https 308(caddy 정상)
- 공개 HTTPS(`https://api.saramquant.com`) 검증은 NameCheap A레코드 등록 후 가능

## 최종 리뷰 잔여 사항 (파킹/마이너, 후속 참고)

- 파킹: PR CI가 실버킷 `app-test/` 프리픽스로 통합 테스트 수행(설계상 승인), 방문 통계 의미 축소(geolocation 제거에 따른 수용)
- 마이너: audit 일자 존재 확인 listKeys 비효율, fundamentals 글로벌 max(date) 앵커(마켓 간 10일 이상 적재 시차 시 공백), UserStore 포인터 reclaim TOCTOU, DuckDB 404 마커 광범위 매치, 관리자 로그 기본 조회 범위 전체→90일 변경(캡 92일)

## 로컬 검증 결과 (T11, 2026-08-10)

`./gradlew test` 146건 통과(조건부 skip 1건: 실 레이크 데이터 필요). `scripts/run-with-env.ps1`로 bootRun 기동 → 3.9초 만에 부팅 성공, DataSource/Hibernate 초기화 없음. `refresh_token_cleanup` 구조화 로그가 기동 직후 정상 출력(S3 스토어 연결 확인).

| # | 요청 | 결과 | 비고 |
|---|---|---|---|
| 1 | `POST /api/auth/send-verification` | 200 | S3 검증 문서 기록 + SES 발송 성공(예외 없음) |
| 2 | `POST /api/auth/signup` (미검증 verificationId) | 403 | `EMAIL_NOT_VERIFIED` — 의도된 거부 |
| 3 | `GET /api/dashboard/sectors` | 200 | `[]` (sector 집계 미적재) |
| 4 | `GET /api/dashboard/stocks?market=KR_KOSPI` | 200 | 실제 종목 페이지 반환 — Glue/DuckDB 경로 정상 |
| 5 | `GET /api/dashboard/data-freshness` | 200 | 전 필드 null (run-summary 미생성) |
| 6 | `GET /api/home/summary` | 200 | 벤치마크 0값 (benchmark 테이블 미적재) |
| 7 | `/api/**` 인증 헤더 누락 | 403 | `GatewayAuthFilter` 정상 동작 |

## 타 세션 대기/전달 사항

- **calc 세션에서 받아야 할 것**: 신규 `CALC_SERVER_URL`(API Gateway URL) — 수신 전까지 GH variable은 플레이스홀더. `warehouse/` Iceberg 적재 완료 여부(대시보드 실데이터 검증 게이트).
- **calc 세션에 전달**: gateway는 calc 스펙 §8.1 계약(holdings 바디 전달)을 그대로 구현 중. §2.4의 refresh_tokens DynamoDB 권고는 채택하지 않음 — gateway는 Iceberg가 아닌 S3 KV JSON(사용자 승인)이라 해당 우려 미적용.

## 사용자 액션 필요

절차는 `docs/aws-deploy-guide.md` 참고.

1. NameCheap `api.saramquant.com` A레코드 → EIP (첫 `main` 배포 후 GH Actions job summary에서 값 확인)
2. Google/Kakao 콘솔에 프로덕션 redirect URI 등록 (`https://api.saramquant.com/login/oauth2/code/{google,kakao}`)
3. Vercel `GATEWAY_INTERNAL_URL` → `https://api.saramquant.com` 갱신 후 재배포
4. GH Variable `FRONTEND_REDIRECT_URL`·`CORS_ALLOWED_ORIGIN`은 `https://saramquant.com`으로 등록 완료 — 프론트엔드를 다른 도메인으로 서빙한다면 병합 전에 실제 도메인으로 교체할 것

`COOKIE_SECURE`는 T14에서 이미 `true`로 갱신 완료 — 별도 조치 불필요.
