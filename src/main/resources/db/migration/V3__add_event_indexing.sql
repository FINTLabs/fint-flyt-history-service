CREATE INDEX nameIndex ON event (name);

CREATE INDEX sourceApplicationAggregateIdAndNameAndTimestampIndex ON event (source_application_id,
                                                                            source_application_integration_id,
                                                                            source_application_instance_id, name,
                                                                            timestamp);

CREATE INDEX sourceApplicationAggregateIdAndNameIndex ON event (source_application_id,
                                                                source_application_integration_id,
                                                                source_application_instance_id, name);

CREATE INDEX timestampIndex ON event (timestamp);