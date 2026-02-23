# Cross-Service Security Architecture

## 전체 흐름

```
Browser ──▶ Next.js (Proxy) ──▶ Gateway (Spring Boot) ──▶ Calc Server (Flask)
                                       │                        │
                                       │                   Railway Private Network
                                       ▼                        │
                                   Supabase (RLS)               ▼
                                                     USA FS Collector (Flask)
```

각 서비스 경계마다 독립적인 보안 계층이 존재하며, 단일 계층이 뚫려도 다음 계층에서 차단되는 **Defense in Depth** 구조다.

---

## 1. Next.js Reverse Proxy

### 목적

Gateway의 실제 URL을 브라우저에 노출하지 않는다.

### 동작

`next.config.ts`의 `rewrites`로 모든 API 요청을 내부 Gateway로 프록시한다.

```
/api/:path*         → ${GATEWAY_INTERNAL_URL}/api/:path*
/oauth2/:path*      → ${GATEWAY_INTERNAL_URL}/oauth2/:path*
/login/oauth2/:path* → ${GATEWAY_INTERNAL_URL}/login/oauth2/:path*
```

| 항목 | 값 |
|---|---|
| `GATEWAY_INTERNAL_URL` | 배포 시 Railway 내부 DNS, 로컬은 `localhost:8080` |
| 방식 | 서버 사이드 프록시 (브라우저는 `/api/*`만 인식) |

### 보안 효과

- 브라우저 DevTools에서 Gateway 주소 확인 불가
- Gateway가 공개 네트워크에 노출되어도 직접 접근 경로를 숨김

---

## 2. X-Gateway-Auth-Key (Frontend → Gateway)

### 목적

Gateway가 허가된 클라이언트(Next.js)에서 온 요청만 수락하도록 한다.

### 동작

| 구분 | 내용 |
|---|---|
| 헤더 이름 | `X-Gateway-Auth-Key` |
| 전송 주체 | Next.js API Client (`src/lib/api/client.ts`) |
| 검증 주체 | Gateway `GatewayAuthFilter` |
| 대상 | `/api/**` 경로 전체 (OPTIONS 제외) |
| 실패 응답 | `403 Forbidden` |

```
Browser → fetch('/api/...', { headers: { 'X-Gateway-Auth-Key': key } })
       → Next.js Proxy → Gateway (GatewayAuthFilter에서 검증)
```

> **참고**: `NEXT_PUBLIC_GATEWAY_AUTH_KEY`로 클라이언트에 노출되지만, Reverse Proxy + CORS 정책과 결합하여 외부 직접 호출을 차단한다.

---

## 3. CORS (Gateway)

### 목적

허가된 Origin(Next.js 도메인)만 API를 호출할 수 있도록 제한한다.

### 설정

| 항목 | 값 |
|---|---|
| Allowed Origin | `${CORS_ALLOWED_ORIGIN}` (단일 도메인) |
| Allowed Methods | GET, POST, PATCH, DELETE, OPTIONS |
| Allowed Headers | `Content-Type`, `X-Gateway-Auth-Key` |
| Credentials | `true` (쿠키 전송 허용) |
| 적용 범위 | `/**` |

`allowCredentials: true` + 단일 Origin 조합으로 와일드카드(`*`)가 아닌 명시적 Origin만 허용한다.

---

## 4. Rate Limiting (Gateway)

### 목적

단일 IP에서의 과도한 요청을 차단하여 DDoS 및 Brute Force를 방어한다.

### 구현

| 항목 | 값 |
|---|---|
| 알고리즘 | Token Bucket (Bucket4j) |
| 버스트 용량 | 10 tokens |
| 충전 속도 | 5 tokens/sec |
| 키 | 클라이언트 IP |
| IP 추출 | `X-Forwarded-For` → `X-Real-IP` → `CF-Connecting-IP` → `remoteAddr` |
| 초과 응답 | `429 Too Many Requests` + `Retry-After: 1` |
| 메모리 관리 | 1시간마다 미사용 버킷 정리 |

### 필터 실행 순서

```
GatewayAuthFilter → RateLimitFilter → JwtAuthenticationFilter → AuditLogFilter
```

인증 키 검증 직후, JWT 검증 **이전에** Rate Limit을 적용하여 무효 요청의 JWT 파싱 비용을 절감한다.

---

## 5. JWT 인증 (Gateway)

### 목적

Stateless 인증으로 유저 세션을 관리한다.

### 토큰 구성

| 토큰 | TTL | 쿠키 이름 | Path | 용도 |
|---|---|---|---|---|
| Access Token | 15분 | `__sq_at` | `/api` | API 인가 |
| Refresh Token | 7일 | `__sq_rt` | `/api/auth` | AT 갱신 |

### 쿠키 보안 속성

| 속성 | 값 | 효과 |
|---|---|---|
| HttpOnly | `true` | JavaScript에서 접근 불가 (XSS 방어) |
| Secure | 배포 시 `true` | HTTPS에서만 전송 |
| SameSite | `Lax` | Cross-site POST에서 쿠키 미전송 (CSRF 완화) |

### 서명 알고리즘

RS256 (RSA-SHA256). 비대칭 키 방식으로 Private Key는 토큰 발급에만, Public Key는 검증에만 사용한다.

### 자동 갱신

프론트엔드 API Client가 `401` 응답 시 자동으로 `/api/auth/refresh`를 호출하고, 실패하면 `sq:auth-expired` 이벤트로 로그아웃 처리한다.

---

