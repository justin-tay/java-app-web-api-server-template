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

CREATE TABLE app_user (id bigint NOT NULL, public_id uniqueidentifier NOT NULL, username varchar(100) NOT NULL, name varchar(100) NOT NULL, email varchar(254), department varchar(100), status varchar(20) CONSTRAINT DF_app_user_status DEFAULT 'ACTIVE' NOT NULL, suspended_at DATETIMEOFFSET, suspension_reason_code varchar(40), suspension_note varchar(200), inactivity_clock_started_at DATETIMEOFFSET CONSTRAINT DF_app_user_inactivity_clock_started_at DEFAULT SYSDATETIMEOFFSET() NOT NULL, last_login_at DATETIMEOFFSET, created_at DATETIMEOFFSET NOT NULL, updated_at DATETIMEOFFSET NOT NULL, created_by varchar(100) NOT NULL, updated_by varchar(100) NOT NULL, CONSTRAINT pk_app_user PRIMARY KEY (id), CONSTRAINT uk_app_user_public_id UNIQUE (public_id), CONSTRAINT uk_app_user_username UNIQUE (username));

CREATE NONCLUSTERED INDEX ix_app_user_department ON app_user(department);

CREATE TABLE app_group (id bigint NOT NULL, public_id uniqueidentifier NOT NULL, name varchar(100) NOT NULL, created_at DATETIMEOFFSET NOT NULL, updated_at DATETIMEOFFSET NOT NULL, created_by varchar(100) NOT NULL, updated_by varchar(100) NOT NULL, CONSTRAINT pk_app_group PRIMARY KEY (id), CONSTRAINT uk_app_group_public_id UNIQUE (public_id), CONSTRAINT uk_app_group_name UNIQUE (name));

CREATE TABLE app_role (id bigint NOT NULL, public_id uniqueidentifier NOT NULL, name varchar(100) NOT NULL, display_name varchar(100) NOT NULL, created_at DATETIMEOFFSET NOT NULL, updated_at DATETIMEOFFSET NOT NULL, created_by varchar(100) NOT NULL, updated_by varchar(100) NOT NULL, CONSTRAINT pk_app_role PRIMARY KEY (id), CONSTRAINT uk_app_role_public_id UNIQUE (public_id), CONSTRAINT uk_app_role_name UNIQUE (name));

CREATE TABLE app_user_group (user_id bigint NOT NULL, group_id bigint NOT NULL);

ALTER TABLE app_user_group ADD CONSTRAINT pk_app_user_group PRIMARY KEY (user_id, group_id);

ALTER TABLE app_user_group ADD CONSTRAINT fk_app_user_group_user FOREIGN KEY (user_id) REFERENCES app_user (id);

ALTER TABLE app_user_group ADD CONSTRAINT fk_app_user_group_group FOREIGN KEY (group_id) REFERENCES app_group (id);

CREATE TABLE app_group_role (group_id bigint NOT NULL, role_id bigint NOT NULL);

ALTER TABLE app_group_role ADD CONSTRAINT pk_app_group_role PRIMARY KEY (group_id, role_id);

ALTER TABLE app_group_role ADD CONSTRAINT fk_app_group_role_group FOREIGN KEY (group_id) REFERENCES app_group (id);

ALTER TABLE app_group_role ADD CONSTRAINT fk_app_group_role_role FOREIGN KEY (role_id) REFERENCES app_role (id);

CREATE NONCLUSTERED INDEX ix_app_user_group_group ON app_user_group(group_id);

CREATE NONCLUSTERED INDEX ix_app_group_role_role ON app_group_role(role_id);

CREATE TABLE user_entities (id varchar(1000) NOT NULL, name varchar(100) NOT NULL, display_name varchar(200), CONSTRAINT pk_user_entities PRIMARY KEY (id), CONSTRAINT uk_user_entities_name UNIQUE (name));

CREATE TABLE user_credentials (credential_id varchar(1000) NOT NULL, user_entity_user_id varchar(1000) NOT NULL, public_key varbinary(MAX) NOT NULL, signature_count bigint, uv_initialized bit, backup_eligible bit NOT NULL, authenticator_transports varchar(1000), public_key_credential_type varchar(100), backup_state bit NOT NULL, attestation_object varbinary(MAX), attestation_client_data_json varbinary(MAX), created datetime2, last_used datetime2, label varchar(1000) NOT NULL, CONSTRAINT pk_user_credentials PRIMARY KEY (credential_id));

ALTER TABLE user_credentials ADD CONSTRAINT fk_user_credentials_user FOREIGN KEY (user_entity_user_id) REFERENCES user_entities (id) ON DELETE CASCADE;

CREATE NONCLUSTERED INDEX ix_user_credentials_user ON user_credentials(user_entity_user_id);

CREATE TABLE account_audit_event (id bigint NOT NULL, public_id uniqueidentifier NOT NULL, occurred_at DATETIMEOFFSET NOT NULL, actor varchar(100) NOT NULL, action varchar(50) NOT NULL, target_type varchar(20) NOT NULL, target_id varchar(100), target_name varchar(100), target_full_name varchar(100), reason_code varchar(40), reason_note varchar(200), details varchar(MAX), CONSTRAINT pk_account_audit_event PRIMARY KEY (id), CONSTRAINT uk_account_audit_event_public_id UNIQUE (public_id));

CREATE NONCLUSTERED INDEX ix_account_audit_event_occurred ON account_audit_event(occurred_at);

CREATE NONCLUSTERED INDEX ix_account_audit_event_actor ON account_audit_event(actor);

