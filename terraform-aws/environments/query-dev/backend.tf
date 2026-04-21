terraform {
  backend "s3" {
    bucket         = "REPLACE_WITH_YOUR_ACCOUNT_ID-ledger-tfstate"
    key            = "ledger/query-dev/terraform.tfstate"
    region         = "us-east-1"
    dynamodb_table = "ledger-tfstate-lock"
    encrypt        = true
  }
}

