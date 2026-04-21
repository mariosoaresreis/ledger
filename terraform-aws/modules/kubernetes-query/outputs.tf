output "query_service_name" {
  value = kubernetes_service.query_app.metadata[0].name
}

