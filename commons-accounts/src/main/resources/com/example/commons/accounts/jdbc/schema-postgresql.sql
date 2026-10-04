-- Changeset com/example/commons/accounts/jdbc/schema.yaml::accounts-schema::commons-accounts
CREATE TABLE app_user (id UUID NOT NULL, username VARCHAR(100) NOT NULL, name VARCHAR(100) NOT NULL, email VARCHAR(254), status VARCHAR(20) DEFAULT 'ACTIVE' NOT NULL, suspended_at TIMESTAMP WITH TIME ZONE, suspension_reason_code VARCHAR(40), suspension_note VARCHAR(200), inactivity_clock_started_at TIMESTAMP WITH TIME ZONE DEFAULT NOW() NOT NULL, last_login_at TIMESTAMP WITH TIME ZONE, created_at TIMESTAMP WITH TIME ZONE NOT NULL, updated_at TIMESTAMP WITH TIME ZONE NOT NULL, created_by VARCHAR(100) NOT NULL, updated_by VARCHAR(100) NOT NULL, CONSTRAINT pk_app_user PRIMARY KEY (id), CONSTRAINT uk_app_user_username UNIQUE (username));

CREATE TABLE app_group (id UUID NOT NULL, name VARCHAR(100) NOT NULL, created_at TIMESTAMP WITH TIME ZONE NOT NULL, updated_at TIMESTAMP WITH TIME ZONE NOT NULL, created_by VARCHAR(100) NOT NULL, updated_by VARCHAR(100) NOT NULL, CONSTRAINT pk_app_group PRIMARY KEY (id), CONSTRAINT uk_app_group_name UNIQUE (name));

CREATE TABLE app_role (id UUID NOT NULL, name VARCHAR(100) NOT NULL, display_name VARCHAR(100) NOT NULL, created_at TIMESTAMP WITH TIME ZONE NOT NULL, updated_at TIMESTAMP WITH TIME ZONE NOT NULL, created_by VARCHAR(100) NOT NULL, updated_by VARCHAR(100) NOT NULL, CONSTRAINT pk_app_role PRIMARY KEY (id), CONSTRAINT uk_app_role_name UNIQUE (name));

CREATE TABLE app_user_group (user_id UUID NOT NULL, group_id UUID NOT NULL);

ALTER TABLE app_user_group ADD CONSTRAINT pk_app_user_group PRIMARY KEY (user_id, group_id);

ALTER TABLE app_user_group ADD CONSTRAINT fk_app_user_group_user FOREIGN KEY (user_id) REFERENCES app_user (id);

ALTER TABLE app_user_group ADD CONSTRAINT fk_app_user_group_group FOREIGN KEY (group_id) REFERENCES app_group (id);

CREATE TABLE app_group_role (group_id UUID NOT NULL, role_id UUID NOT NULL);

ALTER TABLE app_group_role ADD CONSTRAINT pk_app_group_role PRIMARY KEY (group_id, role_id);

ALTER TABLE app_group_role ADD CONSTRAINT fk_app_group_role_group FOREIGN KEY (group_id) REFERENCES app_group (id);

ALTER TABLE app_group_role ADD CONSTRAINT fk_app_group_role_role FOREIGN KEY (role_id) REFERENCES app_role (id);

CREATE INDEX ix_app_user_group_group ON app_user_group(group_id);

CREATE INDEX ix_app_group_role_role ON app_group_role(role_id);

CREATE TABLE user_entities (id VARCHAR(1000) NOT NULL, name VARCHAR(100) NOT NULL, display_name VARCHAR(200), CONSTRAINT pk_user_entities PRIMARY KEY (id), CONSTRAINT uk_user_entities_name UNIQUE (name));

CREATE TABLE user_credentials (credential_id VARCHAR(1000) NOT NULL, user_entity_user_id VARCHAR(1000) NOT NULL, public_key BYTEA NOT NULL, signature_count BIGINT, uv_initialized BOOLEAN, backup_eligible BOOLEAN NOT NULL, authenticator_transports VARCHAR(1000), public_key_credential_type VARCHAR(100), backup_state BOOLEAN NOT NULL, attestation_object BYTEA, attestation_client_data_json BYTEA, created TIMESTAMP WITHOUT TIME ZONE, last_used TIMESTAMP WITHOUT TIME ZONE, label VARCHAR(1000) NOT NULL, CONSTRAINT pk_user_credentials PRIMARY KEY (credential_id));

