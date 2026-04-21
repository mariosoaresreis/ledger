output "db_endpoint" {
  description = "RDS instance endpoint (host:port)"
  value       = aws_db_instance.postgres.endpoint
}

output "db_host" {
  description = "RDS instance hostname"
  value       = aws_db_instance.postgres.address
}

output "db_port" {
  value = aws_db_instance.postgres.port
}

