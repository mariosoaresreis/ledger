output "app_service_ip" {
  description = "External IP of the ledger-command-service LoadBalancer (may take a minute to provision)"
  value       = kubernetes_service.ledger_app.status[0].load_balancer[0].ingress[0].ip
}

output "kafka_service_host" {
  description = "Kafka broker address inside the cluster"
  value       = "${var.kafka_release_name}.ledger.svc.cluster.local:9092"
}

output "namespace" {
  description = "Kubernetes namespace used for the ledger workloads"
  value       = kubernetes_namespace.ledger.metadata[0].name
}

