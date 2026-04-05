# ── Namespace ────────────────────────────────────────────────────────────────
resource "kubernetes_namespace" "ledger" {
  metadata {
    name = "ledger"
    labels = {
      managed-by = "terraform"
    }
  }
}

# ── ConfigMap – non-sensitive runtime config ─────────────────────────────────
resource "kubernetes_config_map" "ledger_config" {
  metadata {
    name      = "ledger-command-config"
    namespace = kubernetes_namespace.ledger.metadata[0].name
  }

  data = {
    # Cloud SQL private IP (set by Terraform from Cloud SQL module output)
    LEDGER_DB_HOST = var.db_host
    LEDGER_DB_PORT = "5432"

    # Kafka deployed on GKE via Helm; service name = <release-name>.<namespace>
    LEDGER_KAFKA_BOOTSTRAP_SERVERS = "${var.kafka_release_name}.${kubernetes_namespace.ledger.metadata[0].name}.svc.cluster.local:9092"

    # Cloud Memorystore private IP (set by Terraform from Memorystore module output)
    LEDGER_REDIS_HOST = var.redis_host
    LEDGER_REDIS_PORT = "6379"
  }
}

# ── Secret – sensitive credentials ───────────────────────────────────────────
resource "kubernetes_secret" "ledger_secret" {
  metadata {
    name      = "ledger-command-secret"
    namespace = kubernetes_namespace.ledger.metadata[0].name
  }

  data = {
    LEDGER_DB_USERNAME = var.db_username
    LEDGER_DB_PASSWORD = var.db_password
  }

  type = "Opaque"
}

# ── Kafka – Bitnami Helm chart in KRaft mode (no Zookeeper) ──────────────────
resource "helm_release" "kafka" {
  name       = var.kafka_release_name
  repository = "https://charts.bitnami.com/bitnami"
  chart      = "kafka"
  version    = "26.8.5" # Kafka 3.7.x – pin and bump deliberately
  namespace  = kubernetes_namespace.ledger.metadata[0].name

  # KRaft combined mode: single pod acts as broker + controller
  set {
    name  = "controller.replicaCount"
    value = tostring(var.kafka_replicas)
  }

  # No separate brokers; controller nodes handle both roles
  set {
    name  = "broker.replicaCount"
    value = "0"
  }

  set {
    name  = "controller.persistence.enabled"
    value = "true"
  }

  set {
    name  = "controller.persistence.size"
    value = "8Gi"
  }

  # PLAINTEXT inside the cluster – add TLS for production
  set {
    name  = "listeners.client.protocol"
    value = "PLAINTEXT"
  }

  set {
    name  = "listeners.controller.protocol"
    value = "PLAINTEXT"
  }

  set {
    name  = "listeners.interbroker.protocol"
    value = "PLAINTEXT"
  }

  set {
    name  = "externalAccess.enabled"
    value = "false"
  }

  set {
    name  = "serviceAccount.create"
    value = "true"
  }

  depends_on = [kubernetes_namespace.ledger]
}

# ── Application Deployment ────────────────────────────────────────────────────
resource "kubernetes_deployment" "ledger_app" {
  metadata {
    name      = "ledger-command-service"
    namespace = kubernetes_namespace.ledger.metadata[0].name
    labels = {
      app        = "ledger-command-service"
      managed-by = "terraform"
    }
  }

  spec {
    replicas = var.app_replicas

    selector {
      match_labels = {
        app = "ledger-command-service"
      }
    }

    template {
      metadata {
        labels = {
          app = "ledger-command-service"
        }
      }

      spec {
        container {
          name              = "ledger-command-service"
          image             = var.app_image
          image_pull_policy = "Always"

          port {
            name           = "http"
            container_port = 8080
            protocol       = "TCP"
          }

          env_from {
            config_map_ref {
              name = kubernetes_config_map.ledger_config.metadata[0].name
            }
          }

          env_from {
            secret_ref {
              name = kubernetes_secret.ledger_secret.metadata[0].name
            }
          }

          readiness_probe {
            http_get {
              path = "/actuator/health"
              port = 8080
            }
            initial_delay_seconds = 30
            period_seconds        = 10
            failure_threshold     = 3
          }

          liveness_probe {
            http_get {
              path = "/actuator/health"
              port = 8080
            }
            initial_delay_seconds = 60
            period_seconds        = 15
            failure_threshold     = 3
          }

          resources {
            requests = {
              cpu    = "250m"
              memory = "512Mi"
            }
            limits = {
              cpu    = "1000m"
              memory = "1Gi"
            }
          }
        }
      }
    }
  }

  depends_on = [
    helm_release.kafka,
    kubernetes_config_map.ledger_config,
    kubernetes_secret.ledger_secret,
  ]
}

# ── Application Service – LoadBalancer exposes the API externally ─────────────
resource "kubernetes_service" "ledger_app" {
  metadata {
    name      = "ledger-command-service"
    namespace = kubernetes_namespace.ledger.metadata[0].name
    labels = {
      app        = "ledger-command-service"
      managed-by = "terraform"
    }
    annotations = {
      # GCP creates a regional TCP load balancer by default; swap for internal LB if needed:
      # "cloud.google.com/load-balancer-type" = "Internal"
    }
  }

  spec {
    selector = {
      app = "ledger-command-service"
    }

    port {
      name        = "http"
      port        = 80
      target_port = 8080
      protocol    = "TCP"
    }

    type = "LoadBalancer"
  }
}

