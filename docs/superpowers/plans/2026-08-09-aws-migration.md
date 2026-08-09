# Gateway AWS 마이그레이션 구현 계획

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.
> **사용자 지시: 태스크별 코드 리뷰는 생략, 최종 PR 게이트에서 1회만 리뷰한다.**

**Goal:** Supabase/Railway 기반 gateway를 S3 KV + DuckDB(Iceberg) + ECS on EC2로 전환하고 배포 완주.

**Architecture:** 트랜잭셔널 데이터는 `s3://saramquant-bucket/app/` 아래 엔티티별 JSON 객체(S3 KV), 시장 데이터는 DuckDB JDBC로 Glue 카탈로그의 Iceberg 테이블(`warehouse/`)을 읽는다. 컴퓨트는 t4g.small 1대(ECS on EC2) + caddy TLS, IaC는 Terraform + GitHub Actions.

**Tech Stack:** Kotlin/Spring Boot 4.0.2, AWS SDK v2 (s3/glue/sesv2), duckdb_jdbc, Terraform, GitHub Actions.

**Spec:** `docs/superpowers/specs/2026-08-09-aws-migration-design.md` (본 계획의 상위 문서).
**레이크 스키마·계약 기준:** `saramquant-calc-server/docs/superpowers/specs/2026-08-09-aws-migration-design.md` §2·§8.

## Global Constraints

- 커밋 메시지: `260809_TaskNameCamelCase_kyoungin` (날짜는 실제 작업일로).
- 주석 최소(파일당 최대 2줄, 한국어), 로그·에러 메시지는 영어.
- 파일 300줄 이하 유지. verb-object 네이밍.
- 변경 불필요 로직(인증 흐름, LLM 에이전트 루프, 레이트리밋, 이메일 템플릿 등)은 손대지 않는다.
- AWS 접근: 로컬/CI는 `.env`/GH secrets의 `SARAMQUANT_IAM_KEY_ACCESS/SECRET`만. 데스크톱 기본 AWS CLI 프로필 절대 금지.
- Terraform: 로컬은 `make check`만. plan/apply는 CI 전용.
- 버킷: `saramquant-bucket` (ap-northeast-2). Glue DB: `saramquant`. 태그: `project=saramquant`.
- S3 KV 문서의 PII(email, name, providerId, nickname)는 기존 `AesEncryptor`로 암호화해 저장한다.
- Iceberg 읽기: Glue `GetTable` → `Parameters["metadata_location"]` → `iceberg_scan()`. Parquet 글롭 금지.
- 통합 테스트는 `@EnabledIfEnvironmentVariable(named="SARAMQUANT_IAM_KEY_ACCESS", ...)`로 가드하고 실버킷 `app-test/` 프리픽스 사용, 테스트 후 정리.
- 테스트 실행: `./gradlew test --tests '<class>'`. `.env` 로드는 bootRun에만 있으므로 통합 테스트 실행 전 환경변수 주입 필요(아래 Task 2 참조).
- JPA 제거는 Task 11에서 일괄 수행 — 그 전 태스크들은 JPA 의존성이 남아 있어도 컴파일 그린 유지가 기준. 전체 서버 기동 검증은 Task 11에서.

## 파일 구조 (신규/핵심 수정)

```
src/main/kotlin/me/saramquantgateway/
  infra/storage/s3/        S3ClientConfig.kt, S3StorageProperties.kt, S3KvStore.kt
  infra/storage/service/   ProfileImageService.kt (수정: S3 + presign)
  infra/duckdb/            DuckDbConfig.kt, DuckDbQueryExecutor.kt, LakeTableResolver.kt,
                           GlueLakeTableResolver.kt
  domain/store/            UserStore.kt, RefreshTokenStore.kt, EmailVerificationStore.kt,
                           PortfolioStore.kt, LlmCacheStore.kt, LlmUsageStore.kt,
                           RecommendationStore.kt, AuditLogStore.kt
  domain/document/         UserDoc.kt, RefreshTokenDoc.kt, EmailVerificationDoc.kt,
                           PortfolioDoc.kt, LlmAnalysisDoc.kt, RecommendationDoc.kt, AuditLogDoc.kt
  domain/lake/             StockLakeDao.kt, PriceLakeDao.kt, IndicatorLakeDao.kt,
                           FundamentalLakeDao.kt, FactorLakeDao.kt, RiskBadgeLakeDao.kt,
                           SectorLakeDao.kt, MarketRefLakeDao.kt, DashboardLakeDao.kt
infra/                     Terraform 전체 (backend.tf, providers.tf, variables.tf, vpc.tf, sg.tf,
                           iam.tf, ecr.tf, ecs.tf, ec2.tf, ssm.tf, logs.tf, outputs.tf, tf 래퍼)
.github/workflows/deploy.yml
Makefile
docs/aws-deploy-guide.md   (NameCheap DNS + 운영 가이드)
docs/temp/aws-migration-status.md
```

기존 `domain/entity/**`의 시장 엔티티는 JPA 어노테이션을 벗겨 플레인 데이터 클래스로 재사용(필드명 유지 → 서비스 변경 최소화). 유저·트랜잭셔널 엔티티는 `domain/document/`의 문서 클래스로 대체.

---

### Task 0: 준비 — 상태 문서·CLAUDE.md 태그 수정

**Files:**
- Create: `docs/temp/aws-migration-status.md`
- Modify: `CLAUDE.md` (project=ontology → project=saramquant 문구 수정)
- Create: branch `feat/aws-migration`

- [ ] **Step 1:** `git checkout -b feat/aws-migration`
- [ ] **Step 2:** `docs/temp/aws-migration-status.md` 작성 — 섹션: 확정 사안 요약(스펙 링크), 태스크 체크리스트(본 계획 태스크 번호), 타 세션 전달 사항(신규 CALC_SERVER_URL 대기 중 등), 현재 진행 상태. 이후 매 태스크 완료 시 이 문서 갱신.
- [ ] **Step 3:** CLAUDE.md의 `project=ontology` 태그 문구를 `project=saramquant`로 수정.
- [ ] **Step 4:** Commit `260809_PrepMigrationStatusDoc_kyoungin`

