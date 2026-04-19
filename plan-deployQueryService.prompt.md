# Deploy ledger-query-service – Step-by-Step Plan

## Prerequisites (one-time)

The query service shares the same GCS state bucket (`ledger-tfstate`) and the same
Artifact Registry (`us-central1-docker.pkg.dev/ledger-493222/ledger`) as the command
service. Both must already exist before proceeding.

Confirm they are available:
```bash
gsutil ls gs://ledger-tfstate
gcloud artifacts repositories list --project=ledger-493222 --location=us-central1
```

> **If the bucket is missing** (e.g. after `terraform destroy` or project recreation), recreate it:
> ```bash
> gsutil mb -p ledger-493222 -l us-central1 gs://ledger-tfstate
> gsutil versioning set on gs://ledger-tfstate
> ```
> Both command (`dev` prefix) and query (`query-dev` prefix) share this single bucket.

---

## Stage 0 – Confirm the query service source code exists

The `ledger-query-service/` Spring Boot project must exist at the repo root.
Check:
```bash
ls /home/bat/projects/java/ledger/ledger-query-service/
```

If the directory is missing, the source code must be implemented first (separate task).

---

## Stage 1 – Build the query service JAR

```bash
cd /home/bat/projects/java/ledger/ledger-query-service
./mvnw clean package -DskipTests
```

Expected output: `BUILD SUCCESS` and a JAR at `target/ledger-query-service-*.jar`.

---

## Stage 2 – Build and push the Docker image

### 2a – Authenticate Docker to Artifact Registry (if not already done)
```bash
gcloud auth configure-docker us-central1-docker.pkg.dev
```

### 2b – Build the image
```bash
export IMAGE=us-central1-docker.pkg.dev/ledger-493222/ledger/ledger-query-service

cd /home/bat/projects/java/ledger-query-service
docker build -t $IMAGE:latest .

# Optional: tag with git SHA for traceability
export TAG=$(git rev-parse --short HEAD)
docker build -t $IMAGE:$TAG .
```

### 2c – Push to Artifact Registry
```bash
docker push $IMAGE:latest
# and/or
docker push $IMAGE:$TAG
```

---

## Stage 3 – Configure Terraform variables

```bash
cd /home/bat/projects/java/ledger/terraform/environments/query-dev
cp terraform.tfvars.example terraform.tfvars
```

Edit `terraform.tfvars` and fill in **at minimum**:

| Variable | Value | Notes |
|---|---|---|
| `project_id` | `ledger-493222` | Your GCP project |
| `db_password` | `<choose a password>` | For `ledger_query` Cloud SQL user |
| `kafka_bootstrap_servers` | `<command-side Kafka external IP>:9092` | Get from `kubectl get svc -n ledger` on the command cluster |
| `app_image_tag` | `latest` or the git SHA from Stage 2 | |

Example `terraform.tfvars`:
```hcl
project_id              = "ledger-493222"
db_password             = "s3cr3t-query-pw"
kafka_bootstrap_servers = "34.134.143.95:9092"
app_image_tag           = "latest"
```

> **How to find Kafka's external IP on the command cluster:**
> ```bash
> # Switch context to command cluster first
> gcloud container clusters get-credentials ledger-493222-dev-gke \
>   --zone us-central1-a --project ledger-493222
> kubectl get svc -n ledger | grep kafka
> ```

---

## Stage 4 – Bootstrap the query GKE cluster

The Kubernetes and Helm providers depend on GKE outputs, so the cluster must exist
before Terraform can plan the workloads.

```bash
cd /home/bat/projects/java/ledger/terraform/environments/query-dev

terraform init

# Phase 1: network + cluster only
terraform apply \
  -target=module.network \
  -target=module.gke
```

Wait for both to complete (≈ 10–20 minutes for GKE).

---

## Stage 5 – Full apply (Cloud SQL + Kubernetes workloads)

```bash
terraform apply
```

This provisions:
- **Cloud SQL** – PostgreSQL 16, `us-east1`, private IP, DB `ledger_query`, user `ledger_query`
- **Kubernetes namespace** `ledger-query`
- **In-cluster Redis** (StatefulSet)
- **ConfigMap** `ledger-query-config` + **Secret** `ledger-query-secret`
- **Deployment** `ledger-query-service`
- **NodePort Service** + **GCE Ingress** → new external IP in `us-east1`

