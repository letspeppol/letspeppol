CREATE TYPE account_type AS ENUM ('ADMIN', 'USER', 'USER_DRAFT', 'USER_READ', 'APP', 'APP_USER', 'ACCOUNTANT');

ALTER TABLE account ADD COLUMN type account_type NOT NULL DEFAULT 'ADMIN';
ALTER TABLE company ALTER COLUMN peppol_id DROP NOT NULL;

CREATE TABLE IF NOT EXISTS account_link (
    account_id        bigint NOT NULL,
    linked_account_id bigint NOT NULL,
    created_on        timestamp with time zone NOT NULL DEFAULT now(),
    PRIMARY KEY (account_id, linked_account_id),
    CONSTRAINT fk_account_link_account
        FOREIGN KEY (account_id) REFERENCES account(id) ON DELETE CASCADE,
    CONSTRAINT fk_account_link_linked
        FOREIGN KEY (linked_account_id) REFERENCES account(id) ON DELETE CASCADE,
    CONSTRAINT chk_account_link_not_self
        CHECK (account_id <> linked_account_id)
);

CREATE INDEX idx_account_link_account_id        ON account_link(account_id);
CREATE INDEX idx_account_link_linked_account_id ON account_link(linked_account_id);
