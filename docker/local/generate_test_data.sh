#!/bin/bash

# Configuration
CONTAINER_NAME="local-db-1"
DB_NAME="app"
DB_USER="postgres"

echo "Generating testing partners in database '$DB_NAME'..."

SQL_COMMANDS=$(cat <<EOF
SET search_path = app;

DO \$\$
DECLARE
    cid bigint;
    aid_1 bigint;
    aid_2 bigint;
    aid_3 bigint;
BEGIN
    -- 1. Ensure a company exists to own the partners
    SELECT id INTO cid FROM company LIMIT 1;
    
    IF cid IS NULL THEN
        INSERT INTO address (city, country_code, postal_code, street, created_on) 
        VALUES ('Bruxelles', 'BE', '1000', 'Rue de la Loi 1', now()) RETURNING id INTO aid_1;
        
        INSERT INTO company (name, peppol_id, vat_number, subscriber, subscriber_email, registered_office_id, created_on)
        VALUES ('Test Own Company', '0208:0741965074', 'BE0741965074', 'Developer', 'dev@letspeppol.org', aid_1, now())
        RETURNING id INTO cid;
        
        RAISE NOTICE 'Created owner company with ID %', cid;
    ELSE
        RAISE NOTICE 'Using existing company with ID %', cid;
    END IF;

    -- 2. Insert Partner 1 (Customer)
    INSERT INTO address (city, country_code, postal_code, street, created_on)
    VALUES ('Antwerp', 'BE', '2000', 'Meir 10', now()) RETURNING id INTO aid_1;
    
    INSERT INTO partner (name, vat_number, email, peppol_id, customer, supplier, company_id, registered_office_id, created_on)
    VALUES ('Acme Customer', 'BE0999999999', 'billing@acme.be', '0208:0999999999', true, false, cid, aid_1, now());

    -- 3. Insert Partner 2 (Supplier)
    INSERT INTO address (city, country_code, postal_code, street, created_on)
    VALUES ('Ghent', 'BE', '9000', 'Veldstraat 5', now()) RETURNING id INTO aid_2;

    INSERT INTO partner (name, vat_number, email, peppol_id, customer, supplier, company_id, registered_office_id, created_on)
    VALUES ('Global Supplier', 'BE0888888888', 'sales@globalsupplier.be', '0208:0888888888', false, true, cid, aid_2, now());

    -- 4. Insert Partner 3 (Both)
    INSERT INTO address (city, country_code, postal_code, street, created_on)
    VALUES ('Liege', 'BE', '4000', 'Place Saint-Lambert 1', now()) RETURNING id INTO aid_3;

    INSERT INTO partner (name, vat_number, email, peppol_id, customer, supplier, company_id, registered_office_id, created_on)
    VALUES ('Omni Partner', 'BE0777777777', 'info@omni.be', '0208:0777777777', true, true, cid, aid_3, now());

    RAISE NOTICE 'Inserted 3 testing partners.';

    -- 5. Insert Product Category
    INSERT INTO product_category (name, color, company_id, created_on)
    VALUES ('General Services', '#3B82F6', cid, now()) RETURNING id INTO aid_1;

    -- 6. Insert Products
    INSERT INTO product (name, description, sale_price, tax_percentage, category_id, company_id, created_on)
    VALUES ('Consulting', 'Hourly consulting rate', 120.00, 21.0, aid_1, cid, now());

    INSERT INTO product (name, description, sale_price, tax_percentage, category_id, company_id, created_on)
    VALUES ('Support Plan', 'Monthly support subscription', 50.00, 21.0, aid_1, cid, now());

    RAISE NOTICE 'Inserted 2 testing products.';
END \$\$;
EOF
)

docker exec -i "$CONTAINER_NAME" psql -U "$DB_USER" -d "$DB_NAME" -c "$SQL_COMMANDS"

if [ $? -eq 0 ]; then
    echo "Successfully generated test data for 'app' database!"
else
    echo "Failed to generate test data for 'app' database."
fi

# --- Simulation of Incoming Invoice in Proxy ---
PROXY_DB="proxy"
PROXY_USER="proxyuser"
OWNER_ID="0208:0741965074"
SENDER_ID="0208:0888888888" # Global Supplier
DOC_ID=$(uuidgen | tr '[:upper:]' '[:lower:]')

echo "Generating simulated incoming invoice in database '$PROXY_DB'..."

UBL_CONTENT="<Invoice xmlns=\"urn:oasis:names:specification:ubl:schema:xsd:Invoice-2\" xmlns:cbc=\"urn:oasis:names:specification:ubl:schema:xsd:CommonBasicComponents-2\" xmlns:cac=\"urn:oasis:names:specification:ubl:schema:xsd:CommonAggregateComponents-2\">
    <cbc:CustomizationID>urn:cen.eu:en16931:2017#compliant#urn:fdc:peppol.eu:2017:poacc:billing:3.0</cbc:CustomizationID>
    <cbc:ProfileID>urn:fdc:peppol.eu:2017:poacc:billing:01:1.0</cbc:ProfileID>
    <cbc:ID>INV-RECEIVE-$(date +%s)</cbc:ID>
    <cbc:IssueDate>$(date +%Y-%m-%d)</cbc:IssueDate>
    <cbc:InvoiceTypeCode>380</cbc:InvoiceTypeCode>
    <cbc:DocumentCurrencyCode>EUR</cbc:DocumentCurrencyCode>
    <cac:AccountingSupplierParty>
        <cac:Party>
            <cbc:EndpointID schemeID=\"0208\">0888888888</cbc:EndpointID>
            <cac:PartyName><cbc:Name>Global Supplier</cbc:Name></cac:PartyName>
        </cac:Party>
    </cac:AccountingSupplierParty>
    <cac:AccountingCustomerParty>
        <cac:Party>
            <cbc:EndpointID schemeID=\"0208\">0741965074</cbc:EndpointID>
            <cac:PartyName><cbc:Name>Test Own Company</cbc:Name></cac:PartyName>
        </cac:Party>
    </cac:AccountingCustomerParty>
    <cac:LegalMonetaryTotal>
        <cbc:LineExtensionAmount currencyID=\"EUR\">100.00</cbc:LineExtensionAmount>
        <cbc:TaxExclusiveAmount currencyID=\"EUR\">100.00</cbc:TaxExclusiveAmount>
        <cbc:TaxInclusiveAmount currencyID=\"EUR\">121.00</cbc:TaxInclusiveAmount>
        <cbc:PayableAmount currencyID=\"EUR\">121.00</cbc:PayableAmount>
    </cac:LegalMonetaryTotal>
</Invoice>"

PROXY_SQL="
INSERT INTO proxy.ubl_document (id, direction, type, owner_peppol_id, partner_peppol_id, created_on, ubl, hash, download_count, access_point)
VALUES ('$DOC_ID', 'INCOMING', 'INVOICE', '$OWNER_ID', '$SENDER_ID', NOW(), '$UBL_CONTENT', 'hash-$DOC_ID', 0, 'NONE');
"

docker exec -i "$CONTAINER_NAME" psql -U "$PROXY_USER" -d "$PROXY_DB" -c "$PROXY_SQL"

if [ $? -eq 0 ]; then
    echo "Simulated incoming invoice $DOC_ID created in Proxy DB."
    echo "Wait up to 1 minute for it to appear in the UI (or restart the 'app' container)."
else
    echo "Failed to generate simulated incoming invoice in Proxy DB."
fi
