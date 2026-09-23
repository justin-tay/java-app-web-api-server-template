--liquibase formatted sql

-- Development and test fixtures. The "@" makes the dev context required: Liquibase
-- applies this changeset only when the dev context is explicitly requested
-- (spring.liquibase.contexts in application-local.yaml and application-test.yaml), and
-- skips it when no context is given, so a production migration never creates these
-- enabled users. See docs/adr/0018.

--changeset app:004-development-seed context:@dev
INSERT INTO app_group (id, name, created_at, updated_at) VALUES ('00000000-0000-0000-0000-000000000012', 'Test Users', TIMESTAMP '2026-01-01 00:00:00', TIMESTAMP '2026-01-01 00:00:00');
INSERT INTO app_group_role (group_id, role_id) VALUES ('00000000-0000-0000-0000-000000000012', '00000000-0000-0000-0000-000000000004');
INSERT INTO app_user (id, username, display_name, enabled, created_at, updated_at) VALUES ('00000000-0000-0000-0000-000000000021', 'admin', 'Administrator', TRUE, TIMESTAMP '2026-01-01 00:00:00', TIMESTAMP '2026-01-01 00:00:00');
INSERT INTO app_user (id, username, display_name, enabled, created_at, updated_at) VALUES ('00000000-0000-0000-0000-000000000022', 'test-user', 'Test User', TRUE, TIMESTAMP '2026-01-01 00:00:00', TIMESTAMP '2026-01-01 00:00:00');
INSERT INTO app_user (id, username, display_name, enabled, created_at, updated_at) VALUES ('00000000-0000-0000-0000-000000000023', 'multi-group-user', 'Multi Group User', TRUE, TIMESTAMP '2026-01-01 00:00:00', TIMESTAMP '2026-01-01 00:00:00');
INSERT INTO app_user_group (user_id, group_id) VALUES ('00000000-0000-0000-0000-000000000021', '00000000-0000-0000-0000-000000000011');
INSERT INTO app_user_group (user_id, group_id) VALUES ('00000000-0000-0000-0000-000000000022', '00000000-0000-0000-0000-000000000012');
INSERT INTO app_user_group (user_id, group_id) VALUES ('00000000-0000-0000-0000-000000000023', '00000000-0000-0000-0000-000000000011');
INSERT INTO app_user_group (user_id, group_id) VALUES ('00000000-0000-0000-0000-000000000023', '00000000-0000-0000-0000-000000000012');
