output "eks_cluster_name" {
  value = module.eks.cluster_name
}

output "ecr_command_url" {
  description = "ECR URL for the command service image"
  value       = module.ecr.repository_urls["ledger-command-service"]
}

output "ecr_query_url" {
  description = "ECR URL for the query service image"
  value       = module.ecr.repository_urls["ledger-query-service"]
}

output "rds_endpoint" {
  value     = module.rds.db_endpoint
  sensitive = true
}

output "kubectl_config_command" {
  description = "Run this to configure kubectl for the EKS cluster"
  value       = "aws eks update-kubeconfig --name ${module.eks.cluster_name} --region ${var.region}"
}

output "docker_login_command" {
  description = "Authenticate Docker with ECR"
  value       = "aws ecr get-login-password --region ${var.region} | docker login --username AWS --password-stdin ${module.ecr.registry_id}.dkr.ecr.${var.region}.amazonaws.com"
}

