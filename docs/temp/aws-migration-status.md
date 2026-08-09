# Gateway AWS 마이그레이션 진행 현황

- 스펙: `docs/superpowers/specs/2026-08-09-aws-migration-design.md`
- 계획: `docs/superpowers/plans/2026-08-09-aws-migration.md`
- 레이크 스키마·계약 기준: calc 스펙 §2·§8 (`saramquant-calc-server/docs/superpowers/specs/2026-08-09-aws-migration-design.md`)
- 브랜치: `feat/aws-migration`

## 태스크 체크리스트

- [x] T0 브랜치·상태문서·CLAUDE.md 태그
- [ ] T1 Gradle 의존성 + S3 설정 골격
- [ ] T2 S3KvStore
- [ ] T3 DuckDB 실행기 + Glue 리졸버
- [ ] T4 유저·인증 스토리지
- [ ] T5 포트폴리오 스토리지
- [ ] T6 LLM 스토리지
- [ ] T7 감사로그 + Naver 제거
- [ ] T8 시장 데이터 Lake DAO
- [ ] T9 calc 계약 변경
- [ ] T10 프로필 이미지 S3
- [ ] T11 JPA 제거 + 로컬 검증
- [ ] T12 Dockerfile + caddy
- [ ] T13 Terraform
- [ ] T14 deploy.yml + GH 변수
- [ ] T15 가이드 문서
- [ ] T16 최종 리뷰→배포→완주

## 타 세션 대기/전달 사항

- **calc 세션에서 받아야 할 것**: 신규 `CALC_SERVER_URL`(API Gateway URL) — 수신 전까지 GH variable은 플레이스홀더. `warehouse/` Iceberg 적재 완료 여부(대시보드 실데이터 검증 게이트).
- **calc 세션에 전달**: gateway는 calc 스펙 §8.1 계약(holdings 바디 전달)을 그대로 구현 중. §2.4의 refresh_tokens DynamoDB 권고는 채택하지 않음 — gateway는 Iceberg가 아닌 S3 KV JSON(사용자 승인)이라 해당 우려 미적용.

## 사용자 액션 필요 (완주 후 정리해 재안내 예정)

1. NameCheap `api.saramquant.com` A레코드 → EIP (T16에서 값 전달)
2. Google/Kakao 콘솔 프로덕션 redirect URI 등록
3. Vercel `GATEWAY_INTERNAL_URL`·`COOKIE_SECURE` 갱신
