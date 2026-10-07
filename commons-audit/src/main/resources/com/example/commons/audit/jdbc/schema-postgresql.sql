-- Changeset com/example/commons/audit/jdbc/schema.yaml::audit-schema::commons-audit
CREATE SEQUENCE  IF NOT EXISTS audit_event_seq START WITH 1000 INCREMENT BY 50;

CREATE TABLE audit_event (id BIGINT NOT NULL, public_id UUID NOT NULL, occurred_at TIMESTAMP WITH TIME ZONE NOT NULL, actor VARCHAR(100) NOT NULL, action VARCHAR(50) NOT NULL, outcome VARCHAR(10) NOT NULL, target_type VARCHAR(50) NOT NULL, target_id VARCHAR(100), target_name VARCHAR(100), target_full_name VARCHAR(100), reason_code VARCHAR(40), reason_note VARCHAR(200), details TEXT, CONSTRAINT pk_audit_event PRIMARY KEY (id), CONSTRAINT uk_audit_event_public_id UNIQUE (public_id));

CREATE INDEX ix_audit_event_occurred ON audit_event(occurred_at);

CREATE INDEX ix_audit_event_actor ON audit_event(actor);

CREATE INDEX ix_audit_event_target ON audit_event(target_type, target_name);

CREATE INDEX ix_audit_event_action ON audit_event(action);
