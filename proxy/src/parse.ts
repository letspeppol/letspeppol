import { XMLParser } from 'fast-xml-parser';
import { components } from './front.js';

export function parseDocument(documentXml: string, userId: string): {
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
  const receiver =
    jObj[docType]?.['cac:AccountingCustomerParty']?.['cac:Party'];
  if (!sender?.['cbc:EndpointID']?.['#text']) {
    throw new Error('Missing sender EndpointID text');
  }
  if (!receiver?.['cbc:EndpointID']?.['#text']) {
    throw new Error('Missing recipient EndpointID text');
  }
  const senderId =sender?.['cbc:EndpointID']?.['#text'];
  const receiverId = receiver?.['cbc:EndpointID']?.['#text'];
  let counterPartyId;
  let counterPartyName;
  if (userId === senderId) {
    counterPartyId = receiverId;
    counterPartyName = receiver?.['cac:PartyName']?.['cbc:Name'];
  } else if (userId === receiverId) {
    counterPartyId = senderId;
    counterPartyName = sender?.['cac:PartyName']?.['cbc:Name'];
  } else {
    throw new Error(
      `User ID ${userId} does not match sender ID ${senderId} or receiver ID ${receiverId}`,
    );
  }
  return {
    docDetails: {
      userId,
      counterPartyId,
      counterPartyName,
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
