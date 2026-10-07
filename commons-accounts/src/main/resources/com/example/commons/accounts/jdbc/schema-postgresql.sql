-- Changeset com/example/commons/accounts/jdbc/schema.yaml::accounts-schema::commons-accounts
CREATE SEQUENCE  IF NOT EXISTS app_user_seq START WITH 1000 INCREMENT BY 50;

CREATE SEQUENCE  IF NOT EXISTS app_role_seq START WITH 1000 INCREMENT BY 50;

CREATE SEQUENCE  IF NOT EXISTS app_permission_seq START WITH 1000 INCREMENT BY 50;

CREATE SEQUENCE  IF NOT EXISTS app_setting_seq START WITH 1000 INCREMENT BY 50;

CREATE SEQUENCE  IF NOT EXISTS task_seq START WITH 1000 INCREMENT BY 50;

CREATE SEQUENCE  IF NOT EXISTS account_review_item_seq START WITH 1000 INCREMENT BY 50;

CREATE SEQUENCE  IF NOT EXISTS account_review_attestation_seq START WITH 1000 INCREMENT BY 50;

CREATE SEQUENCE  IF NOT EXISTS account_review_population_entry_seq START WITH 1000 INCREMENT BY 50;

CREATE SEQUENCE  IF NOT EXISTS account_review_report_seq START WITH 1000 INCREMENT BY 50;

CREATE TABLE app_user (id BIGINT NOT NULL, public_id UUID NOT NULL, username VARCHAR(100) NOT NULL, name VARCHAR(100) NOT NULL, email VARCHAR(254), department VARCHAR(100), status VARCHAR(20) DEFAULT 'ACTIVE' NOT NULL, suspended_at TIMESTAMP WITH TIME ZONE, suspension_reason_code VARCHAR(40), suspension_note VARCHAR(200), inactivity_clock_started_at TIMESTAMP WITH TIME ZONE DEFAULT NOW() NOT NULL, last_login_at TIMESTAMP WITH TIME ZONE, created_at TIMESTAMP WITH TIME ZONE NOT NULL, updated_at TIMESTAMP WITH TIME ZONE NOT NULL, created_by VARCHAR(100) NOT NULL, updated_by VARCHAR(100) NOT NULL, CONSTRAINT pk_app_user PRIMARY KEY (id), CONSTRAINT uk_app_user_public_id UNIQUE (public_id), CONSTRAINT uk_app_user_username UNIQUE (username));

CREATE INDEX ix_app_user_department ON app_user(department);

CREATE TABLE app_role (id BIGINT NOT NULL, public_id UUID NOT NULL, name VARCHAR(100) NOT NULL, created_at TIMESTAMP WITH TIME ZONE NOT NULL, updated_at TIMESTAMP WITH TIME ZONE NOT NULL, created_by VARCHAR(100) NOT NULL, updated_by VARCHAR(100) NOT NULL, CONSTRAINT pk_app_role PRIMARY KEY (id), CONSTRAINT uk_app_role_public_id UNIQUE (public_id), CONSTRAINT uk_app_role_name UNIQUE (name));

CREATE TABLE app_permission (id BIGINT NOT NULL, public_id UUID NOT NULL, domain VARCHAR(50) NOT NULL, action VARCHAR(50) NOT NULL, privileged BOOLEAN DEFAULT FALSE NOT NULL, CONSTRAINT pk_app_permission PRIMARY KEY (id), CONSTRAINT uk_app_permission_public_id UNIQUE (public_id));

ALTER TABLE app_permission ADD CONSTRAINT uk_app_permission_domain_action UNIQUE (domain, action);

CREATE TABLE app_user_role (user_id BIGINT NOT NULL, role_id BIGINT NOT NULL);

ALTER TABLE app_user_role ADD CONSTRAINT pk_app_user_role PRIMARY KEY (user_id, role_id);

ALTER TABLE app_user_role ADD CONSTRAINT fk_app_user_role_user FOREIGN KEY (user_id) REFERENCES app_user (id);

ALTER TABLE app_user_role ADD CONSTRAINT fk_app_user_role_role FOREIGN KEY (role_id) REFERENCES app_role (id);

CREATE TABLE app_role_permission (role_id BIGINT NOT NULL, permission_id BIGINT NOT NULL);

ALTER TABLE app_role_permission ADD CONSTRAINT pk_app_role_permission PRIMARY KEY (role_id, permission_id);

ALTER TABLE app_role_permission ADD CONSTRAINT fk_app_role_permission_role FOREIGN KEY (role_id) REFERENCES app_role (id);

