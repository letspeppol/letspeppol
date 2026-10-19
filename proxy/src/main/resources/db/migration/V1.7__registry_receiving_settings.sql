SET search_path = proxy;

ALTER TABLE registry ADD COLUMN receive_documents boolean;

-- These providers register sending and receiving together.
UPDATE registry SET receive_documents = true
WHERE access_point IN ('SCRADA', 'E_INVOICE');
UPDATE registry SET receive_documents = false WHERE access_point = 'NONE';
