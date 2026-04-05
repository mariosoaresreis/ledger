variable "project_id" {
  type        = string
  description = "GCP project ID"
}

variable "environment" {
  type        = string
  description = "Environment name"
}

variable "region" {
  type        = string
  description = "GCP region"
}

variable "vpc_self_link" {
  type        = string
  description = "VPC network self-link for private IP configuration"
}

variable "tier" {
  type        = string
  description = "Cloud SQL machine tier"
  default     = "db-f1-micro"
}

variable "availability_type" {
  type        = string
  description = "ZONAL or REGIONAL (use REGIONAL for HA in production)"
  default     = "ZONAL"
}

variable "disk_size_gb" {
  type        = number
  description = "Storage size in GB"
  default     = 20
}

variable "db_username" {
  type        = string
  description = "PostgreSQL username"
  default     = "ledger"
}

variable "db_password" {
  type        = string
  description = "PostgreSQL password"
  sensitive   = true
}

