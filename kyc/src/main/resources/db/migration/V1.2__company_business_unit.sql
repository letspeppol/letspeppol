ALTER TABLE company ADD business_unit bigint;
CREATE INDEX IF NOT EXISTS idx_company_business_unit_vat_number ON company(business_unit, vat_number);