### Task 1: Gradle 의존성 + S3/DuckDB 설정 골격

**Files:**
- Modify: `build.gradle.kts`
- Modify: `src/main/resources/application.properties`
- Create: `src/main/kotlin/me/saramquantgateway/infra/storage/s3/S3StorageProperties.kt`
- Create: `src/main/kotlin/me/saramquantgateway/infra/storage/s3/S3ClientConfig.kt`

**Interfaces (Produces):**
```kotlin
@ConfigurationProperties(prefix = "app.s3")
data class S3StorageProperties(val bucket: String, val appPrefix: String, val region: String)
// Bean: S3Client (region ap-northeast-2, DefaultCredentialsProvider), S3Presigner
```

- [ ] **Step 1:** `build.gradle.kts`에 추가 (BOM은 기존 `software.amazon.awssdk:bom:2.34.0` 활용):
```kotlin
implementation("software.amazon.awssdk:s3")
implementation("software.amazon.awssdk:glue")
implementation("org.duckdb:duckdb_jdbc:1.4.4")   // 최신 안정 버전 확인 후 조정
```
- [ ] **Step 2:** `application.properties`에 추가:
```properties
app.s3.bucket=${SARAMQUANT_S3_BUCKET_NAME}
app.s3.app-prefix=app/
app.s3.region=ap-northeast-2
app.lake.glue-database=${GLUE_DATABASE:saramquant}
app.lake.run-summary-prefix=run-summary/
```
- [ ] **Step 3:** `S3StorageProperties`, `S3ClientConfig`(S3Client + S3Presigner 빈, `DefaultCredentialsProvider` — 로컬은 env 키, EC2는 인스턴스 롤) 작성.
- [ ] **Step 4:** `./gradlew compileKotlin` 그린 확인.
- [ ] **Step 5:** Commit `260809_AddS3DuckDbDeps_kyoungin`

### Task 2: S3KvStore (핵심 KV 레이어)

**Files:**
- Create: `src/main/kotlin/me/saramquantgateway/infra/storage/s3/S3KvStore.kt`
- Test: `src/test/kotlin/me/saramquantgateway/infra/storage/s3/S3KvStoreTest.kt`

**Interfaces (Produces):**
```kotlin
@Component
class S3KvStore(private val s3: S3Client, private val props: S3StorageProperties,
                private val objectMapper: ObjectMapper) {
    fun <T : Any> get(key: String, type: Class<T>): T?          // 404 → null
    fun <T : Any> put(key: String, value: T)
    fun <T : Any> putIfAbsent(key: String, value: T): Boolean   // If-None-Match: "*", 이미 있으면 false
    fun delete(key: String)                                      // 멱등
    fun listKeys(prefix: String): List<String>                   // 페이지네이션 처리, 전체 키 반환
    fun listEntries(prefix: String): List<KvEntry>               // key + lastModified(Instant)
}
data class KvEntry(val key: String, val lastModified: Instant)
```
key는 `app/` 프리픽스를 제외한 논리 키(스토어가 `props.appPrefix + key`로 조합). JSON 직렬화는 objectMapper(JavaTimeModule 포함 — Spring 기본 ObjectMapper 주입).

- [ ] **Step 1: 실패 테스트 작성** — `@EnabledIfEnvironmentVariable(named="SARAMQUANT_IAM_KEY_ACCESS", matches=".+")` 통합 테스트. Spring 컨텍스트 없이 직접 인스턴스화(S3Client 빌더에 env 자격증명·ap-northeast-2). 테스트 프리픽스 `app-test/{uuid}/` 사용, `@AfterAll`에서 삭제. 케이스: put→get 라운드트립(Instant 필드 포함), get 미존재 null, putIfAbsent 최초 true·재시도 false, delete 후 get null, listKeys/listEntries 프리픽스 필터.
```kotlin
data class TestDoc(val id: String, val createdAt: Instant)
@Test fun `put then get roundtrips`() { store.put("$p/a.json", TestDoc("a", now)); assertEquals("a", store.get("$p/a.json", TestDoc::class.java)!!.id) }
@Test fun `putIfAbsent returns false when key exists`() { assertTrue(store.putIfAbsent("$p/b.json", doc)); assertFalse(store.putIfAbsent("$p/b.json", doc)) }
```
- [ ] **Step 2:** 환경변수 주입 방법: PowerShell에서 `.env` 파싱 후 `$env:` 세팅하는 `scripts/run-with-env.ps1` 작성(테스트·로컬 실행 공용, `AWS_ACCESS_KEY_ID`/`AWS_SECRET_ACCESS_KEY`에 SARAMQUANT 키 매핑 포함). 실행해 FAIL(클래스 미존재) 확인.
- [ ] **Step 3:** S3KvStore 구현. putIfAbsent는 `PutObjectRequest.builder().ifNoneMatch("*")`, 412 `S3Exception`(statusCode 412) → false. get은 `NoSuchKeyException` → null.
- [ ] **Step 4:** 테스트 PASS 확인.
- [ ] **Step 5:** Commit `260809_AddS3KvStore_kyoungin`

### Task 3: DuckDB 실행기 + Glue 리졸버

**Files:**
- Create: `infra/duckdb/DuckDbConfig.kt` (커넥션 초기화: 확장 로드, secret, memory_limit)
- Create: `infra/duckdb/DuckDbQueryExecutor.kt`
- Create: `infra/duckdb/LakeTableResolver.kt` + `GlueLakeTableResolver.kt`
- Test: `src/test/kotlin/.../duckdb/DuckDbQueryExecutorTest.kt`, `GlueLakeTableResolverTest.kt`

