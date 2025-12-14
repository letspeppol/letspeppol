CREATE SCHEMA IF NOT EXISTS app;
SET search_path = app;

-- Company
ALTER TABLE company
    ADD COLUMN no_archive boolean DEFAULT false,
    ADD COLUMN last_document_sync_at timestamp with time zone;
