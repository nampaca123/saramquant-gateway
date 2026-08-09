# AWS 배포·운영 가이드

saramquant-gateway는 EC2(t4g.small) 1대 위에서 ECS(EC2 launch type)가 `gateway`(스프링 부트) + `caddy`(TLS 리버스 프록시) 컨테이너를 함께 띄우는 구조다. 배포는 `main` 브랜치 push 시 GitHub Actions(`​.github/workflows/deploy.yml`)가 Terraform(`infra/`)으로 전부 수행하며, 이 문서는 인프라 코드가 건드리지 않는 **콘솔·DNS·서드파티 설정**만 다룬다.

- 클러스터: `saramquant` / 서비스·태스크 패밀리: `saramquant-gateway`
- 도메인: `api.saramquant.com` (caddy가 TLS 종료, 뒤에서 gateway는 8080 포트)
- 로그 그룹: `/saramquant/gateway`

---

## 1. NameCheap에서 `api.saramquant.com` A레코드 등록

인프라는 고정 IP(Elastic IP, EIP) 1개로 뜬다. 이 IP를 NameCheap에서 A레코드로 등록해야 caddy가 인증서를 받고 요청을 받는다.

### 1-1. EIP 값 확인
`main`에 push되어 `deploy` 워크플로가 성공하면, 해당 워크플로 실행의 **Summary** 탭에 `### Gateway EIP` 아래 IP가 출력된다(`deploy.yml`의 "Publish EIP" 스텝). GitHub 저장소 → **Actions** → 가장 최근 `deploy` 실행 → 상단 Summary에서 확인.

### 1-2. NameCheap 등록 절차
1. NameCheap 로그인 → **Domain List** → `saramquant.com` → **Manage**
2. **Advanced DNS** 탭 이동
3. **Add New Record** 클릭 → 다음과 같이 입력
   - Type: `A Record`
   - Host: `api`
   - Value: 1-1에서 확인한 EIP (예: `3.35.x.x`)
   - TTL: `Automatic`
4. 저장(체크 버튼)

### 1-3. 전파 확인
DNS 전파는 보통 몇 분~수십 분 걸린다. 로컬에서 확인:

```
nslookup api.saramquant.com
```

응답의 IP가 1-1에서 확인한 EIP와 일치하면 전파 완료. 일치하지 않으면 몇 분 뒤 재시도(로컬 DNS 캐시 때문에 늦게 반영될 수 있음).

> EIP는 인스턴스가 교체되지 않는 한 고정이다(`infra/ec2.tf`의 `aws_eip.gateway` + `aws_eip_association`). 재배포로 바뀌지 않으므로 A레코드는 최초 1회만 등록하면 된다.

---

## 2. Caddy Let's Encrypt 자동 발급

`infra/ecs.tf`의 `caddy` 컨테이너가 `caddy reverse-proxy --from api.saramquant.com --to localhost:8080` 명령으로 실행되며, 80/443 포트를 그대로 EC2에 바인딩한다(`network_mode = "host"`). 별도 설정 파일 없이 caddy 자체 ACME 클라이언트가 인증서를 발급한다.

### 발급 조건
- **1번 항목의 DNS 전파가 끝난 뒤**, `https://api.saramquant.com`으로 **첫 요청이 들어오는 시점**에 caddy가 Let's Encrypt에 인증서를 요청한다. DNS가 아직 EIP를 가리키지 않으면 ACME HTTP-01 챌린지가 실패한다.
- 발급받은 인증서는 `/caddy-data` 볼륨(EC2 호스트 경로, `infra/ecs.tf`의 `caddy-data` volume)에 저장되어 태스크가 재시작되어도 재발급 없이 재사용된다.

### 실패 시 재시도
caddy는 ACME 실패 시 자체적으로 지수 백오프(exponential backoff)로 재시도한다. 별도 조치 없이 DNS 전파가 끝나고 재요청이 들어오면 자동으로 성공한다.

### 로그 확인
CloudWatch 로그 그룹 `/saramquant/gateway`의 caddy 컨테이너 로그 스트림(`caddy/caddy/<task-id>` 형태, `awslogs-stream-prefix = "caddy"`)에서 확인. 확인 방법은 5번 항목 참고. `certificate obtained successfully` 또는 `obtaining certificate` 관련 로그로 발급 진행 상황을 볼 수 있다.

---

## 3. Google/Kakao 콘솔 프로덕션 redirect URI 등록

Gateway의 OAuth 콜백 엔드포인트는 고정 경로(`/login/oauth2/code/{provider}`)이므로, 등록할 URI는 다음과 같다(GitHub Variable `GOOGLE_OAUTH_REDIRECT_URI`/`KAKAO_OAUTH_REDIRECT_URI`에도 동일하게 설정되어 있음):

- Google: `https://api.saramquant.com/login/oauth2/code/google`
- Kakao: `https://api.saramquant.com/login/oauth2/code/kakao`

