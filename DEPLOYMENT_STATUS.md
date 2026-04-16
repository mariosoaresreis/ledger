# Ledger Application - GCP Deployment Status

## Deployment Complete ✅

### Infrastructure Status
- **GKE Cluster**: `ledger-493222-dev-gke` (us-central1-a)
  - Machine Type: e2-small
  - Nodes: 1
  - Status: RUNNING
  
- **Cloud SQL PostgreSQL 16**:  `ledger-493222-dev-postgres`
  - IP: 10.130.0.3 (private)
  - Status: RUNNABLE
  - Tier: db-f1-micro

- **Kafka**: Deployed via Helm in ledger namespace
  - Release: kafka
  - Controllers: 1 (KRaft mode)
  - Status: Running
  - Internal DNS: `kafka.ledger.svc.cluster.local:9092`

- **Redis**: In-cluster StatefulSet
  - Name: redis
  - IP: redis.ledger.svc.cluster.local:6379
  - Status: Running

- **Docker Image**: Pushed to Artifact Registry
  - Repository: `us-central1-docker.pkg.dev/ledger-493222/ledger/ledger-command-service:latest`
  - Size: ~149MB
  - Status: Available

### Application Deployment
- **Deployment**: ledger-command-service
  - Namespace: ledger
  - Replicas: 1
  - Image: us-central1-docker.pkg.dev/ledger-493222/ledger/ledger-command-service:latest
  - Status: Pods running (observed in Cloud Logging)

- **Service**: ledger-command-service
  - Type: NodePort
  - Port: 80
  - Target Port: 8080
  - NodePort: 32371
  - Cluster IP: 10.30.146.31

### Accessing the Application

#### From Within GCP VPC (Recommended for Testing)
```bash
# Method 1: Via Cluster IP (requires being in the cluster)
curl http://10.30.146.31/swagger-ui/index.html

# Method 2: Via Node IP + NodePort
curl http://10.10.0.3:32371/swagger-ui/index.html
```

#### From Outside GCP (Ingress - ✅ Complete!)
```bash
# Ingress is now fully provisioned!
curl http://34.149.219.109/swagger-ui/index.html
curl http://34.149.219.109/actuator/health
```

#### From Outside GCP (Alternative - Port Forwarding)
```bash
# Option A: Port Forward via kubectl (requires gke-gcloud-auth-plugin)
gcloud container clusters get-credentials ledger-493222-dev-gke --zone us-central1-a --project ledger-493222
kubectl port-forward -n ledger svc/ledger-command-service 8080:80 &
curl http://localhost:8080/swagger-ui/index.html
```

### Environment Variables
The pods are configured with:
```
LEDGER_DB_HOST=10.130.0.3
LEDGER_DB_PORT=5432
LEDGER_DB_USERNAME=ledger
LEDGER_DB_PASSWORD=<configured>

LEDGER_KAFKA_BOOTSTRAP_SERVERS=kafka.ledger.svc.cluster.local:9092
LEDGER_REDIS_HOST=redis.ledger.svc.cluster.local
LEDGER_REDIS_PORT=6379
```

### Probe Configuration
- **Readiness Probe**:  HTTP GET `/actuator/health` on port 8080
  - Initial Delay: 120s
  - Period: 10s
  - Failure Threshold: 5

- **Liveness Probe**: HTTP GET `/actuator/health` on port 8080
  - Initial Delay: 180s
  - Period: 15s
  - Failure Threshold: 3

### Troubleshooting

**If app isn't responding:**

1. **Check pod status**:
   ```bash
   gcloud logging read 'resource.type="k8s_pod" AND resource.labels.namespace_name="ledger"' --limit 10 --project ledger-493222
   ```

2. **Check for pod events**:
   ```bash
   gcloud logging read 'resource.type="k8s_event" AND resource.labels.namespace_name="ledger"' --limit 20 --project ledger-493222
   ```

3. **Force pod restart** (if stuck in probe failure):
   ```bash
   kubectl rollout restart deployment/ledger-command-service -n ledger
   ```

4. **Check database connectivity** - Verify Cloud SQL private IP is reachable from pods

5. **Check Kafka connectivity** - Verify Kafka pods are healthy:
   ```bash
   gcloud logging read 'resource.type="k8s_pod" AND resource.labels.pod_name=~"kafka.*"' --limit 5 --project ledger-493222
   ```

### Known Issues
1. gke-gcloud-auth-plugin installation issues - kubectl commands require workarounds
2. Cloud Logging may not capture all Java application logs immediately
3. LoadBalancer service had routing issues - switched to NodePort for reliability

### Next Steps
1. Verify app health via health endpoint
2. Test a sample API call (e.g., POST /api/v1/accounts)
3. Check Kafka message production
4. Monitor logs in Cloud Logging
5. Configure Ingress for public access if needed
