# Swagger/OpenAPI Configuration

## Overview

Swagger (OpenAPI 3.0) has been successfully integrated into the Ledger Command Service. This provides interactive API documentation and testing capabilities.

## Access Swagger UI

Once the application is running, access the Swagger UI at:

```
http://localhost:8080/swagger-ui/index.html
```

Or directly at:
```
http://localhost:8080/swagger-ui.html
```

### OpenAPI Specification JSON

The raw OpenAPI specification is available at:
```
http://localhost:8080/v3/api-docs
```

### YAML Format

For YAML format:
```
http://localhost:8080/v3/api-docs.yaml
```

---

## Features

✅ **Interactive API Documentation**
- Browse all endpoints with full descriptions
- View request/response schemas
- See example payloads

✅ **Try It Out**
- Send test requests directly from the UI
- Automatic validation of request parameters
- Response display with status codes

✅ **Schema Documentation**
- All request/response DTOs documented with examples
- Field descriptions and constraints shown
- Validation rules visible

✅ **OpenAPI Compliant**
- Standard OpenAPI 3.0 specification
- Compatible with code generation tools
- Integrates with API management platforms

---

## Endpoints Documented

### Account Management

**POST /api/v1/accounts**
- Create a new account
- Requires: `Idempotency-Key` header
- Body: `CreateAccountRequest`
- Response: `CommandResponse`

**POST /api/v1/accounts/{accountId}/credits**
- Credit an account with funds
- Requires: `Idempotency-Key` header, `accountId` path parameter
- Body: `MoneyCommandRequest`
- Response: `CommandResponse`

**POST /api/v1/accounts/{accountId}/debits**
- Debit an account (withdraw funds)
- Requires: `Idempotency-Key` header, `accountId` path parameter
- Body: `MoneyCommandRequest`
- Response: `CommandResponse`

**PATCH /api/v1/accounts/{accountId}/status**
- Update account status (ACTIVE, FROZEN, CLOSED)
- Requires: `Idempotency-Key` header, `accountId` path parameter
- Body: `UpdateAccountStatusRequest`
- Response: `CommandResponse`

### Transfers

**POST /api/v1/transfers**
- Initiate a transfer between two accounts
- Requires: `Idempotency-Key` header
- Body: `TransferRequest`
- Response: `CommandResponse`

---

## Configuration Files

### 1. Dependencies (pom.xml)

The following dependency is included:

```xml
<dependency>
    <groupId>org.springdoc</groupId>
    <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
    <version>2.8.9</version>
</dependency>
```

This includes:
- OpenAPI 3.0 support
- Swagger UI
- Auto-generated API documentation

### 2. Application Properties

Configuration in `src/main/resources/application.properties`:

```properties
# Swagger/OpenAPI Configuration
springdoc.swagger-ui.enabled=true
springdoc.swagger-ui.path=/swagger-ui.html
springdoc.swagger-ui.urls-primary-name=Ledger API
springdoc.api-docs.path=/v3/api-docs
springdoc.api-docs.enabled=true
springdoc.show-actuator=false
```

### 3. Swagger Configuration Class

File: `src/main/java/com/marioreis/ledger/config/SwaggerConfig.java`

This class defines:
- API title, version, and description
- Contact information
- License details
- Available servers (local dev, production)

---

## Request/Response Examples

### Create Account

**Request:**
```bash
curl -X POST http://localhost:8080/api/v1/accounts \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: 550e8400-e29b-41d4-a716-446655440000" \
  -d '{
    "ownerId": "550e8400-e29b-41d4-a716-446655440001",
    "currency": "USD"
  }'
```

**Response (202 Accepted):**
```json
{
  "commandId": "550e8400-e29b-41d4-a716-446655440002",
  "idempotencyKey": "550e8400-e29b-41d4-a716-446655440000",
  "operation": "CREATE_ACCOUNT",
  "status": "ACCEPTED",
  "primaryResourceId": "550e8400-e29b-41d4-a716-446655440003",
  "events": [
    {
      "aggregateId": "550e8400-e29b-41d4-a716-446655440003",
      "eventType": "ACCOUNT_CREATED",
      "version": 1
    }
  ],
  "processedAt": "2024-01-15T10:30:00Z"
}
```

