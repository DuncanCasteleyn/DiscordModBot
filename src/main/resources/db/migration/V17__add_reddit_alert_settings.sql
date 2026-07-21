create table reddit_alert_settings
(
    guild_id  bigint       not null,
    channel_id bigint,
    subreddit varchar(100) not null,
    primary key (guild_id)
);
