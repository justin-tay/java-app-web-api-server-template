--liquibase formatted sql

-- Development and test fixtures. The "@" makes the dev context required: Liquibase applies
-- these changesets only when the dev context is explicitly requested
-- (spring.liquibase.contexts in application-local.yaml and application-test.yaml), and skips
-- them when no context is given, so a production migration never creates these users. See
-- docs/adr/0018.

--changeset app:development-seed context:@dev
INSERT INTO app_group (id, name, created_at, updated_at, created_by, updated_by) VALUES ('00000000-0000-0000-0000-000000000012', 'Users', '2026-01-01 00:00:00+00:00', '2026-01-01 00:00:00+00:00', 'system', 'system');
INSERT INTO app_group_role (group_id, role_id) VALUES ('00000000-0000-0000-0000-000000000012', '00000000-0000-0000-0000-000000000004');
INSERT INTO app_user (id, username, name, email, created_at, updated_at, created_by, updated_by) VALUES ('00000000-0000-0000-0000-000000000021', 'admin', 'Alan Tan', 'admin@example.test', '2026-01-01 00:00:00+00:00', '2026-01-01 00:00:00+00:00', 'system', 'system');
INSERT INTO app_user (id, username, name, email, created_at, updated_at, created_by, updated_by) VALUES ('00000000-0000-0000-0000-000000000022', 'user', 'Mary Goh', 'user@example.test', '2026-01-01 00:00:00+00:00', '2026-01-01 00:00:00+00:00', 'system', 'system');
INSERT INTO app_user (id, username, name, email, created_at, updated_at, created_by, updated_by) VALUES ('00000000-0000-0000-0000-000000000023', 'multi-group-user', 'Grace Lee', 'multi-group-user@example.test', '2026-01-01 00:00:00+00:00', '2026-01-01 00:00:00+00:00', 'system', 'system');
INSERT INTO app_user_group (user_id, group_id) VALUES ('00000000-0000-0000-0000-000000000021', '00000000-0000-0000-0000-000000000011');
INSERT INTO app_user_group (user_id, group_id) VALUES ('00000000-0000-0000-0000-000000000022', '00000000-0000-0000-0000-000000000012');
INSERT INTO app_user_group (user_id, group_id) VALUES ('00000000-0000-0000-0000-000000000023', '00000000-0000-0000-0000-000000000011');
INSERT INTO app_user_group (user_id, group_id) VALUES ('00000000-0000-0000-0000-000000000023', '00000000-0000-0000-0000-000000000012');

-- The two account reviewers can review each other. The automation is switched off so the
-- fixtures are never suspended, removed or put into a review unless a test or a developer
-- enables it.
INSERT INTO app_user (id, username, name, email, status, inactivity_clock_started_at, created_at, updated_at, created_by, updated_by) VALUES ('00000000-0000-0000-0000-000000000024', 'account-reviewer-1', 'Rachel Lim', 'account-reviewer-1@example.test', 'ACTIVE', ${instant.now}, '2026-01-01 00:00:00+00:00', '2026-01-01 00:00:00+00:00', 'system', 'system');
INSERT INTO app_user (id, username, name, email, status, inactivity_clock_started_at, created_at, updated_at, created_by, updated_by) VALUES ('00000000-0000-0000-0000-000000000025', 'account-reviewer-2', 'Ravi Nair', 'account-reviewer-2@example.test', 'ACTIVE', ${instant.now}, '2026-01-01 00:00:00+00:00', '2026-01-01 00:00:00+00:00', 'system', 'system');
INSERT INTO app_user_group (user_id, group_id) VALUES ('00000000-0000-0000-0000-000000000024', '00000000-0000-0000-0000-000000000013');
INSERT INTO app_user_group (user_id, group_id) VALUES ('00000000-0000-0000-0000-000000000025', '00000000-0000-0000-0000-000000000013');
UPDATE app_setting SET setting_value = 'false' WHERE name IN ('inactivity.enabled', 'review.enabled');

-- Sample accounts to practise the account review on, applied only when the demo context is
-- requested, which the local profile does and the tests do not (see docs/adr/0018). They
-- have no Keycloak account, so nobody can sign in as them; they are only there to be
-- reviewed. They cover the states a reviewer sees: signed in recently and long ago, never
-- signed in, long unused, suspended for different reasons, and accounts already removed.
-- The review is switched on so the scheduler creates a task covering every account soon
-- after the application starts. The date arithmetic is H2's, which the local profile uses.

