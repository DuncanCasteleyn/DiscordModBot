create table member_gate_attempts
(
    user_id       bigint       not null,
    attempt_count int          not null,
    last_modified timestamp(6) not null,
    primary key (user_id)
);

create index idx_member_gate_attempts_last_modified
    on member_gate_attempts (last_modified);
