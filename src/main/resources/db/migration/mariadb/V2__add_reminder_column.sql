DROP TABLE IF EXISTS guild_member_gate_welcome_messages;
ALTER TABLE guild_member_gate
    ADD COLUMN `reminder_time_hours` bigint(20) DEFAULT NULL;
