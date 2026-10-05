-- Changeset com/example/commons/accounts/jdbc/schema.yaml::accounts-schema::commons-accounts
CREATE SEQUENCE app_user_seq START WITH 1000 INCREMENT BY 50;

CREATE SEQUENCE app_group_seq START WITH 1000 INCREMENT BY 50;

CREATE SEQUENCE app_role_seq START WITH 1000 INCREMENT BY 50;

CREATE SEQUENCE account_audit_event_seq START WITH 1000 INCREMENT BY 50;

CREATE SEQUENCE app_setting_seq START WITH 1000 INCREMENT BY 50;

CREATE SEQUENCE task_seq START WITH 1000 INCREMENT BY 50;

CREATE SEQUENCE account_review_item_seq START WITH 1000 INCREMENT BY 50;

CREATE SEQUENCE account_review_attestation_seq START WITH 1000 INCREMENT BY 50;

CREATE SEQUENCE account_review_population_entry_seq START WITH 1000 INCREMENT BY 50;

CREATE SEQUENCE account_review_report_seq START WITH 1000 INCREMENT BY 50;

CREATE TABLE app_user (id BIGINT NOT NULL, public_id UUID NOT NULL, username VARCHAR(100) NOT NULL, name VARCHAR(100) NOT NULL, email VARCHAR(254), department VARCHAR(100), status VARCHAR(20) DEFAULT 'ACTIVE' NOT NULL, suspended_at TIMESTAMP WITH TIME ZONE, suspension_reason_code VARCHAR(40), suspension_note VARCHAR(200), inactivity_clock_started_at TIMESTAMP WITH TIME ZONE DEFAULT NOW() NOT NULL, last_login_at TIMESTAMP WITH TIME ZONE, created_at TIMESTAMP WITH TIME ZONE NOT NULL, updated_at TIMESTAMP WITH TIME ZONE NOT NULL, created_by VARCHAR(100) NOT NULL, updated_by VARCHAR(100) NOT NULL, CONSTRAINT pk_app_user PRIMARY KEY (id), CONSTRAINT uk_app_user_public_id UNIQUE (public_id), CONSTRAINT uk_app_user_username UNIQUE (username));

CREATE INDEX ix_app_user_department ON app_user(department);

CREATE TABLE app_group (id BIGINT NOT NULL, public_id UUID NOT NULL, name VARCHAR(100) NOT NULL, created_at TIMESTAMP WITH TIME ZONE NOT NULL, updated_at TIMESTAMP WITH TIME ZONE NOT NULL, created_by VARCHAR(100) NOT NULL, updated_by VARCHAR(100) NOT NULL, CONSTRAINT pk_app_group PRIMARY KEY (id), CONSTRAINT uk_app_group_public_id UNIQUE (public_id), CONSTRAINT uk_app_group_name UNIQUE (name));

CREATE TABLE app_role (id BIGINT NOT NULL, public_id UUID NOT NULL, name VARCHAR(100) NOT NULL, display_name VARCHAR(100) NOT NULL, created_at TIMESTAMP WITH TIME ZONE NOT NULL, updated_at TIMESTAMP WITH TIME ZONE NOT NULL, created_by VARCHAR(100) NOT NULL, updated_by VARCHAR(100) NOT NULL, CONSTRAINT pk_app_role PRIMARY KEY (id), CONSTRAINT uk_app_role_public_id UNIQUE (public_id), CONSTRAINT uk_app_role_name UNIQUE (name));

CREATE TABLE app_user_group (user_id BIGINT NOT NULL, group_id BIGINT NOT NULL);

ALTER TABLE app_user_group ADD CONSTRAINT pk_app_user_group PRIMARY KEY (user_id, group_id);

ALTER TABLE app_user_group ADD CONSTRAINT fk_app_user_group_user FOREIGN KEY (user_id) REFERENCES app_user (id);

ALTER TABLE app_user_group ADD CONSTRAINT fk_app_user_group_group FOREIGN KEY (group_id) REFERENCES app_group (id);

CREATE TABLE app_group_role (group_id BIGINT NOT NULL, role_id BIGINT NOT NULL);

