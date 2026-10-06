-- Changeset com/example/commons/accounts/jdbc/schema.yaml::accounts-schema::commons-accounts
CREATE SEQUENCE app_user_seq START WITH 1000 INCREMENT BY 50;

CREATE SEQUENCE app_role_seq START WITH 1000 INCREMENT BY 50;

CREATE SEQUENCE app_permission_seq START WITH 1000 INCREMENT BY 50;

CREATE SEQUENCE account_audit_event_seq START WITH 1000 INCREMENT BY 50;

CREATE SEQUENCE app_setting_seq START WITH 1000 INCREMENT BY 50;

CREATE SEQUENCE task_seq START WITH 1000 INCREMENT BY 50;

CREATE SEQUENCE account_review_item_seq START WITH 1000 INCREMENT BY 50;

CREATE SEQUENCE account_review_attestation_seq START WITH 1000 INCREMENT BY 50;

CREATE SEQUENCE account_review_population_entry_seq START WITH 1000 INCREMENT BY 50;

CREATE SEQUENCE account_review_report_seq START WITH 1000 INCREMENT BY 50;

CREATE TABLE app_user (id bigint NOT NULL, public_id uniqueidentifier NOT NULL, username varchar(100) NOT NULL, name varchar(100) NOT NULL, email varchar(254), department varchar(100), status varchar(20) CONSTRAINT DF_app_user_status DEFAULT 'ACTIVE' NOT NULL, suspended_at DATETIMEOFFSET, suspension_reason_code varchar(40), suspension_note varchar(200), inactivity_clock_started_at DATETIMEOFFSET CONSTRAINT DF_app_user_inactivity_clock_started_at DEFAULT SYSDATETIMEOFFSET() NOT NULL, last_login_at DATETIMEOFFSET, created_at DATETIMEOFFSET NOT NULL, updated_at DATETIMEOFFSET NOT NULL, created_by varchar(100) NOT NULL, updated_by varchar(100) NOT NULL, CONSTRAINT pk_app_user PRIMARY KEY (id), CONSTRAINT uk_app_user_public_id UNIQUE (public_id), CONSTRAINT uk_app_user_username UNIQUE (username));

CREATE NONCLUSTERED INDEX ix_app_user_department ON app_user(department);

CREATE TABLE app_role (id bigint NOT NULL, public_id uniqueidentifier NOT NULL, name varchar(100) NOT NULL, created_at DATETIMEOFFSET NOT NULL, updated_at DATETIMEOFFSET NOT NULL, created_by varchar(100) NOT NULL, updated_by varchar(100) NOT NULL, CONSTRAINT pk_app_role PRIMARY KEY (id), CONSTRAINT uk_app_role_public_id UNIQUE (public_id), CONSTRAINT uk_app_role_name UNIQUE (name));

CREATE TABLE app_permission (id bigint NOT NULL, public_id uniqueidentifier NOT NULL, domain varchar(50) NOT NULL, action varchar(50) NOT NULL, privileged bit CONSTRAINT DF_app_permission_privileged DEFAULT 0 NOT NULL, CONSTRAINT pk_app_permission PRIMARY KEY (id), CONSTRAINT uk_app_permission_public_id UNIQUE (public_id));

ALTER TABLE app_permission ADD CONSTRAINT uk_app_permission_domain_action UNIQUE (domain, action);

CREATE TABLE app_user_role (user_id bigint NOT NULL, role_id bigint NOT NULL);

ALTER TABLE app_user_role ADD CONSTRAINT pk_app_user_role PRIMARY KEY (user_id, role_id);

ALTER TABLE app_user_role ADD CONSTRAINT fk_app_user_role_user FOREIGN KEY (user_id) REFERENCES app_user (id);

ALTER TABLE app_user_role ADD CONSTRAINT fk_app_user_role_role FOREIGN KEY (role_id) REFERENCES app_role (id);

CREATE TABLE app_role_permission (role_id bigint NOT NULL, permission_id bigint NOT NULL);

ALTER TABLE app_role_permission ADD CONSTRAINT pk_app_role_permission PRIMARY KEY (role_id, permission_id);

ALTER TABLE app_role_permission ADD CONSTRAINT fk_app_role_permission_role FOREIGN KEY (role_id) REFERENCES app_role (id);

ALTER TABLE app_role_permission ADD CONSTRAINT fk_app_role_permission_permission FOREIGN KEY (permission_id) REFERENCES app_permission (id);

CREATE NONCLUSTERED INDEX ix_app_user_role_role ON app_user_role(role_id);

CREATE NONCLUSTERED INDEX ix_app_role_permission_permission ON app_role_permission(permission_id);

CREATE TABLE app_permission_conflict (permission_id bigint NOT NULL, conflicting_permission_id bigint NOT NULL);

ALTER TABLE app_permission_conflict ADD CONSTRAINT pk_app_permission_conflict PRIMARY KEY (permission_id, conflicting_permission_id);

ALTER TABLE app_permission_conflict ADD CONSTRAINT fk_app_permission_conflict_permission FOREIGN KEY (permission_id) REFERENCES app_permission (id);

ALTER TABLE app_permission_conflict ADD CONSTRAINT fk_app_permission_conflict_other FOREIGN KEY (conflicting_permission_id) REFERENCES app_permission (id);

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

