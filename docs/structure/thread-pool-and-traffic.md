# 스레드, 스레드 풀, 트래픽 관리

> 서버가 요청을 "동시에" 처리하는 원리와, 요청이 몰릴 때 터지지 않게 관리하는 방법

---

## 1. 스레드란?

### 1-1. 비유: 식당의 직원

서버 = 식당, 요청 = 주문이라고 생각하면 된다.

```
식당(서버)에 손님(요청)이 들어온다.
직원(스레드)이 주문을 받고, 요리하고, 서빙한다.

직원이 1명이면? → 한 번에 하나의 주문만 처리 가능. 나머지 손님은 대기.
직원이 여러 명이면? → 동시에 여러 주문 처리 가능.
```

**스레드 = 서버 안에서 일을 처리하는 일꾼 하나.**

프로그램은 기본적으로 "메인 스레드" 1개로 시작하지만, 동시에 여러 요청을 처리하려면 스레드를 여러 개 만들어야 한다.

### 1-2. Spring Boot에서의 스레드

Spring Boot의 내장 톰캣은 기본적으로 **200개의 스레드**를 준비한다.

```
사용자 A의 요청 → 톰캣 스레드 #1이 처리
사용자 B의 요청 → 톰캣 스레드 #2가 처리
사용자 C의 요청 → 톰캣 스레드 #3이 처리
... 동시에 최대 200개까지 병렬 처리
```

이 "미리 만들어둔 스레드 묶음"이 바로 **스레드 풀**이다.

---

## 2. 스레드 풀이란?

### 2-1. 왜 풀(Pool)이 필요한가

스레드를 매번 새로 만들고 버리면 비용이 크다.

```
[스레드 없이 매번 생성]
요청 → 스레드 생성(비용 큼) → 작업 → 스레드 파괴(비용 큼)
요청 → 스레드 생성(비용 큼) → 작업 → 스레드 파괴(비용 큼)

[스레드 풀 사용]
서버 시작 시 → 스레드 4개를 미리 생성해두고 재사용
요청 → 놀고 있는 스레드 배정 → 작업 → 스레드 반납(파괴 안 함)
요청 → 놀고 있는 스레드 배정 → 작업 → 스레드 반납
```

비유: 배달 앱에서 주문마다 라이더를 채용하고 해고하는 것(스레드 생성/파괴)보다, 라이더 N명을 미리 계약해두고(풀) 돌려 쓰는 게 훨씬 효율적이다.

### 2-2. 풀의 핵심 설정 3가지

```kotlin
ThreadPoolTaskExecutor().apply {
    corePoolSize = 4      // 기본 직원 수 (항상 대기)
    maxPoolSize = 8       // 바쁠 때 늘릴 수 있는 최대 직원 수
    queueCapacity = 20    // 직원이 다 바쁠 때, 대기열 크기
}
```

이걸 식당 비유로 풀면:

```
corePoolSize = 4   → 평소에 항상 일하는 직원 4명
maxPoolSize = 8    → 피크 시간에 최대 8명까지 추가 투입 가능
queueCapacity = 20 → 직원 8명이 다 바빠도, 20명까지는 줄 서서 대기 가능

만약 직원 8명 다 바쁘고 + 대기 줄 20명도 꽉 차면?
→ 새 요청은 거부됨 (RejectedExecutionException)
```

---

## 3. 우리 코드에서 뭐가 문제였나

### 3-1. CompletableFuture.supplyAsync의 함정

원래 코드:

```kotlin
CompletableFuture.supplyAsync {
    generateAndCache(...)  // LLM 호출 (최대 90초) + DB 저장
}
```

`supplyAsync`에 executor를 안 넘기면 **ForkJoinPool.commonPool()** 을 사용한다.

#### ForkJoinPool.commonPool()의 문제