--changeset app:demo-sample-data context:@demo dbms:h2
INSERT INTO app_user (id, username, name, email, status, last_login_at, inactivity_clock_started_at, created_at, updated_at, created_by, updated_by) VALUES ('00000000-0000-0000-0000-000000000031', 'olivia.chan', 'Olivia Chan', 'olivia.chan@example.test', 'ACTIVE', DATEADD('DAY', -3, CURRENT_TIMESTAMP), DATEADD('DAY', -203, CURRENT_TIMESTAMP), '2026-01-01 00:00:00+00:00', '2026-01-01 00:00:00+00:00', 'system', 'system');
INSERT INTO app_user (id, username, name, email, status, last_login_at, inactivity_clock_started_at, created_at, updated_at, created_by, updated_by) VALUES ('00000000-0000-0000-0000-000000000032', 'daniel.ong', 'Daniel Ong', 'daniel.ong@example.test', 'ACTIVE', DATEADD('DAY', -12, CURRENT_TIMESTAMP), DATEADD('DAY', -212, CURRENT_TIMESTAMP), '2026-01-01 00:00:00+00:00', '2026-01-01 00:00:00+00:00', 'system', 'system');
INSERT INTO app_user (id, username, name, email, status, last_login_at, inactivity_clock_started_at, created_at, updated_at, created_by, updated_by) VALUES ('00000000-0000-0000-0000-000000000033', 'priya.menon', 'Priya Menon', 'priya.menon@example.test', 'ACTIVE', DATEADD('DAY', -30, CURRENT_TIMESTAMP), DATEADD('DAY', -230, CURRENT_TIMESTAMP), '2026-01-01 00:00:00+00:00', '2026-01-01 00:00:00+00:00', 'system', 'system');
INSERT INTO app_user (id, username, name, email, status, last_login_at, inactivity_clock_started_at, created_at, updated_at, created_by, updated_by) VALUES ('00000000-0000-0000-0000-000000000034', 'wei.jie.koh', 'Wei Jie Koh', 'wei.jie.koh@example.test', 'ACTIVE', DATEADD('DAY', -58, CURRENT_TIMESTAMP), DATEADD('DAY', -258, CURRENT_TIMESTAMP), '2026-01-01 00:00:00+00:00', '2026-01-01 00:00:00+00:00', 'system', 'system');
INSERT INTO app_user (id, username, name, email, status, last_login_at, inactivity_clock_started_at, created_at, updated_at, created_by, updated_by) VALUES ('00000000-0000-0000-0000-000000000035', 'nur.aisyah', 'Nur Aisyah', 'nur.aisyah@example.test', 'ACTIVE', DATEADD('DAY', -76, CURRENT_TIMESTAMP), DATEADD('DAY', -276, CURRENT_TIMESTAMP), '2026-01-01 00:00:00+00:00', '2026-01-01 00:00:00+00:00', 'system', 'system');
INSERT INTO app_user (id, username, name, email, status, last_login_at, inactivity_clock_started_at, created_at, updated_at, created_by, updated_by) VALUES ('00000000-0000-0000-0000-000000000036', 'kumar.raj', 'Kumar Raj', 'kumar.raj@example.test', 'ACTIVE', NULL, DATEADD('DAY', -20, CURRENT_TIMESTAMP), '2026-01-01 00:00:00+00:00', '2026-01-01 00:00:00+00:00', 'system', 'system');
INSERT INTO app_user (id, username, name, email, status, last_login_at, inactivity_clock_started_at, created_at, updated_at, created_by, updated_by) VALUES ('00000000-0000-0000-0000-000000000037', 'mei.ling.tan', 'Mei Ling Tan', 'mei.ling.tan@example.test', 'ACTIVE', NULL, DATEADD('DAY', -20, CURRENT_TIMESTAMP), '2026-01-01 00:00:00+00:00', '2026-01-01 00:00:00+00:00', 'system', 'system');
INSERT INTO app_user (id, username, name, email, status, last_login_at, inactivity_clock_started_at, created_at, updated_at, created_by, updated_by) VALUES ('00000000-0000-0000-0000-000000000038', 'jason.lee', 'Jason Lee', 'jason.lee@example.test', 'ACTIVE', NULL, DATEADD('DAY', -150, CURRENT_TIMESTAMP), '2026-01-01 00:00:00+00:00', '2026-01-01 00:00:00+00:00', 'system', 'system');
INSERT INTO app_user (id, username, name, email, status, last_login_at, inactivity_clock_started_at, created_at, updated_at, created_by, updated_by) VALUES ('00000000-0000-0000-0000-000000000039', 'hui.min.ng', 'Hui Min Ng', 'hui.min.ng@example.test', 'ACTIVE', NULL, DATEADD('DAY', -95, CURRENT_TIMESTAMP), '2026-01-01 00:00:00+00:00', '2026-01-01 00:00:00+00:00', 'system', 'system');
INSERT INTO app_user (id, username, name, email, status, suspended_at, suspension_reason_code, suspension_note, inactivity_clock_started_at, created_at, updated_at, created_by, updated_by) VALUES ('00000000-0000-0000-0000-00000000003a', 'farid.hassan', 'Farid Hassan', 'farid.hassan@example.test', 'SUSPENDED', DATEADD('DAY', -100, CURRENT_TIMESTAMP), 'inactive_account', NULL, DATEADD('DAY', -190, CURRENT_TIMESTAMP), '2026-01-01 00:00:00+00:00', '2026-01-01 00:00:00+00:00', 'system', 'system');
INSERT INTO app_user (id, username, name, email, status, suspended_at, suspension_reason_code, suspension_note, inactivity_clock_started_at, created_at, updated_at, created_by, updated_by) VALUES ('00000000-0000-0000-0000-00000000003b', 'alicia.wong', 'Alicia Wong', 'alicia.wong@example.test', 'SUSPENDED', DATEADD('DAY', -40, CURRENT_TIMESTAMP), 'left_organisation', 'Resigned, access to be removed after handover', DATEADD('DAY', -130, CURRENT_TIMESTAMP), '2026-01-01 00:00:00+00:00', '2026-01-01 00:00:00+00:00', 'system', 'account-reviewer-1');
INSERT INTO app_user (id, username, name, email, status, suspended_at, suspension_reason_code, suspension_note, inactivity_clock_started_at, created_at, updated_at, created_by, updated_by) VALUES ('00000000-0000-0000-0000-00000000003c', 'benjamin.teo', 'Benjamin Teo', 'benjamin.teo@example.test', 'SUSPENDED', DATEADD('DAY', -15, CURRENT_TIMESTAMP), 'policy_violation', 'Shared credentials, see incident 2026-114', DATEADD('DAY', -105, CURRENT_TIMESTAMP), '2026-01-01 00:00:00+00:00', '2026-01-01 00:00:00+00:00', 'system', 'account-reviewer-1');
INSERT INTO app_user_group (user_id, group_id) VALUES ('00000000-0000-0000-0000-000000000031', '00000000-0000-0000-0000-000000000012');
INSERT INTO app_user_group (user_id, group_id) VALUES ('00000000-0000-0000-0000-000000000032', '00000000-0000-0000-0000-000000000012');
INSERT INTO app_user_group (user_id, group_id) VALUES ('00000000-0000-0000-0000-000000000033', '00000000-0000-0000-0000-000000000012');
INSERT INTO app_user_group (user_id, group_id) VALUES ('00000000-0000-0000-0000-000000000034', '00000000-0000-0000-0000-000000000012');
INSERT INTO app_user_group (user_id, group_id) VALUES ('00000000-0000-0000-0000-000000000035', '00000000-0000-0000-0000-000000000012');
INSERT INTO app_user_group (user_id, group_id) VALUES ('00000000-0000-0000-0000-000000000036', '00000000-0000-0000-0000-000000000012');
INSERT INTO app_user_group (user_id, group_id) VALUES ('00000000-0000-0000-0000-000000000037', '00000000-0000-0000-0000-000000000012');
INSERT INTO app_user_group (user_id, group_id) VALUES ('00000000-0000-0000-0000-000000000038', '00000000-0000-0000-0000-000000000012');
INSERT INTO app_user_group (user_id, group_id) VALUES ('00000000-0000-0000-0000-000000000039', '00000000-0000-0000-0000-000000000012');
INSERT INTO app_user_group (user_id, group_id) VALUES ('00000000-0000-0000-0000-00000000003a', '00000000-0000-0000-0000-000000000012');
INSERT INTO app_user_group (user_id, group_id) VALUES ('00000000-0000-0000-0000-00000000003b', '00000000-0000-0000-0000-000000000012');
INSERT INTO app_user_group (user_id, group_id) VALUES ('00000000-0000-0000-0000-00000000003c', '00000000-0000-0000-0000-000000000012');
INSERT INTO account_audit_event (id, occurred_at, actor, action, target_type, target_id, target_name, target_full_name, reason_code, reason_note, details) VALUES ('00000000-0000-0000-0000-0000000000a1', DATEADD('MINUTE', -30, CURRENT_TIMESTAMP), 'hr.system', 'delete_user', 'USER', '00000000-0000-0000-0000-0000000000a1', 'sarah.lim', 'Sarah Lim', 'left_organisation', 'Left the organisation', '{"status":"active","groups":["Users"],"roles":["APPLICATION_USER"]}');
INSERT INTO account_audit_event (id, occurred_at, actor, action, target_type, target_id, target_name, target_full_name, reason_code, reason_note, details) VALUES ('00000000-0000-0000-0000-0000000000a2', DATEADD('MINUTE', -30, CURRENT_TIMESTAMP), 'system', 'delete_user', 'USER', '00000000-0000-0000-0000-0000000000a2', 'tom.yeo', 'Tom Yeo', 'inactive_account', NULL, '{"status":"active","groups":["Users"],"roles":["APPLICATION_USER"]}');
UPDATE app_setting SET setting_value = 'true' WHERE name = 'review.enabled';