**Interfaces (Produces):**
```kotlin
class DuckDbQueryExecutor(...) {   // 전역 단일 Connection, synchronized 접근 or 커넥션 duplicate()
    fun <T> query(sql: String, params: List<Any?> = emptyList(), mapper: (ResultSet) -> T): List<T>
}
interface LakeTableResolver { fun ref(table: String): String }
// GlueLakeTableResolver: "iceberg_scan('<metadata_location>')" 반환, Caffeine 5분 TTL 캐시
```
DuckDB 초기화(설정 순서 중요): `INSTALL httpfs; INSTALL iceberg; LOAD httpfs; LOAD iceberg;`
`SET extension_directory='${DUCKDB_EXT_DIR:/duckdb-ext}'`, `SET memory_limit='512MB'`, `SET temp_directory='/tmp/duckdb'`,
`CREATE OR REPLACE SECRET s3sec (TYPE s3, PROVIDER credential_chain, REGION 'ap-northeast-2')`.
확장은 컨테이너 기동 시 1회 설치(호스트 볼륨 `/duckdb-ext` 캐시로 재기동 시 오프라인). 로컬 테스트는 기본 홈 디렉토리 캐시.
쿼리 파라미터는 PreparedStatement 바인딩. 동시성: DuckDB JDBC는 단일 Connection에서 `duplicate()`로 커서 분리 — executor가 스레드마다 duplicate 커넥션을 ThreadLocal로 관리.

- [ ] **Step 1: 실패 테스트** — 순수 로컬(자격증명 불필요): in-memory DuckDB에 `CREATE TABLE t AS SELECT ...` 후 query() 매핑 검증, 파라미터 바인딩 검증, 동시 2스레드 쿼리 검증. Glue 리졸버는 `@EnabledIfEnvironmentVariable` 가드 통합 테스트: `saramquant` DB의 테이블 하나(예: stocks)가 존재하면 metadata_location 문자열이 `s3://saramquant-bucket/warehouse/`로 시작함을 확인 — **calc 세션이 아직 테이블을 안 만들었으면 skip 처리**(`assumeTrue(tableExists)`).
- [ ] **Step 2:** FAIL 확인 → 구현 → PASS.
- [ ] **Step 3:** Commit `260809_AddDuckDbExecutor_kyoungin`

### Task 4: 유저·인증 스토리지 (users / refresh-tokens / email-verification)

**Files:**
- Create: `domain/document/UserDoc.kt`, `RefreshTokenDoc.kt`, `EmailVerificationDoc.kt`
- Create: `domain/store/UserStore.kt`, `RefreshTokenStore.kt`, `EmailVerificationStore.kt`
- Modify: `infra/user/service/UserService.kt`, `infra/user/service/ProfileService.kt`,
  `infra/auth/service/AuthService.kt`, `infra/jwt/service/RefreshTokenService.kt`,
  `infra/systememail/service/EmailVerificationService.kt`,
  `infra/systememail/scheduler/VerificationCodeCleanupScheduler.kt`,
  `feature/recommendation/controller/RecommendationController.kt` (UserProfileRepository → UserStore)
- Delete (이 태스크에서 참조 제거, 파일 삭제는 Task 11): User/UserProfile/RefreshToken/EmailVerificationCode 엔티티와 리포지토리

**Interfaces (Produces):**
```kotlin
// UserDoc: User+UserProfile+preferredMarkets 병합. 필드명은 기존 엔티티와 동일하게 유지.
data class UserDoc(
    val id: UUID, val email: String /*암호화 저장*/, val emailHash: String,
    val name: String /*암호화*/, val provider: AuthProvider, val providerId: String /*암호화*/,
    var passwordHash: String?, val role: UserRole, var isActive: Boolean,
    var deactivatedAt: Instant?, val createdAt: Instant, var lastLoginAt: Instant,
    // profile 병합
    var nickname: String? /*암호화*/, var birthYear: Int?, var gender: Gender?,
    var profileImageKey: String?,   // S3 키 (기존 profileImageUrl 대체, Task 10에서 presign)
    var investmentExperience: InvestmentExperience, var preferredMarkets: MutableSet<Market>,
    var updatedAt: Instant)

@Component class UserStore(kv: S3KvStore, aes: AesEncryptor) {
    fun findById(userId: UUID): UserDoc?                    // users/{userId}.json
    fun findByEmailHash(emailHash: String): UserDoc?        // 포인터 경유 2-hop
    fun create(doc: UserDoc): Boolean                       // 포인터 putIfAbsent → 본문 put. 포인터 실패 시 false
    fun save(doc: UserDoc)                                  // 본문만 갱신
    fun deactivate(...) 등 기존 UserService가 필요로 하는 변경 헬퍼는 save로 흡수
}
@Component class RefreshTokenStore(kv: S3KvStore) {
    fun findByTokenHash(hash: String): RefreshTokenDoc?     // refresh-tokens/{hash}.json
    fun save(doc: RefreshTokenDoc)                          // + by-user/{userId}/{hash}.json 마커
    fun revokeAllByUserId(userId: UUID, now: Instant)       // by-user 리스트 → 각 revokedAt 세팅
    fun deleteExpired(now: Instant): Int                    // listEntries("refresh-tokens/") lastModified+TTL 기준
}
@Component class EmailVerificationStore(kv: S3KvStore) {
    fun findLatest(emailHash: String, purpose: String): EmailVerificationDoc?  // email-verification/{purpose}/{emailHash}.json
    fun save(doc: EmailVerificationDoc)                     // 같은 키 덮어쓰기 = 최신 1건 유지
    fun findVerified(id: UUID, emailHash: String, purpose: String): EmailVerificationDoc?
    fun deleteExpired(cutoff: Instant): Int
}
```
암호화 필드는 스토어의 직렬화 전/후 처리로 AesEncryptor 적용 (JSON에는 암호문 저장).
`create` 실패 시 정리: 포인터 성공 후 본문 put 실패하면 포인터 delete (best-effort try/catch).

