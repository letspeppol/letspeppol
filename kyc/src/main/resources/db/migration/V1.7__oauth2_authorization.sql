-- Spring Authorization Server JDBC schema
-- https://docs.spring.io/spring-authorization-server/reference/guides/how-to-jpa.html

CREATE TABLE oauth2_authorization (
    id                            VARCHAR(100)  NOT NULL,
    registered_client_id          VARCHAR(100)  NOT NULL,
    principal_name                VARCHAR(200)  NOT NULL,
    authorization_grant_type      VARCHAR(100)  NOT NULL,
    authorized_scopes             VARCHAR(1000)  DEFAULT NULL,
    attributes                    TEXT           DEFAULT NULL,
    state                         VARCHAR(500)   DEFAULT NULL,
    authorization_code_value      TEXT           DEFAULT NULL,
    authorization_code_issued_at  TIMESTAMP      DEFAULT NULL,
    authorization_code_expires_at TIMESTAMP      DEFAULT NULL,
    authorization_code_metadata   TEXT           DEFAULT NULL,
    access_token_value            TEXT           DEFAULT NULL,
    access_token_issued_at        TIMESTAMP      DEFAULT NULL,
    access_token_expires_at       TIMESTAMP      DEFAULT NULL,
    access_token_metadata         TEXT           DEFAULT NULL,
    access_token_type             VARCHAR(100)   DEFAULT NULL,
    access_token_scopes           VARCHAR(1000)  DEFAULT NULL,
    oidc_id_token_value           TEXT           DEFAULT NULL,
    oidc_id_token_issued_at       TIMESTAMP      DEFAULT NULL,
    oidc_id_token_expires_at      TIMESTAMP      DEFAULT NULL,
    oidc_id_token_metadata        TEXT           DEFAULT NULL,
    refresh_token_value           TEXT           DEFAULT NULL,
    refresh_token_issued_at       TIMESTAMP      DEFAULT NULL,
    refresh_token_expires_at      TIMESTAMP      DEFAULT NULL,
    refresh_token_metadata        TEXT           DEFAULT NULL,
    user_code_value               TEXT           DEFAULT NULL,
    user_code_issued_at           TIMESTAMP      DEFAULT NULL,
    user_code_expires_at          TIMESTAMP      DEFAULT NULL,
    user_code_metadata            TEXT           DEFAULT NULL,
    device_code_value             TEXT           DEFAULT NULL,
    device_code_issued_at         TIMESTAMP      DEFAULT NULL,
    device_code_expires_at        TIMESTAMP      DEFAULT NULL,
    device_code_metadata          TEXT           DEFAULT NULL,
    PRIMARY KEY (id)
);

CREATE TABLE oauth2_authorization_consent (
    registered_client_id VARCHAR(100)  NOT NULL,
    principal_name       VARCHAR(200)  NOT NULL,
    authorities          VARCHAR(1000) NOT NULL,
    PRIMARY KEY (registered_client_id, principal_name)
);
