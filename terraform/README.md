# Ledger – Terraform Infrastructure (GCP)
This directory manages all GCP infrastructure for the `ledger-command-service` using Terraform.
## Architecture
```
GCP Project
├── VPC (private)
│   ├── GKE Subnet  10.10.0.0/24
│   ├── Pod range   10.20.0.0/16
│   └── Svc range   10.30.0.0/16
├── GKE Cluster (private nodes, Workload Identity)
│   └── Namespace: ledger
│       ├── Kafka (Bitnami Helm chart – KRaft mode)
│       ├── ledger-command-service (Deployment + LoadBalancer Service)
│       ├── ConfigMap  ledger-command-config
│       └── Secret     ledger-command-secret
├── Cloud SQL – PostgreSQL 16 (private IP, no public endpoint)
├── Cloud Memorystore – Redis 7 (private IP)
├── Artifact Registry – Docker repository (ledger)
└── Cloud NAT – outbound internet for private GKE nodes
```
## Module Layout
```
terraform/
  modules/
    network/           VPC, subnets, Cloud NAT, private service access
    gke/               GKE cluster + node pool + node SA
    cloud-sql/         Cloud SQL PostgreSQL instance, database, user
    memorystore/       Cloud Memorystore Redis instance
    artifact-registry/ Docker image repository
    kubernetes-app/    Namespace, ConfigMap, Secret, Kafka (Helm), App Deployment
  environments/
    dev/               Entry point – calls all modules, configures providers
```
## Prerequisites
1. **Terraform >= 1.7** – https://developer.hashicorp.com/terraform/install
2. **gcloud CLI** authenticated: `gcloud auth application-default login`
3. **GCP project** with billing enabled
4. **GCS bucket** for remote state (create once):
   ```bash
   export PROJECT_ID=your-gcp-project-id
   gsutil mb -p $PROJECT_ID -l us-central1 gs://${PROJECT_ID}-ledger-tfstate
   gsutil versioning set on gs://${PROJECT_ID}-ledger-tfstate
   ```
5. Update `backend.tf` with your bucket name.
## Deploying
### 1 – Configure variables
```bash
cd terraform/environments/dev
cp terraform.tfvars.example terraform.tfvars
# Edit terraform.tfvars – set project_id and db_password at minimum
```
### 2 – Bootstrap the GKE cluster first
The Kubernetes and Helm providers are configured from GKE outputs, so on the
very first run you must create the cluster before Terraform can plan the workloads:
```bash
terraform init
terraform apply -target=module.network -target=module.gke -target=module.artifact_registry
```
### 3 – Full apply
```bash
terraform apply
```
This provisions Cloud SQL, Memorystore, and deploys all Kubernetes workloads
(Kafka via Helm + the application Deployment).
### 4 – Configure kubectl
Terraform prints the command after apply:
```bash
gcloud container clusters get-credentials <cluster-name> --region us-central1 --project <project-id>
kubectl get pods -n ledger
```
## Building & Pushing the Docker Image
```bash
# Authenticate Docker to Artifact Registry
gcloud auth configure-docker us-central1-docker.pkg.dev
# Build and push
IMAGE=us-central1-docker.pkg.dev/<PROJECT_ID>/ledger/ledger-command-service
docker build -t $IMAGE:latest .
docker push $IMAGE:latest
# Or with a specific tag:
TAG=$(git rev-parse --short HEAD)
docker build -t $IMAGE:$TAG .
docker push $IMAGE:$TAG
# Deploy the new tag via Terraform:
terraform apply -var="app_image_tag=$TAG"
```
## Accessing the API
```bash
# Get the external IP of the LoadBalancer
kubectl get svc ledger-command-service -n ledger
# Swagger UI
curl http://<EXTERNAL_IP>/swagger-ui/index.html
```
## Service Connectivity (Private IPs)
| Service         | Host                                              | Port |
|-----------------|---------------------------------------------------|------|
| PostgreSQL      | Cloud SQL private IP (Terraform output)           | 5432 |
| Redis           | Memorystore private IP (Terraform output)         | 6379 |
| Kafka (in-cluster) | kafka.ledger.svc.cluster.local               | 9092 |
All services are reachable only within the VPC. No public endpoints are exposed
for the database or cache.
## Destroying
```bash
terraform destroy
```
> **Warning**: This deletes all data including the Cloud SQL database.
> Set `deletion_protection = true` in the Cloud SQL module for production.
## Environment Promotion
To add a `staging` or `prod` environment:
```bash
cp -r terraform/environments/dev terraform/environments/staging
# Edit variables.tf defaults (larger machine types, REGIONAL availability, etc.)
# Update backend.tf prefix to ledger/staging/terraform.tfstate
```
