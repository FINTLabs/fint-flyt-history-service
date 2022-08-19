create table error
(
    id         bigserial not null,
    error_code varchar(255),
    event_id   int8,
    primary key (id)
);
create table error_args
(
    error_id int8         not null,
    value    varchar(255),
    map_key  varchar(255) not null,
    primary key (error_id, map_key)
);
create table event
(
    id                                bigserial not null,
    application_id                    varchar(255),
    archive_case_id                   varchar(255),
    configuration_id                  varchar(255),
    correlation_id                    varchar(255),
    instance_id                       varchar(255),
    org_id                            varchar(255),
    source_application_id             varchar(255),
    source_application_instance_id    varchar(255),
    source_application_integration_id varchar(255),
    name                              varchar(255),
    timestamp                         timestamp,
    type                              varchar(255),
    primary key (id)
);
alter table error
    add constraint FK7xmwq897wsr8gttga5gtw7r04 foreign key (event_id) references event;
alter table error_args
    add constraint FKecq6ndoidl3jtr5s2cik283h7 foreign key (error_id) references error;
