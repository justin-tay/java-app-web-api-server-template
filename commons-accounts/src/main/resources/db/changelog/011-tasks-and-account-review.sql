--liquibase formatted sql

--changeset app:011-tasks-and-account-review
-- A generic task, with the periodic account review as its first type. A review item has no
-- foreign key to app_user, so it outlives a removed account. See docs/adr/0032.
CREATE TABLE task (
    id CHAR(36) NOT NULL,
    type VARCHAR(40) NOT NULL,
    status VARCHAR(20) NOT NULL,
    start_date DATE NOT NULL,
    due_date DATE NOT NULL,
    created_at TIMESTAMP NOT NULL,
    completed_at TIMESTAMP,
    completed_by VARCHAR(100),
    CONSTRAINT pk_task PRIMARY KEY (id),
    CONSTRAINT uk_task_type_start UNIQUE (type, start_date)
);

CREATE TABLE review_item (
    id CHAR(36) NOT NULL,
    task_id CHAR(36) NOT NULL,
    user_id CHAR(36) NOT NULL,
    username VARCHAR(100) NOT NULL,
    name VARCHAR(100) NOT NULL,
    review_status VARCHAR(30) NOT NULL,
    decided_at TIMESTAMP,
    decided_by VARCHAR(100),
    decided_account_status VARCHAR(20),
    decided_last_login_at TIMESTAMP,
    decided_suspended_at TIMESTAMP,
    decided_reason_code VARCHAR(40),
    decided_reason_note VARCHAR(200),
    CONSTRAINT pk_review_item PRIMARY KEY (id),
    CONSTRAINT fk_review_item_task FOREIGN KEY (task_id) REFERENCES task (id),
    CONSTRAINT uk_review_item_task_user UNIQUE (task_id, user_id)
);

CREATE INDEX ix_review_item_task_status ON review_item (task_id, review_status);
