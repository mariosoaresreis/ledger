variable "environment" { type = string }
variable "region"      { type = string }

variable "vpc_id" {
  type        = string
  description = "VPC ID where the EKS cluster is deployed"
}

variable "private_subnet_ids" {
  type        = list(string)
  description = "Private subnet IDs for EKS node groups"
}

variable "node_sg_id" {
  type        = string
  description = "Security group ID for EKS nodes"
}

variable "node_instance_type" {
  type    = string
  default = "t3.medium"
}

variable "node_desired_size" {
  type    = number
  default = 2
}

variable "node_min_size" {
  type    = number
  default = 1
}

variable "node_max_size" {
  type    = number
  default = 3
}

