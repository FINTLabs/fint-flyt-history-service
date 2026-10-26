CREATE TABLE instance_registered_outbox
(
    id uuid PRIMARY KEY,
    origin_cluster_id text NOT NULL CHECK (btrim(origin_cluster_id) <> ''),
    origin_topic_id uuid NOT NULL,
    origin_topic_name text NOT NULL,
    origin_partition_id integer NOT NULL CHECK (origin_partition_id >= 0),
    origin_offset bigint NOT NULL CHECK (origin_offset >= 0),
    instance_flow_headers jsonb NOT NULL CHECK (jsonb_typeof(instance_flow_headers) = 'object'),
    instance_payload jsonb NOT NULL CHECK (jsonb_typeof(instance_payload) = 'object'),
    event_timestamp timestamptz NOT NULL,
    application_id text NOT NULL CHECK (btrim(application_id) <> ''),
    created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
    attempts integer NOT NULL DEFAULT 0 CHECK (attempts >= 0),
    next_attempt_at timestamptz NOT NULL DEFAULT clock_timestamp(),
    claim_token uuid,
    claimed_until timestamptz,
    last_error_type text,
    CHECK ((claim_token IS NULL) = (claimed_until IS NULL)),
    UNIQUE (origin_cluster_id, origin_topic_id, origin_topic_name, origin_partition_id, origin_offset)
);

CREATE INDEX instance_registered_outbox_ready_idx ON instance_registered_outbox (next_attempt_at, created_at);
