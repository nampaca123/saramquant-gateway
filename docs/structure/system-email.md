# System Email

AWS SES + Thymeleaf 기반 시스템 이메일. 회원가입/탈퇴/재가입 시 자동 발송.

## 구조

```
infra/aws/lib/
├── AwsSesProperties.kt       @ConfigurationProperties(prefix = "aws.ses")
└── AwsSesClient.kt           SesV2Client 래퍼 — sendHtmlEmail(to, subject, html)

feature/systememail/
├── service/
│   └── SystemEmailService.kt 3종 @Async 발송 + AuditLog 기록
└── util/
    └── EmailTemplateRenderer.kt  Thymeleaf 렌더링 래퍼

resources/templates/email/
├── welcome.html
├── account-deactivated.html
└── account-reactivated.html
```

## 트리거

| 이벤트 | 메서드 | 호출 위치 |
|--------|--------|-----------|
| 회원가입 | `sendWelcomeEmail` | AuthService.oauthLogin, manualSignup |
| 탈퇴 | `sendDeactivationEmail` | UserController.deactivateAccount |
| 재가입 | `sendReactivationEmail` | AuthService.oauthLogin, manualLogin |

## 설정

```properties
aws.ses.region=${AWS_REGION}
aws.ses.access-key=${AWS_SES_ACCESS_KEY}
aws.ses.secret-key=${AWS_SES_SECRET_KEY}
aws.ses.sender-email=${AWS_SES_SENDER_EMAIL}
```

## 감사 로그

`audit_log` 테이블에 `action = "EMAIL"`, `metadata = {"type": "WELCOME|DEACTIVATION|REACTIVATION", "recipient": "..."}` 형태로 기록.

## 설계 원칙

- `@Async`: 이메일 발송이 비즈니스 로직을 블로킹하지 않음
- try-catch: 발송 실패 시 SLF4J warn 로그만 남기고 비즈니스 흐름 유지
- 단일 책임: AwsSesClient(전송) / EmailTemplateRenderer(렌더링) / SystemEmailService(오케스트레이션) 분리
