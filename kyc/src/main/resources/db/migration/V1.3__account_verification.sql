ALTER TABLE account RENAME COLUMN identity_verified TO verified;
ALTER TABLE account RENAME COLUMN identity_verified_on TO verified_on;
ALTER TABLE account ALTER COLUMN password_hash DROP NOT NULL;
