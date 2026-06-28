DROP TABLE IF EXISTS `welcome_messages`;
CREATE TABLE `welcome_messages`
(
    `id`        bigint(20)    NOT NULL,
    `guild_id`  bigint(20)    NOT NULL,
    `image_url` varchar(255)  NOT NULL,
    `message`   varchar(2048) NOT NULL,
    PRIMARY KEY (`id`, `guild_id`)
);
CREATE SEQUENCE `welcome_message_seq`
