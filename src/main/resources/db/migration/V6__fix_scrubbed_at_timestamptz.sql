ALTER TABLE event
    ALTER COLUMN scrubbed_at TYPE timestamptz USING scrubbed_at AT TIME ZONE 'UTC';
