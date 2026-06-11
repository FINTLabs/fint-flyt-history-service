ALTER TABLE event
    ADD COLUMN is_scrubbed boolean NOT NULL DEFAULT FALSE,
    ADD COLUMN scrubbed_at timestamp NULL;

CREATE INDEX is_scrubbed_and_timestamp_index
    ON event (is_scrubbed, timestamp)
    WHERE is_scrubbed = FALSE;
