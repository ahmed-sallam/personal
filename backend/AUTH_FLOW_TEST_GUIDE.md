# Authentication Flow Verification Guide

This guide provides step-by-step instructions for manually verifying the complete authentication flow (whitelist → OTP → JWT → Protected Endpoint).

## Prerequisites

1. **Docker Desktop** installed and running
2. **Java 21** installed
3. **Maven 3.8+** installed

## Setup

### 1. Start Infrastructure Services

```bash
cd backend
docker-compose up -d postgres redis rabbitmq
```

Wait for all services to be healthy:
```bash
docker-compose ps
```

Expected output should show all services as "Up".

### 2. Set Environment Variables

Create a `.env` file in the `backend` directory:

```bash
# JWT Configuration
JWT_SECRET_KEY=aVerySecureSecretKeyForJWTTokenGenerationThatIsAtLeast256BitsLong
JWT_EXPIRATION=3600000

# Database
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/productivity_tracker
SPRING_DATASOURCE_USERNAME=postgres
SPRING_DATASOURCE_PASSWORD=postgres

# Redis
SPRING_REDIS_HOST=localhost
SPRING_REDIS_PORT=6379

# RabbitMQ
SPRING_RABBITMQ_HOST=localhost
SPRING_RABBITMQ_PORT=5672
SPRING_RABBITMQ_USERNAME=guest
SPRING_RABBITMQ_PASSWORD=guest

# AI API Keys (optional for auth flow testing)
SPRING_AI_OPENAI_API_KEY=sk-or-v1-placeholder
SPRING_AI_ANTHROPIC_API_KEY=sk-or-v1-placeholder
```

### 3. Initialize Database with Test User

Start the Spring Boot application:
```bash
mvn spring-boot:run
```

Then, connect to the database and create a whitelisted user:

```bash
# Using docker exec
docker exec -it -u postgres postgres psql -d productivity_tracker

# In psql:
INSERT INTO users (email, is_whitelisted) VALUES ('test@example.com', true);
INSERT INTO users (email, is_whitelisted) VALUES ('blocked@example.com', false);

-- Verify users
SELECT * FROM users;

-- Exit psql
\q
```

## Manual Authentication Flow Test

### Step 1: Request OTP (Whitelisted Email)

```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email": "test@example.com"}'
```

**Expected Response:**
```json
{
  "success": true,
  "message": "OTP sent successfully",
  "otp": "123456"
}
```

Note the `otp` value - you'll need it for the next step.

### Step 2: Verify OTP and Receive JWT

```bash
curl -X POST http://localhost:8080/api/auth/verify \
  -H "Content-Type: application/json" \
  -d '{"email": "test@example.com", "otp": "YOUR_OTP_HERE"}'
```

**Expected Response:**
```json
{
  "success": true,
  "message": "Authentication successful",
  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "userId": "550e8400-e29b-41d4-a716-446655440000",
  "email": "test@example.com"
}
```

Note the `token` value - you'll need it for accessing protected endpoints.

### Step 3: Access Protected Endpoint with JWT

```bash
curl -X GET http://localhost:8080/api/audio \
  -H "Authorization: Bearer YOUR_JWT_TOKEN"
```

**Expected Response:**
- Status: 200 OK (with empty array or audio records)
- NOT 401 Unauthorized

### Step 4: Try Protected Endpoint without JWT

```bash
curl -X GET http://localhost:8080/api/audio
```

**Expected Response:**
- Status: 401 Unauthorized

## Negative Test Cases

### Test 1: Non-whitelisted Email

```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email": "blocked@example.com"}'
```

**Expected Response:**
- Status: 403 Forbidden
```json
{
  "type": "https://api.example.com/errors/whitelist",
  "title": "Whitelist Validation Failed",
  "status": 403,
  "detail": "Email 'blocked@example.com' is not whitelisted for access.",
  "instance": "/api/auth/login"
}
```

### Test 2: Invalid OTP

```bash
curl -X POST http://localhost:8080/api/auth/verify \
  -H "Content-Type: application/json" \
  -d '{"email": "test@example.com", "otp": "000000"}'
```

**Expected Response:**
- Status: 401 Unauthorized

### Test 3: Expired OTP (wait 5 minutes)

After requesting OTP, wait 5 minutes and then try to verify:

```bash
curl -X POST http://localhost:8080/api/auth/verify \
  -H "Content-Type: application/json" \
  -d '{"email": "test@example.com", "otp": "YOUR_OTP_HERE"}'
```

**Expected Response:**
- Status: 401 Unauthorized (OTP expired)

### Test 4: OTP Reuse Prevention

After successfully verifying OTP, try to use the same OTP again:

```bash
curl -X POST http://localhost:8080/api/auth/verify \
  -H "Content-Type: application/json" \
  -d '{"email": "test@example.com", "otp": "YOUR_OTP_HERE"}'
```

**Expected Response:**
- Status: 401 Unauthorized (OTP already used)

## Verification Checklist

Run through this checklist to verify the authentication flow:

- [ ] Whitelisted email can request OTP → 200 OK with OTP
- [ ] Non-whitelisted email cannot request OTP → 403 Forbidden
- [ ] OTP is stored in Redis with 5-minute TTL
- [ ] Valid OTP generates JWT token → 200 OK with token
- [ ] Invalid OTP is rejected → 401 Unauthorized
- [ ] OTP is deleted after successful verification
- [ ] Same OTP cannot be reused → 401 Unauthorized
- [ ] JWT token contains user ID and email claims
- [ ] Protected endpoint accessible with valid JWT → 200 OK
- [ ] Protected endpoint returns 401 without JWT
- [ ] Protected endpoint returns 401 with invalid JWT

## Debugging Tips

### Check Application Logs

```bash
# View logs
tail -f backend/logs/application.log

# Or if using console
mvn spring-boot:run 2>&1 | grep -E "OTP|JWT|Auth"
```

### Verify Redis

```bash
# Connect to Redis
docker exec -it redis redis-cli

# Check OTP storage
KEYS otp:*
GET otp:test@example.com
TTL otp:test@example.com

# Exit
exit
```

### Verify Database

```bash
# Connect to PostgreSQL
docker exec -it -u postgres postgres psql -d productivity_tracker

# Check users
SELECT * FROM users;

# Exit
\q
```

### Check JWT Token Contents

Use jwt.io to decode and inspect your JWT token:
- Copy the token from the verify response
- Paste at https://jwt.io
- Verify it contains: sub (user ID), email, iat, exp

## Automated Testing

For automated testing, run the integration test:

```bash
cd backend
mvn test -Dtest=AuthFlowIntegrationTest
```

This will:
1. Start PostgreSQL and Redis containers via Testcontainers
2. Create test users in database
3. Run through all authentication scenarios
4. Report results

## Troubleshooting

**Issue:** "Connection refused" to PostgreSQL/Redis
- **Solution:** Ensure `docker-compose up -d` was run and containers are healthy

**Issue:** "Whitelist validation failed" for whitelisted user
- **Solution:** Check database to verify `is_whitelisted = true`

**Issue:** "Invalid or expired OTP"
- **Solution:** OTP expires after 5 minutes; request a new one

**Issue:** "401 Unauthorized" with valid JWT
- **Solution:** Check JWT expiration (default 1 hour); verify token format in Authorization header

## Cleanup

Stop and remove containers:

```bash
cd backend
docker-compose down

# Or remove volumes too
docker-compose down -v
```

Clean test database:
```bash
docker exec -it -u postgres postgres psql -d productivity_tracker -c "DELETE FROM users WHERE email LIKE '%example.com%';"
```
