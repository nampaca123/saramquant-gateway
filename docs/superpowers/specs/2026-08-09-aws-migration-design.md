# SaramQuant Gateway AWS 마이그레이션 설계 (2026-08-09)

Supabase(PostgreSQL)/Railway 기반 gateway를 S3+DuckDB+AWS(ECS on EC2) 기반으로 전환한다.
선례: `C:\Users\a\Desktop\CodeWork\work\ontology-for-nabus-adtrigger` (코드베이스 우선, 문서는 참고만).
**레이크하우스 스키마·파티셔닝·서비스 계약의 단일 기준은 calc-server 스펙**
(`saramquant-calc-server/docs/superpowers/specs/2026-08-09-aws-migration-design.md`)의 §2·§8이며,
본 문서는 그 계약을 소비하는 쪽으로 정합을 맞춘다.

## 0. 확정 결정 사항

| 항목 | 결정 |
|---|---|
| 트랜잭셔널 데이터 | S3 KV — 엔티티별 JSON 객체 (`app/` 프리픽스), 조건부 쓰기로 생성 경합 방어 |
| 시장 데이터 조회 | gateway에 DuckDB JDBC 내장, `warehouse/` Iceberg 직접 읽기 (Glue metadata_location 경유) |
| 컴퓨트/노출 | ECS on EC2 t4g.small ×1 (온디맨드) + EIP + caddy 사이드카 TLS. ALB 없음 |
| DNS/웹 | NameCheap DNS (A레코드 → EIP), 프론트는 Vercel 유지. 설정 가이드를 docs/에 별도 작성 |
| 런타임 자격증명 | EC2 인스턴스 롤 (로컬/CI는 SARAMQUANT_IAM 키만) |
| 기존 데이터 | 이관 없음, 새 출발 |
| 태그 | `project=saramquant` (CLAUDE.md의 ontology는 템플릿 잔재 → 수정) |
| 리전 | gateway: ap-northeast-2, SES: us-east-1 (`AWS_SES_REGION`) |
| Supabase/Railway/Naver Cloud | 전부 제거 |

## 1. 전체 아키텍처

```
[Vercel web] --HTTPS--> api.saramquant.com (NameCheap A레코드 → EIP)
                              │
                    EC2 t4g.small (ECS on EC2, ap-northeast-2, 퍼블릭 서브넷)
                    ┌─────────────────────────────┐
                    │ caddy (80/443, Let's Encrypt)│
                    │   └→ gateway (Spring Boot)   │──→ SES (us-east-1, 기존 키 env)
                    └─────────────────────────────┘
                              │ EC2 인스턴스 롤
                    s3://saramquant-bucket
                    ├── warehouse/<table>/   (Iceberg — calc/fstatements가 쓰기, gateway는 RO)
                    ├── run-summary/         (배치 런 레코드 — freshness 판단용, RO)
                    └── app/                 (트랜잭셔널 KV JSON — gateway 전용 RW)
                    Glue DB `saramquant` (Iceberg 카탈로그, RO)
```

- VPC: 퍼블릭 서브넷만, NAT 없음, S3 게이트웨이 엔드포인트 부착 (비용 0 구조).
- 메모리 배분(2GB): JVM 힙 ~768MB, DuckDB `memory_limit` 512MB, caddy ~30MB.
- 단일 인스턴스 전제: 인메모리 Bucket4j·Caffeine·S3 KV 단일 라이터 모두 이 전제에 의존한다.

## 2. 시장 데이터 조회 — DuckDB JDBC

- `org.duckdb:duckdb_jdbc` 추가. httpfs·iceberg 확장은 Docker 빌드 시 설치·로드 검증,
  런타임은 `autoinstall/autoload=false` + `extension_directory` 오프라인 로드 (선례 패턴).
- DuckDB secret은 `credential_chain` → 인스턴스 롤 자동 인식. 커넥션은 전역 1개 재사용.
- 읽기 경로는 calc 스펙 §2.1 규약 준수: Glue `GetTable`(AWS SDK) → `Parameters["metadata_location"]` →
  `iceberg_scan()`. **Parquet 경로 직접 글롭 금지.** metadata_location은 짧은 TTL로 캐시.
