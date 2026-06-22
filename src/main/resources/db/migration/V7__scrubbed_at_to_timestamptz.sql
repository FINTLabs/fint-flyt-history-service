ALTER TABLE event
    ALTER scrubbed_at TYPE timestamptz USING scrubbed_at AT TIME ZONE 'UTC';
