UPDATE guild_member_gate_questions
SET question = LEFT(question, 100);

ALTER TABLE guild_member_gate_questions
    MODIFY COLUMN question VARCHAR(100);
