create sequence error_seq start 1 increment 50;
create sequence event_seq start 1 increment 50;
create table error
(
    id         int8 not null,
    error_code varchar(255),
    event_id   int8,
    primary key (id)
);
create table error_args
(
    error_id int8         not null,
    "value"  text,
    map_key  varchar(255) not null,
    primary key (error_id, map_key)
);
create table event
(
    id                                int8 not null,
    application_id                    varchar(255),
    archive_instance_id               varchar(255),
    configuration_id                  int8,
    correlation_id                    uuid,
    instance_id                       int8,
    integration_id                    int8,
    source_application_id             int8,
    source_application_instance_id    varchar(255),
    source_application_integration_id varchar(255),
    name                              varchar(255),
    timestamp                         timestamp,
    type                              varchar(255),
    primary key (id)
);
create table file_id
(
    event_id int8 not null,
    file_id  uuid
);
create index sourceApplicationAggregateIdAndNameIndex on event (source_application_id,
                                                                source_application_integration_id,
                                                                source_application_instance_id, name);
create index sourceApplicationAggregateIdAndNameAndTimestampIndex on event (source_application_id,
                                                                            source_application_integration_id,
                                                                            source_application_instance_id, name,
                                                                            timestamp);
create index timestampIndex on event (timestamp);
create index nameIndex on event (name);
alter table error
    add constraint FK7xmwq897wsr8gttga5gtw7r04 foreign key (event_id) references event;
alter table error_args
    add constraint FKecq6ndoidl3jtr5s2cik283h7 foreign key (error_id) references error;
alter table file_id
    add constraint FK5k2a6mwnqrpqctw3qmgcbtl6n foreign key (event_id) references event;