| 특성 | 값 |
|------|-----|
| 스레드 수 | CPU 코어 - 1 (보통 3~7개) |
| 용도 | JVM 전체가 공유하는 범용 풀 |
| Spring 관리 | X (Spring이 모르는 스레드) |

이걸 식당 비유로 보면:

```
[상황]
식당 전체(JVM)에 공용 직원 3명이 있다.
LLM 분석 요청이 들어오면 이 공용 직원이 처리한다.

[문제]
LLM 호출은 90초까지 걸릴 수 있다.
공용 직원 3명이 전부 LLM 호출에 묶이면?
→ 다른 작업(parallel stream 등)도 전부 멈춤
→ 3명 다 바쁜 상태에서 DB save()를 해야 하는데, 커넥션 경합 발생
```

### 3-2. Spring이 모르는 스레드에서 DB 접근

핵심 개념: Spring의 JPA(`EntityManager`)는 **스레드 로컬(Thread-Local)** 기반이다.

```
[톰캣 스레드에서 DB 접근]
요청 시작 → Spring이 EntityManager를 이 스레드에 바인딩
         → save() 호출 → EntityManager 정상 사용
         → 요청 끝나면 EntityManager 정리

[ForkJoinPool 스레드에서 DB 접근]
Spring: "이 스레드 누구임? 나 모르는 스레드인데?"
      → EntityManager 바인딩 없음
      → save()는 자체 @Transactional이 있어서 어찌어찌 되긴 하지만...
      → 커넥션 풀 고갈 시, 타이밍 이슈로 간헐적으로 터짐
```

"잘 되다가 갑자기 터지는" 이유가 이것이다. 평소에는 우연히 커넥션이 있어서 되지만, 동시 요청이 몇 개만 겹쳐도 실패한다.

### 3-3. cacheKey 버그 (PortfolioLlmService)

```kotlin
// 버그: 매번 다른 키 생성 → 중복 방지 불가능
val cacheKey = "$portfolioId:$today:$preset:$lang:${System.nanoTime()}"

// 수정: 동일 조건이면 같은 키 → 동시에 같은 요청이 오면 하나만 실행
val cacheKey = "$portfolioId:$today:$preset:$lang"
```

`inFlight`의 `computeIfAbsent`는 **같은 키가 이미 있으면 기존 Future를 재사용**하는 구조다.
키에 `System.nanoTime()`이 들어가면 매번 유니크 → 중복 방지가 0% → 같은 분석을 동시에 N번 호출.

---

## 4. 수정된 구조

### 4-1. 전용 스레드 풀 분리

```
[수정 전]
LLM 호출 ──→ ForkJoinPool.commonPool() (JVM 공유, 3~7개, Spring 무관)
                  ↓
              DB save() → 커넥션 경합 → 간헐적 에러

[수정 후]
LLM 호출 ──→ llmExecutor (LLM 전용, 4~8개, Spring 관리)
                  ↓
              DB save() → 안정적인 커넥션 확보
```

```kotlin
// LlmExecutorConfig.kt
@Bean("llmExecutor")
fun llmExecutor(): Executor = ThreadPoolTaskExecutor().apply {
    corePoolSize = 4       // LLM용 전담 스레드 4개
    maxPoolSize = 8        // 피크 시 최대 8개
    queueCapacity = 20     // 대기열 20개
    setThreadNamePrefix("llm-")  // 로그에서 "llm-1", "llm-2"로 식별 가능
}
```

### 4-2. Executor 주입

```kotlin
// PortfolioLlmService.kt
CompletableFuture.supplyAsync(
    { generateAndCache(...) },
    llmExecutor  // ← ForkJoinPool 대신 전용 풀 사용
)
```

두 번째 인자로 executor를 넘기면, 해당 풀의 스레드에서 작업이 실행된다.

---

## 5. 트래픽 관리의 기본 원칙

### 5-1. 리소스에는 한계가 있다

우리 서버의 리소스 한계:

