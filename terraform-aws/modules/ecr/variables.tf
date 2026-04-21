variable "region"      { type = string }
variable "environment" { type = string }

variable "repository_names" {
  type        = list(string)
  description = "List of ECR repository names to create"
  default     = ["ledger-command-service", "ledger-query-service"]
}