ALTER TABLE app_group_role ADD CONSTRAINT pk_app_group_role PRIMARY KEY (group_id, role_id);

ALTER TABLE app_group_role ADD CONSTRAINT fk_app_group_role_group FOREIGN KEY (group_id) REFERENCES app_group (id);

ALTER TABLE app_group_role ADD CONSTRAINT fk_app_group_role_role FOREIGN KEY (role_id) REFERENCES app_role (id);

CREATE INDEX ix_app_user_group_group ON app_user_group(group_id);

CREATE INDEX ix_app_group_role_role ON app_group_role(role_id);

CREATE TABLE user_entities (id VARCHAR(1000) NOT NULL, name VARCHAR(100) NOT NULL, display_name VARCHAR(200), CONSTRAINT pk_user_entities PRIMARY KEY (id), CONSTRAINT uk_user_entities_name UNIQUE (name));

CREATE TABLE user_credentials (credential_id VARCHAR(1000) NOT NULL, user_entity_user_id VARCHAR(1000) NOT NULL, public_key LONGVARBINARY NOT NULL, signature_count BIGINT, uv_initialized BOOLEAN, backup_eligible BOOLEAN NOT NULL, authenticator_transports VARCHAR(1000), public_key_credential_type VARCHAR(100), backup_state BOOLEAN NOT NULL, attestation_object LONGVARBINARY, attestation_client_data_json LONGVARBINARY, created TIMESTAMP, last_used TIMESTAMP, label VARCHAR(1000) NOT NULL, CONSTRAINT pk_user_credentials PRIMARY KEY (credential_id));

ALTER TABLE user_credentials ADD CONSTRAINT fk_user_credentials_user FOREIGN KEY (user_entity_user_id) REFERENCES user_entities (id) ON DELETE CASCADE;

CREATE INDEX ix_user_credentials_user ON user_credentials(user_entity_user_id);

CREATE TABLE account_audit_event (id BIGINT NOT NULL, public_id UUID NOT NULL, occurred_at TIMESTAMP WITH TIME ZONE NOT NULL, actor VARCHAR(100) NOT NULL, action VARCHAR(50) NOT NULL, target_type VARCHAR(20) NOT NULL, target_id VARCHAR(100), target_name VARCHAR(100), target_full_name VARCHAR(100), reason_code VARCHAR(40), reason_note VARCHAR(200), details CLOB, CONSTRAINT pk_account_audit_event PRIMARY KEY (id), CONSTRAINT uk_account_audit_event_public_id UNIQUE (public_id));

CREATE INDEX ix_account_audit_event_occurred ON account_audit_event(occurred_at);

CREATE INDEX ix_account_audit_event_actor ON account_audit_event(actor);

CREATE INDEX ix_account_audit_event_target ON account_audit_event(target_type, target_name);

CREATE INDEX ix_account_audit_event_action ON account_audit_event(action);

CREATE TABLE app_setting (id BIGINT NOT NULL, name VARCHAR(100) NOT NULL, setting_value VARCHAR(100) NOT NULL, updated_at TIMESTAMP WITH TIME ZONE NOT NULL, updated_by VARCHAR(100) NOT NULL, CONSTRAINT pk_app_setting PRIMARY KEY (id), CONSTRAINT uk_app_setting_name UNIQUE (name));

CREATE TABLE task (id BIGINT NOT NULL, public_id UUID NOT NULL, type VARCHAR(40) NOT NULL, status VARCHAR(20) NOT NULL, start_date date NOT NULL, due_date date NOT NULL, created_at TIMESTAMP WITH TIME ZONE NOT NULL, completed_at TIMESTAMP WITH TIME ZONE, completed_by VARCHAR(100), CONSTRAINT pk_task PRIMARY KEY (id), CONSTRAINT uk_task_public_id UNIQUE (public_id));

ALTER TABLE task ADD CONSTRAINT uk_task_type_start UNIQUE (type, start_date);