### Credit Account

**Request:**
```bash
curl -X POST http://localhost:8080/api/v1/accounts/550e8400-e29b-41d4-a716-446655440003/credits \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: 550e8400-e29b-41d4-a716-446655440010" \
  -d '{
    "amount": "100.50",
    "currency": "USD",
    "reference": "DEPOSIT-001"
  }'
```

**Response (202 Accepted):**
```json
{
  "commandId": "550e8400-e29b-41d4-a716-446655440011",
  "idempotencyKey": "550e8400-e29b-41d4-a716-446655440010",
  "operation": "CREDIT_ACCOUNT",
  "status": "ACCEPTED",
  "primaryResourceId": "550e8400-e29b-41d4-a716-446655440003",
  "events": [
    {
      "aggregateId": "550e8400-e29b-41d4-a716-446655440003",
      "eventType": "ACCOUNT_CREDITED",
      "version": 2
    }
  ],
  "processedAt": "2024-01-15T10:30:05Z"
}
```

### Transfer

**Request:**
```bash
curl -X POST http://localhost:8080/api/v1/transfers \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: 550e8400-e29b-41d4-a716-446655440020" \
  -d '{
    "sourceAccountId": "550e8400-e29b-41d4-a716-446655440003",
    "targetAccountId": "550e8400-e29b-41d4-a716-446655440004",
    "amount": "50.00",
    "currency": "USD"
  }'
```

**Response (202 Accepted):**
```json
{
  "commandId": "550e8400-e29b-41d4-a716-446655440021",
  "idempotencyKey": "550e8400-e29b-41d4-a716-446655440020",
  "operation": "TRANSFER",
  "status": "ACCEPTED",
  "primaryResourceId": "550e8400-e29b-41d4-a716-446655440003",
  "events": [
    {
      "aggregateId": "550e8400-e29b-41d4-a716-446655440003",
      "eventType": "TRANSFER_INITIATED",
      "version": 3
    },
    {
      "aggregateId": "550e8400-e29b-41d4-a716-446655440003",
      "eventType": "ACCOUNT_DEBITED",
      "version": 4
    },
    {
      "aggregateId": "550e8400-e29b-41d4-a716-446655440004",
      "eventType": "ACCOUNT_CREDITED",
      "version": 1
    }
  ],
  "processedAt": "2024-01-15T10:30:10Z"
}
```

---

## Key Headers

All mutating endpoints require:

**Idempotency-Key** (UUID)
- Ensures idempotent request execution
- Prevents duplicate processing
- Same key will return cached response on retry
- Format: UUID (e.g., `550e8400-e29b-41d4-a716-446655440000`)

**Content-Type** (Standard)
- Value: `application/json`
- Required for POST/PATCH requests

---

## Response Status Codes

| Status | Description |
|--------|-------------|
| **202** | Accepted - Command processing started (async) |
| **400** | Bad Request - Invalid parameters |
| **404** | Not Found - Resource not found |
| **409** | Conflict - Business logic violation (insufficient balance, frozen account, idempotency conflict) |
| **500** | Internal Server Error |

---

## Running the Application

### Local Development

```bash
# Build
mvn clean package

# Run
java -jar target/ledger-0.0.1-SNAPSHOT.jar
```

### With Docker Compose

```bash
docker-compose up
```

The application will start on `http://localhost:8080`

### With Kubernetes

```bash
kubectl port-forward svc/ledger-command-service -n ledger 8080:80
```

Then access: `http://localhost:8080/swagger-ui/index.html`

---

## Disabling Swagger in Production

To disable Swagger UI in production, set environment variable:

```bash
export SPRINGDOC_SWAGGER_UI_ENABLED=false
```

