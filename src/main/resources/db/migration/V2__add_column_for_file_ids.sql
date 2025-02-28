create table file_id
(
    event_id bigint not null,
    file_id  UUID,
    constraint fk_event foreign key (event_id) references event (id) on delete cascade
);