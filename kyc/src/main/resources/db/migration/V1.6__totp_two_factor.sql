SET search_path = kyc;

ALTER TABLE account ADD COLUMN totp_secret TEXT;
ALTER TABLE account ADD COLUMN totp_enabled BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE account ADD COLUMN totp_recovery_codes TEXT;
