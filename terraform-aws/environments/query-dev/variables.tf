variable "region" {
  type    = string
  default = "us-east-1"
}

variable "environment" {
  type    = string
  default = "dev"
}

variable "db_username" {
  type    = string
  default = "ledger_query"
}

variable "db_password" {
  type      = string
  sensitive = true
}

variable "db_instance_class" {
  type    = string
  default = "db.t3.micro"
}

variable "kafka_bootstrap_servers" {
  type        = string
  description = "Kafka bootstrap address reachable from this cluster (e.g. command cluster Kafka internal DNS via VPC peering, or external NLB address)"
}

variable "ecr_base_url" {
  type        = string
  description = "ECR base URL from the command-side dev environment (e.g. 123456789012.dkr.ecr.us-east-1.amazonaws.com)"
}

variable "node_instance_type" {
  type    = string
  default = "t3.medium"
}

variable "node_count" {
  type    = number
  default = 1
}

variable "min_nodes" {
  type    = number
  default = 1
}

variable "max_nodes" {
  type    = number
  default = 2
}

variable "app_image_tag" {
  type    = string
  default = "latest"
}

variable "app_replicas" {
  type    = number
  default = 1
}

