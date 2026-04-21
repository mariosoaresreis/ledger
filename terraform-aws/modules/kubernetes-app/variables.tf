variable "db_host"       { type = string }
variable "db_port"       { type = string; default = "5432" }
variable "db_username"   { type = string }
variable "db_password"   { type = string; sensitive = true }
variable "app_image"     { type = string }
variable "app_replicas"  { type = number; default = 1 }
variable "kafka_replicas" { type = number; default = 1 }

variable "redis_host" {
  type        = string
  description = "External Redis host. Leave empty to deploy in-cluster Redis."
  default     = ""
}

variable "kafka_release_name" {
  type    = string
  default = "kafka"
}

variable "storage_class" {
  type        = string
  description = "StorageClass for PVCs (gp2 for EKS)"
  default     = "gp2"
}

