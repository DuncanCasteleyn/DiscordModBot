create table guild_trap_channels
(
    guild_id   bigint not null,
    channel_id bigint not null,
    primary key (guild_id)
);

create table trap_channel_unbans
(
    guild_id bigint    not null,
    user_id  bigint    not null,
    unban_at timestamp not null,
    primary key (guild_id, user_id)
);
