CREATE SCHEMA IF NOT EXISTS proxy;
SET search_path = proxy;

-- Enums
CREATE TYPE document_type AS ENUM ('INVOICE', 'CREDIT_NOTE');

-- UblDocument
ALTER TABLE ubl_document ADD COLUMN type document_type;
