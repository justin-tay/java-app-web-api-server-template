-- Changeset com/example/commons/audit/jdbc/schema.yaml::audit-schema::commons-audit
CREATE SEQUENCE audit_event_seq START WITH 1000 INCREMENT BY 50;

CREATE TABLE audit_event (id bigint NOT NULL, public_id uniqueidentifier NOT NULL, occurred_at DATETIMEOFFSET NOT NULL, actor varchar(100) NOT NULL, action varchar(50) NOT NULL, outcome varchar(10) NOT NULL, target_type varchar(50) NOT NULL, target_id varchar(100), target_name varchar(100), target_full_name varchar(100), reason_code varchar(40), reason_note varchar(200), details varchar(MAX), CONSTRAINT pk_audit_event PRIMARY KEY (id), CONSTRAINT uk_audit_event_public_id UNIQUE (public_id));

CREATE NONCLUSTERED INDEX ix_audit_event_occurred ON audit_event(occurred_at);

CREATE NONCLUSTERED INDEX ix_audit_event_actor ON audit_event(actor);

CREATE NONCLUSTERED INDEX ix_audit_event_target ON audit_event(target_type, target_name);

CREATE NONCLUSTERED INDEX ix_audit_event_action ON audit_event(action);