- [ ] **Step 1: 실패 테스트** — 각 스토어 통합 테스트(가드, `app-test/` 프리픽스에 S3StorageProperties 오버라이드). 핵심 케이스: 가입 경합(포인터 중복 → create false), 이메일 조회 2-hop, 토큰 revokeAll 후 findByTokenHash의 revokedAt 확인, verification 최신 1건 갱신, PII 암호문이 raw JSON에 평문 노출 안 됨(S3 GetObject raw 문자열에 email 평문 미포함 검증).
- [ ] **Step 2:** FAIL 확인 → 문서·스토어 구현 → PASS.
- [ ] **Step 3:** 서비스 리와이어. 원칙: 서비스 메서드 시그니처·동작 불변, 의존만 Repository→Store 교체. `@Transactional` 어노테이션은 이 서비스들에서 제거(무의미). AuthService.oauthLogin/manualSignup의 다중 쓰기는 순서 고정: 이메일 포인터(예약) → 유저 문서 → 토큰. 각 서비스 수정 후 관련 단위 테스트(기존 테스트 파일이 없으므로 스토어 목킹한 서비스 테스트를 AuthService.manualSignup·oauthLogin에 대해 신규 작성 — 중복가입 시 예외, 재활성화 경로).
- [ ] **Step 4:** `./gradlew test` + `compileKotlin` 그린.
- [ ] **Step 5:** Commit `260809_MigrateAuthStorageToS3_kyoungin`

### Task 5: 포트폴리오 스토리지

**Files:**
- Create: `domain/document/PortfolioDoc.kt`, `domain/store/PortfolioStore.kt`
- Modify: `feature/portfolio/service/PortfolioService.kt`, `feature/home/service/HomeService.kt`(포트폴리오 부분)
- Delete 참조: UserPortfolio/PortfolioHolding 엔티티·리포지토리

**Interfaces (Produces):**
```kotlin
// portfolios/{userId}.json — KR/US 두 포트폴리오와 보유종목을 한 문서에
data class PortfolioDoc(val userId: UUID, val portfolios: MutableList<PortfolioEntry>)
data class PortfolioEntry(val id: Long, val marketGroup: String, val createdAt: Instant,
                          var updatedAt: Instant, val holdings: MutableList<HoldingEntry>)
data class HoldingEntry(val stockId: Long, var shares: BigDecimal, var avgPrice: BigDecimal,
                        val currency: String, val purchasedAt: LocalDate,
                        var purchaseFxRate: BigDecimal?, var priceSource: String,
                        val createdAt: Instant, var updatedAt: Instant)
@Component class PortfolioStore(kv: S3KvStore) {
    fun findByUserId(userId: UUID): PortfolioDoc?
    fun save(doc: PortfolioDoc)
    fun findEntry(userId: UUID, portfolioId: Long): PortfolioEntry?
}
// 포트폴리오 id는 결정적 채번: abs(SHA-256("$userId:$marketGroup") 앞 8바이트 Long)
```
PortfolioService의 기존 public 메서드(get/buy/sell/reset/ensurePortfoliosExist/analysis/priceLookup 등) 시그니처 유지. holdingRepo/portfolioRepo 접근을 PortfolioStore 문서 조작으로 교체(읽고-수정-save 패턴). HomeService의 countByPortfolioId는 doc의 holdings.size로.
**주의:** PortfolioService는 Stock 조회(StockRepository)도 사용 — Task 8 전까지는 기존 리포지토리 그대로 두고 이 태스크에서는 포트폴리오 저장만 교체.

- [ ] **Step 1: 실패 테스트** — PortfolioStore 통합 테스트(buy→sell→reset 시나리오를 문서 조작으로), 결정적 id 채번 안정성(같은 입력 → 같은 id), PortfolioService 단위 테스트(스토어·기존 리포지토리 목킹: buy 신규/기존 보유 평균단가 재계산, sell 전량/부분, 소유권 검증 예외).
- [ ] **Step 2:** FAIL → 구현 → PASS.
- [ ] **Step 3:** Commit `260809_MigratePortfolioStorageToS3_kyoungin`

### Task 6: LLM 캐시·사용량·추천 스토리지

**Files:**
- Create: `domain/document/LlmAnalysisDoc.kt`, `RecommendationDoc.kt`
- Create: `domain/store/LlmCacheStore.kt`, `LlmUsageStore.kt`, `RecommendationStore.kt`
- Modify: `feature/llm/service/StockLlmService.kt`, `PortfolioLlmService.kt`, `LlmUsageService.kt`,
  `LlmCacheCleanupScheduler.kt`, `feature/stock/service/StockService.kt`(StockLlmAnalysisRepository 사용부),
  `feature/recommendation/service/RecommendationAgentService.kt`, `RecommendationController.kt`,
  `feature/portfolio/controller/PortfolioController.kt`(PortfolioLlmAnalysisRepository 사용부)

**Interfaces (Produces):**
```kotlin
@Component class LlmCacheStore(kv: S3KvStore) {
    // llm-cache/stock/{stockId}-{date}-{preset}-{lang}.json / llm-cache/portfolio/{portfolioId}-{date}-{preset}-{lang}.json
    fun findStock(stockId: Long, date: LocalDate, preset: String, lang: String): LlmAnalysisDoc?
    fun saveStock(doc: LlmAnalysisDoc)
    fun findPortfolio(portfolioId: Long, date: LocalDate, preset: String, lang: String): LlmAnalysisDoc?
    fun listPortfolioHistory(portfolioId: Long): List<LlmAnalysisDoc>   // 프리픽스 리스트 → createdAt desc
    fun savePortfolio(doc: LlmAnalysisDoc)
    fun deletePortfolioAll(portfolioId: Long)
    fun deleteOlderThan(cutoff: Instant): Int      // listEntries lastModified 기준, stock+portfolio 모두
}
@Component class LlmUsageStore(kv: S3KvStore) {    // llm-usage/{userId}/{date}.json {count:Int}
    fun getCount(userId: UUID, date: LocalDate): Int
    fun incrementBy(userId: UUID, date: LocalDate, amount: Int): Int   // read-modify-write, 결과 count 반환
    fun decrementBy(userId: UUID, date: LocalDate, amount: Int): Int   // GREATEST(0,...)
}
@Component class RecommendationStore(kv: S3KvStore) {
    // recommendations/{userId}/{marketGroup}/{역순타임스탬프}.json  (키: %019d 포맷의 Long.MAX-epochMillis → 리스트가 최신순)
    fun save(doc: RecommendationDoc)
    fun findPage(userId: UUID, marketGroup: String, page: Int, size: Int): PageResult<RecommendationDoc>
}
data class PageResult<T>(val content: List<T>, val totalElements: Long, val hasNext: Boolean)
```

