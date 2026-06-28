DROP TABLE IF EXISTS `channel_order_locked_guild`;
CREATE TABLE `channel_order_locked_guild`
(
    `guild_id` bigint(20) NOT NULL,
    `enabled`  bit(1) NOT NULL,
    `unlocked` bit(1) NOT NULL,
    PRIMARY KEY (`guild_id`)
);