ALTER TABLE app_role_permission ADD CONSTRAINT fk_app_role_permission_permission FOREIGN KEY (permission_id) REFERENCES app_permission (id);

CREATE INDEX ix_app_user_role_role ON app_user_role(role_id);

CREATE INDEX ix_app_role_permission_permission ON app_role_permission(permission_id);

CREATE TABLE app_permission_conflict (permission_id BIGINT NOT NULL, conflicting_permission_id BIGINT NOT NULL);

ALTER TABLE app_permission_conflict ADD CONSTRAINT pk_app_permission_conflict PRIMARY KEY (permission_id, conflicting_permission_id);

ALTER TABLE app_permission_conflict ADD CONSTRAINT fk_app_permission_conflict_permission FOREIGN KEY (permission_id) REFERENCES app_permission (id);

ALTER TABLE app_permission_conflict ADD CONSTRAINT fk_app_permission_conflict_other FOREIGN KEY (conflicting_permission_id) REFERENCES app_permission (id);

CREATE TABLE user_entities (id VARCHAR(1000) NOT NULL, name VARCHAR(100) NOT NULL, display_name VARCHAR(200), CONSTRAINT pk_user_entities PRIMARY KEY (id), CONSTRAINT uk_user_entities_name UNIQUE (name));

CREATE TABLE user_credentials (credential_id VARCHAR(1000) NOT NULL, user_entity_user_id VARCHAR(1000) NOT NULL, public_key BYTEA NOT NULL, signature_count BIGINT, uv_initialized BOOLEAN, backup_eligible BOOLEAN NOT NULL, authenticator_transports VARCHAR(1000), public_key_credential_type VARCHAR(100), backup_state BOOLEAN NOT NULL, attestation_object BYTEA, attestation_client_data_json BYTEA, created TIMESTAMP WITHOUT TIME ZONE, last_used TIMESTAMP WITHOUT TIME ZONE, label VARCHAR(1000) NOT NULL, CONSTRAINT pk_user_credentials PRIMARY KEY (credential_id));

ALTER TABLE user_credentials ADD CONSTRAINT fk_user_credentials_user FOREIGN KEY (user_entity_user_id) REFERENCES user_entities (id) ON DELETE CASCADE;

CREATE INDEX ix_user_credentials_user ON user_credentials(user_entity_user_id);

CREATE TABLE app_setting (id BIGINT NOT NULL, name VARCHAR(100) NOT NULL, setting_value VARCHAR(100) NOT NULL, updated_at TIMESTAMP WITH TIME ZONE NOT NULL, updated_by VARCHAR(100) NOT NULL, CONSTRAINT pk_app_setting PRIMARY KEY (id), CONSTRAINT uk_app_setting_name UNIQUE (name));

CREATE TABLE task (id BIGINT NOT NULL, public_id UUID NOT NULL, type VARCHAR(40) NOT NULL, status VARCHAR(20) NOT NULL, start_date date NOT NULL, due_date date NOT NULL, created_at TIMESTAMP WITH TIME ZONE NOT NULL, completed_at TIMESTAMP WITH TIME ZONE, completed_by VARCHAR(100), CONSTRAINT pk_task PRIMARY KEY (id), CONSTRAINT uk_task_public_id UNIQUE (public_id));

ALTER TABLE task ADD CONSTRAINT uk_task_type_start UNIQUE (type, start_date);

CREATE TABLE account_review_item (id BIGINT NOT NULL, public_id UUID NOT NULL, task_id BIGINT NOT NULL, user_public_id UUID NOT NULL, username VARCHAR(100) NOT NULL, full_name VARCHAR(100) NOT NULL, category VARCHAR(20) NOT NULL, outcome VARCHAR(30) NOT NULL, decided_at TIMESTAMP WITH TIME ZONE, decided_by VARCHAR(100), removal_audit_event_id UUID, department VARCHAR(100), account_created_at TIMESTAMP WITH TIME ZONE, last_login_at TIMESTAMP WITH TIME ZONE, last_activity_at TIMESTAMP WITH TIME ZONE, suspended_at TIMESTAMP WITH TIME ZONE, suspended_by VARCHAR(100), suspension_reason_code VARCHAR(40), suspension_note VARCHAR(200), roles_before TEXT, roles_after TEXT, privileged_permissions TEXT, CONSTRAINT pk_account_review_item PRIMARY KEY (id), CONSTRAINT uk_account_review_item_public_id UNIQUE (public_id));

