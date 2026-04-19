locals {
  name_prefix = "${var.project_id}-${var.environment}"
}

resource "google_sql_database_instance" "postgres" {
  name             = "${local.name_prefix}-postgres"
  database_version = "POSTGRES_16"
  region           = var.region
  project          = var.project_id

  # Requires private service access to be established first (depends_on at module call)
  settings {
    tier              = var.tier
    availability_type = var.availability_type
    disk_size         = var.disk_size_gb
    disk_type         = "PD_SSD"

    ip_configuration {
      ipv4_enabled    = var.enable_public_ip
      private_network = var.enable_public_ip ? null : var.vpc_self_link

      dynamic "authorized_networks" {
        for_each = var.authorized_networks
        content {
          name  = authorized_networks.value.name
          value = authorized_networks.value.value
        }
      }
    }

    backup_configuration {
      enabled    = true
      start_time = "03:00"
    }

    database_flags {
      name  = "max_connections"
      value = "200"
    }

    insights_config {
      query_insights_enabled = true
    }
  }

  deletion_protection = false
}

resource "google_sql_database" "ledger" {
  name     = var.db_name
  instance = google_sql_database_instance.postgres.name
  project  = var.project_id
}

resource "google_sql_user" "ledger" {
  name     = var.db_username
  instance = google_sql_database_instance.postgres.name
  password = var.db_password
  project  = var.project_id
}

