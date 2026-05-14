create table sticky_role_configs
(
    guild_id bigint not null,
    primary key (guild_id)
);

create table sticky_role_config_roles
(
    sticky_role_config_guild_id bigint not null,
    role_id                     bigint not null,
    primary key (sticky_role_config_guild_id, role_id)
);

create table sticky_role_snapshots
(
    guild_id bigint not null,
    user_id  bigint not null,
    primary key (guild_id, user_id)
);

create table sticky_role_snapshot_roles
(
    sticky_role_snapshot_guild_id bigint not null,
    sticky_role_snapshot_user_id  bigint not null,
    role_id                       bigint not null,
    primary key (sticky_role_snapshot_guild_id, sticky_role_snapshot_user_id, role_id)
);

alter table sticky_role_config_roles
    add constraint fk_sticky_role_config_roles
        foreign key (sticky_role_config_guild_id) references sticky_role_configs (guild_id);

alter table sticky_role_snapshot_roles
    add constraint fk_sticky_role_snapshot_roles
        foreign key (sticky_role_snapshot_guild_id, sticky_role_snapshot_user_id)
            references sticky_role_snapshots (guild_id, user_id);
