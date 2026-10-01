--liquibase formatted sql

--changeset app:009-account-lifecycle
-- An account is active or suspended, replacing the enabled flag, and records when and why
-- it was suspended. The inactivity clock restarts when an account is created or unsuspended,
-- and every existing account starts it at the migration time so an upgrade gives everyone a
-- full threshold. See docs/adr/0031.
ALTER TABLE app_user ADD COLUMN status VARCHAR(20) DEFAULT 'ACTIVE' NOT NULL;
ALTER TABLE app_user ADD COLUMN suspended_at TIMESTAMP;
ALTER TABLE app_user ADD COLUMN suspension_reason_code VARCHAR(40);
ALTER TABLE app_user ADD COLUMN suspension_note VARCHAR(200);
ALTER TABLE app_user ADD COLUMN inactivity_clock_started_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL;
UPDATE app_user SET status = 'SUSPENDED', suspended_at = CURRENT_TIMESTAMP, suspension_reason_code = 'other' WHERE enabled = FALSE;
ALTER TABLE app_user DROP COLUMN enabled;
