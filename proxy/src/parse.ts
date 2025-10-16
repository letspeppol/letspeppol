import { XMLParser } from 'fast-xml-parser';

export function parseDocument(documentXml: string): { sender: string | undefined; recipient: string | undefined; docType: string | undefined, senderName?: string, recipientName?: string, amount?: number, docId?: string } {
  const parserOptions = {
    ignoreAttributes: false,
    numberParseOptions: {
      leadingZeros: false,
      hex: true,
      skipLike: /(?:)/ // Disable number parsing
    }
  };
  const parser = new XMLParser(parserOptions);
  const jObj = parser.parse(documentXml);
  if (!jObj) {
    throw new Error('Failed to parse XML document');
  }
  if (Object.keys(jObj)[0] !== '?xml') {
    throw new Error('Missing top level ?xml declaration');
  }
  const docType = Object.keys(jObj)[1];
  if (!docType) {
    throw new Error('Could not determine document type from XML');
  }
  const sender = jObj[docType]?.['cac:AccountingSupplierParty']?.['cac:Party']?.['cbc:EndpointID'];
  const recipient = jObj[docType]?.['cac:AccountingCustomerParty']?.['cac:Party']?.['cbc:EndpointID'];
  if (!sender['#text']) {
    throw new Error('Missing sender EndpointID text');
  }
  if (!recipient['#text']) {
    throw new Error('Missing recipient EndpointID text');
  }
  return {
    sender: `${sender['@_schemeID']}:${sender['#text']}`,
    senderName: jObj[docType]?.['cac:AccountingSupplierParty']?.['cac:Party']?.['cac:PartyName']?.['cbc:Name'],
    recipient: `${recipient['@_schemeID']}:${recipient['#text']}`,
    recipientName: jObj[docType]?.['cac:AccountingCustomerParty']?.['cac:Party']?.['cac:PartyName']?.['cbc:Name'],
    amount: parseFloat(jObj[docType]?.['cac:LegalMonetaryTotal']?.['cbc:PayableAmount']?.['#text']),
    docType: docType === 'Invoice' ? 'Invoice' : docType === 'CreditNote' ? 'CreditNote' : undefined,
    docId: jObj[docType]?.['cbc:ID'],
  };
}