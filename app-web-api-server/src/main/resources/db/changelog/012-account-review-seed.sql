--liquibase formatted sql

-- Reference data for the account review, applied in every environment: the two roles it
-- adds, the Account Reviewers group, and SETTINGS_MANAGE for administrators. The group has
-- no members here; reviewers are added through the administration API. Administrators do not
-- hold ACCOUNT_REVIEWER, so the person who maintains accounts is not the one who reviews
-- them. See docs/specifications/account-review.

--changeset app:012-account-review-seed
INSERT INTO app_role (id, name, created_at, updated_at, created_by, updated_by) VALUES ('00000000-0000-0000-0000-000000000005', 'ACCOUNT_REVIEWER', TIMESTAMP '2026-01-01 00:00:00', TIMESTAMP '2026-01-01 00:00:00', 'system', 'system');
INSERT INTO app_role (id, name, created_at, updated_at, created_by, updated_by) VALUES ('00000000-0000-0000-0000-000000000006', 'SETTINGS_MANAGE', TIMESTAMP '2026-01-01 00:00:00', TIMESTAMP '2026-01-01 00:00:00', 'system', 'system');
INSERT INTO app_group (id, name, created_at, updated_at, created_by, updated_by) VALUES ('00000000-0000-0000-0000-000000000013', 'Account Reviewers', TIMESTAMP '2026-01-01 00:00:00', TIMESTAMP '2026-01-01 00:00:00', 'system', 'system');
INSERT INTO app_group_role (group_id, role_id) VALUES ('00000000-0000-0000-0000-000000000013', '00000000-0000-0000-0000-000000000005');
INSERT INTO app_group_role (group_id, role_id) VALUES ('00000000-0000-0000-0000-000000000011', '00000000-0000-0000-0000-000000000006');
