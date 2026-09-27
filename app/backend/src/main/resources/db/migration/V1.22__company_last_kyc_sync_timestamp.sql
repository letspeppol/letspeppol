SET search_path = app;

ALTER TABLE company
    ADD COLUMN last_kyc_sync_timestamp timestamp with time zone;
