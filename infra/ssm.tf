# for_each 키는 리터럴 이름 목록 — 민감값을 키로 쓰면 terraform이 for_each를 거부한다
locals {
  secret_names = [
    "GOOGLE_OAUTH_CLIENT_SECRET",
    "KAKAO_OAUTH_LOGIN_SECRET",
    "JWT_PRIVATE_KEY_BASE64",
    "HASH_SECRET",
    "GATEWAY_AUTH_KEY",
    "CALC_AUTH_KEY",
    "CLAUDE_API_KEY",
    "OPENAI_API_KEY",
    "AWS_SES_ACCESS_KEY",
    "AWS_SES_SECRET_KEY",
  ]

  secret_values = {
    GOOGLE_OAUTH_CLIENT_SECRET = var.google_oauth_client_secret
    KAKAO_OAUTH_LOGIN_SECRET   = var.kakao_oauth_login_secret
    JWT_PRIVATE_KEY_BASE64     = var.jwt_private_key_base64
    HASH_SECRET                = var.hash_secret
    GATEWAY_AUTH_KEY           = var.gateway_auth_key
    CALC_AUTH_KEY              = var.calc_auth_key
    CLAUDE_API_KEY             = var.claude_api_key
    OPENAI_API_KEY             = var.openai_api_key
    AWS_SES_ACCESS_KEY         = var.aws_ses_access_key
    AWS_SES_SECRET_KEY         = var.aws_ses_secret_key
  }
}

resource "aws_ssm_parameter" "gateway" {
  for_each = toset(local.secret_names)

  name  = "${local.ssm_prefix}/${each.value}"
  type  = "SecureString"
  value = local.secret_values[each.value]
}
