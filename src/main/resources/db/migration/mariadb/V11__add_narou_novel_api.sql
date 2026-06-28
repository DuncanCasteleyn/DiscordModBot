create table narou_novel_snapshots
(
    ncode            varchar(255) not null,
    general_lastup   varchar(32)  not null,
    general_all_no   integer      not null,
    length_value     bigint       not null,
    reading_time     integer      not null,
    novel_updated_at varchar(32)  not null,
    api_updated_at   varchar(32)  not null,
    primary key (ncode)
);

create table narou_novel_alert_settings
(
    guild_id                    bigint not null,
    channel_id                  bigint,
    length_threshold            bigint not null,
    last_alerted_length         bigint,
    last_alerted_general_all_no integer,
    primary key (guild_id)
);
