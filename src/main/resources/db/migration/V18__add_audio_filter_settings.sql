create table audio_filter_settings
(
    guild_id        bigint not null,
    timeout_minutes bigint null,
    primary key (guild_id)
);
