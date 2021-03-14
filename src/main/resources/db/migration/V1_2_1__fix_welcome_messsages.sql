ALTER TABLE `guild_member_gate_welcome_messages`
    ADD UNIQUE (`guild_member_gate_guild_id`, `image_url`, `message`);
alter table guild_member_gate_questions
    DROP CONSTRAINT FKhadujm5yqqs7x4f61eibshr5r;
ALTER TABLE `guild_member_gate_welcome_messages`
    DROP PRIMARY KEY;
ALTER TABLE `guild_member_gate_welcome_messages`
    MODIFY COLUMN `message` VARCHAR(2048) NOT NULL;
