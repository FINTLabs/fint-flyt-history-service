ALTER TABLE instance_received_offset
    ADD COLUMN cluster_id text,
    ADD COLUMN topic_id uuid,
    ADD CONSTRAINT instance_received_cluster_id_not_blank CHECK (btrim(cluster_id) <> ''),
    ADD CONSTRAINT instance_received_topic_id_not_zero CHECK (topic_id <> '00000000-0000-0000-0000-000000000000'),
    ADD CONSTRAINT instance_received_generation_complete CHECK ((cluster_id IS NULL) = (topic_id IS NULL));
