ALTER TABLE `guild_member_gate_welcome_messages`
    DROP PRIMARY KEY;
ALTER TABLE `guild_member_gate_welcome_messages`
    MODIFY COLUMN `message` VARBINARY(1000) NOT NULL;
ALTER TABLE `guild_member_gate_welcome_messages`
    ADD PRIMARY KEY (`guild_member_gate_guild_id`, `image_url`, `message`)
