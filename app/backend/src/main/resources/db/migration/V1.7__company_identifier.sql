ALTER TABLE company ADD COLUMN identifier varchar(255) NOT NULL default '';
UPDATE company SET identifier = SUBSTRING(vat_number FROM 3) WHERE vat_number LIKE 'BE%';
CREATE INDEX idx_company_identifier ON company(identifier);

ALTER TABLE partner ADD COLUMN identifier varchar(255) NOT NULL default '';
UPDATE partner SET identifier = SUBSTRING(vat_number FROM 3) WHERE vat_number LIKE 'BE%';