CREATE NONCLUSTERED INDEX ix_account_audit_event_target ON account_audit_event(target_type, target_name);

CREATE NONCLUSTERED INDEX ix_account_audit_event_action ON account_audit_event(action);

CREATE TABLE app_setting (id bigint NOT NULL, name varchar(100) NOT NULL, setting_value varchar(100) NOT NULL, updated_at DATETIMEOFFSET NOT NULL, updated_by varchar(100) NOT NULL, CONSTRAINT pk_app_setting PRIMARY KEY (id), CONSTRAINT uk_app_setting_name UNIQUE (name));

CREATE TABLE task (id bigint NOT NULL, public_id uniqueidentifier NOT NULL, type varchar(40) NOT NULL, status varchar(20) NOT NULL, start_date date NOT NULL, due_date date NOT NULL, created_at DATETIMEOFFSET NOT NULL, completed_at DATETIMEOFFSET, completed_by varchar(100), CONSTRAINT pk_task PRIMARY KEY (id), CONSTRAINT uk_task_public_id UNIQUE (public_id));

ALTER TABLE task ADD CONSTRAINT uk_task_type_start UNIQUE (type, start_date);

CREATE TABLE account_review_item (id bigint NOT NULL, public_id uniqueidentifier NOT NULL, task_id bigint NOT NULL, user_public_id uniqueidentifier NOT NULL, username varchar(100) NOT NULL, full_name varchar(100) NOT NULL, outcome varchar(30) NOT NULL, decided_at DATETIMEOFFSET, decided_by varchar(100), removal_audit_event_id bigint, department varchar(100), last_login_at DATETIMEOFFSET, groups_before varchar(MAX), groups_after varchar(MAX), CONSTRAINT pk_account_review_item PRIMARY KEY (id), CONSTRAINT uk_account_review_item_public_id UNIQUE (public_id));

ALTER TABLE account_review_item ADD CONSTRAINT fk_account_review_item_task FOREIGN KEY (task_id) REFERENCES task (id);

ALTER TABLE account_review_item ADD CONSTRAINT uk_account_review_item_task_user UNIQUE (task_id, user_public_id);

CREATE NONCLUSTERED INDEX ix_account_review_item_task_outcome ON account_review_item(task_id, outcome);

CREATE TABLE account_review_attestation (id bigint NOT NULL, public_id uniqueidentifier NOT NULL, task_id bigint NOT NULL, population varchar(20) NOT NULL, confirmed_by varchar(100) NOT NULL, confirmed_at DATETIMEOFFSET NOT NULL, note varchar(200), entry_count int NOT NULL, CONSTRAINT pk_account_review_attestation PRIMARY KEY (id), CONSTRAINT uk_account_review_attestation_public_id UNIQUE (public_id));

ALTER TABLE account_review_attestation ADD CONSTRAINT fk_account_review_attestation_task FOREIGN KEY (task_id) REFERENCES task (id);

ALTER TABLE account_review_attestation ADD CONSTRAINT uk_account_review_attestation_task_population UNIQUE (task_id, population);

CREATE TABLE account_review_population_entry (id bigint NOT NULL, public_id uniqueidentifier NOT NULL, attestation_id bigint NOT NULL, user_public_id uniqueidentifier NOT NULL, username varchar(100) NOT NULL, full_name varchar(100) NOT NULL, department varchar(100), last_login_at DATETIMEOFFSET, occurred_at DATETIMEOFFSET NOT NULL, actor varchar(100), reason_code varchar(40), reason_note varchar(200), CONSTRAINT pk_account_review_population_entry PRIMARY KEY (id), CONSTRAINT uk_account_review_population_entry_public_id UNIQUE (public_id));

ALTER TABLE account_review_population_entry ADD CONSTRAINT fk_account_review_population_entry_attestation FOREIGN KEY (attestation_id) REFERENCES account_review_attestation (id);

CREATE NONCLUSTERED INDEX ix_account_review_population_entry_attestation ON account_review_population_entry(attestation_id);

CREATE TABLE account_review_report (id bigint NOT NULL, public_id uniqueidentifier NOT NULL, task_id bigint NOT NULL, content varbinary(MAX) NOT NULL, size_bytes bigint NOT NULL, sha256 varchar(64) NOT NULL, generated_at DATETIMEOFFSET NOT NULL, generated_by varchar(100) NOT NULL, CONSTRAINT pk_account_review_report PRIMARY KEY (id), CONSTRAINT uk_account_review_report_public_id UNIQUE (public_id), CONSTRAINT uk_account_review_report_task UNIQUE (task_id));

ALTER TABLE account_review_report ADD CONSTRAINT fk_account_review_report_task FOREIGN KEY (task_id) REFERENCES task (id);

INSERT INTO app_setting (id, name, setting_value, updated_at, updated_by) VALUES (1, 'inactivity.enabled', 'true', SYSDATETIMEOFFSET(), 'system');

INSERT INTO app_setting (id, name, setting_value, updated_at, updated_by) VALUES (2, 'inactivity.suspendAfterDays', '90', SYSDATETIMEOFFSET(), 'system');

INSERT INTO app_setting (id, name, setting_value, updated_at, updated_by) VALUES (3, 'inactivity.removeAfterDays', '180', SYSDATETIMEOFFSET(), 'system');

INSERT INTO app_setting (id, name, setting_value, updated_at, updated_by) VALUES (4, 'review.enabled', 'true', SYSDATETIMEOFFSET(), 'system');

INSERT INTO app_setting (id, name, setting_value, updated_at, updated_by) VALUES (5, 'review.intervalMonths', '3', SYSDATETIMEOFFSET(), 'system');
