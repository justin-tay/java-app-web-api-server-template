--liquibase formatted sql

--changeset app:010-account-audit-and-settings
-- The business audit trail. It has no foreign key to app_user, so an event outlives the
-- account it records. See docs/adr/0030.
CREATE TABLE account_audit_event (
    id CHAR(36) NOT NULL,
    occurred_at TIMESTAMP NOT NULL,
    actor VARCHAR(100) NOT NULL,
    action VARCHAR(50) NOT NULL,
    target_type VARCHAR(20) NOT NULL,
    target_id VARCHAR(100),
    target_name VARCHAR(100),
    target_display_name VARCHAR(100),
    reason_code VARCHAR(40),
    reason_note VARCHAR(200),
    details CLOB,
    CONSTRAINT pk_account_audit_event PRIMARY KEY (id)
);

CREATE INDEX ix_account_audit_event_occurred ON account_audit_event (occurred_at);
CREATE INDEX ix_account_audit_event_actor ON account_audit_event (actor);
CREATE INDEX ix_account_audit_event_target ON account_audit_event (target_type, target_name);
CREATE INDEX ix_account_audit_event_action ON account_audit_event (action);

-- Application settings, edited by a settings administrator.
CREATE TABLE app_setting (
    name VARCHAR(100) NOT NULL,
    setting_value VARCHAR(100) NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    updated_by VARCHAR(100) NOT NULL,
    CONSTRAINT pk_app_setting PRIMARY KEY (name)
);

INSERT INTO app_setting (name, setting_value, updated_at, updated_by) VALUES ('inactivity.enabled', 'true', CURRENT_TIMESTAMP, 'system');
INSERT INTO app_setting (name, setting_value, updated_at, updated_by) VALUES ('inactivity.suspendAfterDays', '90', CURRENT_TIMESTAMP, 'system');
INSERT INTO app_setting (name, setting_value, updated_at, updated_by) VALUES ('inactivity.removeAfterDays', '180', CURRENT_TIMESTAMP, 'system');
INSERT INTO app_setting (name, setting_value, updated_at, updated_by) VALUES ('review.enabled', 'true', CURRENT_TIMESTAMP, 'system');
INSERT INTO app_setting (name, setting_value, updated_at, updated_by) VALUES ('review.intervalMonths', '3', CURRENT_TIMESTAMP, 'system');