```
┌─────────────────────────────────────────┐
│  톰캣 스레드:    200개 (HTTP 요청 처리)    │
│  HikariCP:      10개  (DB 커넥션)        │
│  LLM Executor:  4~8개 (AI 분석 처리)     │
└─────────────────────────────────────────┘
```

200명이 동시에 요청해도 톰캣은 처리할 수 있지만,
그 중 11명이 동시에 DB를 쓰면 HikariCP가 부족해진다.
그 중 9명이 동시에 LLM 분석을 요청하면 llmExecutor 대기열에 쌓인다.

**병목은 항상 가장 좁은 지점에서 발생한다.**

### 5-2. 풀 사이징 계산법

```
[DB 커넥션 풀]
공식: connections = (core_count * 2) + disk_spindles
현실: 클라우드 DB는 대부분 SSD → disk_spindles = 1
예시: 2코어 서버 → (2 * 2) + 1 = 5개 ~ 10개면 충분

[LLM 스레드 풀]
LLM 호출은 CPU가 아니라 네트워크 I/O 대기가 대부분.
→ 코어 수보다 많아도 됨
→ 하지만 각 호출이 DB 커넥션 1개를 소비하므로,
   HikariCP 풀 크기(10)를 넘지 않도록 설정

결론: maxPoolSize(8) < hikari.maximum-pool-size(10) → 안전
```

### 5-3. 대기열(Queue) 전략

```
요청이 풀 용량을 넘으면 3가지 선택지:

1. 대기시킨다 (queueCapacity = 20)
   → 사용자가 기다리긴 하지만 결국 처리됨

2. 거부한다 (CallerRunsPolicy)
   → 호출한 쪽의 스레드에서 직접 실행 → 톰캣 스레드가 블로킹됨

3. 버린다 (AbortPolicy - 기본값)
   → RejectedExecutionException → 에러 응답

현재 설정: 8개 스레드 + 20개 대기열 = 동시 28개까지 수용
29번째부터 → 에러 → 프론트에서 "잠시 후 다시 시도" 안내
```

---

## 6. 한눈에 보는 요청 흐름

```
사용자 요청 (LLM 분석)
    │
    ▼
[톰캣 스레드 풀] (200개)
    │ analyze() 호출
    │
    ▼
[inFlight 체크] ── 같은 키가 이미 실행 중? ──→ 기존 Future 재사용 (중복 호출 방지)
    │                                              │
    │ 없으면                                        │
    ▼                                              │
[llmExecutor] (4~8개)                              │
    │ generateAndCache() 실행                       │
    │                                              │
    ├─ LLM API 호출 (최대 90초 대기)                  │
    │                                              │
    ├─ DB save() ─→ [HikariCP] (10개) ─→ PostgreSQL │
    │                                              │
    ▼                                              ▼
[톰캣 스레드] future.get()으로 결과 수신 ←─────────────┘
    │
    ▼
사용자에게 응답 반환
```

---

## 7. 핵심 정리

| 개념 | 한줄 설명 |
|------|----------|
| **스레드** | 서버 안에서 일을 처리하는 일꾼 1명 |
| **스레드 풀** | 일꾼을 미리 N명 고용해두고 돌려 쓰는 구조 |
| **ForkJoinPool** | JVM이 기본 제공하는 공유 풀. Spring과 무관해서 DB 접근에 위험 |
| **ThreadPoolTaskExecutor** | Spring이 관리하는 풀. DB 접근이 안전 |
| **HikariCP** | DB 커넥션을 미리 만들어두는 풀. 커넥션도 비싸니까 풀로 관리 |
| **inFlight** | 같은 분석 요청 중복 실행 방지용 Map |
| **병목** | 가장 좁은 리소스(보통 DB 커넥션)에서 터진다 |

> 풀의 핵심 원칙: **가장 느린 작업에 전용 풀을 두고, 그 풀의 크기를 하위 리소스(DB 커넥션) 한계 내로 제한한다.**
