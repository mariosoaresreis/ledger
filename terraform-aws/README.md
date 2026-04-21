# Ledger – AWS Terraform

This directory contains the AWS infrastructure for the Ledger CQRS system.  
The GCP infrastructure lives in [`../terraform/`](../terraform/).

## Folder Structure

```
terraform-aws/
├── environments/
│   ├── dev/          # Command service (EKS + RDS + ECR + Kafka + Redis)
│   └── query-dev/    # Query service  (EKS + RDS + Kafka consumer)
└── modules/
    ├── vpc/              AWS VPC, subnets, IGW, NAT, security groups
    ├── eks/              EKS cluster, node group, OIDC provider, ALB Controller IAM
    ├── rds/              RDS PostgreSQL 16
    ├── ecr/              ECR repositories (command + query images)
    ├── kubernetes-app/   Command service K8s workloads (Kafka, Redis, Deployment, ALB Ingress)
    └── kubernetes-query/ Query service K8s workloads (Deployment, ALB Ingress)
```

## AWS Services Used

| GCP equivalent       | AWS equivalent                         |
|----------------------|----------------------------------------|
| GKE                  | EKS (Elastic Kubernetes Service)       |
| Cloud SQL            | RDS PostgreSQL 16                      |
| Artifact Registry    | ECR (Elastic Container Registry)       |
| GCS (Terraform state)| S3 + DynamoDB (state locking)          |
| GCE Load Balancer    | ALB via AWS Load Balancer Controller   |
| Cloud NAT            | NAT Gateway                            |
| Cloud Router         | Route Table + Internet Gateway         |

