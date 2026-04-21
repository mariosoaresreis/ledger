# How to Deploy to GCP

This guide walks through deploying the Ledger CQRS system to Google Cloud Platform.
The Terraform configuration lives in [`terraform/`](./terraform/) (GCP only).

> **AWS deployment guide**: see [`how_to_deploy_aws.md`](./how_to_deploy_aws.md).

---

## Folder Layout

```
terraform/
├── environments/
│   ├── dev/          # Command service (GKE + Cloud SQL + Artifact Registry + Kafka + Redis)
│   └── query-dev/    # Query service  (GKE + Cloud SQL + Kafka consumer)
└── modules/
    ├── network/           VPC, subnets, Cloud NAT, private service access
    ├── gke/               GKE cluster + node pool + service account
    ├── cloud-sql/         Cloud SQL PostgreSQL 16
    ├── artifact-registry/ Artifact Registry repository
    ├── memorystore/       Cloud Memorystore (Redis) – optional
    ├── kubernetes-app/    Command service K8s workloads (Kafka, Redis, Deployment, GCE Ingress)
    └── kubernetes-query/  Query service K8s workloads (Deployment, GCE Ingress)
```

---

## Prerequisites

| Tool      | Minimum version | Install |
|-----------|-----------------|---------|
| Terraform | 1.7             | https://developer.hashicorp.com/terraform/install |
| gcloud    | latest          | https://cloud.google.com/sdk/install |
| Docker    | 24+             | https://docs.docker.com/get-docker/ |
| kubectl   | 1.29+           | https://kubernetes.io/docs/tasks/tools/ |
| helm      | 3.14+           | https://helm.sh/docs/intro/install/ |

```bash
gcloud auth login
gcloud auth application-default login
gcloud config set project YOUR_PROJECT_ID
```

---

## Part 1 – Command Service

### 1. Bootstrap remote state (GCS)

```bash
PROJECT_ID=your-gcp-project-id
gsutil mb -p $PROJECT_ID -l us-central1 gs://${PROJECT_ID}-ledger-tfstate
gsutil versioning set on gs://${PROJECT_ID}-ledger-tfstate
```

### 2. Configure the command environment

```bash
cd terraform/environments/dev
cp terraform.tfvars.example terraform.tfvars
```

Edit `terraform.tfvars` (minimum required fields):
```hcl
project_id   = "your-gcp-project-id"
environment  = "dev"
region       = "us-central1"
db_password  = "YourSecurePassword123!"
machine_type = "e2-medium"
node_count   = 2
min_nodes    = 1
max_nodes    = 2
db_tier      = "db-f1-micro"
app_image_tag  = "latest"
app_replicas   = 1
kafka_replicas = 1
```

Edit `backend.tf` – set your bucket name:
```hcl
bucket = "your-gcp-project-id-ledger-tfstate"
```

### 3. Bootstrap network + GKE + Artifact Registry first

```bash
terraform init
terraform apply \
  -target=module.network \
  -target=module.gke \
  -target=module.artifact_registry
```

### 4. Configure kubectl

```bash
gcloud container clusters get-credentials ledger-dev-gke \
  --region us-central1 \
  --project YOUR_PROJECT_ID

kubectl get nodes
```

### 5. Build and push the command service image

```bash
PROJECT_ID=your-gcp-project-id
REGION=us-central1
IMAGE=${REGION}-docker.pkg.dev/${PROJECT_ID}/ledger/ledger-command-service

gcloud auth configure-docker ${REGION}-docker.pkg.dev

docker build -t ${IMAGE}:latest .
docker push ${IMAGE}:latest
```

### 6. Full apply

```bash
terraform apply
```

### 7. Get the command service external IP

```bash
kubectl get ingress ledger-command-service -n ledger
# Wait 2–3 minutes for the GCE LB to provision
curl http://EXTERNAL_IP/actuator/health
```

---

## Part 2 – Query Service

### 8. Configure the query environment

```bash
cd terraform/environments/query-dev
cp terraform.tfvars.example terraform.tfvars
```

Edit `terraform.tfvars`:
```hcl
project_id              = "your-gcp-project-id"
environment             = "dev"
region                  = "us-central1"
db_password             = "YourSecurePassword123!"
kafka_bootstrap_servers = "kafka.ledger.svc.cluster.local:9092"
artifact_registry_url   = "us-central1-docker.pkg.dev/your-gcp-project-id/ledger"
machine_type            = "e2-medium"
node_count              = 1
app_image_tag           = "latest"
app_replicas            = 1
```

Edit `backend.tf`:
```hcl
bucket = "your-gcp-project-id-ledger-tfstate"
prefix = "ledger/query-dev/terraform.tfstate"
```

### 9. Deploy query cluster

```bash
terraform init
terraform apply -target=module.network -target=module.gke
terraform apply
```

### 10. Build and push the query service image

```bash
IMAGE=${REGION}-docker.pkg.dev/${PROJECT_ID}/ledger/ledger-query-service
docker build -f Dockerfile.query -t ${IMAGE}:latest .
docker push ${IMAGE}:latest
```

### 11. Get the query service external IP

```bash
gcloud container clusters get-credentials ledger-dev-query-gke \
  --region us-central1 --project YOUR_PROJECT_ID

kubectl get ingress ledger-query-service -n ledger-query
curl http://EXTERNAL_IP/actuator/health
```

---

## Flyway Migrations

Migrations run automatically on startup. To connect manually to Cloud SQL:

```bash
gcloud sql connect ledger-dev-postgres --user=ledger --database=ledger
```

---

## Updating the Application

```bash
docker build -t ${IMAGE}:v1.2.3 . && docker push ${IMAGE}:v1.2.3

# In terraform.tfvars: app_image_tag = "v1.2.3"
terraform apply

# Or rolling restart without tag change:
kubectl rollout restart deployment/ledger-command-service -n ledger
```

---

## Tearing Down

```bash
cd terraform/environments/dev
terraform destroy
```

Both the `google_service_networking_connection` and `google_sql_user`/`google_sql_database`
resources use `deletion_policy = "ABANDON"`, so Terraform skips explicit deletion API calls
for them and lets the Cloud SQL instance cascade-delete everything. A plain `terraform destroy`
should complete cleanly.

> **If you hit destroy errors on an existing environment** (state created before this fix):
> ```bash
> # Remove the stuck resources from state, then retry
> terraform state rm module.network.google_service_networking_connection.private_vpc_connection
> terraform state rm module.cloud_sql.google_sql_user.ledger
> terraform destroy
> ```

> Cloud SQL has `deletion_protection = false` in dev. Set it to `true` for production.

---

## Cost Estimate (dev, us-central1)

| Resource               | Approx monthly cost |
|------------------------|---------------------|
| GKE cluster (control)  | ~$73                |
| 2× e2-medium nodes     | ~$67                |
| Cloud SQL db-f1-micro  | ~$10                |
| Cloud NAT              | ~$32                |
| GCE Load Balancers     | ~$18                |
| Artifact Registry      | ~$1                 |
| **Total**              | **~$201/month**     |

