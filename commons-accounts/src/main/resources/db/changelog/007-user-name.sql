--liquibase formatted sql

--changeset app:007-user-name
-- The user's name is called "name" everywhere, matching groups and roles.
ALTER TABLE app_user RENAME COLUMN display_name TO name;
