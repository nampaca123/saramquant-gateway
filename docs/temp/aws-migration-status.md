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
- [ ] T12 Dockerfile + caddy
- [ ] T13 Terraform
- [ ] T14 deploy.yml + GH 변수
- [ ] T15 가이드 문서
- [ ] T16 최종 리뷰→배포→완주

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

## 사용자 액션 필요 (완주 후 정리해 재안내 예정)

1. NameCheap `api.saramquant.com` A레코드 → EIP (T16에서 값 전달)
2. Google/Kakao 콘솔 프로덕션 redirect URI 등록
3. Vercel `GATEWAY_INTERNAL_URL`·`COOKIE_SECURE` 갱신
