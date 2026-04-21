variable "query_db_host"     { type = string }
variable "query_db_port"     { type = string; default = "5432" }
variable "query_db_username" { type = string }
variable "query_db_password" { type = string; sensitive = true }
variable "app_image"         { type = string }
variable "app_replicas"      { type = number; default = 1 }
variable "kafka_bootstrap_servers" { type = string }

variable "redis_host" {
  type    = string
  default = ""
}

variable "storage_class" {
  type    = string
  default = "gp2"
}