ALTER TABLE account_review_item ADD CONSTRAINT fk_account_review_item_task FOREIGN KEY (task_id) REFERENCES task (id);

ALTER TABLE account_review_item ADD CONSTRAINT uk_account_review_item_task_user UNIQUE (task_id, user_public_id);

CREATE INDEX ix_account_review_item_task_outcome ON account_review_item(task_id, outcome);

CREATE TABLE account_review_attestation (id BIGINT NOT NULL, public_id UUID NOT NULL, task_id BIGINT NOT NULL, population VARCHAR(20) NOT NULL, confirmed_by VARCHAR(100) NOT NULL, confirmed_at TIMESTAMP WITH TIME ZONE NOT NULL, note VARCHAR(200), entry_count INTEGER NOT NULL, CONSTRAINT pk_account_review_attestation PRIMARY KEY (id), CONSTRAINT uk_account_review_attestation_public_id UNIQUE (public_id));

ALTER TABLE account_review_attestation ADD CONSTRAINT fk_account_review_attestation_task FOREIGN KEY (task_id) REFERENCES task (id);

ALTER TABLE account_review_attestation ADD CONSTRAINT uk_account_review_attestation_task_population UNIQUE (task_id, population);

CREATE TABLE account_review_population_entry (id BIGINT NOT NULL, public_id UUID NOT NULL, attestation_id BIGINT NOT NULL, user_public_id UUID NOT NULL, username VARCHAR(100) NOT NULL, full_name VARCHAR(100) NOT NULL, department VARCHAR(100), created_at TIMESTAMP WITH TIME ZONE, last_login_at TIMESTAMP WITH TIME ZONE, last_activity_at TIMESTAMP WITH TIME ZONE, occurred_at TIMESTAMP WITH TIME ZONE NOT NULL, actor VARCHAR(100), reason_code VARCHAR(40), reason_note VARCHAR(200), CONSTRAINT pk_account_review_population_entry PRIMARY KEY (id), CONSTRAINT uk_account_review_population_entry_public_id UNIQUE (public_id));

ALTER TABLE account_review_population_entry ADD CONSTRAINT fk_account_review_population_entry_attestation FOREIGN KEY (attestation_id) REFERENCES account_review_attestation (id);

CREATE INDEX ix_account_review_population_entry_attestation ON account_review_population_entry(attestation_id);

CREATE TABLE account_review_report (id BIGINT NOT NULL, public_id UUID NOT NULL, task_id BIGINT NOT NULL, content BYTEA NOT NULL, size_bytes BIGINT NOT NULL, sha256 VARCHAR(64) NOT NULL, generated_at TIMESTAMP WITH TIME ZONE NOT NULL, generated_by VARCHAR(100) NOT NULL, CONSTRAINT pk_account_review_report PRIMARY KEY (id), CONSTRAINT uk_account_review_report_public_id UNIQUE (public_id), CONSTRAINT uk_account_review_report_task UNIQUE (task_id));

ALTER TABLE account_review_report ADD CONSTRAINT fk_account_review_report_task FOREIGN KEY (task_id) REFERENCES task (id);

INSERT INTO app_setting (id, name, setting_value, updated_at, updated_by) VALUES (1, 'inactivity.enabled', 'true', NOW(), 'system');

INSERT INTO app_setting (id, name, setting_value, updated_at, updated_by) VALUES (2, 'inactivity.suspendAfterDays', '90', NOW(), 'system');

INSERT INTO app_setting (id, name, setting_value, updated_at, updated_by) VALUES (3, 'inactivity.removeAfterDays', '180', NOW(), 'system');

INSERT INTO app_setting (id, name, setting_value, updated_at, updated_by) VALUES (4, 'review.enabled', 'true', NOW(), 'system');

INSERT INTO app_setting (id, name, setting_value, updated_at, updated_by) VALUES (5, 'review.privilegedIntervalMonths', '1', NOW(), 'system');

INSERT INTO app_setting (id, name, setting_value, updated_at, updated_by) VALUES (6, 'review.nonPrivilegedIntervalMonths', '12', NOW(), 'system');

INSERT INTO app_permission (id, public_id, domain, action, privileged) VALUES (1, '00000000-0000-0000-0100-000000000001', 'application', 'access', FALSE);

INSERT INTO app_permission (id, public_id, domain, action, privileged) VALUES (2, '00000000-0000-0000-0100-000000000002', 'user', 'read', FALSE);

