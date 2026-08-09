# state 락은 DynamoDB 대신 S3 lockfile 사용, 버킷은 calc-server 세션과 공유
terraform {
  backend "s3" {
    bucket       = "saramquant-tfstate"
    key          = "gateway/terraform.tfstate"
    region       = "ap-northeast-2"
    encrypt      = true
    use_lockfile = true
  }
}