- [ ] **Step 1: 실패 테스트** — 통합: 캐시 저장/조회/기간삭제, usage increment→decrement 경계(0 미만 방지), recommendation 역순 페이지네이션(3건 저장 → page 0 size 2 → 최신 2건 + hasNext).
- [ ] **Step 2:** FAIL → 구현 → 서비스 리와이어(시그니처 유지) → PASS. LlmCacheCleanupScheduler는 구조화 로그 `{event:"llm_cache_cleanup", run_id, status, deleted, duration_ms}` try/finally 기록으로 재작성.
- [ ] **Step 3:** Commit `260809_MigrateLlmStorageToS3_kyoungin`

### Task 7: 감사 로그 S3 전환 + Naver geolocation 제거

**Files:**
- Create: `domain/document/AuditLogDoc.kt`, `domain/store/AuditLogStore.kt`
- Modify: `infra/log/filter/AuditLogFilter.kt`(AuditEventListener), `infra/log/service/AuditLogService.kt`,
  `infra/log/controller/AdminController.kt`, `infra/systememail/service/SystemEmailService.kt`(audit 기록부)
- Delete: `infra/log/client/NaverGeolocationClient.kt`, `infra/log/service/IpGeolocationService.kt`,
  `infra/log/entity/IpGeolocation.kt`, `infra/log/repository/IpGeolocationRepository.kt`,
  `infra/log/entity/AuditLog.kt`, `infra/log/repository/AuditLogRepository.kt`
- Modify: `application.properties`(naver-geo 제거)

**Interfaces (Produces):**
```kotlin
// audit-log/dt=YYYY-MM-DD/{epochMillis}-{shortId}.json
data class AuditLogDoc(val id: UUID, val server: String, val action: String, val method: String?,
    val path: String?, val ipMasked: String?, val userId: UUID?, val statusCode: Int?,
    val durationMs: Long?, val metadata: String?, val createdAt: Instant)
@Component class AuditLogStore(kv: S3KvStore, duckDb: DuckDbQueryExecutor, props: S3StorageProperties) {
    fun append(doc: AuditLogDoc)
    fun findFiltered(server: String?, action: String?, from: Instant, to: Instant,
                     page: Int, size: Int): PageResult<AuditLogDoc>
    // findFiltered: 날짜 범위의 dt= 프리픽스들만 DuckDB read_json_auto("s3://.../audit-log/dt={d}/*.json") UNION, WHERE·ORDER·LIMIT
    fun countVisitors(from: Instant, to: Instant): List<VisitorStat>   // AdminController.visitors 요구 형태로 (ipMasked 기준 distinct, country="UNKNOWN")
}
```
ipGeolocationId → ipMasked 인라인으로 대체(기존 마스킹 로직 재사용). AdminController 응답 DTO의 필드 구조는 유지하되 geolocation 상세는 UNKNOWN/null 고정. DuckDB용 S3 read_json은 s3sec secret(credential_chain)으로 동작 — Task 3 기반 재사용.

- [ ] **Step 1: 실패 테스트** — 통합: append 후 findFiltered(당일 범위)로 조회, action 필터, 페이지네이션. visitors 집계 스모크.
- [ ] **Step 2:** FAIL → 구현 → Naver 관련 파일 삭제·참조 제거 → PASS.
- [ ] **Step 3:** Commit `260809_MigrateAuditLogRemoveNaver_kyoungin`

### Task 8: 시장 데이터 DuckDB DAO (JPA 시장 리포지토리 대체)

**Files:**
- Create: `domain/lake/` 아래 DAO들 (파일 구조 절 참조, 파일당 300줄 이하 유지 위해 분리)
- Modify: 시장 리포지토리를 쓰는 모든 서비스의 의존 교체:
  `feature/stock/service/StockService.kt`, `feature/dashboard/service/DashboardService.kt`,
  `feature/home/service/HomeService.kt`, `feature/portfolio/service/PortfolioService.kt`,
  `feature/llm/service/StockLlmService.kt`, `PortfolioLlmService.kt`,
  `feature/recommendation/service/RecommendationContextBuilder.kt`, `RecommendationToolExecutor.kt`
- Delete 참조: 시장 JPA 리포지토리 15종 + `feature/dashboard/repository/DashboardQueryRepository.kt`
- Modify: 시장 엔티티들(JPA 어노테이션 제거는 Task 11, 여기선 그대로 재사용)

**Interfaces (Produces):** 기존 리포지토리 메서드와 1:1 시그니처의 DAO. 매핑 규칙:

