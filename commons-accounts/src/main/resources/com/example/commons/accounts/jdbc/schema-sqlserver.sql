-- Changeset com/example/commons/accounts/jdbc/schema.yaml::accounts-schema::commons-accounts
CREATE TABLE app_user (id uniqueidentifier NOT NULL, username varchar(100) NOT NULL, name varchar(100) NOT NULL, email varchar(254), status varchar(20) CONSTRAINT DF_app_user_status DEFAULT 'ACTIVE' NOT NULL, suspended_at DATETIMEOFFSET, suspension_reason_code varchar(40), suspension_note varchar(200), inactivity_clock_started_at DATETIMEOFFSET CONSTRAINT DF_app_user_inactivity_clock_started_at DEFAULT SYSDATETIMEOFFSET() NOT NULL, last_login_at DATETIMEOFFSET, created_at DATETIMEOFFSET NOT NULL, updated_at DATETIMEOFFSET NOT NULL, created_by varchar(100) NOT NULL, updated_by varchar(100) NOT NULL, CONSTRAINT pk_app_user PRIMARY KEY (id), CONSTRAINT uk_app_user_username UNIQUE (username));

CREATE TABLE app_group (id uniqueidentifier NOT NULL, name varchar(100) NOT NULL, created_at DATETIMEOFFSET NOT NULL, updated_at DATETIMEOFFSET NOT NULL, created_by varchar(100) NOT NULL, updated_by varchar(100) NOT NULL, CONSTRAINT pk_app_group PRIMARY KEY (id), CONSTRAINT uk_app_group_name UNIQUE (name));

CREATE TABLE app_role (id uniqueidentifier NOT NULL, name varchar(100) NOT NULL, display_name varchar(100) NOT NULL, created_at DATETIMEOFFSET NOT NULL, updated_at DATETIMEOFFSET NOT NULL, created_by varchar(100) NOT NULL, updated_by varchar(100) NOT NULL, CONSTRAINT pk_app_role PRIMARY KEY (id), CONSTRAINT uk_app_role_name UNIQUE (name));

CREATE TABLE app_user_group (user_id uniqueidentifier NOT NULL, group_id uniqueidentifier NOT NULL);

ALTER TABLE app_user_group ADD CONSTRAINT pk_app_user_group PRIMARY KEY (user_id, group_id);

ALTER TABLE app_user_group ADD CONSTRAINT fk_app_user_group_user FOREIGN KEY (user_id) REFERENCES app_user (id);

ALTER TABLE app_user_group ADD CONSTRAINT fk_app_user_group_group FOREIGN KEY (group_id) REFERENCES app_group (id);

CREATE TABLE app_group_role (group_id uniqueidentifier NOT NULL, role_id uniqueidentifier NOT NULL);

ALTER TABLE app_group_role ADD CONSTRAINT pk_app_group_role PRIMARY KEY (group_id, role_id);

ALTER TABLE app_group_role ADD CONSTRAINT fk_app_group_role_group FOREIGN KEY (group_id) REFERENCES app_group (id);

ALTER TABLE app_group_role ADD CONSTRAINT fk_app_group_role_role FOREIGN KEY (role_id) REFERENCES app_role (id);

CREATE NONCLUSTERED INDEX ix_app_user_group_group ON app_user_group(group_id);

CREATE NONCLUSTERED INDEX ix_app_group_role_role ON app_group_role(role_id);

CREATE TABLE user_entities (id varchar(1000) NOT NULL, name varchar(100) NOT NULL, display_name varchar(200), CONSTRAINT pk_user_entities PRIMARY KEY (id), CONSTRAINT uk_user_entities_name UNIQUE (name));

CREATE TABLE user_credentials (credential_id varchar(1000) NOT NULL, user_entity_user_id varchar(1000) NOT NULL, public_key varbinary(MAX) NOT NULL, signature_count bigint, uv_initialized bit, backup_eligible bit NOT NULL, authenticator_transports varchar(1000), public_key_credential_type varchar(100), backup_state bit NOT NULL, attestation_object varbinary(MAX), attestation_client_data_json varbinary(MAX), created datetime2, last_used datetime2, label varchar(1000) NOT NULL, CONSTRAINT pk_user_credentials PRIMARY KEY (credential_id));

