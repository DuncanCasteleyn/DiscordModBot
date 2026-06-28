create sequence i_am_roles_seq;
create sequence welcome_message_seq;

create table activity_report_settings
(
    guild_id       bigint not null,
    report_channel bigint,
    constraint pk_activity_report_settings primary key (guild_id)
);

create table activity_report_settings_tracked_role_or_member
(
    activity_report_settings_guild_id bigint not null,
    tracked                           bigint,
    constraint fk_activity_report_settings_tracked_role_or_member
        foreign key (activity_report_settings_guild_id) references activity_report_settings (guild_id)
);

create table channel_order_locked_guild
(
    guild_id bigint  not null,
    enabled  boolean not null,
    unlocked boolean not null,
    constraint pk_channel_order_locked_guild primary key (guild_id)
);

create table guild_member_gate
(
    guild_id             bigint not null,
    gate_text_channel    bigint,
    member_role          bigint,
    rules_text_channel   bigint,
    welcome_text_channel bigint,
    remove_time_hours    bigint,
    reminder_time_hours  bigint,
    constraint pk_guild_member_gate primary key (guild_id)
);

create table guild_member_gate_questions
(
    guild_member_gate_guild_id bigint not null,
    question                   varchar(100),
    constraint fk_guild_member_gate_questions
        foreign key (guild_member_gate_guild_id) references guild_member_gate (guild_id)
);

create table guild_trap_channels
(
    guild_id   bigint not null,
    channel_id bigint not null,
    constraint pk_guild_trap_channels primary key (guild_id)
);

create table guild_warn_point_settings
(
    guild_id                      bigint  not null,
    announce_channel_id           bigint  not null,
    announce_points_summary_limit integer not null,
    max_points_per_reason         integer not null,
    override_warn_command         boolean not null,
    constraint pk_guild_warn_point_settings primary key (guild_id)
);

create table guild_warn_points
(
    user_id       bigint                   not null,
    guild_id      bigint                   not null,
    id            bytea                    not null,
    points        integer                  not null,
    creator_id    bigint                   not null,
    reason        text                     not null,
    creation_date timestamp with time zone not null,
    expire_date   timestamp with time zone not null,
    constraint pk_guild_warn_points primary key (user_id, guild_id, id),
    constraint ck_guild_warn_points_id_length check (octet_length(id) = 16)
);

create table i_am_roles_categories
(
    category_id   bigint       not null,
    guild_id      bigint       not null,
    allowed_roles integer      not null,
    category_name varchar(255) not null,
    constraint pk_i_am_roles_categories primary key (category_id, guild_id),
    constraint uk_i_am_roles_categories_guild_id_category_name unique (guild_id, category_name)
);

create table i_am_roles_category_roles
(
    iam_roles_category_category_id bigint not null,
    iam_roles_category_guild_id    bigint not null,
    roles                          bigint not null,
    constraint pk_i_am_roles_category_roles
        primary key (iam_roles_category_category_id, iam_roles_category_guild_id, roles),
    constraint fk_i_am_roles_category_roles
        foreign key (iam_roles_category_category_id, iam_roles_category_guild_id)
            references i_am_roles_categories (category_id, guild_id)
);

create table logging_settings
(
    guild_id              bigint  not null,
    log_member_ban        boolean not null,
    log_member_join       boolean not null,
    log_member_leave      boolean not null,
    log_member_remove_ban boolean not null,
    log_message_delete    boolean not null,
    log_message_update    boolean not null,
    mod_log_channel       bigint  not null,
    user_log_channel      bigint,
    constraint pk_logging_settings primary key (guild_id)
);

create table logging_ignored_channels
(
    logging_settings_guild_id bigint not null,
    ignored_channels          bigint,
    constraint fk_logging_ignored_channels
        foreign key (logging_settings_guild_id) references logging_settings (guild_id)
);

create table mute_roles
(
    guild_id bigint not null,
    role_id  bigint not null,
    constraint pk_mute_roles primary key (guild_id)
);

create table muted_users
(
    mute_role_guild_id bigint not null,
    muted_users        bigint,
    constraint fk_muted_users
        foreign key (mute_role_guild_id) references mute_roles (guild_id)
);

create table narou_novel_snapshots
(
    ncode            varchar(255) not null,
    general_lastup   varchar(32)  not null,
    general_all_no   integer      not null,
    length_value     bigint       not null,
    reading_time     integer      not null,
    novel_updated_at varchar(32)  not null,
    api_updated_at   varchar(32)  not null,
    constraint pk_narou_novel_snapshots primary key (ncode)
);

create table narou_novel_alert_settings
(
    guild_id                       bigint not null,
    channel_id                     bigint,
    ping_role_id                   bigint,
    length_threshold               bigint not null,
    last_alerted_length            bigint,
    last_alerted_general_all_no    integer,
    last_alerted_novel_updated_at  varchar(32),
    constraint pk_narou_novel_alert_settings primary key (guild_id)
);

create table report_channels
(
    guild_id        bigint not null,
    text_channel_id bigint not null,
    constraint pk_report_channels primary key (guild_id)
);

create table report_settings
(
    guild_id       bigint  not null,
    urgent_role_id bigint,
    enabled        boolean not null default false,
    constraint pk_report_settings primary key (guild_id)
);

create table report_settings_blocked_users
(
    guild_id bigint not null,
    user_id  bigint not null,
    constraint pk_report_settings_blocked_users primary key (guild_id, user_id),
    constraint fk_report_settings_blocked_users
        foreign key (guild_id) references report_settings (guild_id)
);

create table scheduled_unmutes
(
    guild_id         bigint                   not null,
    user_id          bigint                   not null,
    unmute_date_time timestamp with time zone not null,
    constraint pk_scheduled_unmutes primary key (guild_id, user_id)
);

create table sticky_role_configs
(
    guild_id bigint not null,
    constraint pk_sticky_role_configs primary key (guild_id)
);

create table sticky_role_config_roles
(
    sticky_role_config_guild_id bigint not null,
    role_id                     bigint not null,
    constraint pk_sticky_role_config_roles primary key (sticky_role_config_guild_id, role_id),
    constraint fk_sticky_role_config_roles
        foreign key (sticky_role_config_guild_id) references sticky_role_configs (guild_id)
);

create table sticky_role_snapshots
(
    guild_id bigint not null,
    user_id  bigint not null,
    constraint pk_sticky_role_snapshots primary key (guild_id, user_id)
);

create table sticky_role_snapshot_roles
(
    sticky_role_snapshot_guild_id bigint not null,
    sticky_role_snapshot_user_id  bigint not null,
    role_id                       bigint not null,
    constraint pk_sticky_role_snapshot_roles
        primary key (sticky_role_snapshot_guild_id, sticky_role_snapshot_user_id, role_id),
    constraint fk_sticky_role_snapshot_roles
        foreign key (sticky_role_snapshot_guild_id, sticky_role_snapshot_user_id)
            references sticky_role_snapshots (guild_id, user_id)
);

create table trap_channel_unbans
(
    guild_id  bigint                   not null,
    user_id   bigint                   not null,
    unban_at  timestamp with time zone not null,
    constraint pk_trap_channel_unbans primary key (guild_id, user_id)
);

create table voting_emotes
(
    guild_id       bigint not null,
    vote_no_emote  bigint not null,
    vote_yes_emote bigint not null,
    constraint pk_voting_emotes primary key (guild_id)
);

create table welcome_messages
(
    id        bigint        not null,
    guild_id  bigint        not null,
    image_url varchar(255)  not null,
    message   varchar(2048) not null,
    constraint pk_welcome_messages primary key (id, guild_id)
);
