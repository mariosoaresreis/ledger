# How to Deploy to AWS

This guide walks through deploying the Ledger CQRS system to AWS.  
The Terraform configuration lives in [`terraform-aws/`](./terraform-aws/).

> **GCP deployment guide**: see [`how_to_deploy.md`](./how_to_deploy.md).

---

## Prerequisites

| Tool         | Minimum version | Install link |
|--------------|-----------------|--------------|
| Terraform    | 1.7             | https://developer.hashicorp.com/terraform/install |
| AWS CLI      | 2.x             | https://docs.aws.amazon.com/cli/latest/userguide/install-cliv2.html |
| Docker       | 24+             | https://docs.docker.com/get-docker/ |
| kubectl      | 1.29+           | https://kubernetes.io/docs/tasks/tools/ |
| helm         | 3.14+           | https://helm.sh/docs/intro/install/ |

### AWS credentials

```bash
aws configure
# or use environment variables:
# export AWS_ACCESS_KEY_ID=...
# export AWS_SECRET_ACCESS_KEY=...
# export AWS_DEFAULT_REGION=us-east-1
```

Your IAM user/role needs at minimum:
- `AdministratorAccess` (dev) **or** the following managed policies for least-privilege:
  `AmazonEKSFullAccess`, `AmazonRDSFullAccess`, `AmazonEC2FullAccess`,
  `AmazonECRFullAccess`, `IAMFullAccess`, `AmazonS3FullAccess`, `AmazonDynamoDBFullAccess`

---

## Part 1 – Command Service (EKS + RDS + Kafka)

### 1. Bootstrap remote state (S3 + DynamoDB)

Do this once per AWS account.

```bash
ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)
REGION=us-east-1

# Create S3 bucket for Terraform state
aws s3 mb s3://${ACCOUNT_ID}-ledger-tfstate --region $REGION
aws s3api put-bucket-versioning \
  --bucket ${ACCOUNT_ID}-ledger-tfstate \
  --versioning-configuration Status=Enabled

# Create DynamoDB table for state locking
aws dynamodb create-table \
  --table-name ledger-tfstate-lock \
  --attribute-definitions AttributeName=LockID,AttributeType=S \
  --key-schema AttributeName=LockID,KeyType=HASH \
  --billing-mode PAY_PER_REQUEST \
  --region $REGION
```

### 2. Configure the command environment

```bash
cd terraform-aws/environments/dev
cp terraform.tfvars.example terraform.tfvars
```

Edit `terraform.tfvars`:
```hcl
region             = "us-east-1"
environment        = "dev"
db_password        = "YourSecurePassword123!"
db_instance_class  = "db.t3.micro"      # upgrade to db.t3.small+ for staging/prod
node_instance_type = "t3.medium"
node_count         = 2
min_nodes          = 1
max_nodes          = 3
app_image_tag      = "latest"
app_replicas       = 1
kafka_replicas     = 1
```

Edit `backend.tf` — replace `REPLACE_WITH_YOUR_ACCOUNT_ID` with your actual account ID:
```hcl
bucket = "123456789012-ledger-tfstate"
```

### 3. Bootstrap VPC + EKS first

```bash
terraform init
terraform apply -target=module.vpc -target=module.eks -target=module.ecr
```

This creates the VPC, EKS cluster, node group, and ECR repositories.  
Wait for all resources to finish (~10–15 minutes).

### 4. Configure kubectl

```bash
aws eks update-kubeconfig \
  --name ledger-dev-eks \
  --region us-east-1
kubectl get nodes   # should show 2 Ready nodes
```

### 5. Build and push the command service image

```bash
ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)
REGION=us-east-1
ECR_URL=${ACCOUNT_ID}.dkr.ecr.${REGION}.amazonaws.com

# Authenticate Docker with ECR
aws ecr get-login-password --region $REGION \
  | docker login --username AWS --password-stdin $ECR_URL

# Build and push (run from the repo root)
docker build -t ${ECR_URL}/ledger-command-service:latest .
docker push ${ECR_URL}/ledger-command-service:latest
```

### 6. Full apply (RDS + ALB Controller + Kubernetes workloads)

```bash
terraform apply
```

This deploys:
- RDS PostgreSQL 16 (private, accessible only from EKS nodes)
- AWS Load Balancer Controller (Helm) into `kube-system`
- Kafka (Bitnami Helm) in the `ledger` namespace
- In-cluster Redis StatefulSet
- `ledger-command-service` Deployment + ALB Ingress

### 7. Get the command service URL

```bash
kubectl get ingress ledger-command-service -n ledger
# Look for the ADDRESS column – this is your ALB DNS name
# Example: k8s-ledger-ledgerco-abc123.us-east-1.elb.amazonaws.com
```

Wait 2–3 minutes for the ALB to provision, then test:
```bash
ALB=<ADDRESS from above>
curl http://${ALB}/actuator/health
```

---

## Part 2 – Query Service

The query service runs in a separate EKS cluster (same AWS account, same region by default).  
It connects to Kafka in the command cluster.

### Kafka connectivity between clusters

Since both clusters are in the **same AWS account and region**, the recommended approach is to
expose Kafka via an **internal Network Load Balancer** with VPC peering, or simply use the
**same EKS cluster** (deploy query service into the same cluster in a separate namespace).

For simplicity in dev, you can deploy the query service into the **same EKS cluster** as the
command service by pointing both Terraform environments at the same cluster. Alternatively,
for true separation, expose Kafka with an NLB (see `Kafka NLB` section below).

