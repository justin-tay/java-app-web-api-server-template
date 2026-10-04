--liquibase formatted sql

-- Reference data applied in every environment. The application's authorization rules refer
-- to these role names, and the Administrators group is how an administrator receives the
-- management roles. The group has no members here; see development-seed.sql for the
-- development and test users, and the "Bootstrapping the first administrator" section of
-- docs/system-design/08-crosscutting-concepts/02-security-and-authentication/authorization.md.
--
-- The commons-accounts schema ships no application data, so each application seeds its own.
-- The commons-accounts administration API requires USER_MANAGE, GROUP_MANAGE, and
-- ROLE_MANAGE by name; APPLICATION_USER, ACCOUNT_REVIEWER and SETTINGS_MANAGE are this
-- application's own roles. Administrators do not hold ACCOUNT_REVIEWER, so the person who
-- maintains accounts is not the one who reviews them. See docs/specifications/account-review.

--changeset app:reference-data
INSERT INTO app_role (id, public_id, name, display_name, created_at, updated_at, created_by, updated_by) VALUES (1, '00000000-0000-0000-0000-000000000001', 'USER_MANAGE', 'Manage users', '2026-01-01 00:00:00+00:00', '2026-01-01 00:00:00+00:00', 'system', 'system');
INSERT INTO app_role (id, public_id, name, display_name, created_at, updated_at, created_by, updated_by) VALUES (2, '00000000-0000-0000-0000-000000000002', 'GROUP_MANAGE', 'Manage groups', '2026-01-01 00:00:00+00:00', '2026-01-01 00:00:00+00:00', 'system', 'system');
INSERT INTO app_role (id, public_id, name, display_name, created_at, updated_at, created_by, updated_by) VALUES (3, '00000000-0000-0000-0000-000000000003', 'ROLE_MANAGE', 'Manage roles', '2026-01-01 00:00:00+00:00', '2026-01-01 00:00:00+00:00', 'system', 'system');
INSERT INTO app_role (id, public_id, name, display_name, created_at, updated_at, created_by, updated_by) VALUES (4, '00000000-0000-0000-0000-000000000004', 'APPLICATION_USER', 'Application user', '2026-01-01 00:00:00+00:00', '2026-01-01 00:00:00+00:00', 'system', 'system');
INSERT INTO app_group (id, public_id, name, created_at, updated_at, created_by, updated_by) VALUES (17, '00000000-0000-0000-0000-000000000011', 'Administrators', '2026-01-01 00:00:00+00:00', '2026-01-01 00:00:00+00:00', 'system', 'system');
INSERT INTO app_role (id, public_id, name, display_name, created_at, updated_at, created_by, updated_by) VALUES (5, '00000000-0000-0000-0000-000000000005', 'ACCOUNT_REVIEWER', 'Account reviewer', '2026-01-01 00:00:00+00:00', '2026-01-01 00:00:00+00:00', 'system', 'system');
INSERT INTO app_role (id, public_id, name, display_name, created_at, updated_at, created_by, updated_by) VALUES (6, '00000000-0000-0000-0000-000000000006', 'SETTINGS_MANAGE', 'Manage settings', '2026-01-01 00:00:00+00:00', '2026-01-01 00:00:00+00:00', 'system', 'system');
INSERT INTO app_group (id, public_id, name, created_at, updated_at, created_by, updated_by) VALUES (19, '00000000-0000-0000-0000-000000000013', 'Account Reviewers', '2026-01-01 00:00:00+00:00', '2026-01-01 00:00:00+00:00', 'system', 'system');
INSERT INTO app_group_role (group_id, role_id) VALUES (17, 1);
INSERT INTO app_group_role (group_id, role_id) VALUES (17, 2);
INSERT INTO app_group_role (group_id, role_id) VALUES (17, 3);
INSERT INTO app_group_role (group_id, role_id) VALUES (19, 5);
INSERT INTO app_group_role (group_id, role_id) VALUES (17, 6);