| DAO | 대체 대상 | 비고 |
|---|---|---|
| StockLakeDao | StockRepository 전체 | `findById(Long)`/`findByIdIn` 포함. Page 반환 메서드는 LIMIT/OFFSET+COUNT |
| PriceLakeDao | DailyPriceRepository, BenchmarkDailyPriceRepository | top2/latest는 윈도우 함수(`row_number() over (partition by stock_id order by date desc)`) |
| IndicatorLakeDao | StockIndicatorRepository | 스냅샷 테이블 — findLatest 계열은 단순 WHERE stock_id IN |
| FundamentalLakeDao | StockFundamentalRepository, FinancialStatementRepository | fundamentals는 months(date) 파티션 → 최신값은 `date >= today-90d` 필터로 프루닝 유도 |
| FactorLakeDao | FactorExposureRepository, FactorCovarianceRepository | covariance matrix는 JSON string 그대로 |
| RiskBadgeLakeDao | RiskBadgeRepository | dimensions JSONB → DuckDB `json_extract` 파싱해 기존 `Map<String,Any>`로 |
| SectorLakeDao | SectorAggregateRepository | |
| MarketRefLakeDao | ExchangeRateRepository, RiskFreeRateRepository | |
| DashboardLakeDao | DashboardQueryRepository | `search(filter): DashboardPage`, `dataFreshness()` |

구현 규칙:
- 모든 SQL은 `LakeTableResolver.ref(table)`로 테이블 참조. `daily_prices`·`financial_statements` 쿼리에는 반드시 `market` 필터(파티션 프루닝), 날짜 조회에는 date 범위 필터.
- 반환 타입은 기존 엔티티 클래스 그대로 매핑(필드명 동일 → 서비스 무변경). enum은 `Market.valueOf(rs.getString(...))`.
- `dataFreshness()`는 SQL 대신 `run-summary/calc_kr.json`·`calc_us.json`을 S3KvStore-외부 경로로 GetObject(전용 메서드)해 `written_at_utc`를 기존 DataFreshnessResponse 4필드에 매핑(가격/재무 구분이 없어지므로 kr/us 각각 동일값 — DTO 구조 유지).
- `DashboardLakeDao.search`: 기존 native SQL을 DuckDB 방언으로 재작성. 스냅샷 테이블 단순화(LATERAL 제거), 가격 최신 2건은 윈도우, JSONB 필터는 `json_extract_string(dimensions, '$.dims.<dim>')`(**구현 시 기존 코드의 실제 JSON 구조를 읽고 맞출 것**), ILIKE는 DuckDB 지원 그대로.
- 테스트 시임: DAO는 SQL 문자열을 `LakeTableResolver.ref()` 결과로 조립하므로, 테스트에서는 리졸버를 로컬 임시 테이블명 반환으로 바꿔치기 — in-memory DuckDB에 fixture 테이블(`CREATE TABLE stocks AS SELECT ...`)을 만들어 SQL 정확성 검증(자격증명 불필요, CI에서 항상 실행).

- [ ] **Step 1: 실패 테스트** — DAO별 로컬 DuckDB fixture 테스트. 필수 케이스: stocks symbol+market 조회, daily_prices 기간 조회 정렬, top2 윈도우(2건 미만 종목), screener search(마켓+티어 필터, 페이지네이션, 검색어 ILIKE), risk_badges dimensions 파싱, benchmark top2, exchange_rate `dateLessThanEqual` 폴백.
- [ ] **Step 2:** FAIL → DAO 구현(파일별 커밋 가능) → PASS.
- [ ] **Step 3:** 서비스 의존 교체(시그니처 유지, `feature/**` 서비스 로직 무변경 원칙) → `./gradlew test` 그린.
- [ ] **Step 4:** Commit `260809_AddLakeDaosReplaceMarketJpa_kyoungin` (규모 크면 DAO/리와이어 2커밋)

### Task 9: calc 클라이언트 계약 변경

**Files:**
- Modify: `feature/simulation/service/SimulationService.kt`, `feature/llm/service/PortfolioLlmService.kt`,
  `feature/portfolio/controller/PortfolioController.kt` (full-analysis 호출부)
- Test: 각 호출부 단위 테스트(CalcServerClient 목킹)

**Interfaces (Consumes):** calc 스펙 §8.1 —
`POST /internal/portfolios/full-analysis`·`POST /internal/portfolios/simulation` 바디:
```json
{"market_group": "KR", "holdings": [{"symbol": "...", "market": "KR_KOSPI", "shares": 1.0,
  "avg_price": 10000, "currency": "KRW", "purchased_at": "2026-01-01", "purchase_fx_rate": null}]}
```
holdings는 PortfolioStore 문서 + StockLakeDao(symbol/market 해석)로 구성. 포트폴리오 simulation 경로에서 `{id}` 제거. `GET /internal/stocks/{symbol}/simulation`, `price-lookup` 불변.

- [ ] **Step 1: 실패 테스트** — 목킹 테스트: full-analysis 호출 시 바디에 market_group·holdings 포함 검증, 빈 포트폴리오 시 기존 동작(예외 or 빈 응답) 유지 검증.
- [ ] **Step 2:** FAIL → 구현 → PASS.
- [ ] **Step 3:** Commit `260809_UpdateCalcClientContract_kyoungin`

### Task 10: 프로필 이미지 S3 + presigned URL

**Files:**
- Modify: `infra/storage/service/ProfileImageService.kt` (Supabase → S3)
- Create: `infra/storage/s3/S3ProfileImageClient.kt` (upload/delete/presign, `app/profile-images/{userId}.{ext}`)
- Modify: `infra/user/service/ProfileService.kt`·응답 DTO 조립부 — profileImageKey 저장, 응답 시 presign(1h)
- Delete: `infra/storage/lib/SupabaseStorageClient.kt`, `SupabaseStorageProperties.kt`
- Modify: `application.properties` supabase.* 제거

- [ ] **Step 1: 실패 테스트** — 통합(가드): 업로드→presigned GET url로 실제 HTTP 200 확인→delete. ProfileService 단위: 응답에 presigned URL 포함.
- [ ] **Step 2:** FAIL → 구현 → PASS. OAuth 아바타 `uploadFromUrl` 흐름 유지.
- [ ] **Step 3:** Commit `260809_MigrateProfileImageToS3_kyoungin`

### Task 11: JPA 완전 제거 + 설정 정리 + 로컬 완주 검증

