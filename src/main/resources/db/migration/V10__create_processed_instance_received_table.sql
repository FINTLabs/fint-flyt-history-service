CREATE TABLE processed_instance_received
(
    topic_name     text        NOT NULL CHECK (btrim(topic_name) <> ''),
    partition_id   integer     NOT NULL CHECK (partition_id >= 0),
    message_offset bigint      NOT NULL CHECK (message_offset >= 0),
    processed_at   timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (topic_name, partition_id, message_offset)
);
