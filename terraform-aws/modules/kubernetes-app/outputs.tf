output "app_service_name" {
  value = kubernetes_service.ledger_app.metadata[0].name
}

