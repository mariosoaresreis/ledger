# 1. Set up GCS bucket for state
gsutil mb -p my-project gs://my-project-ledger-tfstate
gsutil versioning set on gs://my-project-ledger-tfstate

# 2. Edit backend.tf with your bucket name, then configure vars
cd terraform/environments/dev
cp terraform.tfvars.example terraform.tfvars   # fill in project_id + db_password

# 3. Bootstrap cluster first (providers depend on GKE outputs)
terraform init
terraform apply -target=module.network -target=module.gke -target=module.artifact_registry

# 4. Build & push your image
IMAGE=us-central1-docker.pkg.dev/YOUR_PROJECT/ledger/ledger-command-service
docker build -t $IMAGE:latest . && docker push $IMAGE:latest

# 5. Full apply (provisions Cloud SQL, Memorystore, deploys Kafka + app)
terraform apply

