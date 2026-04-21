# Remote state stored in S3.
# Create the bucket before running terraform init:
#   aws s3 mb s3://YOUR_ACCOUNT_ID-ledger-tfstate --region us-east-1
#   aws s3api put-bucket-versioning \
#     --bucket YOUR_ACCOUNT_ID-ledger-tfstate \
#     --versioning-configuration Status=Enabled
terraform {
  backend "s3" {
    bucket         = "REPLACE_WITH_YOUR_ACCOUNT_ID-ledger-tfstate"
    key            = "ledger/dev/terraform.tfstate"
    region         = "us-east-1"
    dynamodb_table = "ledger-tfstate-lock"
    encrypt        = true
  }
}