### Option A – Same cluster (simplest for dev)

Skip creating a new EKS cluster. Instead, run the `kubernetes-query` module manually or point
`query-dev` to reuse the command cluster's EKS endpoint and configure
`kafka_bootstrap_servers` to the in-cluster Kafka service DNS:

```
kafka.ledger.svc.cluster.local:9092
```

### Option B – Separate cluster with VPC Peering + Kafka NLB

#### 8a. Expose Kafka via internal NLB in the command cluster

```bash
kubectl apply -f - <<EOF
apiVersion: v1
kind: Service
metadata:
  name: kafka-nlb
  namespace: ledger
  annotations:
    service.beta.kubernetes.io/aws-load-balancer-type: "nlb"
    service.beta.kubernetes.io/aws-load-balancer-internal: "true"
spec:
  type: LoadBalancer
  selector:
    app.kubernetes.io/name: kafka
    app.kubernetes.io/instance: kafka
    app.kubernetes.io/component: controller-eligible
  ports:
  - name: kafka
    port: 9092
    targetPort: 9092
EOF

kubectl get svc kafka-nlb -n ledger
# Note the EXTERNAL-IP (private VPC IP of the NLB)
```

#### 8b. Peer the two VPCs

```bash
# Get VPC IDs
COMMAND_VPC=$(aws eks describe-cluster --name ledger-dev-eks \
  --query "cluster.resourcesVpcConfig.vpcId" --output text)
QUERY_VPC=$(aws eks describe-cluster --name ledger-dev-query-eks \
  --query "cluster.resourcesVpcConfig.vpcId" --output text)

# Create peering connection
PEER_ID=$(aws ec2 create-vpc-peering-connection \
  --vpc-id $COMMAND_VPC \
  --peer-vpc-id $QUERY_VPC \
  --query "VpcPeeringConnection.VpcPeeringConnectionId" --output text)

aws ec2 accept-vpc-peering-connection --vpc-peering-connection-id $PEER_ID

# Add routes (update both route tables to point to each other via peering)
# See AWS docs: https://docs.aws.amazon.com/vpc/latest/peering/working-with-vpc-peering.html
```

#### 8c. Configure query-dev

```bash
cd terraform-aws/environments/query-dev
cp terraform.tfvars.example terraform.tfvars
```

Edit `terraform.tfvars`:
```hcl
region                  = "us-east-1"
environment             = "dev"
db_password             = "YourSecurePassword123!"
kafka_bootstrap_servers = "INTERNAL_NLB_DNS_OR_IP:9092"  # from step 8a
ecr_base_url            = "123456789012.dkr.ecr.us-east-1.amazonaws.com"
node_instance_type      = "t3.medium"
node_count              = 1
app_image_tag           = "latest"
app_replicas            = 1
```

Edit `backend.tf` — same S3 bucket, different key (`ledger/query-dev/terraform.tfstate`).

#### 8d. Bootstrap and deploy query cluster

```bash
terraform init
terraform apply -target=module.vpc -target=module.eks
# configure kubectl for query cluster
aws eks update-kubeconfig --name ledger-dev-query-eks --region us-east-1
```

#### 8e. Build and push the query service image

```bash
ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)
REGION=us-east-1
ECR_URL=${ACCOUNT_ID}.dkr.ecr.${REGION}.amazonaws.com

# Build the query service (assumes separate query service repo/module)
docker build -f Dockerfile.query -t ${ECR_URL}/ledger-query-service:latest .
docker push ${ECR_URL}/ledger-query-service:latest
```

#### 8f. Full apply for query cluster

```bash
terraform apply
```

#### 8g. Get the query service URL

```bash
kubectl get ingress ledger-query-service -n ledger-query
ALB=<ADDRESS from above>
curl http://${ALB}/actuator/health
```

---

## Flyway Migrations

Migrations run automatically on application startup via Spring Boot.  
RDS is private — to run migrations manually, connect via AWS Session Manager or a bastion host:

```bash
# Port-forward through a pod in the cluster
kubectl run psql-client --image=postgres:16 -n ledger --rm -it --restart=Never -- \
  psql "postgresql://ledger:PASSWORD@RDS_HOST:5432/ledger"
```

---

## Updating the Application

```bash
# Rebuild and push
docker build -t ${ECR_URL}/ledger-command-service:v1.2.3 .
docker push ${ECR_URL}/ledger-command-service:v1.2.3

# Update via Terraform
cd terraform-aws/environments/dev
# Edit terraform.tfvars: app_image_tag = "v1.2.3"
terraform apply

# Or force a rolling restart without changing the tag:
kubectl rollout restart deployment/ledger-command-service -n ledger
```

---

## Tearing Down

```bash
# Destroy workloads first (avoids ALB/ENI orphan issues)
cd terraform-aws/environments/dev
terraform destroy -target=module.kubernetes_app
terraform destroy
```

> **Note**: RDS has `skip_final_snapshot = true` in dev. Set it to `false` and configure
> `final_snapshot_identifier` before destroying a production database.

---

## Cost Estimate (dev, us-east-1)

| Resource              | Approx monthly cost |
|-----------------------|---------------------|
| EKS cluster (control) | ~$73                |
| 2× t3.medium nodes    | ~$60                |
| RDS db.t3.micro       | ~$15                |
| NAT Gateway           | ~$32                |
| ALB                   | ~$16                |
| ECR storage           | ~$1                 |
| **Total**             | **~$197/month**     |

Reduce costs in dev by using `t3.small` nodes and stopping the cluster overnight.

