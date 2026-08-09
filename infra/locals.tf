# ECS 클러스터명은 gateway 전용 단일 호스트 클러스터(calc는 saramquant-calc로 분리)
locals {
  app_name       = "saramquant-gateway"
  cluster_name   = "saramquant"
  log_group      = "/saramquant/gateway"
  ssm_prefix     = "/saramquant/gateway"
  duckdb_ext_dir = "/duckdb-ext"

  public_subnet_cidrs = {
    "${var.region}a" = "10.20.1.0/24"
    "${var.region}c" = "10.20.2.0/24"
  }
}
