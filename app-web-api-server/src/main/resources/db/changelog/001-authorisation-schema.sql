--liquibase formatted sql

--changeset app:001-authorisation-schema
CREATE TABLE app_user (
    id CHAR(36) NOT NULL,
    username VARCHAR(100) NOT NULL,
    display_name VARCHAR(100) NOT NULL,
    email VARCHAR(254),
    enabled BOOLEAN NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT pk_app_user PRIMARY KEY (id),
    CONSTRAINT uk_app_user_username UNIQUE (username)
);

CREATE TABLE app_group (
    id CHAR(36) NOT NULL,
    name VARCHAR(100) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT pk_app_group PRIMARY KEY (id),
    CONSTRAINT uk_app_group_name UNIQUE (name)
);

CREATE TABLE app_role (
    id CHAR(36) NOT NULL,
    name VARCHAR(100) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT pk_app_role PRIMARY KEY (id),
    CONSTRAINT uk_app_role_name UNIQUE (name)
);

CREATE TABLE app_user_group (
    user_id CHAR(36) NOT NULL,
    group_id CHAR(36) NOT NULL,
    CONSTRAINT pk_app_user_group PRIMARY KEY (user_id, group_id),
    CONSTRAINT fk_app_user_group_user FOREIGN KEY (user_id) REFERENCES app_user (id),
    CONSTRAINT fk_app_user_group_group FOREIGN KEY (group_id) REFERENCES app_group (id)
);

CREATE TABLE app_group_role (
    group_id CHAR(36) NOT NULL,
    role_id CHAR(36) NOT NULL,
    CONSTRAINT pk_app_group_role PRIMARY KEY (group_id, role_id),
    CONSTRAINT fk_app_group_role_group FOREIGN KEY (group_id) REFERENCES app_group (id),
    CONSTRAINT fk_app_group_role_role FOREIGN KEY (role_id) REFERENCES app_role (id)
);

CREATE INDEX ix_app_user_group_group ON app_user_group (group_id);
CREATE INDEX ix_app_group_role_role ON app_group_role (role_id);