**Files:**
- Modify: `build.gradle.kts` — `plugin.jpa`, `starter-data-jpa`, `postgresql` 제거
- Delete: 남은 엔티티의 JPA 어노테이션 제거(시장 엔티티는 플레인 데이터 클래스화, `@IdClass`/`Persistable` 제거), 미사용 리포지토리·`EncryptionConverter`(컨버터 자체; AesEncryptor는 유지), predictions/ml_models 관련 참조 없음 확인
- Modify: `application.properties` — datasource/hikari/jpa 블록 삭제, `aws.ses.region=${AWS_SES_REGION}` 수정
- Modify: `RefreshTokenService`·`VerificationCodeCleanupScheduler` 등 스케줄러 구조화 로그 최종 확인

- [ ] **Step 1:** 의존·설정 제거, 컴파일 에러 나는 잔여 JPA 참조 일괄 정리.
- [ ] **Step 2:** `./gradlew test` 전체 그린.
- [ ] **Step 3: 로컬 서버 완주 검증(§5 task-stage DoD)** — `scripts/run-with-env.ps1`로 bootRun 기동(실 S3 사용) 후 curl: `/api/auth/signup`(신규 이메일) → 508 검증코드 흐름은 SES 실발송이므로 `send-verification` 호출 성공(202/200)까지, `/api/auth/login`, `/api/user/me`, `/api/portfolios/price-lookup`(calc 미가동 시 5xx 허용·로그 확인), `/api/dashboard/stocks`(warehouse 미적재 시 빈 결과 or 명시적 오류 — 빈 결과 처리 확인). 결과를 status 문서에 기록.
- [ ] **Step 4:** Commit `260809_RemoveJpaFinalizeConfig_kyoungin`

### Task 12: Dockerfile + caddy 로컬 구성

**Files:**
- Modify: `Dockerfile` — builder는 `FROM --platform=$BUILDPLATFORM eclipse-temurin:25-jdk`(JAR 크로스 빌드), runtime은 타깃 arm64 `eclipse-temurin:25-jre`. `DUCKDB_EXT_DIR=/duckdb-ext` env, `/tmp/duckdb` 생성.
- Create: `deploy/caddy/Caddyfile`:
```
{$CADDY_DOMAIN} {
    reverse_proxy localhost:8080
}
```

- [ ] **Step 1:** `docker build --platform linux/amd64 -t gw-local .` 후 `docker run --env-file .env -e SARAMQUANT_S3_BUCKET_NAME=saramquant-bucket ... -p 8080:8080` 기동, `/api/dashboard/sectors` 등 스모크 200/정상 오류 확인 (AWS_ACCESS_KEY_ID 매핑 주의).
- [ ] **Step 2:** Commit `260809_UpdateDockerfileAddCaddy_kyoungin`

### Task 13: Terraform 인프라

**Files (Create, `infra/`):**
- `backend.tf` — S3 backend `saramquant-tfstate`, key `gateway/terraform.tfstate`, region ap-northeast-2, `use_lockfile = true`, `encrypt = true`
- `providers.tf` — aws `~> 6.0`, `default_tags { project = "saramquant" }`
- `variables.tf` — 기본값 없는 변수 + validation(누락 시 GH Variable/Secret 이름 안내). 목록: 앱 시크릿 전체(§Task 14 표), `image_tag`, `caddy_domain`
- `vpc.tf` — VPC 10.20.0.0/16, 퍼블릭 서브넷 2개(a/c), IGW, 라우트, S3 Gateway 엔드포인트
- `sg.tf` — ingress 80/443 from 0.0.0.0/0, egress all
- `ec2.tf` — `aws_instance` t4g.small, ECS-optimized ARM AMI(SSM `/aws/service/ecs/optimized-ami/amazon-linux-2023/arm64/recommended` data source, `ignore_changes=[ami]`), user_data로 ECS_CLUSTER 조인 + `/duckdb-ext`·`/caddy-data` 디렉토리 생성, `aws_eip` + association, 루트 볼륨 gp3 30GB
- `iam.tf` — 인스턴스 롤: `AmazonEC2ContainerServiceforEC2Role` 관리형 + 인라인(S3 `app/*` RW·`warehouse/*`,`run-summary/*` RO·버킷 List, Glue GetTable/GetDatabase(saramquant 한정), SSM 파라미터 `/saramquant/gateway/*` 읽기), task execution role(ECR pull, logs, SSM secrets)
- `ecr.tf` — `saramquant-gateway` 리포, 라이프사이클 최근 3개
- `ecs.tf` — 클러스터 `saramquant`, taskdef(호스트 네트워크, gateway 컨테이너 메모리 1280 hard + caddy 128, awslogs, gateway env/secrets, caddy `caddy:2-alpine` + `/caddy-data` 볼륨 + CADDY_DOMAIN env + command로 Caddyfile 인라인(`caddy reverse-proxy --from {$CADDY_DOMAIN} --to localhost:8080`)), 서비스 desired 1, `deployment_minimum_healthy_percent = 0`
- `ssm.tf` — `for_each`로 시크릿들을 `/saramquant/gateway/<NAME>` SecureString 등록
- `logs.tf` — `/saramquant/gateway` 30일
- `outputs.tf` — `eip_public_ip`, `ecr_repo_url`
- `tf` (bash 래퍼: CI 환경변수 `GITHUB_ACTIONS` 없으면 plan/apply/destroy/state 계열 차단), `Makefile` — `check: terraform fmt -check -recursive && terraform init -backend=false && terraform validate`
- `.gitignore`에 `.terraform/` 추가

**주의:** `saramquant-tfstate` 버킷은 calc 세션과 공유 — 존재하면 그대로 backend init, 없으면 CI 첫 실행 전 수동 부트스트랩 스텝이 deploy.yml에 포함(`aws s3api create-bucket` idempotent guard).

- [ ] **Step 1:** 파일 작성 → `make check` 통과.
- [ ] **Step 2:** Commit `260809_AddTerraformInfra_kyoungin`

### Task 14: deploy.yml + GitHub 변수·시크릿 정리

