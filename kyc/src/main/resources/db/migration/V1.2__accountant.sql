ALTER TABLE email_verification ADD COLUMN requester_account_id bigint;
ALTER TABLE email_verification ADD COLUMN type account_type NOT NULL DEFAULT 'ADMIN';
