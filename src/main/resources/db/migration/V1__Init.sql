DROP TABLE IF EXISTS `activity_report_settings`;
CREATE TABLE `activity_report_settings`
(
    `guild_id`       bigint(20) NOT NULL,
    `report_channel` bigint(20) DEFAULT NULL,
    PRIMARY KEY (`guild_id`)
);

DROP TABLE IF EXISTS `activity_report_settings_tracked_role_or_member`;
CREATE TABLE `activity_report_settings_tracked_role_or_member`
(
    `activity_report_settings_guild_id` bigint(20) NOT NULL,
    `tracked`                           bigint(20) DEFAULT NULL
);

DROP TABLE IF EXISTS `black_listed_word`;
CREATE TABLE `black_listed_word`
(
    `word`          varchar(255) NOT NULL,
    `guild_id`      bigint(20) NOT NULL,
    `filter_method` int(11) NOT NULL,
    PRIMARY KEY (`word`, `guild_id`)
);

DROP TABLE IF EXISTS `blocked_users`;
CREATE TABLE `blocked_users`
(
    `user_id` bigint(20) NOT NULL,
    PRIMARY KEY (`user_id`)
);

DROP TABLE IF EXISTS `command_channels_list`;
CREATE TABLE `command_channels_list`
(
    `guild_command_channels_guild_id` bigint(20) NOT NULL,
    `whitelisted_channels`            bigint(20) DEFAULT NULL
);

DROP TABLE IF EXISTS `guild_command_channels`;
CREATE TABLE `guild_command_channels`
(
    `guild_id` bigint(20) NOT NULL,
    PRIMARY KEY (`guild_id`)
);

DROP TABLE IF EXISTS `guild_member_gate`;
CREATE TABLE `guild_member_gate`
(
    `guild_id`             bigint(20) NOT NULL,
    `gate_text_channel`    bigint(20) DEFAULT NULL,
    `member_role`          bigint(20) DEFAULT NULL,
    `rules_text_channel`   bigint(20) DEFAULT NULL,
    `welcome_text_channel` bigint(20) DEFAULT NULL,
    `remove_time_hours`    bigint(20) DEFAULT NULL,
    PRIMARY KEY (`guild_id`)
);

DROP TABLE IF EXISTS `guild_member_gate_questions`;
CREATE TABLE `guild_member_gate_questions`
(
    `guild_member_gate_guild_id` bigint(20) NOT NULL,
    `question`                   varchar(255) DEFAULT NULL
);

DROP TABLE IF EXISTS `guild_member_gate_welcome_messages`;
CREATE TABLE `guild_member_gate_welcome_messages`
(
    `guild_member_gate_guild_id` bigint(20) NOT NULL,
    `image_url`                  varchar(255) NOT NULL,
    `message`                    varchar(255) NOT NULL,
    PRIMARY KEY (`guild_member_gate_guild_id`, `image_url`, `message`)
);

DROP TABLE IF EXISTS `guild_warn_point_settings`;
CREATE TABLE `guild_warn_point_settings`
(
    `guild_id`                      bigint(20) NOT NULL,
    `announce_channel_id`           bigint(20) NOT NULL,
    `announce_points_summary_limit` int(11) NOT NULL,
    `max_points_per_reason`         int(11) NOT NULL,
    `override_warn_command`         bit(1) NOT NULL,
    PRIMARY KEY (`guild_id`)
);

DROP TABLE IF EXISTS `guild_warn_points`;
CREATE TABLE `guild_warn_points`
(
    `user_id`  bigint(20) NOT NULL,
    `guild_id` bigint(20) NOT NULL,
    PRIMARY KEY (`user_id`, `guild_id`)
);

DROP TABLE IF EXISTS `i_am_roles_categories`;
CREATE TABLE `i_am_roles_categories`
(
    `category_id`   bigint(20) NOT NULL,
    `guild_id`      bigint(20) NOT NULL,
    `allowed_roles` int(11) NOT NULL,
    `category_name` varchar(255) NOT NULL,
    PRIMARY KEY (`category_id`, `guild_id`),
    UNIQUE KEY (`category_name`)
);

DROP TABLE IF EXISTS `i_am_roles_category_roles`;
CREATE TABLE `i_am_roles_category_roles`
(
    `iam_roles_category_category_id` bigint(20) NOT NULL,
    `iam_roles_category_guild_id`    bigint(20) NOT NULL,
    `roles`                          bigint(20) NOT NULL,
    PRIMARY KEY (`iam_roles_category_category_id`, `iam_roles_category_guild_id`, `roles`)
);

DROP TABLE IF EXISTS `i_am_roles_seq`;
CREATE TABLE `i_am_roles_seq`
(
    `next_val` bigint(20) DEFAULT NULL
);