Expected: all resources `Apply complete!` with no errors.

---

## Stage 6 – Configure kubectl for the query cluster

```bash
gcloud container clusters get-credentials ledger-493222-dev-query-gke \
  --zone us-east1-b \
  --project ledger-493222
```

Verify pods:
```bash
kubectl get pods -n ledger-query
# Expected: ledger-query-service-* Running, redis-0 Running
```

---

## Stage 7 – Get the external IP

```bash
kubectl get ingress -n ledger-query
# or
kubectl get svc -n ledger-query
```

Note the `EXTERNAL-IP` — this is distinct from the command service's `34.8.164.128`.

---

## Stage 8 – Smoke tests

Replace `<QUERY_IP>` with the IP from Stage 7.

### Health check
```bash
curl http://<QUERY_IP>/actuator/health
# Expected: {"status":"UP"}
```

### Swagger UI
```
http://<QUERY_IP>/swagger-ui/index.html
```

### Balance query (account must exist on command side first)
```bash
curl http://<QUERY_IP>/api/v1/accounts/<accountId>/balance
# Expected: { "accountId": "...", "balance": "...", "currency": "USD", "asOf": "..." }
```

### Transaction history
```bash
curl "http://<QUERY_IP>/api/v1/accounts/<accountId>/transactions?page=0&size=20"
```

### Kafka consumer lag
```bash
curl http://<QUERY_IP>/api/v1/health/lag
# Expected: { "group": "ledger-query-group", "lag": 0 }
```

---

## Re-deploy after code changes

```bash
# 1. Build JAR
cd /home/bat/projects/java/ledger/ledger-query-service
./mvnw clean package -DskipTests

# 2. Build and push new image
export IMAGE=us-central1-docker.pkg.dev/ledger-493222/ledger/ledger-query-service
docker build -t $IMAGE:latest .
docker push $IMAGE:latest

# 3. Rolling restart (no Terraform needed for image-only changes)
kubectl rollout restart deployment/ledger-query-service -n ledger-query
kubectl rollout status deployment/ledger-query-service -n ledger-query
```

---

## Update SwaggerConfig in the query service

After getting the external IP from Stage 7, update
`ledger-query-service/src/main/java/.../config/SwaggerConfig.java`:

```java
new Server().url("http://<QUERY_IP>").description("GKE Query DEV"),
new Server().url("http://localhost:8081").description("Local Development")
```

Then re-deploy using the steps above.

---

## Isolation guarantees summary

| Concern | Command side | Query side |
|---|---|---|
| Region | `us-central1` | `us-east1` |
| GKE cluster | `ledger-493222-dev-gke` | `ledger-493222-dev-query-gke` |
| Database | Cloud SQL `ledger` | Cloud SQL `ledger_query` |
| External IP | `34.8.164.128` | new IP from `us-east1` ingress |
| Kafka role | Producer only | Consumer only |
| Failure mode | Query keeps serving from its DB + Redis cache | Command keeps accepting writes |

---

## Known pitfalls

| Problem | Fix |
|---|---|
| `gke-gcloud-auth-plugin not found` | `sudo apt-get install google-cloud-cli-gke-gcloud-auth-plugin` or copy binary from `/usr/lib/google-cloud-sdk/bin/` to `~/.local/bin/` and set `USE_GKE_GCLOUD_AUTH_PLUGIN=True` |
| `bucket doesn't exist` on `terraform init` | The GCS bucket must be created before init: `gsutil mb -p ledger-493222 gs://ledger-tfstate` |
| Kafka unreachable from query cluster | Kafka NodePort service on command cluster must expose port 9092 externally; verify with `kubectl get svc kafka -n ledger` |
| `context deadline exceeded` for Kafka Helm | Increase Helm timeout or pre-pull images; Kafka on command side must be healthy before query consumer connects |
| Cloud SQL private IP not reachable | Both VPCs need VPC peering or the Cloud SQL proxy sidecar; query service can use Cloud SQL Auth Proxy |
| Image push `denied: Unauthenticated` | Re-run `gcloud auth configure-docker us-central1-docker.pkg.dev` and ensure the active gcloud account has `roles/artifactregistry.writer` |

