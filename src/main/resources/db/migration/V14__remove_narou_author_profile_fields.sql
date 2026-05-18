alter table narou_novel_snapshots
    drop column author_profile_length;

alter table narou_novel_alert_settings
    drop column prediction_length_threshold;

alter table narou_novel_alert_settings
    drop column last_alerted_author_profile_length;
