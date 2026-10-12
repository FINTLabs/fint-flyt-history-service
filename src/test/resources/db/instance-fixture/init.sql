-- Test-only instance schema, matching instance-service migrations V1-V4.
CREATE SCHEMA mapping_test_instance;
SET search_path TO mapping_test_instance;

-- V1__init.sql
create table instance_object
(
    id                            bigserial not null,
    instance_object_collection_id int8,
    primary key (id)
);
create table instance_object_value_per_key
(
    instance_object_id int8         not null,
    value              varchar(255),
    key                varchar(255) not null,
    primary key (instance_object_id, key)
);
create table instance_object_collection
(
    id                 bigserial not null,
    instance_object_id int8,
    key                varchar(255),
    primary key (id)
);
alter table instance_object
    add constraint FKp6f8ogjmuv3trkdxfp1eb7so0 foreign key (instance_object_collection_id) references instance_object_collection;
alter table instance_object_value_per_key
    add constraint FKjw2gv69q6faqejinh37u6te94 foreign key (instance_object_id) references instance_object;
alter table instance_object_collection
    add constraint FK7bq0pdfgejo5hxcakb3vxq079 foreign key (instance_object_id) references instance_object;

-- V2__increase_max_size_instance_value.sql
alter table instance_object_value_per_key alter column value type TEXT;

-- V3__add_timestamp_to_instance_object_table.sql
alter table instance_object
    add column created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP;

-- V4__add_audit_columns_to_instance_object.sql
alter table instance_object
    alter column created_at drop default;

alter table instance_object
    alter column created_at type timestamptz using created_at at time zone 'UTC';

alter table instance_object
    add column created_by jsonb not null default '{"type":"UNKNOWN"}'::jsonb;

SET search_path TO public;
