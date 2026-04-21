# ── VPC ───────────────────────────────────────────────────────────────────────
module "vpc" {
  source      = "../../modules/vpc"
  environment = var.environment
  region      = var.region
}

# ── EKS Cluster ───────────────────────────────────────────────────────────────
module "eks" {
  source             = "../../modules/eks"
  environment        = var.environment
  region             = var.region
  vpc_id             = module.vpc.vpc_id
  private_subnet_ids = module.vpc.private_subnet_ids
  node_sg_id         = module.vpc.eks_nodes_sg_id
  node_instance_type = var.node_instance_type
  node_desired_size  = var.node_count
  node_min_size      = var.min_nodes
  node_max_size      = var.max_nodes
  depends_on         = [module.vpc]
}

# ── RDS PostgreSQL ────────────────────────────────────────────────────────────
module "rds" {
  source          = "../../modules/rds"
  environment     = var.environment
  region          = var.region
  vpc_id          = module.vpc.vpc_id
  subnet_ids      = module.vpc.private_subnet_ids
  rds_sg_id       = module.vpc.rds_sg_id
  db_name         = "ledger"
  db_username     = var.db_username
  db_password     = var.db_password
  instance_class  = var.db_instance_class
  depends_on      = [module.vpc]
}

# ── ECR Repositories ──────────────────────────────────────────────────────────
module "ecr" {
  source      = "../../modules/ecr"
  region      = var.region
  environment = var.environment
}

# ── AWS Load Balancer Controller (installs ALB Ingress Controller into EKS) ───
resource "helm_release" "alb_controller" {
  name       = "aws-load-balancer-controller"
  repository = "https://aws.github.io/eks-charts"
  chart      = "aws-load-balancer-controller"
  version    = "1.7.2"
  namespace  = "kube-system"
  timeout    = 300

  set { name = "clusterName";                      value = module.eks.cluster_name }
  set { name = "serviceAccount.create";            value = "true" }
  set { name = "serviceAccount.name";              value = "aws-load-balancer-controller" }
  set { name = "serviceAccount.annotations.eks\\.amazonaws\\.com/role-arn"; value = module.eks.alb_controller_role_arn }
  set { name = "region";                           value = var.region }
  set { name = "vpcId";                            value = module.vpc.vpc_id }

  depends_on = [module.eks]
}

# ── Kubernetes Workloads (command service + Kafka + Redis) ────────────────────
module "kubernetes_app" {
  source         = "../../modules/kubernetes-app"
  db_host        = module.rds.db_host
  db_username    = var.db_username
  db_password    = var.db_password
  app_image      = "${module.ecr.repository_urls["ledger-command-service"]}:${var.app_image_tag}"
  app_replicas   = var.app_replicas
  kafka_replicas = var.kafka_replicas
  storage_class  = "gp2"
  depends_on     = [module.eks, module.rds, helm_release.alb_controller]
}

