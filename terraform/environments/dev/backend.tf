# Remote state stored in GCS.
# Create the bucket before running terraform init:
#   gsutil mb -p ledger-493222 -l us-central1 gs://ledger-493222-tfstate
#   gsutil versioning set on gs://ledger-493222-tfstate
terraform {
  backend "gcs" {
    bucket = "ledger-493222-tfstate"
    prefix = "ledger/dev/terraform.tfstate"
  }
}
