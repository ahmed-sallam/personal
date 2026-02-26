# Audio Processing Pipeline Test Guide

This guide provides step-by-step instructions for manually testing the audio processing pipeline integration.

## Overview

The audio processing pipeline tests verify the complete flow from audio upload to AI processing:
1. User authentication (JWT token)
2. Audio upload with metadata
3. Processing task creation and status tracking
4. RabbitMQ message queuing
5. Consumer processing (simulated or real)
6. AI summary creation

## Prerequisites

### 1. Start Infrastructure Services

```bash
cd backend
docker-compose up -d
```

Verify services are running:
```bash
docker-compose ps
```

Expected output: All services (postgres, redis, rabbitmq) should show "Up" status.

### 2. Set Environment Variables

Create a `.env` file in the `backend` directory:

```bash
# OpenRouter API Keys (for Whisper and Claude)
SPRING_AI_OPENAI_API_KEY=sk-or-v1-your-openrouter-key
SPRING_AI_ANTHROPIC_API_KEY=sk-or-v1-your-openrouter-key

# JWT Configuration
JWT_SECRET_KEY=your-256-bit-secret-key-here
JWT_EXPIRATION=3600000
```

### 3. Start Spring Boot Application

```bash
mvn spring-boot:run
```

Wait for the application to start (look for "Started ProductivityTrackerApplication").

## Test Steps

### Step 1: Authenticate and Get JWT Token

First, request OTP:

```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "email": "test@example.com"
  }'
```

Expected response (if whitelisted):
```json
{
  "success": true,
  "message": "OTP sent successfully",
  "otp": "123456"
}
```

Save the OTP value, then verify and get JWT:

```bash
curl -X POST http://localhost:8080/api/auth/verify \
  -H "Content-Type: application/json" \
  -d '{
    "email": "test@example.com",
    "otp": "123456"
  }'
```

Expected response:
```json
{
  "success": true,
  "message": "Authentication successful",
  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "userId": "550e8400-e29b-41d4-a716-446655440000",
  "email": "test@example.com"
}
```

Save the JWT token for subsequent requests.

### Step 2: Upload Audio Metadata

Create a test audio file in the expected storage location:

```bash
# Create storage directory
mkdir -p storage/audio/test

# Create a dummy audio file (or use a real one)
dd if=/dev/zero of=storage/audio/test/sample-meeting.mp3 bs=1024 count=240
```

Upload audio metadata:

```bash
curl -X POST http://localhost:8080/api/audio/upload \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer YOUR_JWT_TOKEN" \
  -d '{
    "fileKey": "audio/test/sample-meeting.mp3",
    "fileName": "sample-meeting.mp3",
    "durationSeconds": 120,
    "mimeType": "audio/mpeg",
    "fileSizeBytes": 240000
  }'
```

Expected response (201 Created):
```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "fileName": "sample-meeting.mp3",
  "fileKey": "audio/test/sample-meeting.mp3",
  "durationSeconds": 120,
  "mimeType": "audio/mpeg",
  "fileSizeBytes": 240000,
  "processingStatus": "PENDING",
  "createdAt": "2024-01-15T10:30:00",
  "updatedAt": "2024-01-15T10:30:00",
  "summaryId": null,
  "errorMessage": null
}
```

Save the audio record ID.

### Step 3: Verify Processing Task Status

Check the processing status in the database:

```bash
# Connect to PostgreSQL
docker exec -it postgres-container psql -U postgres -d productivity_tracker

# Query processing task
SELECT id, audio_id, status, retry_count, error_log, created_at, started_at, completed_at
FROM processing_tasks
WHERE audio_id = 'YOUR_AUDIO_RECORD_ID';
```

Expected: Status should be `PENDING` initially, then transition to `PROCESSING`, then `COMPLETED` or `FAILED`.

### Step 4: Verify RabbitMQ Queue

Check if the message is in the RabbitMQ queue:

1. Open RabbitMQ Management UI: http://localhost:15672
2. Login with: guest / guest
3. Navigate to Queues tab
4. Find `audio.processing.queue`
5. Check Ready and Unacked messages

Alternatively, use the CLI:

```bash
# Get list of queues
docker exec rabbitmq-container rabbitmqctl list_queues

# Get messages from queue (requires rabbitmqadmin)
docker exec rabbitmq-container rabbitmqadmin list queues
```

### Step 5: Monitor Processing Logs

Watch the Spring Boot application logs for processing activity:

