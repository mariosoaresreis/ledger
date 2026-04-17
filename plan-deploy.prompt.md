# Deployment Plan — ledger-command-service

End-to-end steps to build, push, and deploy the Spring Boot CQRS command service
to the `ledger-493222-dev-gke` GKE cluster on GCP.

---

## 0. One-time setup (skip if already done)

### 0a. Terraform state bucket
```bash
gsutil mb -p ledger-493222 -l us-central1 gs://ledger-tfstate
gsutil versioning set on gs://ledger-tfstate
```

### 0b. Copy and fill in Terraform variables
```bash
cd terraform/environments/dev
cp terraform.tfvars.example terraform.tfvars
# Edit terraform.tfvars — set at minimum:
#   project_id  = "ledger-493222"
#   db_password = "<strong-password>"
```

### 0c. Install gke-gcloud-auth-plugin (if missing)
> ⚠️ Do NOT use `gcloud components install` — gcloud is managed by apt and that
> command will fail. Install the binary directly instead.

```bash
# Find the current SDK version
SDK_VERSION=$(gcloud version 2>/dev/null | grep "Google Cloud SDK" | awk '{print $4}')

# Download the standalone binary
curl -Lo ~/.local/bin/gke-gcloud-auth-plugin \
  "https://dl.google.com/dl/cloudsdk/channels/rapid/downloads/google-cloud-sdk-${SDK_VERSION}-linux-x86_64.tar.gz" \
  # Alternatively, extract from a full SDK tarball or copy from a Docker image.
  # Simplest approach — copy from the gcloud SDK bin directory if installed system-wide:
find /usr/lib/google-cloud-sdk -name "gke-gcloud-auth-plugin" 2>/dev/null \
  | xargs -I{} cp {} ~/.local/bin/gke-gcloud-auth-plugin

chmod +x ~/.local/bin/gke-gcloud-auth-plugin
export PATH="$HOME/.local/bin:$PATH"

# Verify
gke-gcloud-auth-plugin --version
```

---

## 1. Prerequisites check

```bash
# 1a. Confirm gcloud is authenticated and targeting the correct project
gcloud auth list
gcloud config set project ledger-493222

# 1b. Confirm Application Default Credentials (used by Terraform)
gcloud auth application-default login

# 1c. Authenticate Docker to Artifact Registry
gcloud auth configure-docker us-central1-docker.pkg.dev

# 1d. Confirm kubectl is installed
kubectl version --client

# 1e. Confirm gke-gcloud-auth-plugin is on PATH
export PATH="$HOME/.local/bin:$PATH"
gke-gcloud-auth-plugin --version

# 1f. Set the required env var for kubectl <-> GKE auth (must be in every shell
#     session that calls kubectl or terraform kubernetes provider)
export USE_GKE_GCLOUD_AUTH_PLUGIN=True
```

> 💡 Add both `export` lines to your `~/.bashrc` to avoid repeating them each session:
> ```bash
> echo 'export PATH="$HOME/.local/bin:$PATH"' >> ~/.bashrc
> echo 'export USE_GKE_GCLOUD_AUTH_PLUGIN=True' >> ~/.bashrc
> source ~/.bashrc
> ```

---

## 2. Maven build

Build the fat JAR (the Docker multi-stage build also runs Maven, but building
locally first catches compilation errors early).

```bash
cd /home/bat/projects/java/ledger
./mvnw clean package -DskipTests
# Expected: BUILD SUCCESS, target/ledger-0.0.1-SNAPSHOT.jar created
```

---

## 3. Docker build and push to Artifact Registry

```bash
export IMAGE=us-central1-docker.pkg.dev/ledger-493222/ledger/ledger-command-service

# Build (multi-stage: Maven build inside Docker, then slim JRE runtime image)
docker build -t "${IMAGE}:latest" .

# Push
# ⚠️ Requires step 1c (gcloud auth configure-docker) to have been run first.
docker push "${IMAGE}:latest"
```

---

## 4. Terraform: init and apply

> ⚠️ **Target order matters.** The Kubernetes workload module (`module.kubernetes_app`)
> depends on the GKE cluster, Cloud SQL private IP, and the Docker image already
> existing in Artifact Registry. Always bootstrap infrastructure first, then full apply.

