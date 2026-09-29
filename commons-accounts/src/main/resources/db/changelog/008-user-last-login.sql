--liquibase formatted sql

--changeset app:008-user-last-login
-- When the user last signed in, by OIDC or passkey. Null until the first sign-in.
ALTER TABLE app_user ADD COLUMN last_login_at TIMESTAMP;