## 6. Calc Server 보안 (Private Subnet + API Key)

### 목적

Calc Server를 인터넷에서 완전히 격리하고, Gateway만 접근 가능하도록 한다.

### 이중 보안 계층

```
Internet ──✕──▶ Calc Server   (Private Subnet으로 외부 접근 차단)
Gateway  ──────▶ Calc Server   (x-api-key 헤더로 인증)
```

#### 계층 1: Railway Private Networking

| 항목 | 내용 |
|---|---|
| 네트워크 | Railway 내부 Private Network |
| 접근 가능 대상 | 같은 Railway 프로젝트 내 서비스만 |
| 외부 접근 | 불가 (Public Domain 미할당) |
| 내부 DNS | `calc-server.railway.internal` 형식 |

#### 계층 2: x-api-key 헤더 검증

| 항목 | 내용 |
|---|---|
| 헤더 이름 | `x-api-key` |
| 검증 방식 | `hmac.compare_digest()` (타이밍 공격 방지) |
| 대상 경로 | `/internal/**` |
| 비대상 | `/health` |
| 실패 응답 | `401 Unauthorized` |

Gateway의 `CalcServerClient`가 `RestClient.defaultHeader("x-api-key", authKey)`로 모든 요청에 자동 첨부한다.

---

## 7. USA FS Collector (미국 동부 마이크로서비스)

### 목적

미국 재무제표 데이터를 수집하는 별도 마이크로서비스. 미국 API 레이턴시 최적화를 위해 US East 리전에 배포한다.

### 이중 보안 계층

```
Internet ──✕──▶ USA FS Collector   (Private Subnet으로 외부 접근 차단)
Calc Server ───▶ USA FS Collector   (x-api-key 헤더로 인증, Private Network 경유)
```

#### 계층 1: Railway Private Networking (Cross-Region)

| 항목 | 내용 |
|---|---|
| 네트워크 | Railway 내부 Private Network |
| 리전 | US East (Calc Server와 다른 리전) |
| 내부 DNS | `saramquant-usa-fstatements-colle.railway.internal:8080` |
| 외부 접근 | 불가 (Public Domain 미할당) |

Railway Private Networking은 같은 프로젝트 내라면 **리전이 달라도 동작**한다. Calc Server(AP)에서 USA FS Collector(US East)로의 통신은 Railway 내부망을 통해 이루어진다.

#### 계층 2: x-api-key 헤더 검증

| 항목 | 내용 |
|---|---|
| 헤더 이름 | `x-api-key` |
| 환경변수 | `USA_FS_COLLECTOR_AUTH_KEY` |
| 보호 방식 | Calc Server만 API Key를 보유, Private Network 내 비인가 요청 차단 |

---

## 8. 데이터 보안 (Gateway)

### 암호화

| 대상 | 방식 | 필드 |
|---|---|---|
| email | AES-256-GCM + Blind Index | `email` (암호문), `email_hash` (검색용 HMAC) |
| 이름 | AES-256-GCM | `name` |
| OAuth ID | AES-256-GCM | `providerId` |
| 닉네임 | AES-256-GCM | `nickname` |
| 비밀번호 | BCrypt | `passwordHash` |

단일 `HASH_SECRET`에서 HMAC-SHA256으로 용도별 키(해싱용, 암호화용)를 파생한다.

### Supabase Row Level Security

전체 25개 테이블에 RLS가 활성화되어 있다. 앱은 `postgres` 역할로 접속하여 RLS를 바이패스하지만, Supabase REST API나 Client SDK를 통한 `anon`/`authenticated` 역할의 비인가 접근은 전면 차단된다.

---

## 보안 계층 요약

| 계층 | 위치 | 방어 대상 |
|---|---|---|
| Reverse Proxy | Next.js | Gateway URL 은닉 |
| X-Gateway-Auth-Key | Next.js → Gateway | 비인가 클라이언트 |
| CORS | Gateway | Cross-Origin 요청 |
| Rate Limiting | Gateway | DDoS, Brute Force |
| JWT (HttpOnly Cookie) | Gateway | 세션 탈취, XSS |
| Private Subnet | Railway | Calc Server, USA FS Collector 외부 접근 |
| x-api-key (HMAC) | Calc Server, USA FS Collector | Private Subnet 내 비인가 요청 |
| AES-256-GCM | Gateway → DB | DB 유출 시 개인정보 보호 |
| Row Level Security | Supabase | SDK 경유 비인가 DB 접근 |

---

## 환경변수 보안 키 목록

| 변수 | 서비스 | 용도 |
|---|---|---|
| `GATEWAY_AUTH_KEY` | Gateway | X-Gateway-Auth-Key 검증 |
| `NEXT_PUBLIC_GATEWAY_AUTH_KEY` | Web | Gateway 인증 키 (클라이언트) |
| `CALC_AUTH_KEY` | Gateway, Calc Server | Calc Server API 인증 |
| `USA_FS_COLLECTOR_AUTH_KEY` | Calc Server | USA FS Collector API 인증 |
| `JWT_PRIVATE_KEY_BASE64` | Gateway | JWT 서명 (RS256) |
| `JWT_PUBLIC_KEY_BASE64` | Gateway | JWT 검증 (RS256) |
| `HASH_SECRET` | Gateway | AES/HMAC 키 파생 원본 |
| `COOKIE_SECURE` | Gateway | 쿠키 Secure 플래그 |
| `CORS_ALLOWED_ORIGIN` | Gateway | CORS 허용 Origin |
