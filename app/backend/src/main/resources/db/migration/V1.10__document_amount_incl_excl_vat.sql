ALTER TABLE document RENAME COLUMN amount TO amount_excl_vat;
ALTER TABLE document ADD COLUMN amount_incl_vat numeric;
