--liquibase formatted sql

-- Reference data applied in every environment. The application's authorization rules
-- refer to these role names, and the Administrators group is how an administrator
-- receives the management roles. The group has no members here; see
-- 004-development-seed.sql for the development and test users, and the "Bootstrapping
-- the first administrator" section of
-- docs/system-design/08-crosscutting-concepts/02-security-and-authentication/authorization.md.
--
-- The commons-accounts schema (001) ships no data, so each application seeds its own.
-- The commons-accounts administration API requires USER_MANAGE, GROUP_MANAGE, and
-- ROLE_MANAGE by name; APPLICATION_USER is this application's own role.

--changeset app:002-authorisation-seed
INSERT INTO app_role (id, name, created_at, updated_at) VALUES ('00000000-0000-0000-0000-000000000001', 'USER_MANAGE', TIMESTAMP '2026-01-01 00:00:00', TIMESTAMP '2026-01-01 00:00:00');
INSERT INTO app_role (id, name, created_at, updated_at) VALUES ('00000000-0000-0000-0000-000000000002', 'GROUP_MANAGE', TIMESTAMP '2026-01-01 00:00:00', TIMESTAMP '2026-01-01 00:00:00');
INSERT INTO app_role (id, name, created_at, updated_at) VALUES ('00000000-0000-0000-0000-000000000003', 'ROLE_MANAGE', TIMESTAMP '2026-01-01 00:00:00', TIMESTAMP '2026-01-01 00:00:00');
INSERT INTO app_role (id, name, created_at, updated_at) VALUES ('00000000-0000-0000-0000-000000000004', 'APPLICATION_USER', TIMESTAMP '2026-01-01 00:00:00', TIMESTAMP '2026-01-01 00:00:00');
INSERT INTO app_group (id, name, created_at, updated_at) VALUES ('00000000-0000-0000-0000-000000000011', 'Administrators', TIMESTAMP '2026-01-01 00:00:00', TIMESTAMP '2026-01-01 00:00:00');
INSERT INTO app_group_role (group_id, role_id) VALUES ('00000000-0000-0000-0000-000000000011', '00000000-0000-0000-0000-000000000001');
INSERT INTO app_group_role (group_id, role_id) VALUES ('00000000-0000-0000-0000-000000000011', '00000000-0000-0000-0000-000000000002');
INSERT INTO app_group_role (group_id, role_id) VALUES ('00000000-0000-0000-0000-000000000011', '00000000-0000-0000-0000-000000000003');
