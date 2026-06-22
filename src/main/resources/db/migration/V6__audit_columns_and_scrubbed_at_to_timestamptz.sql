ALTER TABLE event
    ADD COLUMN created_at TIMESTAMPTZ NULL,
    ADD COLUMN created_by JSONB NOT NULL DEFAULT '{"type":"UNKNOWN"}'::jsonb,
    ALTER scrubbed_at TYPE TIMESTAMPTZ USING scrubbed_at AT TIME ZONE 'UTC';
