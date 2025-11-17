import { XMLParser } from 'fast-xml-parser';
import { components } from './front.js';

export function parseDocument(
  documentXml: string,
  direction: string,
): components['schemas']['Document'] {
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
  if (Object.keys(jObj).length !== 2) {
    throw new Error('Please use one <?xml> element and one <Invoice> or <CreditNote> element at the top level');
  }
  if (Object.keys(jObj)[0] !== '?xml') {
    throw new Error('Missing top level ?xml declaration');
  }
  const docType = Object.keys(jObj)[1];
  if (!docType) {
    throw new Error('Could not determine document type from XML');
  }
  const sender = jObj[docType]?.['cac:AccountingSupplierParty']?.['cac:Party'];
  const receiver =
    jObj[docType]?.['cac:AccountingCustomerParty']?.['cac:Party'];
  if (!sender?.['cbc:EndpointID']?.['#text']) {
    throw new Error('Missing sender EndpointID text');
  }
  if (!receiver?.['cbc:EndpointID']?.['#text']) {
    throw new Error('Missing recipient EndpointID text');
  }
  const senderId = `${sender?.['cbc:EndpointID']?.['@_schemeID']}:${sender?.['cbc:EndpointID']?.['#text']}`;
  const receiverId = `${receiver?.['cbc:EndpointID']?.['@_schemeID']}:${receiver?.['cbc:EndpointID']?.['#text']}`;
  let userId;
  let counterPartyId;
  let counterPartyName;
  if (direction === 'outgoing') {
    userId = senderId;
    counterPartyId = receiverId;
    counterPartyName = receiver?.['cac:PartyName']?.['cbc:Name'];
  } else if (direction === 'incoming') {
    userId = receiverId;
    counterPartyId = senderId;
    counterPartyName = sender?.['cac:PartyName']?.['cbc:Name'];
  } else {
    throw new Error(
      `Please specify valid direction 'incoming' or 'outgoing', got: ${direction}`,
    );
  }
  const docTypeMap: { [key: string]: 'invoice' | 'credit-note' } = {
    Invoice: 'invoice',
    CreditNote: 'credit-note',
    'ubl:Invoice': 'invoice',
    'ubl:CreditNote': 'credit-note',
  };
  return {
    // platformId is assigned by the platform
    userId,
    createdAt: new Date().toISOString(),
    docType: docTypeMap[docType] || (() => {
      throw new Error(`Unknown document type: ${docType}`);
            })(),
    direction,
    counterPartyId,
    counterPartyName,
    docId: jObj[docType]?.['cbc:ID'],
    amount: parseFloat(
      jObj[docType]?.['cac:LegalMonetaryTotal']?.['cbc:PayableAmount']?.[
        '#text'
      ],
    ),
    paymentTerms:
      jObj[docType]?.['cac:PaymentTerms']?.['cbc:Note'] || undefined,
    dueDate: jObj[docType]?.['cbc:DueDate'] || undefined,
    // paid: initially undefined
    ubl: documentXml,
  };
}
