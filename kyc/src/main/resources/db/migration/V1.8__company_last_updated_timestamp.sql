SET search_path = kyc;

ALTER TABLE company
    ADD COLUMN last_updated_timestamp timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP;
