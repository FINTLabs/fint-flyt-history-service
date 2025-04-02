CREATE INDEX timestamp_index ON event (timestamp);
CREATE INDEX source_application_aggregate_id_and_timestamp_and_name_index
    ON event (source_application_id,
              source_application_integration_id,
              source_application_instance_id,
              timestamp,
              name);
ANALYZE;