CREATE TABLE account_review_item (id BIGINT NOT NULL, public_id UUID NOT NULL, task_id BIGINT NOT NULL, user_public_id UUID NOT NULL, username VARCHAR(100) NOT NULL, full_name VARCHAR(100) NOT NULL, outcome VARCHAR(30) NOT NULL, decided_at TIMESTAMP WITH TIME ZONE, decided_by VARCHAR(100), removal_audit_event_id BIGINT, department VARCHAR(100), last_login_at TIMESTAMP WITH TIME ZONE, groups_before CLOB, groups_after CLOB, CONSTRAINT pk_account_review_item PRIMARY KEY (id), CONSTRAINT uk_account_review_item_public_id UNIQUE (public_id));

ALTER TABLE account_review_item ADD CONSTRAINT fk_account_review_item_task FOREIGN KEY (task_id) REFERENCES task (id);

ALTER TABLE account_review_item ADD CONSTRAINT uk_account_review_item_task_user UNIQUE (task_id, user_public_id);

CREATE INDEX ix_account_review_item_task_outcome ON account_review_item(task_id, outcome);

CREATE TABLE account_review_attestation (id BIGINT NOT NULL, public_id UUID NOT NULL, task_id BIGINT NOT NULL, population VARCHAR(20) NOT NULL, confirmed_by VARCHAR(100) NOT NULL, confirmed_at TIMESTAMP WITH TIME ZONE NOT NULL, note VARCHAR(200), entry_count INT NOT NULL, CONSTRAINT pk_account_review_attestation PRIMARY KEY (id), CONSTRAINT uk_account_review_attestation_public_id UNIQUE (public_id));

ALTER TABLE account_review_attestation ADD CONSTRAINT fk_account_review_attestation_task FOREIGN KEY (task_id) REFERENCES task (id);

ALTER TABLE account_review_attestation ADD CONSTRAINT uk_account_review_attestation_task_population UNIQUE (task_id, population);

CREATE TABLE account_review_population_entry (id BIGINT NOT NULL, public_id UUID NOT NULL, attestation_id BIGINT NOT NULL, user_public_id UUID NOT NULL, username VARCHAR(100) NOT NULL, full_name VARCHAR(100) NOT NULL, department VARCHAR(100), last_login_at TIMESTAMP WITH TIME ZONE, occurred_at TIMESTAMP WITH TIME ZONE NOT NULL, actor VARCHAR(100), reason_code VARCHAR(40), reason_note VARCHAR(200), CONSTRAINT pk_account_review_population_entry PRIMARY KEY (id), CONSTRAINT uk_account_review_population_entry_public_id UNIQUE (public_id));

ALTER TABLE account_review_population_entry ADD CONSTRAINT fk_account_review_population_entry_attestation FOREIGN KEY (attestation_id) REFERENCES account_review_attestation (id);

CREATE INDEX ix_account_review_population_entry_attestation ON account_review_population_entry(attestation_id);

CREATE TABLE account_review_report (id BIGINT NOT NULL, public_id UUID NOT NULL, task_id BIGINT NOT NULL, content LONGVARBINARY NOT NULL, size_bytes BIGINT NOT NULL, sha256 VARCHAR(64) NOT NULL, generated_at TIMESTAMP WITH TIME ZONE NOT NULL, generated_by VARCHAR(100) NOT NULL, CONSTRAINT pk_account_review_report PRIMARY KEY (id), CONSTRAINT uk_account_review_report_public_id UNIQUE (public_id), CONSTRAINT uk_account_review_report_task UNIQUE (task_id));

ALTER TABLE account_review_report ADD CONSTRAINT fk_account_review_report_task FOREIGN KEY (task_id) REFERENCES task (id);

INSERT INTO app_setting (id, name, setting_value, updated_at, updated_by) VALUES (1, 'inactivity.enabled', 'true', NOW(), 'system');

INSERT INTO app_setting (id, name, setting_value, updated_at, updated_by) VALUES (2, 'inactivity.suspendAfterDays', '90', NOW(), 'system');

INSERT INTO app_setting (id, name, setting_value, updated_at, updated_by) VALUES (3, 'inactivity.removeAfterDays', '180', NOW(), 'system');

INSERT INTO app_setting (id, name, setting_value, updated_at, updated_by) VALUES (4, 'review.enabled', 'true', NOW(), 'system');

INSERT INTO app_setting (id, name, setting_value, updated_at, updated_by) VALUES (5, 'review.intervalMonths', '3', NOW(), 'system');
