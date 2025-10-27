import { INVOICES, CREDIT_NOTES } from './constants.js';

export async function doSendDocument(
  documentXml: string,
  docType: string,
): Promise<Response> {
  const endPoint: Record<string, string> = {
    Invoice: 'https://peppol-sandbox.api.acubeapi.com/invoices/outgoing/ubl',
    CreditNote:
      'https://peppol-sandbox.api.acubeapi.com/credit-notes/outgoing/ubl',
  };
  if (!docType || !endPoint[docType]) {
    throw new Error(
      `Could not determine document type or unsupported document type: ${docType}`,
    );
  }
  return fetch(endPoint[docType], {
    method: 'POST',
    headers: {
      Authorization: `Bearer ${process.env.ACUBE_TOKEN}`,
      'Content-Type': 'application/xml',
    },
    body: documentXml,
  });
}

export async function doGetUuid(identifierValue: string): Promise<Response> {
  return fetch(
    `https://peppol-sandbox.api.acubeapi.com/legal-entities?identifierValue=${identifierValue}`,
    {
      headers: {
        Authorization: `Bearer ${process.env.ACUBE_TOKEN}`,
      },
    },
  );
}

export async function doCreateLegalEntity(
  identifier: string,
): Promise<Response> {
  return fetch('https://peppol-sandbox.api.acubeapi.com/legal-entities', {
    method: 'POST',
    headers: {
      Authorization: `Bearer ${process.env.ACUBE_TOKEN}`,
      'Content-Type': 'application/ld+json',
    },
    body: JSON.stringify({
      registeredName: 'Ponder Source',
      country: 'SE',
      address: 'string',
      city: 'string',
      stateOrProvince: 'string',
      zipCode: 'string',
      identifierScheme: 'iso6523-actorid-upis',
      identifierValue: identifier,
      receivedDocumentNotificationEmails: ['notif@pondersource.com'],
    }),
  });
}

export async function doSetSmpRecord(
  uuid: string,
  enabled: boolean,
): Promise<Response> {
  return fetch(
    `https://peppol-sandbox.api.acubeapi.com/legal-entities/${uuid}/smp`,
    {
      method: 'PUT',
      headers: {
        Authorization: `Bearer ${process.env.ACUBE_TOKEN}`,
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({
        enabled,
        capabilities: [INVOICES, CREDIT_NOTES],
      }),
    },
  );
}

export async function doGetDocumentXml({
  type,
  uuid,
}: {
  type: string;
  uuid: string;
}): Promise<Response> {
  const endpoint = `https://peppol-sandbox.api.acubeapi.com/${type}/${uuid}/source`;
  console.log('Fetching document XML from', endpoint);
  return fetch(endpoint, {
    headers: {
      Authorization: `Bearer ${process.env.ACUBE_TOKEN}`,
      Accept: 'application/xml',
    },
  });
}
