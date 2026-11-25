create table channel_lockdowns
(
    guild_id       bigint      not null,
    channel_id     bigint      not null,
    target_type    varchar(10) not null,
    target_id      bigint      not null,
    previous_allow bigint      not null,
    previous_deny  bigint      not null,
    existed        boolean     not null,
    primary key (guild_id, channel_id, target_type, target_id)
);
