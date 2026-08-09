# 단일 호스트라 재배포는 기존 태스크를 먼저 내린 뒤(min 0 / max 100) 새 태스크를 올린다
resource "aws_ecs_cluster" "gateway" {
  name = local.cluster_name

  setting {
    name  = "containerInsights"
    value = "disabled"
  }
}

resource "aws_ecs_task_definition" "gateway" {
  family                   = local.app_name
  network_mode             = "host"
  requires_compatibilities = ["EC2"]
  execution_role_arn       = aws_iam_role.task_exec.arn

  runtime_platform {
    cpu_architecture        = "ARM64"
    operating_system_family = "LINUX"
  }

  volume {
    name      = "caddy-data"
    host_path = "/caddy-data"
  }

  # Memory budget within the 1280MB hard limit: JVM heap 640MB + DuckDB 384MB + ~200MB native.
  container_definitions = jsonencode([
    {
      name      = "gateway"
      image     = "${aws_ecr_repository.gateway.repository_url}:${var.image_tag}"
      essential = true
      memory    = 1280

      portMappings = [{
        containerPort = 8080
        hostPort      = 8080
        protocol      = "tcp"
      }]

      healthCheck = {
        command     = ["CMD-SHELL", "curl -fsS http://localhost:8080/healthz || exit 1"]
        interval    = 30
        timeout     = 5
        retries     = 3
        startPeriod = 90
      }

      environment = [
        { name = "AWS_REGION", value = var.region },
        { name = "SARAMQUANT_S3_BUCKET_NAME", value = var.s3_bucket_name },
        { name = "GLUE_DATABASE", value = var.glue_database },
        { name = "DUCKDB_EXT_DIR", value = local.duckdb_ext_dir },
        { name = "DUCKDB_MEMORY_LIMIT", value = "384MB" },
        { name = "GOOGLE_OAUTH_CLIENT_ID", value = var.google_oauth_client_id },
        { name = "GOOGLE_OAUTH_REDIRECT_URI", value = var.google_oauth_redirect_uri },
        { name = "KAKAO_OAUTH_REST_KEY", value = var.kakao_oauth_rest_key },
        { name = "KAKAO_OAUTH_REDIRECT_URI", value = var.kakao_oauth_redirect_uri },
        { name = "JWT_PUBLIC_KEY_BASE64", value = var.jwt_public_key_base64 },
        { name = "JWT_ACCESS_TOKEN_TTL", value = var.jwt_access_token_ttl },
        { name = "JWT_REFRESH_TOKEN_TTL", value = var.jwt_refresh_token_ttl },
        { name = "COOKIE_SECURE", value = var.cookie_secure },
        { name = "FRONTEND_REDIRECT_URL", value = var.frontend_redirect_url },
        { name = "CORS_ALLOWED_ORIGIN", value = var.cors_allowed_origin },
        { name = "CALC_SERVER_URL", value = var.calc_server_url },
        { name = "AWS_SES_REGION", value = var.aws_ses_region },
        { name = "AWS_SES_SENDER_EMAIL", value = var.aws_ses_sender_email },
      ]

      secrets = [
        for name in local.secret_names : {
          name      = name
          valueFrom = aws_ssm_parameter.gateway[name].arn
        }
      ]

      logConfiguration = {
        logDriver = "awslogs"
        options = {
          "awslogs-group"         = aws_cloudwatch_log_group.gateway.name
          "awslogs-region"        = var.region
          "awslogs-stream-prefix" = "gateway"
        }
      }
    },
    {
      name      = "caddy"
      image     = "caddy:2-alpine"
      essential = true
      memory    = 128

      command = [
        "caddy", "reverse-proxy",
        "--from", var.caddy_domain,
        "--to", "localhost:8080",
      ]

      portMappings = [
        { containerPort = 80, hostPort = 80, protocol = "tcp" },
        { containerPort = 443, hostPort = 443, protocol = "tcp" },
      ]

      mountPoints = [{
        sourceVolume  = "caddy-data"
        containerPath = "/data"
        readOnly      = false
      }]

      environment = [
        { name = "CADDY_DOMAIN", value = var.caddy_domain },
      ]

      dependsOn = [{
        containerName = "gateway"
        condition     = "HEALTHY"
      }]

      logConfiguration = {
        logDriver = "awslogs"
        options = {
          "awslogs-group"         = aws_cloudwatch_log_group.gateway.name
          "awslogs-region"        = var.region
          "awslogs-stream-prefix" = "caddy"
        }
      }
    },
  ])
}

resource "aws_ecs_service" "gateway" {
  name            = local.app_name
  cluster         = aws_ecs_cluster.gateway.id
  task_definition = aws_ecs_task_definition.gateway.arn
  desired_count   = 1
  launch_type     = "EC2"

  deployment_minimum_healthy_percent = 0
  deployment_maximum_percent         = 100
  wait_for_steady_state              = true

  depends_on = [aws_instance.gateway]
}
