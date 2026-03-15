ALTER TABLE company ADD COLUMN identifier varchar(255) NOT NULL default '';
ALTER TABLE company ADD COLUMN inactive DATE;

UPDATE company SET identifier = SUBSTRING(vat_number FROM 3) WHERE vat_number LIKE 'BE%';

CREATE INDEX idx_company_identifier ON company(identifier);
