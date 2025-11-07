async function genDoc(
  docType: string,
  senderId: string,
  receiverId: string,
  docId: string,
): Promise<string> {
  const [senderScheme, senderSubId] = senderId.split(':');
  const [receiverScheme, receiverSubId] = receiverId.split(':');
  if (docType === 'invoice') {
    return `<?xml version="1.0" encoding="UTF-8"?>
<Invoice xmlns="urn:oasis:names:specification:ubl:schema:xsd:Invoice-2" xmlns:cac="urn:oasis:names:specification:ubl:schema:xsd:CommonAggregateComponents-2" xmlns:cbc="urn:oasis:names:specification:ubl:schema:xsd:CommonBasicComponents-2">
  <cbc:CustomizationID>urn:cen.eu:en16931:2017#compliant#urn:fdc:peppol.eu:2017:poacc:billing:3.0</cbc:CustomizationID>
  <cbc:ProfileID>urn:fdc:peppol.eu:2017:poacc:billing:01:1.0</cbc:ProfileID>
  <cbc:ID>${docId}</cbc:ID>
  <cbc:IssueDate>2023-12-19</cbc:IssueDate>
  <cbc:DueDate>2024-01-18</cbc:DueDate>
  <cbc:InvoiceTypeCode>380</cbc:InvoiceTypeCode>
  <cbc:Note>GalaxyGateway hosted AP BIS3 Billing Test file</cbc:Note>
  <cbc:TaxPointDate>2023-12-19</cbc:TaxPointDate>
  <cbc:DocumentCurrencyCode>EUR</cbc:DocumentCurrencyCode>
  <cbc:TaxCurrencyCode>SEK</cbc:TaxCurrencyCode>
  <cbc:AccountingCost>4025:123:4343</cbc:AccountingCost>
  <cbc:BuyerReference>0150abc</cbc:BuyerReference>
  <cac:InvoicePeriod>
      <cbc:StartDate>2023-11-01</cbc:StartDate>
      <cbc:EndDate>2032-12-31</cbc:EndDate>
  </cac:InvoicePeriod>
  <cac:ContractDocumentReference>
      <cbc:ID>framework no 1</cbc:ID>
  </cac:ContractDocumentReference>
  <cac:AdditionalDocumentReference>
      <cbc:ID schemeID="ABT">DR35141</cbc:ID>
      <cbc:DocumentTypeCode>130</cbc:DocumentTypeCode>
  </cac:AdditionalDocumentReference>
  <cac:AdditionalDocumentReference>
      <cbc:ID>ts12345</cbc:ID>
      <cbc:DocumentDescription>Technical specification</cbc:DocumentDescription>
      <cac:Attachment>
          <cac:ExternalReference>
              <cbc:URI>www.techspec.no</cbc:URI>
          </cac:ExternalReference>
      </cac:Attachment>
  </cac:AdditionalDocumentReference>
  <cac:AccountingSupplierParty>
      <cac:Party>
          <cbc:EndpointID schemeID="${senderScheme}">${senderSubId}</cbc:EndpointID>
          <cac:PartyIdentification>
              <cbc:ID schemeID="${senderScheme}">${senderSubId}</cbc:ID>
          </cac:PartyIdentification>
          <cac:PartyName>
              <cbc:Name>Softwareoplossing.be</cbc:Name>
          </cac:PartyName>
          <cac:PostalAddress>
              <cbc:StreetName>Koning Filipstraat 5</cbc:StreetName>
              <cbc:CityName> Riemst</cbc:CityName>
              <cbc:PostalZone>3770</cbc:PostalZone>
              <cac:Country>
              <cbc:IdentificationCode>BE</cbc:IdentificationCode>
              </cac:Country>
          </cac:PostalAddress>
          <cac:PartyTaxScheme>
              <cbc:CompanyID>SE4598375937</cbc:CompanyID>
              <cac:TaxScheme>
                  <cbc:ID>VAT</cbc:ID>
              </cac:TaxScheme>
          </cac:PartyTaxScheme>
          <cac:PartyLegalEntity>
              <cbc:RegistrationName>Buyer Official Name</cbc:RegistrationName>
              <cbc:CompanyID schemeID="0183">39937423947</cbc:CompanyID>
          </cac:PartyLegalEntity>
          <cac:Contact>
              <cbc:Name>Lisa Johnson</cbc:Name>
              <cbc:Telephone>23434234</cbc:Telephone>
              <cbc:ElectronicMail>lj@buyer.se</cbc:ElectronicMail>
          </cac:Contact>
      </cac:Party>
  </cac:AccountingSupplierParty>

  <cac:AccountingCustomerParty>
      <cac:Party>
          <cbc:EndpointID schemeID="${receiverScheme}">${receiverSubId}</cbc:EndpointID>
          <cac:PartyIdentification>
              <cbc:ID schemeID="${receiverScheme}">${receiverSubId}</cbc:ID>
          </cac:PartyIdentification>
          <cac:PartyName>
              <cbc:Name>Ponder Source Three</cbc:Name>
          </cac:PartyName>
          <cac:PostalAddress>
              <cbc:StreetName>Torenlaan 5A</cbc:StreetName>
              <cbc:CityName>Bussum</cbc:CityName>
              <cbc:PostalZone>1402 AT</cbc:PostalZone>
              <cac:Country>
              <cbc:IdentificationCode>NL</cbc:IdentificationCode>
              </cac:Country>
          </cac:PostalAddress>
          <cac:PartyTaxScheme>
              <cbc:CompanyID>GB1232434</cbc:CompanyID>
              <cac:TaxScheme>
                  <cbc:ID>VAT</cbc:ID>
              </cac:TaxScheme>
          </cac:PartyTaxScheme>
          <cac:PartyLegalEntity>
              <cbc:RegistrationName>Buyer Official Name</cbc:RegistrationName>
              <cbc:CompanyID schemeID="0106">82875715</cbc:CompanyID>
          </cac:PartyLegalEntity>
          <cac:Contact>
              <cbc:Name>Lisa Johnson</cbc:Name>
              <cbc:Telephone>23434234</cbc:Telephone>
              <cbc:ElectronicMail>lj@buyer.se</cbc:ElectronicMail>
          </cac:Contact>
      </cac:Party>
  </cac:AccountingCustomerParty>
  <cac:Delivery>
      <cbc:ActualDeliveryDate>2023-12-01</cbc:ActualDeliveryDate>
      <cac:DeliveryLocation>
          <cbc:ID schemeID="0088">7300010000001</cbc:ID>
          <cac:Address>
              <cbc:StreetName>Delivery street 2</cbc:StreetName>
              <cbc:AdditionalStreetName>Building 56</cbc:AdditionalStreetName>
              <cbc:CityName>Stockholm</cbc:CityName>
              <cbc:PostalZone>21234</cbc:PostalZone>
              <cbc:CountrySubentity>Södermalm</cbc:CountrySubentity>
              <cac:AddressLine>
                  <cbc:Line>Gate 15</cbc:Line>
              </cac:AddressLine>
              <cac:Country>
                  <cbc:IdentificationCode>SE</cbc:IdentificationCode>
              </cac:Country>
          </cac:Address>
      </cac:DeliveryLocation>
      <cac:DeliveryParty>
          <cac:PartyName>
              <cbc:Name>Delivery party Name</cbc:Name>
          </cac:PartyName>
      </cac:DeliveryParty>
  </cac:Delivery>
  <cac:PaymentMeans>
      <cbc:PaymentMeansCode name="Credit transfer">30</cbc:PaymentMeansCode>
      <cbc:PaymentID>Snippet1</cbc:PaymentID>
      <cac:PayeeFinancialAccount>
          <cbc:ID>IBAN32423940</cbc:ID>
          <cbc:Name>AccountName</cbc:Name>
          <cac:FinancialInstitutionBranch>
              <cbc:ID>BIC324098</cbc:ID>
          </cac:FinancialInstitutionBranch>
      </cac:PayeeFinancialAccount>
  </cac:PaymentMeans>
  <cac:PaymentTerms>
      <cbc:Note>Payment within 10 days, 2% discount</cbc:Note>
  </cac:PaymentTerms>

  <cac:AllowanceCharge>
      <cbc:ChargeIndicator>true</cbc:ChargeIndicator>
      <cbc:AllowanceChargeReasonCode>CG</cbc:AllowanceChargeReasonCode>
      <cbc:AllowanceChargeReason>Cleaning</cbc:AllowanceChargeReason>
      <cbc:MultiplierFactorNumeric>20</cbc:MultiplierFactorNumeric>
      <cbc:Amount currencyID="EUR">200</cbc:Amount>
      <cbc:BaseAmount currencyID="EUR">1000</cbc:BaseAmount>
      <cac:TaxCategory>
          <cbc:ID>S</cbc:ID>
          <cbc:Percent>25</cbc:Percent>
          <cac:TaxScheme>
              <cbc:ID>VAT</cbc:ID>
          </cac:TaxScheme>
      </cac:TaxCategory>
  </cac:AllowanceCharge>

  <cac:AllowanceCharge>
      <cbc:ChargeIndicator>false</cbc:ChargeIndicator>
      <cbc:AllowanceChargeReasonCode>95</cbc:AllowanceChargeReasonCode>
      <cbc:AllowanceChargeReason>Discount</cbc:AllowanceChargeReason>
      <cbc:Amount currencyID="EUR">200</cbc:Amount>
      <cac:TaxCategory>
          <cbc:ID>S</cbc:ID>
          <cbc:Percent>25</cbc:Percent>
          <cac:TaxScheme>
              <cbc:ID>VAT</cbc:ID>
          </cac:TaxScheme>
      </cac:TaxCategory>
  </cac:AllowanceCharge>

  <cac:TaxTotal>
      <cbc:TaxAmount currencyID="EUR">1225.00</cbc:TaxAmount>
      <cac:TaxSubtotal>
          <cbc:TaxableAmount currencyID="EUR">4900.0</cbc:TaxableAmount>
          <cbc:TaxAmount currencyID="EUR">1225</cbc:TaxAmount>
          <cac:TaxCategory>
              <cbc:ID>S</cbc:ID>
              <cbc:Percent>25</cbc:Percent>
              <cac:TaxScheme>
                  <cbc:ID>VAT</cbc:ID>
              </cac:TaxScheme>
          </cac:TaxCategory>
      </cac:TaxSubtotal>
      <cac:TaxSubtotal>
          <cbc:TaxableAmount currencyID="EUR">1000.0</cbc:TaxableAmount>
          <cbc:TaxAmount currencyID="EUR">0</cbc:TaxAmount>
          <cac:TaxCategory>
              <cbc:ID>E</cbc:ID>
              <cbc:Percent>0</cbc:Percent>
              <cbc:TaxExemptionReason>Reason for tax exempt</cbc:TaxExemptionReason>
              <cac:TaxScheme>
                  <cbc:ID>VAT</cbc:ID>
              </cac:TaxScheme>
          </cac:TaxCategory>
      </cac:TaxSubtotal>
  </cac:TaxTotal>
  <cac:TaxTotal>
      <cbc:TaxAmount currencyID="SEK">9324.00</cbc:TaxAmount>
  </cac:TaxTotal>
  <cac:LegalMonetaryTotal>
      <cbc:LineExtensionAmount currencyID="EUR">5900</cbc:LineExtensionAmount>
      <cbc:TaxExclusiveAmount currencyID="EUR">5900</cbc:TaxExclusiveAmount>
      <cbc:TaxInclusiveAmount currencyID="EUR">7125</cbc:TaxInclusiveAmount>
      <cbc:AllowanceTotalAmount currencyID="EUR">200</cbc:AllowanceTotalAmount>
      <cbc:ChargeTotalAmount currencyID="EUR">200</cbc:ChargeTotalAmount>
      <cbc:PrepaidAmount currencyID="EUR">1000</cbc:PrepaidAmount>
      <cbc:PayableAmount currencyID="EUR">6125.00</cbc:PayableAmount>
  </cac:LegalMonetaryTotal>
  <cac:InvoiceLine>
  <cbc:ID>1</cbc:ID>
      <cbc:Note>Testing note on line level</cbc:Note>
      <cbc:InvoicedQuantity unitCode="C62">10</cbc:InvoicedQuantity>
      <cbc:LineExtensionAmount currencyID="EUR">4000.00</cbc:LineExtensionAmount>
      <cbc:AccountingCost>Konteringsstreng</cbc:AccountingCost>
      <cac:AllowanceCharge>
          <cbc:ChargeIndicator>true</cbc:ChargeIndicator>
          <cbc:AllowanceChargeReasonCode>CG</cbc:AllowanceChargeReasonCode>
          <cbc:AllowanceChargeReason>Cleaning</cbc:AllowanceChargeReason>
          <cbc:MultiplierFactorNumeric>1</cbc:MultiplierFactorNumeric>
          <cbc:Amount currencyID="EUR">1</cbc:Amount>
          <cbc:BaseAmount currencyID="EUR">100</cbc:BaseAmount>
      </cac:AllowanceCharge>
      <cac:AllowanceCharge>
          <cbc:ChargeIndicator>false</cbc:ChargeIndicator>
          <cbc:AllowanceChargeReasonCode>95</cbc:AllowanceChargeReasonCode>
          <cbc:AllowanceChargeReason>Discount</cbc:AllowanceChargeReason>
          <cbc:Amount currencyID="EUR">101</cbc:Amount>
      </cac:AllowanceCharge>
      <cac:Item>
          <cbc:Description>Description of item</cbc:Description>
          <cbc:Name>item name</cbc:Name>
      
          <cac:SellersItemIdentification>
              <cbc:ID>97iugug876</cbc:ID>
          </cac:SellersItemIdentification>
          <cac:OriginCountry>
              <cbc:IdentificationCode>NO</cbc:IdentificationCode>
          </cac:OriginCountry>
          <cac:CommodityClassification>
              <cbc:ItemClassificationCode listID="SRV">09348023</cbc:ItemClassificationCode>
          </cac:CommodityClassification>
          <cac:ClassifiedTaxCategory>
              <cbc:ID>S</cbc:ID>
              <cbc:Percent>25.0</cbc:Percent>
              <cac:TaxScheme>
                  <cbc:ID>VAT</cbc:ID>
              </cac:TaxScheme>
          </cac:ClassifiedTaxCategory>
    
      </cac:Item>

      <cac:Price>
          <cbc:PriceAmount currencyID="EUR">410</cbc:PriceAmount>
          <cbc:BaseQuantity unitCode="C62">1</cbc:BaseQuantity>
          <cac:AllowanceCharge>
              <cbc:ChargeIndicator>false</cbc:ChargeIndicator>
              <cbc:Amount currencyID="EUR">40</cbc:Amount>
              <cbc:BaseAmount currencyID="EUR">450</cbc:BaseAmount>
          </cac:AllowanceCharge>
      </cac:Price>

  </cac:InvoiceLine>
  <cac:InvoiceLine>
      <cbc:ID>2</cbc:ID>
      <cbc:Note>Testing note on line level</cbc:Note>

      <cbc:InvoicedQuantity unitCode="C62">10</cbc:InvoicedQuantity>
      <cbc:LineExtensionAmount currencyID="EUR">1000.00</cbc:LineExtensionAmount>

      <cbc:AccountingCost>AccountString</cbc:AccountingCost>
      <cac:InvoicePeriod>
          <cbc:StartDate>2023-12-01</cbc:StartDate>
          <cbc:EndDate>2032-12-05</cbc:EndDate>
      </cac:InvoicePeriod>

      <cac:Item>
          <cbc:Description>Description of item</cbc:Description>
          <cbc:Name>item name</cbc:Name>
          <cac:SellersItemIdentification>
              <cbc:ID>97iugug876</cbc:ID>
          </cac:SellersItemIdentification>
          <cac:CommodityClassification>
              <cbc:ItemClassificationCode listID="SRV">86776</cbc:ItemClassificationCode>
          </cac:CommodityClassification>
          <cac:ClassifiedTaxCategory>
              <cbc:ID>E</cbc:ID>
              <cbc:Percent>0.0</cbc:Percent>
              <cac:TaxScheme>
                  <cbc:ID>VAT</cbc:ID>
              </cac:TaxScheme>
          </cac:ClassifiedTaxCategory>
          <cac:AdditionalItemProperty>
              <cbc:Name>AdditionalItemName</cbc:Name>
              <cbc:Value>AdditionalItemValue</cbc:Value>
          </cac:AdditionalItemProperty>
      </cac:Item>
      <cac:Price>
          <cbc:PriceAmount currencyID="EUR">200</cbc:PriceAmount>
          <cbc:BaseQuantity unitCode="C62">2</cbc:BaseQuantity>
      </cac:Price>
  </cac:InvoiceLine>
  <cac:InvoiceLine>
      <cbc:ID>3</cbc:ID>
      <cbc:Note>Testing note on line level</cbc:Note>
      <cbc:InvoicedQuantity unitCode="C62">10</cbc:InvoicedQuantity>
      <cbc:LineExtensionAmount currencyID="EUR">900.00</cbc:LineExtensionAmount>
      <cbc:AccountingCost>Konteringsstreng</cbc:AccountingCost>
      <cac:InvoicePeriod>
          <cbc:StartDate>2023-12-02</cbc:StartDate>
          <cbc:EndDate>2032-12-04</cbc:EndDate>
      </cac:InvoicePeriod>

      <cac:AllowanceCharge>
          <cbc:ChargeIndicator>true</cbc:ChargeIndicator>
          <cbc:AllowanceChargeReasonCode>CG</cbc:AllowanceChargeReasonCode>
          <cbc:AllowanceChargeReason>Charge</cbc:AllowanceChargeReason>
          <cbc:MultiplierFactorNumeric>1</cbc:MultiplierFactorNumeric>
          <cbc:Amount currencyID="EUR">1</cbc:Amount>
          <cbc:BaseAmount currencyID="EUR">100</cbc:BaseAmount>
      </cac:AllowanceCharge>
      <cac:AllowanceCharge>
          <cbc:ChargeIndicator>false</cbc:ChargeIndicator>
          <cbc:AllowanceChargeReasonCode>95</cbc:AllowanceChargeReasonCode>
          <cbc:AllowanceChargeReason>Discount</cbc:AllowanceChargeReason>
          <cbc:Amount currencyID="EUR">101</cbc:Amount>
      </cac:AllowanceCharge>

      <cac:Item>
          <cbc:Description>Description of item</cbc:Description>
          <cbc:Name>item name</cbc:Name>
          <cac:SellersItemIdentification>
              <cbc:ID>97iugug876</cbc:ID>
          </cac:SellersItemIdentification>

          <cac:CommodityClassification>
              <cbc:ItemClassificationCode listID="SRV">86776</cbc:ItemClassificationCode>
          </cac:CommodityClassification>
          <cac:ClassifiedTaxCategory>
              <cbc:ID>S</cbc:ID>
              <cbc:Percent>25.0</cbc:Percent>
              <cac:TaxScheme>
                  <cbc:ID>VAT</cbc:ID>
              </cac:TaxScheme>
          </cac:ClassifiedTaxCategory>
          <cac:AdditionalItemProperty>
              <cbc:Name>AdditionalItemName</cbc:Name>
              <cbc:Value>AdditionalItemValue</cbc:Value>
          </cac:AdditionalItemProperty>
      </cac:Item>

      <cac:Price>
          <cbc:PriceAmount currencyID="EUR">100</cbc:PriceAmount>
      </cac:Price>

  </cac:InvoiceLine>
</Invoice>`;
  } else if (docType === 'credit-note') {
    return `<?xml version="1.0" encoding="UTF-8"?>
<CreditNote xmlns:cac="urn:oasis:names:specification:ubl:schema:xsd:CommonAggregateComponents-2"
         xmlns:cbc="urn:oasis:names:specification:ubl:schema:xsd:CommonBasicComponents-2"
         xmlns="urn:oasis:names:specification:ubl:schema:xsd:CreditNote-2">
  <cbc:CustomizationID>urn:cen.eu:en16931:2017#compliant#urn:fdc:peppol.eu:2017:poacc:billing:3.0</cbc:CustomizationID>
  <cbc:ProfileID>urn:fdc:peppol.eu:2017:poacc:billing:01:1.0</cbc:ProfileID>
  <cbc:ID>${docId}</cbc:ID>
  <cbc:IssueDate>2025-11-13</cbc:IssueDate>
  <cbc:CreditNoteTypeCode>381</cbc:CreditNoteTypeCode>
  <cbc:DocumentCurrencyCode>EUR</cbc:DocumentCurrencyCode>
  <cbc:AccountingCost>4025:123:4343</cbc:AccountingCost>
  <cbc:BuyerReference>0150abc</cbc:BuyerReference>
  <cac:AccountingSupplierParty>
    <cac:Party>
      <cbc:EndpointID schemeID="iso6523-actorid-upis">${senderScheme}"${senderSubId}</cbc:EndpointID>
      <cac:PartyIdentification>
        <cbc:ID schemeID="iso6523-actorid-upis">${senderScheme}"${senderSubId}</cbc:ID>
      </cac:PartyIdentification>
      <cac:PartyName>
        <cbc:Name>SupplierTradingName Ltd.</cbc:Name>
      </cac:PartyName>
      <cac:PostalAddress>
        <cbc:StreetName>Main street 1</cbc:StreetName>
        <cbc:AdditionalStreetName>Postbox 123</cbc:AdditionalStreetName>
        <cbc:CityName>London</cbc:CityName>
        <cbc:PostalZone>GB 123 EW</cbc:PostalZone>
        <cac:Country>
          <cbc:IdentificationCode>GB</cbc:IdentificationCode>
        </cac:Country>
      </cac:PostalAddress>
      <cac:PartyTaxScheme>
        <cbc:CompanyID>GB1232434</cbc:CompanyID>
        <cac:TaxScheme>
          <cbc:ID>VAT</cbc:ID>
        </cac:TaxScheme>
      </cac:PartyTaxScheme>
      <cac:PartyLegalEntity>
        <cbc:RegistrationName>SupplierOfficialName Ltd</cbc:RegistrationName>
        <cbc:CompanyID>GB983294</cbc:CompanyID>
      </cac:PartyLegalEntity>
    </cac:Party>
  </cac:AccountingSupplierParty>
  <cac:AccountingCustomerParty>
    <cac:Party>
      <cbc:EndpointID schemeID="iso6523-actorid-upis">${receiverScheme}"${receiverSubId}</cbc:EndpointID>
      <cac:PartyIdentification>
        <cbc:ID schemeID="iso6523-actorid-upis">${receiverScheme}"${receiverSubId}</cbc:ID>
      </cac:PartyIdentification>
      <cac:PartyName>
        <cbc:Name>BuyerTradingName AS</cbc:Name>
      </cac:PartyName>
      <cac:PostalAddress>
        <cbc:StreetName>Hovedgatan 32</cbc:StreetName>
        <cbc:AdditionalStreetName>Po box 878</cbc:AdditionalStreetName>
        <cbc:CityName>Stockholm</cbc:CityName>
        <cbc:PostalZone>456 34</cbc:PostalZone>
        <cac:Country>
          <cbc:IdentificationCode>SE</cbc:IdentificationCode>
        </cac:Country>
      </cac:PostalAddress>
      <cac:PartyTaxScheme>
        <cbc:CompanyID>SE4598375937</cbc:CompanyID>
        <cac:TaxScheme>
          <cbc:ID>VAT</cbc:ID>
        </cac:TaxScheme>
      </cac:PartyTaxScheme>
      <cac:PartyLegalEntity>
        <cbc:RegistrationName>Buyer Official Name</cbc:RegistrationName>
        <cbc:CompanyID schemeID="0183">39937423947</cbc:CompanyID>
      </cac:PartyLegalEntity>
      <cac:Contact>
        <cbc:Name>Lisa Johnson</cbc:Name>
        <cbc:Telephone>23434234</cbc:Telephone>
        <cbc:ElectronicMail>lj@buyer.se</cbc:ElectronicMail>
      </cac:Contact>
    </cac:Party>
  </cac:AccountingCustomerParty>
  <cac:AllowanceCharge>
    <cbc:ChargeIndicator>true</cbc:ChargeIndicator>
    <cbc:AllowanceChargeReason>Insurance</cbc:AllowanceChargeReason>
    <cbc:Amount currencyID="EUR">25</cbc:Amount>
    <cac:TaxCategory>
      <cbc:ID>S</cbc:ID>
      <cbc:Percent>25.0</cbc:Percent>
      <cac:TaxScheme>
        <cbc:ID>VAT</cbc:ID>
      </cac:TaxScheme>
    </cac:TaxCategory>
  </cac:AllowanceCharge>
  <cac:TaxTotal>
    <cbc:TaxAmount currencyID="EUR">331.25</cbc:TaxAmount>
    <cac:TaxSubtotal>
      <cbc:TaxableAmount currencyID="EUR">1325</cbc:TaxableAmount>
      <cbc:TaxAmount currencyID="EUR">331.25</cbc:TaxAmount>
      <cac:TaxCategory>
        <cbc:ID>S</cbc:ID>
        <cbc:Percent>25.0</cbc:Percent>
        <cac:TaxScheme>
          <cbc:ID>VAT</cbc:ID>
        </cac:TaxScheme>
      </cac:TaxCategory>
    </cac:TaxSubtotal>
  </cac:TaxTotal>
  <cac:LegalMonetaryTotal>
    <cbc:LineExtensionAmount currencyID="EUR">1300</cbc:LineExtensionAmount>
    <cbc:TaxExclusiveAmount currencyID="EUR">1325</cbc:TaxExclusiveAmount>
    <cbc:TaxInclusiveAmount currencyID="EUR">1656.25</cbc:TaxInclusiveAmount>
    <cbc:ChargeTotalAmount currencyID="EUR">25</cbc:ChargeTotalAmount>
    <cbc:PayableAmount currencyID="EUR">1656.25</cbc:PayableAmount>
  </cac:LegalMonetaryTotal>

  <cac:CreditNoteLine>
    <cbc:ID>1</cbc:ID>
    <cbc:CreditedQuantity unitCode="DAY">7</cbc:CreditedQuantity>
    <cbc:LineExtensionAmount currencyID= "EUR">2800</cbc:LineExtensionAmount>
    <cbc:AccountingCost>Konteringsstreng</cbc:AccountingCost>
    <cac:OrderLineReference>
      <cbc:LineID>123</cbc:LineID>
    </cac:OrderLineReference>
    <cac:Item>
      <cbc:Description>Description of item</cbc:Description>
      <cbc:Name>item name</cbc:Name>
      <cac:StandardItemIdentification>
        <cbc:ID schemeID="0088">21382183120983</cbc:ID>
      </cac:StandardItemIdentification>
      <cac:OriginCountry>
        <cbc:IdentificationCode>NO</cbc:IdentificationCode>
      </cac:OriginCountry>
      <cac:CommodityClassification>
        <cbc:ItemClassificationCode listID="SRV">09348023</cbc:ItemClassificationCode>
      </cac:CommodityClassification>
      <cac:ClassifiedTaxCategory>
        <cbc:ID>S</cbc:ID>
        <cbc:Percent>25.0</cbc:Percent>
        <cac:TaxScheme>
          <cbc:ID>VAT</cbc:ID>
        </cac:TaxScheme>
      </cac:ClassifiedTaxCategory>
    </cac:Item>
    <cac:Price>
      <cbc:PriceAmount currencyID="EUR">400</cbc:PriceAmount>
    </cac:Price>
  </cac:CreditNoteLine>
  <cac:CreditNoteLine>
    <cbc:ID>2</cbc:ID>
    <cbc:CreditedQuantity unitCode="DAY">-3</cbc:CreditedQuantity>
    <cbc:LineExtensionAmount currencyID="EUR">-1500</cbc:LineExtensionAmount>
    <cac:OrderLineReference>
      <cbc:LineID>123</cbc:LineID>
    </cac:OrderLineReference>
    <cac:Item>
      <cbc:Description>Description 2</cbc:Description>
      <cbc:Name>item name 2</cbc:Name>
      <cac:StandardItemIdentification>
        <cbc:ID schemeID="0088">21382183120983</cbc:ID>
      </cac:StandardItemIdentification>
      <cac:OriginCountry>
        <cbc:IdentificationCode>NO</cbc:IdentificationCode>
      </cac:OriginCountry>
      <cac:CommodityClassification>
        <cbc:ItemClassificationCode listID="SRV">09348023</cbc:ItemClassificationCode>
      </cac:CommodityClassification>
      <cac:ClassifiedTaxCategory>
        <cbc:ID>S</cbc:ID>
        <cbc:Percent>25.0</cbc:Percent>
        <cac:TaxScheme>
          <cbc:ID>VAT</cbc:ID>
        </cac:TaxScheme>
      </cac:ClassifiedTaxCategory>
    </cac:Item>
    <cac:Price>
      <cbc:PriceAmount currencyID="EUR">500</cbc:PriceAmount>
    </cac:Price>
  </cac:CreditNoteLine>
</CreditNote>`;
  } else {
    throw new Error(`Unsupported document type: ${docType}`);
  }
}

// ...
if (process.argv.length !== 6) {
  console.error(
    'Usage: ts-node src/genDoc.ts <docType> <senderId> <receiverId> <docId>',
  );
  process.exit(1);
}
genDoc(process.argv[2], process.argv[3], process.argv[4], process.argv[5]).then(
  (doc) => {
    console.log(doc);
  },
);
