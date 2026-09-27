--liquibase formatted sql

--changeset app:006-passkey-schema
-- Storage for Spring Security's JdbcPublicKeyCredentialUserEntityRepository and
-- JdbcUserCredentialRepository, with the table and column names they expect. The user
-- entity id is the base64url form of the app_user id (its 16 UUID bytes), which is the
-- WebAuthn user handle, so there is no foreign key to app_user: deleting a user must also
-- delete their user_entities row, which then cascades to their credentials.
CREATE TABLE user_entities (
    id VARCHAR(1000) NOT NULL,
    name VARCHAR(100) NOT NULL,
    display_name VARCHAR(200),
    CONSTRAINT pk_user_entities PRIMARY KEY (id),
    CONSTRAINT uk_user_entities_name UNIQUE (name)
);

CREATE TABLE user_credentials (
    credential_id VARCHAR(1000) NOT NULL,
    user_entity_user_id VARCHAR(1000) NOT NULL,
    public_key LONGVARBINARY NOT NULL,
    signature_count BIGINT,
    uv_initialized BOOLEAN,
    backup_eligible BOOLEAN NOT NULL,
    authenticator_transports VARCHAR(1000),
    public_key_credential_type VARCHAR(100),
    backup_state BOOLEAN NOT NULL,
    attestation_object LONGVARBINARY,
    attestation_client_data_json LONGVARBINARY,
    created TIMESTAMP,
    last_used TIMESTAMP,
    label VARCHAR(1000) NOT NULL,
    CONSTRAINT pk_user_credentials PRIMARY KEY (credential_id),
    CONSTRAINT fk_user_credentials_user FOREIGN KEY (user_entity_user_id) REFERENCES user_entities (id) ON DELETE CASCADE
);

CREATE INDEX ix_user_credentials_user ON user_credentials (user_entity_user_id);