- JPA 읽기 대상이던 시장 테이블을 DuckDB SQL로 재작성:
  `stocks, daily_prices, benchmark_daily_prices, stock_indicators, stock_fundamentals,
  factor_exposures, factor_covariance, sector_aggregates, risk_badges, exchange_rates, risk_free_rates`
- 팩트 테이블 surrogate id 제거·`market` 컬럼 추가·stock_id(long) 조인 키 등 스키마 세부는 calc 스펙
  §2.2–2.3을 그대로 따른다 (쿼리 재작성 시 symbol → stocks 조인으로 stock_id 해석).
- 기존 Caffeine 캐시 유지. `data-freshness`는 `run-summary/calc_kr.json`·`calc_us.json`의
  `written_at_utc`/`status` 읽기로 대체 (calc 스펙 §6.1 포맷).

### 파티셔닝

calc 스펙 §2.3이 확정 기준 (daily_prices: `market`+`months(date)`, financial_statements: `market`,
fundamentals/factor_*: `months(date)`, 나머지 무파티션 + 파일 내 정렬). gateway는 소비만 하므로
별도 계약 문서를 만들지 않고, 쿼리가 파티션 프루닝을 타도록 `market`·`date` 필터를 명시적으로 건다.
파티션 프로젝션은 Hive 전용 개념으로 Iceberg에 해당 없음(양쪽 스펙 합의 완료).

### calc API 계약 변경 반영 (calc 스펙 §8.1)

gateway가 호출하는 calc 엔드포인트는 4개뿐임을 코드로 확인 (`CalcServerClient` 사용처 전수).

| 호출부 | 기존 | 신규 |
|---|---|---|
| PortfolioController·PortfolioLlmService | `POST /internal/portfolios/full-analysis` (portfolio_id 전달) | 동일 경로, **바디에 `{market_group, holdings[]}` 전달** |
| SimulationService | `POST /internal/portfolios/{id}/simulation` | `POST /internal/portfolios/simulation` + holdings 바디 |
| SimulationService | `GET /internal/stocks/{symbol}/simulation` | 불변 |
| PortfolioService | `POST /internal/portfolios/price-lookup` | 불변 (환율 write-back 제거는 calc 내부 사안) |

`CALC_SERVER_URL`은 신규 API Gateway URL로 교체(변수명 유지), `x-api-key: CALC_AUTH_KEY` 유지.
holdings 배열은 gateway가 자기 `portfolios/{userId}.json`에서 구성해 전달 — calc는 더 이상 사용자
데이터에 접근하지 않는다.

## 3. 트랜잭셔널 데이터 — S3 KV (`app/`)

| 키 | 내용 | 비고 |
|---|---|---|
| `users/{userId}.json` | user + profile + preferred_markets 병합 문서 | PII는 기존 AES-GCM 유지 |
| `users/by-email/{emailHash}.json` | → userId 포인터 | HMAC 블라인드 인덱스 재활용, If-None-Match로 중복가입 방지 |
| `refresh-tokens/{tokenHash}.json` | 토큰 메타+만료 | 읽기 시점 만료 검증 |
| `email-verification/{emailHash}.json` | 인증코드+만료 | 읽기 시점 만료 검증 |
| `portfolios/{userId}.json` | 포트폴리오+보유종목 통합 | 단일 문서 갱신 |
| `llm-cache/stock/{symbol}.json`, `llm-cache/portfolio/{userId}.json` | LLM 분석 캐시 | 30일 만료는 스케줄러 |
| `llm-usage/{userId}/{date}.json` | 일일 사용량 카운트 | |
| `recommendations/{userId}/{ts}.json` | 추천 히스토리 | 프리픽스 List로 조회 |
| `audit-log/dt=YYYY-MM-DD/{ts}-{id}.json` | 감사 로그 | 관리자 조회는 DuckDB `read_json` |

- 정리 스케줄러 3종(토큰·인증코드·LLM 캐시)은 S3 list+delete로 재구현 유지.
  버킷 수명주기 규칙은 버킷당 단일 문서라 세션 간 충돌 위험 → 사용하지 않음.
- `ip_geolocations` + Naver geolocation 기능 제거. 방문자 국가는 UNKNOWN 고정.
- 알려진 트레이드오프(승인됨): RDB 수준 트랜잭션/제약 없음. 단일 인스턴스·저빈도 쓰기 전제로 수용.

