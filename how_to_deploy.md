# 1. Set up GCS bucket for state
gsutil mb -p ledger-493222 gs://ledger-493222-tfstate
gsutil versioning set on gs://ledger-493222-tfstate

# 2. Edit backend.tf with your bucket name, then configure vars
cd terraform/environments/dev
cp terraform.tfvars.example terraform.tfvars

# 3. Bootstrap cluster first
terraform init
terraform apply -target=module.network -target=module.gke -target=module.artifact_registry

# 4. Build & push your image
IMAGE=us-central1-docker.pkg.dev/ledger-493222/ledger/ledger-command-service
docker build -t $IMAGE:latest . && docker push $IMAGE:latest

# 5. Full apply
terraform apply