### Google Cloud Console
1. [Google Cloud Console](https://console.cloud.google.com/) → 해당 프로젝트 선택 → **API 및 서비스 → 사용자 인증 정보**
2. 사용 중인 OAuth 2.0 클라이언트 ID 클릭
3. **승인된 리디렉션 URI**에 위 Google URI 추가 → 저장

### Kakao Developers
1. [Kakao Developers](https://developers.kakao.com/) → 해당 애플리케이션 선택 → **카카오 로그인 → Redirect URI**
2. 위 Kakao URI 추가 → 저장
3. **카카오 로그인 → 활성화 설정**이 ON인지 확인(비활성 상태면 프로덕션에서도 로그인 실패)

기존 로컬 개발용 URI(`http://localhost:8080/...`)는 로컬 테스트를 계속할 계획이면 남겨두고, 위 프로덕션 URI를 추가로 등록하면 된다.

---

## 4. Vercel 환경변수 갱신

프론트엔드(Next.js, Vercel)는 `GATEWAY_INTERNAL_URL`을 통해 gateway로 API를 프록시한다(`docs/structure/cross-service-security.md` §1, `next.config.ts` rewrites). Vercel 프로젝트 대시보드 → **Settings → Environment Variables**에서:

| 변수 | 변경 |
|---|---|
| `GATEWAY_INTERNAL_URL` | → `https://api.saramquant.com` |

변경 후 **Redeploy**(또는 다음 push)로 반영해야 한다. 환경변수만 바꾸고 재배포하지 않으면 이전 빌드가 그대로 서빙된다.

> 참고: gateway 쪽 GitHub Variable `FRONTEND_REDIRECT_URL`(OAuth 로그인 후 리다이렉트 대상)과 `CORS_ALLOWED_ORIGIN`(허용 Origin)은 프로덕션 프론트엔드 도메인 `https://saramquant.com`으로 등록되어 있다. **프론트엔드를 다른 도메인(예: `https://<project>.vercel.app`)으로 서빙한다면 병합 전에 이 두 GitHub Variable을 실제 도메인으로 반드시 교체해야 한다** — 값이 틀리면 CORS가 모든 브라우저 요청을 막고 OAuth 로그인 리다이렉트가 엉뚱한 곳으로 간다(Repository → Settings → Secrets and variables → Actions → Variables).

---

## 5. CloudWatch 로그 확인

### 콘솔 경로
1. AWS 콘솔 로그인 → 리전 `ap-northeast-2`(서울) 확인
2. **CloudWatch → Log groups** → `/saramquant/gateway` 선택
3. 로그 스트림은 `gateway/gateway/<task-id>`(앱), `caddy/caddy/<task-id>`(caddy)로 구분된다(`infra/ecs.tf`의 `awslogs-stream-prefix`). 최신 스트림을 열면 실시간에 가까운 로그 확인 가능.
4. 보존 기간은 30일(`infra/logs.tf`의 `retention_in_days`).

### CloudWatch Logs Insights 구조화 로그 필터 예시
gateway는 스케줄러 실행마다 구조화 JSON 로그 1줄을 남긴다(예: `RefreshTokenService.cleanupExpired`가 `{"event":"refresh_token_cleanup","run_id":"...","status":"ok","deleted":0,"duration_ms":12}` 형태로 출력). Logs Insights에서 실패 건만 조회하는 예시:

```
fields @timestamp, @message
| filter @message like /"event":"refresh_token_cleanup"/
| filter @message like /"status":"error"/
| sort @timestamp desc
| limit 20
```

**Logs Insights** 진입: 로그 그룹 화면에서 **Insights 보기** 버튼, 또는 왼쪽 메뉴 **CloudWatch → Logs → Logs Insights**에서 로그 그룹으로 `/saramquant/gateway` 선택 후 위 쿼리 실행.

---

## 6. 배포 트리거·롤백

### 배포 트리거
`main` 브랜치에 push되면 자동 배포된다(`deploy.yml`의 `on.push.branches: [main]`). PR에서는 `terraform plan`까지만 실행되고 apply·이미지 빌드는 일어나지 않는다.

이미지 태그는 `Dockerfile` + `build.gradle.kts` + `settings.gradle.kts` + `src/**` 내용의 해시(앞 12자)로 결정된다. 같은 태그의 이미지가 ECR에 이미 있으면 빌드를 건너뛴다.

### 롤백
**정석은 `git revert` 후 push다.** 되돌린 커밋의 소스 내용은 이전 배포와 동일하므로 이미지 태그도 동일하게 계산되고, ECR에 그 태그가 이미 있으므로 재빌드 없이 바로 그 이미지로 재배포된다.

```
git revert <문제가 된 커밋>
git push origin main
```

### ECS 서비스 강제 재시작
코드 변경 없이 현재 태스크 정의로 태스크만 다시 띄우고 싶을 때(예: 컨테이너가 죽었는데 자동 복구가 안 됐을 때) 사용. 로컬 AWS CLI는 데스크톱 기본 프로필이 아니라 `.env`의 `SARAMQUANT_IAM_KEY_ACCESS`/`SARAMQUANT_IAM_KEY_SECRET`을 `AWS_ACCESS_KEY_ID`/`AWS_SECRET_ACCESS_KEY`로 매핑해서 써야 한다(CLAUDE.md 규칙, `scripts/run-with-env.ps1` 참고).

```
aws ecs update-service \
  --cluster saramquant \
  --service saramquant-gateway \
  --region ap-northeast-2 \
  --force-new-deployment
```

단일 호스트 구성이라(`deployment_minimum_healthy_percent = 0`) 기존 태스크를 먼저 내린 뒤 새 태스크를 올린다 — 재시작 도중 짧은 다운타임이 발생한다.
