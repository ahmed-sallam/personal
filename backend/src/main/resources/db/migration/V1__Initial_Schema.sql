-- ============================================================================
-- V1__Initial_Schema.sql
-- ============================================================================
-- Flyway migration script for Productivity Tracker Backend
-- Creates initial database schema for users, audio_records, processing_tasks,
-- ai_summaries, and action_items tables.
-- ============================================================================

-- ============================================================================
-- Table: users
-- ============================================================================
-- Stores user information for whitelist-based authentication.
-- Only whitelisted users can authenticate via OTP flow.
-- ============================================================================
CREATE TABLE users (
    -- Primary key using UUID for better security and scalability
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    -- User's email address (must be unique)
    email VARCHAR(255) NOT NULL UNIQUE,

    -- Whitelist flag (only whitelisted users can access the system)
    is_whitelisted BOOLEAN NOT NULL DEFAULT false,

    -- Audit timestamps
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Index for efficient email lookups during authentication
CREATE UNIQUE INDEX idx_user_email ON users(email);

-- ============================================================================
-- Table: audio_records
-- ============================================================================
-- Stores audio file metadata uploaded by users.
-- Actual audio files are stored in object storage (MinIO/Hetzner).
-- ============================================================================
CREATE TABLE audio_records (
    -- Primary key using UUID
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    -- Foreign key to users table
    user_id UUID NOT NULL,

    -- Object storage key/path for the audio file
    file_key VARCHAR(500) NOT NULL,

    -- Original filename as uploaded by the user
    file_name VARCHAR(255) NOT NULL,

    -- Duration of the audio recording in seconds
    duration_seconds INTEGER NOT NULL,

    -- MIME type of the audio file (e.g., "audio/mpeg", "audio/wav")
    mime_type VARCHAR(100) NOT NULL,

    -- File size in bytes
    file_size_bytes BIGINT,

    -- Audit timestamps
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- Foreign key constraint
    CONSTRAINT fk_audio_user
        FOREIGN KEY (user_id)
        REFERENCES users(id)
        ON DELETE RESTRICT
        ON UPDATE CASCADE
);

-- Indexes for efficient querying
CREATE INDEX idx_audio_user_id ON audio_records(user_id);
CREATE INDEX idx_audio_created_at ON audio_records(created_at);

-- ============================================================================
-- Table: processing_tasks
-- ============================================================================
-- Tracks the status of asynchronous audio processing pipeline.
-- Status transitions: PENDING -> PROCESSING -> COMPLETED/FAILED
-- ============================================================================
CREATE TABLE processing_tasks (
    -- Primary key using UUID
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    -- Foreign key to audio_records table
    audio_id UUID NOT NULL,

    -- Processing status (PENDING, PROCESSING, COMPLETED, FAILED)
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',

    -- Error log for failed processing attempts
    error_log TEXT,

    -- Number of retry attempts for failed processing
    retry_count INTEGER NOT NULL DEFAULT 0,

    -- Timestamp when processing started
    started_at TIMESTAMP,

    -- Timestamp when processing completed
    completed_at TIMESTAMP,

    -- Audit timestamps
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- Foreign key constraint
    CONSTRAINT fk_processing_audio
        FOREIGN KEY (audio_id)
        REFERENCES audio_records(id)
        ON DELETE CASCADE
        ON UPDATE CASCADE,

    -- Check constraint for valid status values
    CONSTRAINT chk_processing_status
        CHECK (status IN ('PENDING', 'PROCESSING', 'COMPLETED', 'FAILED'))
);

-- Indexes for efficient querying
CREATE INDEX idx_processing_audio_id ON processing_tasks(audio_id);
CREATE INDEX idx_processing_status ON processing_tasks(status);
CREATE INDEX idx_processing_created_at ON processing_tasks(created_at);

-- ============================================================================
-- Table: ai_summaries
-- ============================================================================
-- Stores AI-generated analysis results from audio transcription and NLU.
-- Uses JSONB for flexible structured data storage.
-- ============================================================================
CREATE TABLE ai_summaries (
    -- Primary key using UUID
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    -- Foreign key to audio_records table
    audio_id UUID NOT NULL UNIQUE,

    -- Raw transcription text from Whisper STT model
    content TEXT NOT NULL,

    -- Structured data extracted by Claude (JSONB for flexible querying)
    structured_data JSONB,

    -- Total tokens consumed in AI processing
    tokens_used INTEGER,

    -- AI model identifier used (e.g., "anthropic/claude-3.5-sonnet")
    model VARCHAR(100),

    -- Audit timestamp
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- Foreign key constraint
    CONSTRAINT fk_summary_audio
        FOREIGN KEY (audio_id)
        REFERENCES audio_records(id)
        ON DELETE CASCADE
        ON UPDATE CASCADE
);

-- Indexes for efficient querying
CREATE INDEX idx_summary_audio_id ON ai_summaries(audio_id);
CREATE INDEX idx_summary_created_at ON ai_summaries(created_at);

-- GIN index for JSONB structured_data (enables efficient JSON queries)
CREATE INDEX idx_summary_structured_data ON ai_summaries USING GIN (structured_data);

-- ============================================================================
-- Table: action_items
-- ============================================================================
-- Stores individual action items extracted from AI summaries.
-- Each action item represents a task to be completed.
-- ============================================================================
CREATE TABLE action_items (
    -- Primary key using UUID
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    -- Foreign key to ai_summaries table
    summary_id UUID NOT NULL,

    -- Description of the action item
    description TEXT NOT NULL,

    -- Completion status of the action item
    is_completed BOOLEAN NOT NULL DEFAULT false,

    -- Optional due date for the action item
    due_date TIMESTAMP,

    -- Audit timestamps
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- Timestamp when action was marked as completed
    completed_at TIMESTAMP,

    -- Foreign key constraint
    CONSTRAINT fk_action_summary
        FOREIGN KEY (summary_id)
        REFERENCES ai_summaries(id)
        ON DELETE CASCADE
        ON UPDATE CASCADE
);

-- Indexes for efficient querying
CREATE INDEX idx_action_summary_id ON action_items(summary_id);
CREATE INDEX idx_action_completed ON action_items(is_completed);
CREATE INDEX idx_action_created_at ON action_items(created_at);

-- ============================================================================
-- Update timestamp trigger function (for automatic updated_at)
-- ============================================================================
-- Creates a trigger function to automatically update the updated_at column
-- on row modification. This mimics Hibernate's @UpdateTimestamp behavior.
-- ============================================================================
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- ============================================================================
-- Apply update triggers to all tables with updated_at column
-- ============================================================================
CREATE TRIGGER trigger_users_updated_at
    BEFORE UPDATE ON users
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER trigger_audio_records_updated_at
    BEFORE UPDATE ON audio_records
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER trigger_processing_tasks_updated_at
    BEFORE UPDATE ON processing_tasks
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER trigger_action_items_updated_at
    BEFORE UPDATE ON action_items
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

-- ============================================================================
-- Comments on tables and columns for documentation
-- ============================================================================
COMMENT ON TABLE users IS 'User accounts for whitelist-based authentication';
COMMENT ON TABLE audio_records IS 'Audio file metadata uploaded by users';
COMMENT ON TABLE processing_tasks IS 'Async audio processing task status tracking';
COMMENT ON TABLE ai_summaries IS 'AI-generated transcriptions and structured data';
COMMENT ON TABLE action_items IS 'Extracted action items from AI summaries';

COMMENT ON COLUMN ai_summaries.structured_data IS 'Structured data extracted by Claude (event, duration, category, action_items)';
COMMENT ON COLUMN ai_summaries.tokens_used IS 'Total AI tokens consumed (Whisper + Claude)';
COMMENT ON COLUMN processing_tasks.retry_count IS 'Number of retry attempts for failed processing';
COMMENT ON COLUMN audio_records.file_key IS 'Object storage key/path (not the actual file)';
