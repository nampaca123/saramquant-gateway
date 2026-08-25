# 기본값 없음 + validation — 값 누락 시 CI plan 단계에서 즉시 실패시킨다. 시크릿은 variables_secrets.tf 참조.

variable "region" {
  description = "AWS region for all gateway resources."
  type        = string

  validation {
    condition     = length(var.region) > 0
    error_message = "region must be non-empty. It is fixed as TF_VAR_region in .github/workflows/deploy.yml."
  }
}

variable "image_tag" {
  description = "ECR image tag for the gateway container."
  type        = string

  validation {
    condition     = length(var.image_tag) > 0
    error_message = "image_tag must be non-empty. deploy.yml sets TF_VAR_image_tag (hash of Dockerfile + src + build.gradle.kts)."
  }
}

variable "caddy_domain" {
  description = "Public hostname caddy terminates TLS for."
  type        = string

  validation {
    condition     = length(var.caddy_domain) > 0
    error_message = "caddy_domain must be non-empty. Set GitHub Variable CADDY_DOMAIN (e.g. api.saramquant.com)."
  }
}

variable "s3_bucket_name" {
  description = "Data lake / app KV bucket (already exists, not managed here)."
  type        = string

  validation {
    condition     = length(var.s3_bucket_name) > 0
    error_message = "s3_bucket_name must be non-empty. Set GitHub Variable SARAMQUANT_S3_BUCKET_NAME."
  }
}

variable "glue_database" {
  description = "Glue catalog database holding the Iceberg lake tables."
  type        = string

  validation {
    condition     = length(var.glue_database) > 0
    error_message = "glue_database must be non-empty. Set GitHub Variable GLUE_DATABASE."
  }
}

variable "google_oauth_client_id" {
  description = "Google OAuth client id."
  type        = string

  validation {
    condition     = length(var.google_oauth_client_id) > 0
    error_message = "google_oauth_client_id must be non-empty. Set GitHub Variable GOOGLE_OAUTH_CLIENT_ID."
  }
}

variable "google_oauth_redirect_uri" {
  description = "Google OAuth redirect URI registered in the Google console."
  type        = string

  validation {
    condition     = length(var.google_oauth_redirect_uri) > 0
    error_message = "google_oauth_redirect_uri must be non-empty. Set GitHub Variable GOOGLE_OAUTH_REDIRECT_URI."
  }
}

variable "kakao_oauth_rest_key" {
  description = "Kakao OAuth REST API key (acts as the client id)."
  type        = string

  validation {
    condition     = length(var.kakao_oauth_rest_key) > 0
    error_message = "kakao_oauth_rest_key must be non-empty. Set GitHub Variable KAKAO_OAUTH_REST_KEY."
  }
}

variable "kakao_oauth_redirect_uri" {
  description = "Kakao OAuth redirect URI registered in the Kakao console."
  type        = string

  validation {
    condition     = length(var.kakao_oauth_redirect_uri) > 0
    error_message = "kakao_oauth_redirect_uri must be non-empty. Set GitHub Variable KAKAO_OAUTH_REDIRECT_URI."
  }
}

variable "jwt_public_key_base64" {
  description = "Base64 encoded JWT public key."
  type        = string

  validation {
    condition     = length(var.jwt_public_key_base64) > 0
    error_message = "jwt_public_key_base64 must be non-empty. Set GitHub Variable JWT_PUBLIC_KEY_BASE64."
  }
}

variable "jwt_access_token_ttl" {
  description = "Access token TTL in seconds (e.g. 900). Access tokens are stateless and cannot be revoked."
  type        = string

  validation {
    condition     = can(tonumber(var.jwt_access_token_ttl)) && tonumber(var.jwt_access_token_ttl) > 0 && tonumber(var.jwt_access_token_ttl) <= 3600
    error_message = "jwt_access_token_ttl must be seconds in (0, 3600]. Set GitHub Variable JWT_ACCESS_TOKEN_TTL (e.g. 900)."
  }
}

variable "jwt_refresh_token_ttl" {
  description = "Refresh token TTL in seconds (e.g. 604800)."
  type        = string

  validation {
    condition     = can(tonumber(var.jwt_refresh_token_ttl)) && tonumber(var.jwt_refresh_token_ttl) > 0
    error_message = "jwt_refresh_token_ttl must be a positive number of seconds. Set GitHub Variable JWT_REFRESH_TOKEN_TTL (e.g. 604800)."
  }
}

variable "cookie_secure" {
  description = "Whether auth cookies are marked Secure (true in production)."
  type        = string

  validation {
    condition     = contains(["true", "false"], var.cookie_secure)
    error_message = "cookie_secure must be \"true\" or \"false\". Set GitHub Variable COOKIE_SECURE (true behind caddy TLS)."
  }
}

variable "cookie_domain" {
  description = "Domain attribute for auth cookies (e.g. .saramquant.com to share across api/www)."
  type        = string

  validation {
    condition     = length(var.cookie_domain) > 0
    error_message = "cookie_domain must be non-empty. Set GitHub Variable COOKIE_DOMAIN (e.g. .saramquant.com)."
  }
}

variable "frontend_redirect_url" {
  description = "Frontend URL users land on after OAuth login."
  type        = string

  validation {
    condition     = length(var.frontend_redirect_url) > 0
    error_message = "frontend_redirect_url must be non-empty. Set GitHub Variable FRONTEND_REDIRECT_URL."
  }
}

variable "cors_allowed_origin" {
  description = "Allowed CORS origin for browser calls."
  type        = string

  validation {
    condition     = length(var.cors_allowed_origin) > 0
    error_message = "cors_allowed_origin must be non-empty. Set GitHub Variable CORS_ALLOWED_ORIGIN."
  }
}

variable "calc_server_url" {
  description = "Base URL of the calc-server API."
  type        = string

  validation {
    condition     = length(var.calc_server_url) > 0
    error_message = "calc_server_url must be non-empty. Set GitHub Variable CALC_SERVER_URL."
  }
}

variable "aws_ses_region" {
  description = "Region of the SES identity used for transactional email."
  type        = string

  validation {
    condition     = length(var.aws_ses_region) > 0
    error_message = "aws_ses_region must be non-empty. Set GitHub Variable AWS_SES_REGION."
  }
}

variable "aws_ses_sender_email" {
  description = "Verified SES sender address."
  type        = string

  validation {
    condition     = length(var.aws_ses_sender_email) > 0
    error_message = "aws_ses_sender_email must be non-empty. Set GitHub Variable AWS_SES_SENDER_EMAIL."
  }
}
