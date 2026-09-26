--liquibase formatted sql

--changeset app:005-oidc-session-registry
-- Links an OpenID Provider session to the local session created at login, so a
-- back-channel logout token can be resolved to the local session it ends on any instance.
-- There is no foreign key to SPRING_SESSION: the link is written before Spring Session
-- saves the session row, and JdbcOidcSessionRegistry purges the links of expired sessions.
CREATE TABLE OIDC_SESSION (
    SESSION_ID VARCHAR(64) NOT NULL,
    CREATION_TIME BIGINT NOT NULL,
    ISSUER VARCHAR(255) NOT NULL,
    SUBJECT VARCHAR(255),
    PROVIDER_SESSION_ID VARCHAR(255),
    SESSION_INFORMATION LONGVARBINARY NOT NULL,
    CONSTRAINT OIDC_SESSION_PK PRIMARY KEY (SESSION_ID)
);

CREATE INDEX OIDC_SESSION_IX1 ON OIDC_SESSION (ISSUER, PROVIDER_SESSION_ID);
CREATE INDEX OIDC_SESSION_IX2 ON OIDC_SESSION (ISSUER, SUBJECT);
CREATE INDEX OIDC_SESSION_IX3 ON OIDC_SESSION (CREATION_TIME);