## 4. 코드 변경 범위

- 제거: Spring Data JPA/Hibernate/Postgres 드라이버, Supabase Storage 클라이언트, Naver 클라이언트.
- 추가: AWS SDK S3, DuckDB JDBC, S3 KV 저장소 레이어. 리포지토리 인터페이스 시그니처 최대 보존.
- 프로필 이미지: S3 저장 + presigned URL 응답 (버킷 공개 정책 불필요).
- SES: `aws.ses.region=${AWS_REGION}` → `${AWS_SES_REGION}` 바인딩 수정. SES 전용 키 env 유지.
- 변경 불필요 로직(인증 흐름, LLM 에이전트, calc 프록시, 레이트리밋 등)은 손대지 않는다.

## 5. IaC & CI/CD

- `infra/` Terraform 단일 환경. 백엔드는 공유 상태 버킷 `s3://saramquant-tfstate`,
  key `gateway/terraform.tfstate`, `use_lockfile=true` (calc 스펙 §2.1 합의. 버킷 부트스트랩은
  최초 1회 — calc 세션이 먼저 만들었으면 재사용). `default_tags { project = "saramquant" }`.
- 로컬은 `make check`(fmt/validate)만. plan/apply는 CI 전용.
- `deploy.yml` (push to main): gradle 빌드·테스트 → JAR → arm64 이미지 buildx(콘텐츠 해시 태그,
  동일 태그 존재 시 빌드 스킵) → ECR push → terraform plan/apply.
  인증: `SARAMQUANT_IAM_KEY_ACCESS/SECRET` 시크릿 (OIDC 아님). concurrency 그룹 직렬화.
- 시크릿 전달: GH secrets → Terraform → SSM SecureString → taskdef `secrets` 참조.
- Terraform 관리: VPC/SG, EIP, ASG(×1, ECS ARM AMI), ECS 클러스터/서비스/태스크(gateway+caddy),
  ECR(최근 3개 보존), 인스턴스 롤(`app/*` RW, `warehouse/*`·`run-summary/*` RO, Glue `saramquant`
  DB GetTable RO, SSM RO), CloudWatch 로그 그룹(30일).
- `saramquant-bucket` 자체는 이미 존재 → data source 참조. 버킷 정책/수명주기는 건드리지 않음.

## 6. 로깅

- ECS awslogs → `/saramquant/gateway`, 보존 30일.
- 스케줄러 실행마다 try/finally로 `{event, run_id, status, duration_ms, counts, error}` 구조화
  JSON 1건 기록 (CloudWatch Logs Insights 조회 가능).

## 7. 테스트 & 완주 기준

- 저장소 레이어: 실버킷 `app-test/` 프리픽스 대상 통합 테스트 (로컬 `.env` SARAMQUANT 키).
- 태스크 게이트: 로컬 테스트 통과 + 로컬 서버 기동 검증 후 커밋.
- PR 게이트(1회): 전체 diff 코드리뷰 → 배포 환경 curl 검증 → verification → 브랜치 정리.
- **완주 기준**: `https://api.saramquant.com`에 대해 가입→메일 인증→로그인→포트폴리오 CRUD→
  로그아웃 curl 성공. 대시보드/종목 상세는 calc 세션의 warehouse 적재에 의존 — 적재 전엔 calc 스펙
  §2.3 스키마 기준 샘플 데이터로 검증, 적재 후 실데이터 최종 확인.
- 세션 간 조율: 진행 현황은 `docs/temp/aws-migration-status.md`, 스키마·계약 기준은 calc 스펙 §2·§8.

## 8. 사용자 액션 필요

1. NameCheap `api.saramquant.com` A레코드 → EIP (배포 후 값 전달, 가이드 문서 제공)
2. Google/Kakao 콘솔 프로덕션 redirect URI 등록 + GH variables의 localhost 값 교체
3. Vercel `GATEWAY_INTERNAL_URL`, `COOKIE_SECURE=true` 갱신
4. 작업 중 신규 GH variables 필요 시 목록 전달 예정 (예: `SARAMQUANT_S3_BUCKET` 이름 형식 정리)
