--liquibase formatted sql

-- Development and test fixtures for the account review, applied only when the dev context is
-- requested (see 004-development-seed.sql and docs/adr/0018). The two account reviewers can
-- review each other. The automation is switched off so the fixtures are never suspended,
-- removed or put into a review unless a test or a developer enables it.

--changeset app:013-development-account-review-seed context:@dev
INSERT INTO app_user (id, username, name, email, status, inactivity_clock_started_at, created_at, updated_at, created_by, updated_by) VALUES ('00000000-0000-0000-0000-000000000024', 'account-reviewer-1', 'Rachel Lim', 'account-reviewer-1@example.test', 'ACTIVE', CURRENT_TIMESTAMP, TIMESTAMP '2026-01-01 00:00:00', TIMESTAMP '2026-01-01 00:00:00', 'system', 'system');
INSERT INTO app_user (id, username, name, email, status, inactivity_clock_started_at, created_at, updated_at, created_by, updated_by) VALUES ('00000000-0000-0000-0000-000000000025', 'account-reviewer-2', 'Ravi Nair', 'account-reviewer-2@example.test', 'ACTIVE', CURRENT_TIMESTAMP, TIMESTAMP '2026-01-01 00:00:00', TIMESTAMP '2026-01-01 00:00:00', 'system', 'system');
INSERT INTO app_user_group (user_id, group_id) VALUES ('00000000-0000-0000-0000-000000000024', '00000000-0000-0000-0000-000000000013');
INSERT INTO app_user_group (user_id, group_id) VALUES ('00000000-0000-0000-0000-000000000025', '00000000-0000-0000-0000-000000000013');
UPDATE app_setting SET setting_value = 'false' WHERE name IN ('inactivity.enabled', 'review.enabled');
