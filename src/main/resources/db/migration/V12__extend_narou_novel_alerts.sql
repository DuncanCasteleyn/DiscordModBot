alter table narou_novel_snapshots
    add column author_profile_length bigint not null default 0;

alter table narou_novel_alert_settings
    add column prediction_length_threshold bigint not null default 1000;

alter table narou_novel_alert_settings
    add column last_alerted_author_profile_length bigint null;
