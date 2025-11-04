import { XMLParser } from 'fast-xml-parser';
import { components } from './front.js';

export function parseDocument(documentXml: string): {
  docDetails: components['schemas']['Document'];
  docId: string;
  amount: number;
} {
  const parserOptions = {
    ignoreAttributes: false,
    numberParseOptions: {
      leadingZeros: false,
      hex: true,
      skipLike: /(?:)/, // Disable number parsing
    },
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
  const sender = jObj[docType]?.['cac:AccountingSupplierParty']?.['cac:Party'];
  const recipient =
    jObj[docType]?.['cac:AccountingCustomerParty']?.['cac:Party'];
  if (!sender?.['cbc:EndpointID']?.['#text']) {
    throw new Error('Missing sender EndpointID text');
  }
  if (!recipient?.['cbc:EndpointID']?.['#text']) {
    throw new Error('Missing recipient EndpointID text');
  }
  return {
    docDetails: {
      senderId: sender?.['cbc:EndpointID']?.['#text'],
      senderName: sender?.['cac:PartyName']?.['cbc:Name'],
      receiverId: recipient?.['cbc:EndpointID']?.['#text'],
      receiverName: recipient?.['cac:PartyName']?.['cbc:Name'],
      docType:
        docType === 'Invoice'
          ? 'invoice'
          : docType === 'CreditNote'
            ? 'credit-note'
            : (() => { throw new Error(`Unknown document type: ${docType}`); })(),
    },
    docId: jObj[docType]?.['cbc:ID'],
    amount: parseFloat(
      jObj[docType]?.['cac:LegalMonetaryTotal']?.['cbc:PayableAmount']?.[
        '#text'
      ],
    ),
  };
}
