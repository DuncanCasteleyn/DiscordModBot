create table `report_settings`
(
    `guild_id`       bigint not null,
    `urgent_role_id` bigint null,
    primary key (`guild_id`)
);

create table `report_settings_blocked_users`
(
    `guild_id` bigint not null,
    `user_id`  bigint not null,
    primary key (`guild_id`, `user_id`)
);

alter table `report_settings_blocked_users`
    add constraint `fk_report_settings_blocked_users`
        foreign key (`guild_id`) references `report_settings` (`guild_id`);