Or in `application.properties`:

```properties
springdoc.swagger-ui.enabled=false
```

The OpenAPI spec endpoint (`/v3/api-docs`) can be disabled separately:

```properties
springdoc.api-docs.enabled=false
```

---

## Integration with API Management Tools

### API Gateway
- Import OpenAPI spec from `/v3/api-docs`
- Swagger automatically validates requests
- Generate SDKs for different languages

### Postman
1. Open Postman
2. File → Import → Link
3. Paste: `http://localhost:8080/v3/api-docs`
4. Postman will auto-create collection with all endpoints

### ReDoc (Alternative API Documentation)
```
https://redoc.ly/openapi/<your-json-url>/
```

### Code Generation
Generate clients for any language using:

```bash
# Java
openapi-generator generate -i http://localhost:8080/v3/api-docs -g java

# Python
openapi-generator generate -i http://localhost:8080/v3/api-docs -g python

# TypeScript
openapi-generator generate -i http://localhost:8080/v3/api-docs -g typescript-axios
```

---

## Documentation Improvements

Swagger annotations added to:

✅ **Controller** (`LedgerCommandController.java`)
- Operation summaries and descriptions
- Request/response examples
- HTTP status codes documented
- Error responses with descriptions

✅ **Request DTOs**
- `CreateAccountRequest`
- `MoneyCommandRequest`
- `TransferRequest`
- `UpdateAccountStatusRequest`

✅ **Response DTOs**
- `CommandResponse`
- `EventReceipt`

Each DTO includes:
- Field descriptions
- Example values
- Validation constraints
- Enum documentation

---

## Troubleshooting

### Swagger UI not loading?

1. **Check application is running:**
   ```bash
   curl http://localhost:8080/swagger-ui/index.html
   ```

2. **Verify configuration in application.properties:**
   ```properties
   springdoc.swagger-ui.enabled=true
   ```

3. **Check for conflicting WebSecurityConfig:**
   - Ensure `/swagger-ui/**` and `/v3/api-docs` are not blocked

### Missing endpoints in Swagger?

1. **Ensure endpoints have @RestController:**
   ```java
   @RestController
   @RequestMapping("/api/v1")
   public class LedgerCommandController { ... }
   ```

2. **Check HTTP method annotations:**
   - `@PostMapping`, `@PatchMapping`, etc.

3. **Rebuild project:**
   ```bash
   mvn clean package
   ```

### Schema not showing for DTOs?

1. **Ensure DTOs have @Schema annotations** (already done)

2. **Check imports:**
   ```java
   import io.swagger.v3.oas.annotations.media.Schema;
   ```

---

## Security Considerations

### For Development Only
- Swagger UI is useful for API exploration in dev/test
- Disable in production environments

### For Production
```properties
# In application-prod.properties
springdoc.swagger-ui.enabled=false
springdoc.api-docs.enabled=false
```

### With Authentication
If you add API authentication later:

```java
@Bean
public OpenAPI customOpenAPI() {
    return new OpenAPI()
        .addSecurityItem(new SecurityRequirement().addList("bearer-jwt"))
        .components(new Components()
            .addSecuritySchemes("bearer-jwt",
                new SecurityScheme()
                    .type(SecurityScheme.Type.HTTP)
                    .scheme("bearer")
                    .bearerFormat("JWT")))
        // ... rest of config
}
```

---

## Next Steps

1. ✅ Swagger is now fully configured
2. ✅ All endpoints documented
3. ✅ Request/response schemas complete
4. 🔄 Build and test locally
5. 🔄 Deploy to GCP via Terraform
6. 🔄 Access at your production URL

---

## Resources

- [Springdoc OpenAPI Docs](https://springdoc.org/)
- [OpenAPI 3.0 Specification](https://spec.openapis.org/oas/v3.0.3)
- [Swagger UI Features](https://swagger.io/tools/swagger-ui/)
- [OpenAPI Generator](https://openapi-generator.tech/)