ALTER TABLE user_credentials ADD CONSTRAINT fk_user_credentials_user FOREIGN KEY (user_entity_user_id) REFERENCES user_entities (id) ON DELETE CASCADE;

CREATE NONCLUSTERED INDEX ix_user_credentials_user ON user_credentials(user_entity_user_id);

CREATE TABLE account_audit_event (id uniqueidentifier NOT NULL, occurred_at DATETIMEOFFSET NOT NULL, actor varchar(100) NOT NULL, action varchar(50) NOT NULL, target_type varchar(20) NOT NULL, target_id varchar(100), target_name varchar(100), target_full_name varchar(100), reason_code varchar(40), reason_note varchar(200), details varchar(MAX), CONSTRAINT pk_account_audit_event PRIMARY KEY (id));

CREATE NONCLUSTERED INDEX ix_account_audit_event_occurred ON account_audit_event(occurred_at);

CREATE NONCLUSTERED INDEX ix_account_audit_event_actor ON account_audit_event(actor);

CREATE NONCLUSTERED INDEX ix_account_audit_event_target ON account_audit_event(target_type, target_name);

CREATE NONCLUSTERED INDEX ix_account_audit_event_action ON account_audit_event(action);

CREATE TABLE app_setting (name varchar(100) NOT NULL, setting_value varchar(100) NOT NULL, updated_at DATETIMEOFFSET NOT NULL, updated_by varchar(100) NOT NULL, CONSTRAINT pk_app_setting PRIMARY KEY (name));

CREATE TABLE task (id uniqueidentifier NOT NULL, type varchar(40) NOT NULL, status varchar(20) NOT NULL, start_date date NOT NULL, due_date date NOT NULL, created_at DATETIMEOFFSET NOT NULL, completed_at DATETIMEOFFSET, completed_by varchar(100), CONSTRAINT pk_task PRIMARY KEY (id));

ALTER TABLE task ADD CONSTRAINT uk_task_type_start UNIQUE (type, start_date);

CREATE TABLE review_item (id uniqueidentifier NOT NULL, task_id uniqueidentifier NOT NULL, user_id uniqueidentifier NOT NULL, username varchar(100) NOT NULL, name varchar(100) NOT NULL, review_status varchar(30) NOT NULL, decided_at DATETIMEOFFSET, decided_by varchar(100), decided_account_status varchar(20), decided_last_login_at DATETIMEOFFSET, decided_suspended_at DATETIMEOFFSET, decided_reason_code varchar(40), decided_reason_note varchar(200), CONSTRAINT pk_review_item PRIMARY KEY (id));

ALTER TABLE review_item ADD CONSTRAINT fk_review_item_task FOREIGN KEY (task_id) REFERENCES task (id);

ALTER TABLE review_item ADD CONSTRAINT uk_review_item_task_user UNIQUE (task_id, user_id);

CREATE NONCLUSTERED INDEX ix_review_item_task_status ON review_item(task_id, review_status);

INSERT INTO app_setting (name, setting_value, updated_at, updated_by) VALUES ('inactivity.enabled', 'true', SYSDATETIMEOFFSET(), 'system');

INSERT INTO app_setting (name, setting_value, updated_at, updated_by) VALUES ('inactivity.suspendAfterDays', '90', SYSDATETIMEOFFSET(), 'system');

INSERT INTO app_setting (name, setting_value, updated_at, updated_by) VALUES ('inactivity.removeAfterDays', '180', SYSDATETIMEOFFSET(), 'system');

INSERT INTO app_setting (name, setting_value, updated_at, updated_by) VALUES ('review.enabled', 'true', SYSDATETIMEOFFSET(), 'system');

INSERT INTO app_setting (name, setting_value, updated_at, updated_by) VALUES ('review.intervalMonths', '3', SYSDATETIMEOFFSET(), 'system');
