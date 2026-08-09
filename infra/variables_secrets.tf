# 여기 값들은 SSM SecureString으로 저장되어 taskdef `secrets`로만 주입된다(평문 environment 금지).

variable "google_oauth_client_secret" {
  description = "Google OAuth client secret."
  type        = string
  sensitive   = true

  validation {
    condition     = length(var.google_oauth_client_secret) > 0
    error_message = "google_oauth_client_secret must be non-empty. Set GitHub secret GOOGLE_OAUTH_CLIENT_SECRET."
  }
}

variable "kakao_oauth_login_secret" {
  description = "Kakao OAuth login client secret."
  type        = string
  sensitive   = true

  validation {
    condition     = length(var.kakao_oauth_login_secret) > 0
    error_message = "kakao_oauth_login_secret must be non-empty. Set GitHub secret KAKAO_OAUTH_LOGIN_SECRET."
  }
}

variable "jwt_private_key_base64" {
  description = "Base64 encoded JWT signing private key."
  type        = string
  sensitive   = true

  validation {
    condition     = length(var.jwt_private_key_base64) > 0
    error_message = "jwt_private_key_base64 must be non-empty. Set GitHub secret JWT_PRIVATE_KEY_BASE64."
  }
}

variable "hash_secret" {
  description = "AES key material used to encrypt PII in the S3 KV documents."
  type        = string
  sensitive   = true

  validation {
    condition     = length(var.hash_secret) > 0
    error_message = "hash_secret must be non-empty. Set GitHub secret HASH_SECRET."
  }
}

variable "gateway_auth_key" {
  description = "Shared x-api-key the frontend sends to the gateway."
  type        = string
  sensitive   = true

  validation {
    condition     = length(var.gateway_auth_key) > 0
    error_message = "gateway_auth_key must be non-empty. Set GitHub secret GATEWAY_AUTH_KEY."
  }
}

variable "calc_auth_key" {
  description = "Shared x-api-key the gateway sends to the calc API."
  type        = string
  sensitive   = true

  validation {
    condition     = length(var.calc_auth_key) > 0
    error_message = "calc_auth_key must be non-empty. Set GitHub secret CALC_AUTH_KEY."
  }
}

variable "claude_api_key" {
  description = "Anthropic API key."
  type        = string
  sensitive   = true

  validation {
    condition     = length(var.claude_api_key) > 0
    error_message = "claude_api_key must be non-empty. Set GitHub secret CLAUDE_API_KEY."
  }
}

variable "openai_api_key" {
  description = "OpenAI API key used as the LLM fallback."
  type        = string
  sensitive   = true

  validation {
    condition     = length(var.openai_api_key) > 0
    error_message = "openai_api_key must be non-empty. Set GitHub secret OPENAI_API_KEY."
  }
}

variable "aws_ses_access_key" {
  description = "Access key id of the dedicated SES sending user."
  type        = string
  sensitive   = true

  validation {
    condition     = length(var.aws_ses_access_key) > 0
    error_message = "aws_ses_access_key must be non-empty. Set GitHub secret AWS_SES_ACCESS_KEY."
  }
}

variable "aws_ses_secret_key" {
  description = "Secret access key of the dedicated SES sending user."
  type        = string
  sensitive   = true

  validation {
    condition     = length(var.aws_ses_secret_key) > 0
    error_message = "aws_ses_secret_key must be non-empty. Set GitHub secret AWS_SES_SECRET_KEY."
  }
}