### 4a. Bootstrap infrastructure (first-time or after cluster recreation)

```bash
cd terraform/environments/dev

terraform init   # pulls providers, configures GCS backend (gs://ledger-tfstate)

# Phase 1 — provision network, GKE cluster, and Artifact Registry
terraform apply \
  -target=module.network \
  -target=module.gke \
  -target=module.artifact_registry
# This takes 15–25 minutes (GKE cluster creation is slow — this is normal).
```

> At this point return to **Step 3** and push the Docker image if not done yet.

### 4b. Full apply (deploy all workloads)

```bash
terraform apply
# Provisions Cloud SQL, Kubernetes namespace, ConfigMap, Secret,
# Kafka (via Helm), Redis StatefulSet, and the application Deployment + Ingress.
```

---

## 5. kubectl context setup

```bash
export USE_GKE_GCLOUD_AUTH_PLUGIN=True

gcloud container clusters get-credentials ledger-493222-dev-gke \
  --zone us-central1-a \
  --project ledger-493222

# Confirm context is active
kubectl config current-context
```

---

## 6. Verification

### 6a. Check namespace and pods
```bash
kubectl get pods -n ledger
# All pods should reach Running / Ready state.
# The app pod runs a readiness probe on /actuator/health (allow ~60 s after deploy).
```

### 6b. Check service and external IP
```bash
kubectl get svc -n ledger
kubectl get ingress -n ledger
# Ingress ADDRESS should show: 34.8.164.128
```

### 6c. Health endpoint
```bash
curl http://34.8.164.128/actuator/health
# Expected: {"status":"UP"}
```

### 6d. Smoke test — create an account
```bash
curl -X POST http://34.8.164.128/api/v1/accounts \
  -H "Idempotency-Key: $(uuidgen)" \
  -H "Content-Type: application/json" \
  -d '{"ownerId":"550e8400-e29b-41d4-a716-446655440000","currency":"USD"}'
# Expected: HTTP 202 with CommandResponse body
```

### 6e. Swagger UI
Open in browser:
```
http://34.8.164.128/swagger-ui/index.html
```
The server dropdown should default to `http://34.8.164.128` (GKE DEV).

### 6f. Check logs if pods are not ready
```bash
kubectl logs -n ledger -l app=ledger-command-service --tail=100
kubectl describe pod -n ledger -l app=ledger-command-service
```

---

## Re-deploy after a code change

Repeat steps 2 → 3, then trigger a rolling restart so Kubernetes pulls the new image:

```bash
./mvnw clean package -DskipTests
docker build -t "${IMAGE}:latest" . && docker push "${IMAGE}:latest"
kubectl rollout restart deployment/ledger-command-service -n ledger
kubectl rollout status deployment/ledger-command-service -n ledger
```

---

## Known pitfalls

| Pitfall | Fix |
|---|---|
| `gcloud components install gke-gcloud-auth-plugin` fails | gcloud is apt-managed; copy binary from `/usr/lib/google-cloud-sdk/bin/` to `~/.local/bin` (see §0c) |
| `kubectl` returns "exec: gke-gcloud-auth-plugin not found" | Set `export USE_GKE_GCLOUD_AUTH_PLUGIN=True` and ensure `~/.local/bin` is in `$PATH` |
| `docker push` returns 401 Unauthorized | Re-run `gcloud auth configure-docker us-central1-docker.pkg.dev` |
| `terraform init` fails — bucket doesn't exist | Run §0a to create the GCS state bucket first |
| `terraform apply` fails on `kubernetes_app` — cluster not found | Use `-target` phase order (§4a) and re-run `get-credentials` (§5) before full apply |
| Pods stuck in `ImagePullBackOff` | Verify image was pushed (§3); ensure `image` in terraform.tfvars matches the pushed tag |
| GKE provisioning takes >20 min | Normal for GKE zonal cluster first creation — wait it out |
| `Error: context deadline exceeded` on Kafka Helm release | Kafka readiness can be slow; re-run `terraform apply` — it will pick up where it left off |
| `SwaggerTestApplication` conflict in tests | `SwaggerTestApplication` must be in `src/test/` only, not `src/main/` |

