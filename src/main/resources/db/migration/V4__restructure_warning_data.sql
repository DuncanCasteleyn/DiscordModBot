ALTER TABLE user_has_warn_points
    DROP CONSTRAINT FKeo5hje9e79jvey9bd8odmv8vo;
ALTER TABLE user_has_warn_points
    DROP CONSTRAINT FKc40ohaed91dh2r2rak16229ps;
ALTER TABLE user_has_warn_points
    DROP CONSTRAINT UK_p2t268egb17qo0ml3axqfb0d6;

DROP TABLE `guild_warn_points`;
DROP TABLE `user_has_warn_points`;
DROP TABLE `user_warn_points`;

CREATE TABLE guild_warn_points
(
    user_id       BIGINT     NOT NULL,
    guild_id      BIGINT     NOT NULL,
    id            BINARY(16) NOT NULL,
    points        INT        NOT NULL,
    creator_id    BIGINT     NOT NULL,
    reason        TEXT       NOT NULL,
    creation_date datetime   NOT NULL,
    expire_date   datetime   NOT NULL,
    CONSTRAINT pk_guild_warn_points PRIMARY KEY (user_id, guild_id, id)
);