ALTER TABLE user_credentials ADD CONSTRAINT fk_user_credentials_user FOREIGN KEY (user_entity_user_id) REFERENCES user_entities (id) ON DELETE CASCADE;

CREATE INDEX ix_user_credentials_user ON user_credentials(user_entity_user_id);

CREATE TABLE account_audit_event (id UUID NOT NULL, occurred_at TIMESTAMP WITH TIME ZONE NOT NULL, actor VARCHAR(100) NOT NULL, action VARCHAR(50) NOT NULL, target_type VARCHAR(20) NOT NULL, target_id VARCHAR(100), target_name VARCHAR(100), target_full_name VARCHAR(100), reason_code VARCHAR(40), reason_note VARCHAR(200), details TEXT, CONSTRAINT pk_account_audit_event PRIMARY KEY (id));

CREATE INDEX ix_account_audit_event_occurred ON account_audit_event(occurred_at);

CREATE INDEX ix_account_audit_event_actor ON account_audit_event(actor);

CREATE INDEX ix_account_audit_event_target ON account_audit_event(target_type, target_name);

CREATE INDEX ix_account_audit_event_action ON account_audit_event(action);

CREATE TABLE app_setting (name VARCHAR(100) NOT NULL, setting_value VARCHAR(100) NOT NULL, updated_at TIMESTAMP WITH TIME ZONE NOT NULL, updated_by VARCHAR(100) NOT NULL, CONSTRAINT pk_app_setting PRIMARY KEY (name));

CREATE TABLE task (id UUID NOT NULL, type VARCHAR(40) NOT NULL, status VARCHAR(20) NOT NULL, start_date date NOT NULL, due_date date NOT NULL, created_at TIMESTAMP WITH TIME ZONE NOT NULL, completed_at TIMESTAMP WITH TIME ZONE, completed_by VARCHAR(100), CONSTRAINT pk_task PRIMARY KEY (id));

ALTER TABLE task ADD CONSTRAINT uk_task_type_start UNIQUE (type, start_date);

CREATE TABLE review_item (id UUID NOT NULL, task_id UUID NOT NULL, user_id UUID NOT NULL, username VARCHAR(100) NOT NULL, name VARCHAR(100) NOT NULL, review_status VARCHAR(30) NOT NULL, decided_at TIMESTAMP WITH TIME ZONE, decided_by VARCHAR(100), decided_account_status VARCHAR(20), decided_last_login_at TIMESTAMP WITH TIME ZONE, decided_suspended_at TIMESTAMP WITH TIME ZONE, decided_reason_code VARCHAR(40), decided_reason_note VARCHAR(200), CONSTRAINT pk_review_item PRIMARY KEY (id));

ALTER TABLE review_item ADD CONSTRAINT fk_review_item_task FOREIGN KEY (task_id) REFERENCES task (id);

ALTER TABLE review_item ADD CONSTRAINT uk_review_item_task_user UNIQUE (task_id, user_id);

CREATE INDEX ix_review_item_task_status ON review_item(task_id, review_status);

INSERT INTO app_setting (name, setting_value, updated_at, updated_by) VALUES ('inactivity.enabled', 'true', NOW(), 'system');

INSERT INTO app_setting (name, setting_value, updated_at, updated_by) VALUES ('inactivity.suspendAfterDays', '90', NOW(), 'system');

INSERT INTO app_setting (name, setting_value, updated_at, updated_by) VALUES ('inactivity.removeAfterDays', '180', NOW(), 'system');

INSERT INTO app_setting (name, setting_value, updated_at, updated_by) VALUES ('review.enabled', 'true', NOW(), 'system');

INSERT INTO app_setting (name, setting_value, updated_at, updated_by) VALUES ('review.intervalMonths', '3', NOW(), 'system');
