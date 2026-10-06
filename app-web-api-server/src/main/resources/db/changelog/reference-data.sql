--liquibase formatted sql

-- Reference data applied in every environment: the roles and what they grant. The
-- permissions themselves, and the pairs of them that no user may hold together, come from the
-- commons-accounts schema, because its controllers check them by name. This application
-- seeds the roles, and each is a set of those permissions (see docs/adr/0038):
--
--   Administrators     maintains users, roles and settings, and reads the audit trail. It
--                      holds the privileged permissions, which is why its holders are in the
--                      privileged account review.
--   Account Reviewers  reviews accounts. It can remove roles and accounts but not add them,
--                      and the schema keeps it from being combined with a privileged
--                      permission.
--
-- The roles have no members here; see development-seed.sql for the development and test
-- users, and the "Bootstrapping the first administrator" section of
-- docs/system-design/08-crosscutting-concepts/02-security-and-authentication/authorization.md.

--changeset app:reference-data
INSERT INTO app_role (id, public_id, name, created_at, updated_at, created_by, updated_by) VALUES (17, '00000000-0000-0000-0000-000000000011', 'Administrators', '2026-01-01 00:00:00+00:00', '2026-01-01 00:00:00+00:00', 'system', 'system');
INSERT INTO app_role (id, public_id, name, created_at, updated_at, created_by, updated_by) VALUES (19, '00000000-0000-0000-0000-000000000013', 'Account Reviewers', '2026-01-01 00:00:00+00:00', '2026-01-01 00:00:00+00:00', 'system', 'system');
-- Administrators: application:access, every user, role, permission and settings permission, and audit:read.
INSERT INTO app_role_permission (role_id, permission_id) VALUES (17, 1);
INSERT INTO app_role_permission (role_id, permission_id) VALUES (17, 2);
INSERT INTO app_role_permission (role_id, permission_id) VALUES (17, 3);
INSERT INTO app_role_permission (role_id, permission_id) VALUES (17, 4);
INSERT INTO app_role_permission (role_id, permission_id) VALUES (17, 5);
INSERT INTO app_role_permission (role_id, permission_id) VALUES (17, 6);
INSERT INTO app_role_permission (role_id, permission_id) VALUES (17, 7);
INSERT INTO app_role_permission (role_id, permission_id) VALUES (17, 8);
INSERT INTO app_role_permission (role_id, permission_id) VALUES (17, 9);
INSERT INTO app_role_permission (role_id, permission_id) VALUES (17, 10);
INSERT INTO app_role_permission (role_id, permission_id) VALUES (17, 11);
INSERT INTO app_role_permission (role_id, permission_id) VALUES (17, 12);
INSERT INTO app_role_permission (role_id, permission_id) VALUES (17, 13);
INSERT INTO app_role_permission (role_id, permission_id) VALUES (17, 14);
INSERT INTO app_role_permission (role_id, permission_id) VALUES (17, 15);
INSERT INTO app_role_permission (role_id, permission_id) VALUES (17, 16);
INSERT INTO app_role_permission (role_id, permission_id) VALUES (17, 17);
INSERT INTO app_role_permission (role_id, permission_id) VALUES (17, 18);
INSERT INTO app_role_permission (role_id, permission_id) VALUES (17, 19);
INSERT INTO app_role_permission (role_id, permission_id) VALUES (17, 20);
INSERT INTO app_role_permission (role_id, permission_id) VALUES (17, 21);
-- Account Reviewers: application:access, user:read, user:remove-role, user:remove, role:read, audit:read and every review permission.
INSERT INTO app_role_permission (role_id, permission_id) VALUES (19, 1);
INSERT INTO app_role_permission (role_id, permission_id) VALUES (19, 2);
INSERT INTO app_role_permission (role_id, permission_id) VALUES (19, 6);
INSERT INTO app_role_permission (role_id, permission_id) VALUES (19, 9);
INSERT INTO app_role_permission (role_id, permission_id) VALUES (19, 12);
INSERT INTO app_role_permission (role_id, permission_id) VALUES (19, 21);
INSERT INTO app_role_permission (role_id, permission_id) VALUES (19, 22);
INSERT INTO app_role_permission (role_id, permission_id) VALUES (19, 23);
INSERT INTO app_role_permission (role_id, permission_id) VALUES (19, 24);
INSERT INTO app_role_permission (role_id, permission_id) VALUES (19, 25);
