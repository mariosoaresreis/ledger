variable "environment"        { type = string }
variable "region"             { type = string }
variable "vpc_id"             { type = string }
variable "subnet_ids"         { type = list(string) }
variable "rds_sg_id"          { type = string }

variable "db_name" {
  type    = string
  default = "ledger"
}

variable "db_username" {
  type    = string
  default = "ledger"
}

variable "db_password" {
  type      = string
  sensitive = true
}

variable "instance_class" {
  type    = string
  default = "db.t3.micro"
}

variable "allocated_storage" {
  type    = number
  default = 20
}

variable "multi_az" {
  type    = bool
  default = false
}

