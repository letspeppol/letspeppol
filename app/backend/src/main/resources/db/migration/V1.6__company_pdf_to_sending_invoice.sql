ALTER TABLE company ADD COLUMN add_pdf_to_sending_invoice boolean DEFAULT false NOT NULL;

UPDATE company set add_pdf_to_sending_invoice = true WHERE enable_email_notification = true AND email_notification_cc_list IS NOT NULL;