**Files:**
- Create: `.github/workflows/deploy.yml`

플로우 (PR: plan only / main push: plan+apply, `concurrency: terraform-state-gateway, cancel-in-progress: false`):
1. checkout → JDK 25 → `./gradlew test`(통합 테스트용 SARAMQUANT env 주입) → `make check`
2. aws credentials(v4 액션, 시크릿 키) → `sts get-caller-identity`
3. tfstate 버킷 존재 보장(us-east-1 아님 주의, ap-northeast-2, idempotent)
4. 이미지 태그 = `hashFiles('Dockerfile','src/**','build.gradle.kts')` 앞 12자 → ECR 리포 targeted apply → `docker buildx build --platform linux/arm64 --push`(태그 존재 시 스킵, QEMU setup)
5. `terraform plan -out` (TF_VAR_* 주입) → main이면 `apply`
6. apply 후 `terraform output eip_public_ip`를 잡 서머리에 출력

**GitHub 변수·시크릿 정리(gh CLI, `GH_CONFIG_DIR=C:/Users/a/.config/gh-personal`):**
- 신규 variable: `SARAMQUANT_S3_BUCKET_NAME=saramquant-bucket`, `GLUE_DATABASE=saramquant`, `CADDY_DOMAIN=api.saramquant.com`, `FRONTEND_REDIRECT_URL`·`CORS_ALLOWED_ORIGIN`(Vercel 도메인 — 사용자 확인 필요 시 status 문서에 기록 후 임시로 `https://saramquant.com` 계열), `CALC_SERVER_URL`(calc 세션 산출 대기 — 플레이스홀더 `https://pending.invalid` 등록 후 status 문서에 추적)
- 기존 variable 갱신: `GOOGLE_OAUTH_REDIRECT_URI`/`KAKAO_OAUTH_REDIRECT_URI` → `https://api.saramquant.com/login/oauth2/code/{google,kakao}`, `COOKIE_SECURE=true` (콘솔 등록은 사용자 액션 — status 문서에 명시)
- 시크릿으로 옮겨야 할 것(현재 variable에 평문 노출): `AWS_SES_SECRET_KEY`, `CLAUDE_API_KEY`, `OPENAI_API_KEY`, `HASH_SECRET`, `JWT_PRIVATE_KEY_BASE64`, OAuth secret 계열 — `gh secret set` 후 variable 삭제, deploy.yml은 secrets 참조

- [ ] **Step 1:** deploy.yml 작성 + gh로 변수/시크릿 정리 실행.
- [ ] **Step 2:** Commit `260809_AddDeployWorkflow_kyoungin`

### Task 15: 문서 — NameCheap/운영 가이드 + status 최종화

**Files:**
- Create: `docs/aws-deploy-guide.md` — ① NameCheap에서 `api.saramquant.com` A레코드를 EIP로 등록하는 절차(스크린샷 없이 단계 서술, TTL 권장), ② caddy가 Let's Encrypt 인증서를 자동 발급받는 조건(DNS 전파 후 첫 요청), ③ Google/Kakao redirect URI 프로덕션 등록 절차, ④ Vercel `GATEWAY_INTERNAL_URL` 변경, ⑤ CloudWatch 로그 보는 법(`/saramquant/gateway`), ⑥ 배포 트리거·롤백(이전 이미지 태그로 재배포) 방법
- Modify: `docs/temp/aws-migration-status.md` 최종 상태 갱신

- [ ] **Step 1:** 작성 → Commit `260809_AddDeployGuideDocs_kyoungin`

### Task 16: PR 게이트(리뷰 1회) → 머지 → 배포 → 완주 검증

- [ ] **Step 1:** `superpowers:requesting-code-review`로 **전체 diff 1회 리뷰** → 지적사항 해결. (사용자 지시: 리뷰는 이 1회뿐)
- [ ] **Step 2:** main 머지 전 로컬 전체 테스트 재실행 → PR 생성(한국어, 간결) → 머지(`GH_CONFIG_DIR` 사용).
- [ ] **Step 3:** GitHub Actions 배포 모니터링(`gh run watch`) → 실패 시 수정 커밋으로 fix-forward.
- [ ] **Step 4:** `terraform output eip_public_ip` 값을 **사용자에게 전달**하고 NameCheap A레코드 등록 요청(가이드 문서 링크). DNS 전파 전에는 EIP 직접 + Host 헤더로 검증 불가(caddy TLS) → 임시로 `curl --resolve api.saramquant.com:443:<EIP>`.
- [ ] **Step 5: 배포 환경 curl 완주(§5 PR-stage DoD)** — `https://api.saramquant.com`(또는 --resolve): 가입→`send-verification`→(SES 수신 확인은 사용자 메일이라 코드 하드확인 불가 시 로그로 발송 확인)→로그인→`/api/user/me`→포트폴리오 CRUD→로그아웃. 대시보드/종목 상세는 calc lake 적재 여부 확인 후: 적재 전이면 빈 응답 정상 처리 확인만, 적재 후 실데이터 검증. 결과 증적을 status 문서에 기록.
- [ ] **Step 6:** `superpowers:verification-before-completion` 체크 후 사용자에게 완주 보고(남은 사용자 액션 목록 포함).

---

## Self-Review 결과

- 스펙 커버리지: §1(Task 12·13), §2(Task 3·8), §3(Task 2·4·5·6·7), §4(Task 9·10·11), §5(Task 13·14), §6(Task 6·7·11 구조화 로그), §7(각 태스크 테스트+Task 11·16), §8(Task 15·16) — 전부 매핑됨.
- 시그니처 일관성: S3KvStore·PageResult·LakeTableResolver를 후속 태스크가 동일 명칭으로 참조함을 확인.
- 미결 외부 의존: 신규 `CALC_SERVER_URL`(calc 세션), warehouse 적재(calc 세션), NameCheap A레코드·OAuth 콘솔(사용자) — Task 14·16에서 status 문서로 추적.