```bash
# In the terminal where mvn spring-boot:run is running
# Look for log messages like:

Received audio processing task from queue: audioRecordId=...
Starting audio processing: audioRecordId=..., fileName=...
Loading audio file from storage: fileKey=...
Starting audio transcription with Whisper: audioRecordId=...
Audio transcription completed: audioRecordId=..., transcriptionLength=... characters
Starting transcription analysis with Claude: audioRecordId=...
Transcription analysis completed: audioRecordId=..., event='...', category='...'
Saving AI summary to database: audioRecordId=...
Audio processing completed successfully: audioRecordId=...
```

### Step 6: Verify AI Summary in Database

After processing completes, check the AI summary:

```bash
# Connect to PostgreSQL
docker exec -it postgres-container psql -U postgres -d productivity_tracker

# Query AI summary
SELECT id, audio_id, content, structured_data, tokens_used, model, created_at
FROM ai_summaries
WHERE audio_id = 'YOUR_AUDIO_RECORD_ID';
```

Expected: A row with transcription text and structured data.

### Step 7: Verify Final Processing Task Status

Check the processing task again:

```bash
SELECT id, audio_id, status, retry_count, error_log, created_at, started_at, completed_at
FROM processing_tasks
WHERE audio_id = 'YOUR_AUDIO_RECORD_ID';
```

Expected: Status should be `COMPLETED` with `started_at` and `completed_at` timestamps.

If failed, check `error_log` for details.

## Troubleshooting

### Issue: Upload returns 401 Unauthorized

**Solution:** Verify JWT token is valid and not expired. Re-authenticate if needed.

### Issue: Processing task stays in PENDING status

**Possible causes:**
1. RabbitMQ consumer is not running
2. Message is stuck in the queue
3. Consumer cannot process the message

**Solutions:**
- Check application logs for consumer errors
- Verify RabbitMQ connection
- Check RabbitMQ Management UI for queue depth

### Issue: Processing task status becomes FAILED

**Solution:** Check the `error_log` field in the `processing_tasks` table:

```bash
SELECT error_log FROM processing_tasks WHERE audio_id = 'YOUR_AUDIO_RECORD_ID';
```

Common errors:
- "Audio file not found" - Verify file exists in storage path
- "Whisper API error" - Check OpenRouter API key
- "Claude API error" - Check OpenRouter API key

### Issue: RabbitMQ connection refused

**Solution:** Verify RabbitMQ container is running:

```bash
docker-compose ps rabbitmq
docker-compose logs rabbitmq
```

### Issue: No messages in queue

**Solution:** Verify the producer sent the message. Check application logs for:

```
Sending audio processing task to queue: audioRecordId=...
Audio processing task queued successfully: audioRecordId=...
```

## Integration Test Execution

To run the automated integration tests:

```bash
cd backend
mvn test -Dtest=AudioProcessingPipelineIntegrationTest
```

This will:
1. Start TestContainers (PostgreSQL, Redis, RabbitMQ)
2. Run the complete test suite
3. Verify all pipeline components
4. Generate test report

## Verification Checklist

- [ ] User can authenticate and receive JWT token
- [ ] Audio upload returns 201 Created with audio record ID
- [ ] AudioRecord exists in database with correct metadata
- [ ] ProcessingTask exists with PENDING status
- [ ] RabbitMQ queue receives the message
- [ ] Consumer picks up and processes the message
- [ ] ProcessingTask status transitions: PENDING → PROCESSING → COMPLETED
- [ ] AISummary is created with transcription and structured data
- [ ] No errors in application logs
- [ ] Processing completes within expected time (depends on audio length)

## Expected Timing

| Step | Expected Time |
|------|---------------|
| Authentication | < 1 second |
| Audio upload | < 1 second |
| Queue message | < 100ms |
| Consumer pickup | < 5 seconds |
| Whisper transcription | ~10-30 seconds per minute of audio |
| Claude analysis | ~2-5 seconds |
| Total processing | ~15-60 seconds depending on audio length |

## Test Data Examples

### Valid Audio Upload Request

```json
{
  "fileKey": "audio/2024/01/15/team-standup.mp3",
  "fileName": "team-standup.mp3",
  "durationSeconds": 300,
  "mimeType": "audio/mpeg",
  "fileSizeBytes": 6000000
}
```

### Invalid Audio Upload Request (duration = 0)

```json
{
  "fileKey": "audio/test/invalid.mp3",
  "fileName": "invalid.mp3",
  "durationSeconds": 0,
  "mimeType": "audio/mpeg",
  "fileSizeBytes": 120000
}
```

Expected response: 400 Bad Request

## Additional Resources

- RabbitMQ Management UI: http://localhost:15672
- Spring Boot Actuator Health: http://localhost:8080/actuator/health
- API Documentation: See spec.md for full API reference
- Authentication Test Guide: See AUTH_FLOW_TEST_GUIDE.md
