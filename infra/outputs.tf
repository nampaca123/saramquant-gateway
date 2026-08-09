output "eip_public_ip" {
  description = "Elastic IP to point the api.saramquant.com A record at."
  value       = aws_eip.gateway.public_ip
}

output "ecr_repo_url" {
  description = "ECR repository URL for the gateway image."
  value       = aws_ecr_repository.gateway.repository_url
}
