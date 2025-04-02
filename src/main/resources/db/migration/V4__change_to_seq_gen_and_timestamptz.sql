ALTER SEQUENCE event_id_seq INCREMENT 500;
ALTER SEQUENCE error_id_seq INCREMENT 500;
ALTER TABLE event
    ALTER timestamp TYPE timestamptz USING timestamp AT TIME ZONE 'UTC';
