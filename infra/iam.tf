# 앱은 호스트 네트워크로 인스턴스 메타데이터 자격증명을 쓰므로 앱 권한은 인스턴스 롤에 붙인다
data "aws_caller_identity" "current" {}

locals {
  account_id = data.aws_caller_identity.current.account_id

  ssm_gateway_params_arn = "arn:aws:ssm:${var.region}:${local.account_id}:parameter${local.ssm_prefix}/*"

  glue_arns = [
    "arn:aws:glue:${var.region}:${local.account_id}:catalog",
    "arn:aws:glue:${var.region}:${local.account_id}:database/${var.glue_database}",
    "arn:aws:glue:${var.region}:${local.account_id}:table/${var.glue_database}/*",
  ]

  kms_decrypt_via_ssm = {
    Sid      = "DecryptSecureStringViaSsm"
    Effect   = "Allow"
    Action   = ["kms:Decrypt"]
    Resource = "*"
    Condition = {
      StringEquals = {
        "kms:ViaService" = "ssm.${var.region}.amazonaws.com"
      }
    }
  }
}

resource "aws_iam_role" "instance" {
  name = "${local.app_name}-instance"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Service = "ec2.amazonaws.com" }
      Action    = "sts:AssumeRole"
    }]
  })
}

resource "aws_iam_role_policy_attachment" "instance_ecs" {
  role       = aws_iam_role.instance.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AmazonEC2ContainerServiceforEC2Role"
}

resource "aws_iam_role_policy_attachment" "instance_ssm" {
  role       = aws_iam_role.instance.name
  policy_arn = "arn:aws:iam::aws:policy/AmazonSSMManagedInstanceCore"
}

resource "aws_iam_role_policy" "instance_app" {
  name = "${local.app_name}-app"
  role = aws_iam_role.instance.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Sid      = "ListLakeBucket"
        Effect   = "Allow"
        Action   = ["s3:ListBucket", "s3:GetBucketLocation"]
        Resource = "arn:aws:s3:::${var.s3_bucket_name}"
      },
      {
        Sid    = "ReadWriteAppObjects"
        Effect = "Allow"
        Action = [
          "s3:GetObject",
          "s3:PutObject",
          "s3:DeleteObject",
          "s3:AbortMultipartUpload",
          "s3:ListMultipartUploadParts",
        ]
        Resource = "arn:aws:s3:::${var.s3_bucket_name}/app/*"
      },
      {
        Sid    = "ReadLakeObjects"
        Effect = "Allow"
        Action = ["s3:GetObject"]
        Resource = [
          "arn:aws:s3:::${var.s3_bucket_name}/warehouse/*",
          "arn:aws:s3:::${var.s3_bucket_name}/run-summary/*",
        ]
      },
      {
        Sid      = "ReadGlueCatalog"
        Effect   = "Allow"
        Action   = ["glue:GetDatabase", "glue:GetTable"]
        Resource = local.glue_arns
      },
      {
        Sid      = "ReadGatewayParameters"
        Effect   = "Allow"
        Action   = ["ssm:GetParameter", "ssm:GetParameters"]
        Resource = local.ssm_gateway_params_arn
      },
      local.kms_decrypt_via_ssm,
    ]
  })
}

resource "aws_iam_instance_profile" "instance" {
  name = "${local.app_name}-instance"
  role = aws_iam_role.instance.name
}

resource "aws_iam_role" "task_exec" {
  name = "${local.app_name}-task-exec"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Service = "ecs-tasks.amazonaws.com" }
      Action    = "sts:AssumeRole"
    }]
  })
}

resource "aws_iam_role_policy_attachment" "task_exec_managed" {
  role       = aws_iam_role.task_exec.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AmazonECSTaskExecutionRolePolicy"
}

resource "aws_iam_role_policy" "task_exec" {
  name = "${local.app_name}-task-exec"
  role = aws_iam_role.task_exec.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Sid      = "InjectGatewayParameters"
        Effect   = "Allow"
        Action   = ["ssm:GetParameters"]
        Resource = local.ssm_gateway_params_arn
      },
      local.kms_decrypt_via_ssm,
    ]
  })
}
