resource "aws_db_subnet_group" "main" {
  name       = "ledger-${var.environment}-db-subnet-group"
  subnet_ids = var.subnet_ids

  tags = {
    Name       = "ledger-${var.environment}-db-subnet-group"
    managed-by = "terraform"
  }
}

resource "aws_db_instance" "postgres" {
  identifier        = "ledger-${var.environment}-postgres"
  engine            = "postgres"
  engine_version    = "16"
  instance_class    = var.instance_class
  allocated_storage = var.allocated_storage
  storage_type      = "gp2"

  db_name  = var.db_name
  username = var.db_username
  password = var.db_password

  db_subnet_group_name   = aws_db_subnet_group.main.name
  vpc_security_group_ids = [var.rds_sg_id]

  multi_az                = var.multi_az
  publicly_accessible     = false
  backup_retention_period = 7
  backup_window           = "03:00-04:00"
  skip_final_snapshot     = true

  tags = {
    Name       = "ledger-${var.environment}-postgres"
    managed-by = "terraform"
  }
}

