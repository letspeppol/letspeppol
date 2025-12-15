CREATE SCHEMA IF NOT EXISTS app;
SET search_path = app;

-- Company
ALTER TABLE company ADD COLUMN last_invoice_reference varchar(255);

-- Document
ALTER TABLE document
    ALTER COLUMN partner_name DROP NOT NULL,
    ALTER COLUMN invoice_reference DROP NOT NULL;