CREATE TABLE account_review_item (id bigint NOT NULL, public_id uniqueidentifier NOT NULL, task_id bigint NOT NULL, user_public_id uniqueidentifier NOT NULL, username varchar(100) NOT NULL, full_name varchar(100) NOT NULL, outcome varchar(30) NOT NULL, decided_at DATETIMEOFFSET, decided_by varchar(100), removal_audit_event_id bigint, department varchar(100), last_login_at DATETIMEOFFSET, roles_before varchar(MAX), roles_after varchar(MAX), privileged_permissions varchar(MAX), CONSTRAINT pk_account_review_item PRIMARY KEY (id), CONSTRAINT uk_account_review_item_public_id UNIQUE (public_id));

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

INSERT INTO app_setting (id, name, setting_value, updated_at, updated_by) VALUES (5, 'review.privilegedIntervalMonths', '1', SYSDATETIMEOFFSET(), 'system');

INSERT INTO app_setting (id, name, setting_value, updated_at, updated_by) VALUES (6, 'review.nonPrivilegedIntervalMonths', '12', SYSDATETIMEOFFSET(), 'system');

INSERT INTO app_permission (id, public_id, domain, action, privileged) VALUES (1, '00000000-0000-0000-0100-000000000001', 'application', 'access', 0);

INSERT INTO app_permission (id, public_id, domain, action, privileged) VALUES (2, '00000000-0000-0000-0100-000000000002', 'user', 'read', 0);

INSERT INTO app_permission (id, public_id, domain, action, privileged) VALUES (3, '00000000-0000-0000-0100-000000000003', 'user', 'create', 1);

INSERT INTO app_permission (id, public_id, domain, action, privileged) VALUES (4, '00000000-0000-0000-0100-000000000004', 'user', 'update', 0);

INSERT INTO app_permission (id, public_id, domain, action, privileged) VALUES (5, '00000000-0000-0000-0100-000000000005', 'user', 'add-role', 1);

INSERT INTO app_permission (id, public_id, domain, action, privileged) VALUES (6, '00000000-0000-0000-0100-000000000006', 'user', 'remove-role', 0);

INSERT INTO app_permission (id, public_id, domain, action, privileged) VALUES (7, '00000000-0000-0000-0100-000000000007', 'user', 'suspend', 0);

INSERT INTO app_permission (id, public_id, domain, action, privileged) VALUES (8, '00000000-0000-0000-0100-000000000008', 'user', 'unsuspend', 1);

INSERT INTO app_permission (id, public_id, domain, action, privileged) VALUES (9, '00000000-0000-0000-0100-000000000009', 'user', 'remove', 0);

INSERT INTO app_permission (id, public_id, domain, action, privileged) VALUES (10, '00000000-0000-0000-0100-00000000000a', 'user', 'revoke-session', 0);

INSERT INTO app_permission (id, public_id, domain, action, privileged) VALUES (11, '00000000-0000-0000-0100-00000000000b', 'user', 'remove-passkey', 0);

INSERT INTO app_permission (id, public_id, domain, action, privileged) VALUES (12, '00000000-0000-0000-0100-00000000000c', 'role', 'read', 0);

INSERT INTO app_permission (id, public_id, domain, action, privileged) VALUES (13, '00000000-0000-0000-0100-00000000000d', 'role', 'create', 0);

INSERT INTO app_permission (id, public_id, domain, action, privileged) VALUES (14, '00000000-0000-0000-0100-00000000000e', 'role', 'update', 0);

INSERT INTO app_permission (id, public_id, domain, action, privileged) VALUES (15, '00000000-0000-0000-0100-00000000000f', 'role', 'delete', 0);

INSERT INTO app_permission (id, public_id, domain, action, privileged) VALUES (16, '00000000-0000-0000-0100-000000000010', 'role', 'add-permission', 1);

INSERT INTO app_permission (id, public_id, domain, action, privileged) VALUES (17, '00000000-0000-0000-0100-000000000011', 'role', 'remove-permission', 0);

INSERT INTO app_permission (id, public_id, domain, action, privileged) VALUES (18, '00000000-0000-0000-0100-000000000012', 'permission', 'read', 0);

INSERT INTO app_permission (id, public_id, domain, action, privileged) VALUES (19, '00000000-0000-0000-0100-000000000013', 'settings', 'read', 0);

INSERT INTO app_permission (id, public_id, domain, action, privileged) VALUES (20, '00000000-0000-0000-0100-000000000014', 'settings', 'update', 1);

INSERT INTO app_permission (id, public_id, domain, action, privileged) VALUES (21, '00000000-0000-0000-0100-000000000015', 'audit', 'read', 0);

INSERT INTO app_permission (id, public_id, domain, action, privileged) VALUES (22, '00000000-0000-0000-0100-000000000016', 'review', 'read', 0);

INSERT INTO app_permission (id, public_id, domain, action, privileged) VALUES (23, '00000000-0000-0000-0100-000000000017', 'review', 'decide', 0);

INSERT INTO app_permission (id, public_id, domain, action, privileged) VALUES (24, '00000000-0000-0000-0100-000000000018', 'review', 'confirm-population', 0);

INSERT INTO app_permission (id, public_id, domain, action, privileged) VALUES (25, '00000000-0000-0000-0100-000000000019', 'review', 'download-report', 0);

INSERT INTO app_permission_conflict (permission_id, conflicting_permission_id) VALUES (23, 3);

INSERT INTO app_permission_conflict (permission_id, conflicting_permission_id) VALUES (23, 5);

INSERT INTO app_permission_conflict (permission_id, conflicting_permission_id) VALUES (23, 8);

INSERT INTO app_permission_conflict (permission_id, conflicting_permission_id) VALUES (23, 16);

INSERT INTO app_permission_conflict (permission_id, conflicting_permission_id) VALUES (23, 20);