INSERT INTO app_permission (id, public_id, domain, action, privileged) VALUES (3, '00000000-0000-0000-0100-000000000003', 'user', 'create', TRUE);

INSERT INTO app_permission (id, public_id, domain, action, privileged) VALUES (4, '00000000-0000-0000-0100-000000000004', 'user', 'update', FALSE);

INSERT INTO app_permission (id, public_id, domain, action, privileged) VALUES (5, '00000000-0000-0000-0100-000000000005', 'user', 'add-role', TRUE);

INSERT INTO app_permission (id, public_id, domain, action, privileged) VALUES (6, '00000000-0000-0000-0100-000000000006', 'user', 'remove-role', FALSE);

INSERT INTO app_permission (id, public_id, domain, action, privileged) VALUES (7, '00000000-0000-0000-0100-000000000007', 'user', 'suspend', FALSE);

INSERT INTO app_permission (id, public_id, domain, action, privileged) VALUES (8, '00000000-0000-0000-0100-000000000008', 'user', 'unsuspend', TRUE);

INSERT INTO app_permission (id, public_id, domain, action, privileged) VALUES (9, '00000000-0000-0000-0100-000000000009', 'user', 'remove', FALSE);

INSERT INTO app_permission (id, public_id, domain, action, privileged) VALUES (10, '00000000-0000-0000-0100-00000000000a', 'user', 'revoke-session', FALSE);

INSERT INTO app_permission (id, public_id, domain, action, privileged) VALUES (11, '00000000-0000-0000-0100-00000000000b', 'user', 'remove-passkey', FALSE);

INSERT INTO app_permission (id, public_id, domain, action, privileged) VALUES (12, '00000000-0000-0000-0100-00000000000c', 'role', 'read', FALSE);

INSERT INTO app_permission (id, public_id, domain, action, privileged) VALUES (13, '00000000-0000-0000-0100-00000000000d', 'role', 'create', FALSE);

INSERT INTO app_permission (id, public_id, domain, action, privileged) VALUES (14, '00000000-0000-0000-0100-00000000000e', 'role', 'update', FALSE);

INSERT INTO app_permission (id, public_id, domain, action, privileged) VALUES (15, '00000000-0000-0000-0100-00000000000f', 'role', 'delete', FALSE);

INSERT INTO app_permission (id, public_id, domain, action, privileged) VALUES (16, '00000000-0000-0000-0100-000000000010', 'role', 'add-permission', TRUE);

INSERT INTO app_permission (id, public_id, domain, action, privileged) VALUES (17, '00000000-0000-0000-0100-000000000011', 'role', 'remove-permission', FALSE);

INSERT INTO app_permission (id, public_id, domain, action, privileged) VALUES (18, '00000000-0000-0000-0100-000000000012', 'permission', 'read', FALSE);

INSERT INTO app_permission (id, public_id, domain, action, privileged) VALUES (19, '00000000-0000-0000-0100-000000000013', 'settings', 'read', FALSE);

INSERT INTO app_permission (id, public_id, domain, action, privileged) VALUES (20, '00000000-0000-0000-0100-000000000014', 'settings', 'update', TRUE);

INSERT INTO app_permission (id, public_id, domain, action, privileged) VALUES (21, '00000000-0000-0000-0100-000000000015', 'audit', 'read', FALSE);

INSERT INTO app_permission (id, public_id, domain, action, privileged) VALUES (22, '00000000-0000-0000-0100-000000000016', 'review', 'read', FALSE);

INSERT INTO app_permission (id, public_id, domain, action, privileged) VALUES (23, '00000000-0000-0000-0100-000000000017', 'review', 'decide', FALSE);

INSERT INTO app_permission (id, public_id, domain, action, privileged) VALUES (24, '00000000-0000-0000-0100-000000000018', 'review', 'confirm-population', FALSE);

INSERT INTO app_permission (id, public_id, domain, action, privileged) VALUES (25, '00000000-0000-0000-0100-000000000019', 'review', 'download-report', FALSE);

INSERT INTO app_permission_conflict (permission_id, conflicting_permission_id) VALUES (23, 3);

INSERT INTO app_permission_conflict (permission_id, conflicting_permission_id) VALUES (23, 5);

INSERT INTO app_permission_conflict (permission_id, conflicting_permission_id) VALUES (23, 8);

INSERT INTO app_permission_conflict (permission_id, conflicting_permission_id) VALUES (23, 16);

INSERT INTO app_permission_conflict (permission_id, conflicting_permission_id) VALUES (23, 20);
