ALTER TABLE company ALTER COLUMN add_pdf_to_sending_invoice SET DEFAULT true;
UPDATE company set add_pdf_to_sending_invoice = true;