DROP TABLE IF EXISTS `logging_ignored_channels`;
CREATE TABLE `logging_ignored_channels`
(
    `logging_settings_guild_id` bigint(20) NOT NULL,
    `ignored_channels`          bigint(20) DEFAULT NULL
);

DROP TABLE IF EXISTS `logging_settings`;
CREATE TABLE `logging_settings`
(
    `guild_id`              bigint(20) NOT NULL,
    `log_member_ban`        bit(1) NOT NULL,
    `log_member_join`       bit(1) NOT NULL,
    `log_member_leave`      bit(1) NOT NULL,
    `log_member_remove_ban` bit(1) NOT NULL,
    `log_message_delete`    bit(1) NOT NULL,
    `log_message_update`    bit(1) NOT NULL,
    `mod_log_channel`       bigint(20) NOT NULL,
    `user_log_channel`      bigint(20) DEFAULT NULL,
    PRIMARY KEY (`guild_id`)
);

DROP TABLE IF EXISTS `mute_roles`;
CREATE TABLE `mute_roles`
(
    `guild_id` bigint(20) NOT NULL,
    `role_id`  bigint(20) NOT NULL,
    PRIMARY KEY (`guild_id`)
);

DROP TABLE IF EXISTS `muted_users`;
CREATE TABLE `muted_users`
(
    `mute_role_guild_id` bigint(20) NOT NULL,
    `muted_users`        bigint(20) DEFAULT NULL
);

DROP TABLE IF EXISTS `report_channels`;
CREATE TABLE `report_channels`
(
    `guild_id`        bigint(20) NOT NULL,
    `text_channel_id` bigint(20) NOT NULL,
    PRIMARY KEY (`guild_id`)
);

DROP TABLE IF EXISTS `user_has_warn_points`;
CREATE TABLE `user_has_warn_points`
(
    `guild_warn_points_user_id`  bigint(20) NOT NULL,
    `guild_warn_points_guild_id` bigint(20) NOT NULL,
    `points_id`                  binary(16) NOT NULL
);

DROP TABLE IF EXISTS `user_warn_points`;
CREATE TABLE `user_warn_points`
(
    `id`            binary(16) NOT NULL,
    `creation_date` datetime(6) NOT NULL,
    `creator_id`    bigint(20) NOT NULL,
    `expire_date`   datetime(6) NOT NULL,
    `points`        int(11) NOT NULL,
    `reason`        text NOT NULL,
    PRIMARY KEY (`id`)
);

DROP TABLE IF EXISTS `voting_emotes`;
CREATE TABLE `voting_emotes`
(
    `guild_id`       bigint(20) NOT NULL,
    `vote_no_emote`  bigint(20) NOT NULL,
    `vote_yes_emote` bigint(20) NOT NULL,
    PRIMARY KEY (`guild_id`)
);

alter table i_am_roles_categories
    add constraint UK_s4cjvx1px5y5nrlfmg55x0jwe unique (category_name);
alter table user_has_warn_points
    add constraint UK_p2t268egb17qo0ml3axqfb0d6 unique (points_id);
alter table activity_report_settings_tracked_role_or_member
    add constraint FKmij4g6f4tpi8snbfkj3wrmse0 foreign key (activity_report_settings_guild_id) references activity_report_settings (`guild_id`);
alter table command_channels_list
    add constraint FK68als3dp7u70crgf5dnkbej8e foreign key (guild_command_channels_guild_id) references guild_command_channels (`guild_id`);
alter table guild_member_gate_questions
    add constraint FKhadujm5yqqs7x4f61eibshr5r foreign key (guild_member_gate_guild_id) references guild_member_gate (`guild_id`);
alter table guild_member_gate_welcome_messages
    add constraint FKdbw9lresl2b857lk5re2ihrxg foreign key (guild_member_gate_guild_id) references guild_member_gate (`guild_id`);
alter table i_am_roles_category_roles
    add constraint FKhfe9d689h3xe1glcl9gjllfa5 foreign key (iam_roles_category_category_id, iam_roles_category_guild_id) references i_am_roles_categories (`category_id`, `guild_id`);
alter table logging_ignored_channels
    add constraint FKlj9sf42a0857mr2li8q2w10a6 foreign key (logging_settings_guild_id) references logging_settings (`guild_id`);
alter table muted_users
    add constraint FKh72j5nu1ugy0mjbvdratd3i2t foreign key (mute_role_guild_id) references mute_roles (`guild_id`);
alter table user_has_warn_points
    add constraint FKeo5hje9e79jvey9bd8odmv8vo foreign key (points_id) references user_warn_points (`id`);
alter table user_has_warn_points
    add constraint FKc40ohaed91dh2r2rak16229ps foreign key (guild_warn_points_user_id, guild_warn_points_guild_id) references guild_warn_points (`user_id`, `guild_id`);
