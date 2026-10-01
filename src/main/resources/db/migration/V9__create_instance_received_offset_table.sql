CREATE TABLE instance_received_offset
(
    topic_name   text        NOT NULL,
    partition_id integer     NOT NULL CHECK (partition_id >= 0),
    next_offset  bigint      NOT NULL CHECK (next_offset >= 0),
    updated_at   timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (topic_name, partition_id)
);
