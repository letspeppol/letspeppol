CREATE SCHEMA IF NOT EXISTS app;
SET search_path = app;

-- Company
ALTER TABLE company ADD COLUMN last_invoice_reference varchar(255);
