output "query_gke_cluster_name" {
  value = module.gke.cluster_name
}

output "query_gke_cluster_location" {
  value = module.gke.cluster_location
}

output "query_cloud_sql_private_ip" {
  value     = module.cloud_sql.private_ip
  sensitive = true
}

output "query_external_ip" {
  description = "External IP of the ledger-query-service LoadBalancer"
  value       = module.kubernetes_query.query_service_ip
}

output "kubectl_config_command" {
  value = "gcloud container clusters get-credentials ${module.gke.cluster_name} --region ${var.region} --project ${var.project_id}"
}

output "swagger_url" {
  value = "http://${module.kubernetes_query.query_service_ip}/swagger-ui/index.html"
}

