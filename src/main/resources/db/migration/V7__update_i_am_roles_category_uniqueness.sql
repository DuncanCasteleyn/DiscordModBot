ALTER TABLE i_am_roles_category_roles
    DROP CONSTRAINT FKhfe9d689h3xe1glcl9gjllfa5;

CREATE TABLE i_am_roles_categories_new
(
    category_id bigint(20) NOT NULL,
    guild_id bigint(20) NOT NULL,
    allowed_roles int(11) NOT NULL,
    category_name varchar(255) NOT NULL,
    PRIMARY KEY (category_id, guild_id),
    CONSTRAINT uk_i_am_roles_categories_guild_id_category_name UNIQUE (guild_id, category_name)
);

INSERT INTO i_am_roles_categories_new (category_id, guild_id, allowed_roles, category_name)
SELECT category_id, guild_id, allowed_roles, category_name
FROM i_am_roles_categories;

DROP TABLE i_am_roles_categories;

ALTER TABLE i_am_roles_categories_new
    RENAME TO i_am_roles_categories;

ALTER TABLE i_am_roles_category_roles
    ADD CONSTRAINT FKhfe9d689h3xe1glcl9gjllfa5
        FOREIGN KEY (iam_roles_category_category_id, iam_roles_category_guild_id)
            REFERENCES i_am_roles_categories (category_id, guild_id);
