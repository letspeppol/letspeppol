ALTER TABLE company ADD business_unit varchar(44);
ALTER TABLE company ADD iban varchar(44);
CREATE INDEX IF NOT EXISTS idx_company_business_unit_has_kbo_address ON company(business_unit, has_kbo_address);
