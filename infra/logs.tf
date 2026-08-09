resource "aws_cloudwatch_log_group" "gateway" {
  name              = local.log_group
  retention_in_days = 30
}
