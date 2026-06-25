ALTER TABLE event
    ADD COLUMN created_at TIMESTAMPTZ NULL,
    ADD COLUMN created_by JSONB NOT NULL DEFAULT '{"type":"UNKNOWN"}'::jsonb;
