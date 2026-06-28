drop table if exists scheduled_unmutes;
create table scheduled_unmutes
(
    user_id          bigint    not null,
    guild_id         bigint    not null,
    unmute_date_time timestamp not null,
    primary key (user_id, guild_id)
);
