import { storeDocumentInDb } from "./db.js";
import { parseDocument } from "./parse.js";

export async function checkForIncomingDocs(): Promise<void> {
  console.log('Checking for incoming documents...');
  const res = await fetch(
      `https://apitest.scrada.be/v1/company/${process.env.SCRADA_COMPANY_ID}/peppol/inbound/document/unconfirmed`,
    {
        headers: {
          'X-Api-Key': process.env.SCRADA_API_KEY!,
          'X-Password': process.env.SCRADA_API_PWD!,
        },
      },
    );
  if (!res.ok) {
    console.error('Failed to fetch incoming documents:', res.statusText);
    return;
  }
  const { results } = await res.json();
  const docIds = results.map((doc: { id: string }) => doc.id);
  await Promise.all(docIds.map(async (docId: string) => {
    console.log('Processing incoming document:', docId);
    const res = await fetch(
      `https://apitest.scrada.be/v1/company/${process.env.SCRADA_COMPANY_ID}/peppol/inbound/document/${docId}`,
    {
        headers: {
          'X-Api-Key': process.env.SCRADA_API_KEY!,
          'X-Password': process.env.SCRADA_API_PWD!,
        },
      },
    );
    if (!res.ok) {
      console.error('Failed to fetch incoming document:', docId, res.statusText);
      return;
    }
    const ubl = await res.text();
    const expect = {
      userId: res.headers.get('x-scrada-peppol-receiver-id')!,
      counterPartyId: res.headers.get('x-scrada-peppol-sender-id')!,
    };
    let docDetails;
    try {
      docDetails = parseDocument(ubl, 'incoming');
    } catch (error) {
      console.error('Failed to parse document:', docId, error);
      return;
    }
    Object.keys(expect).forEach(key => {
      if (docDetails[key] !== expect[key]) {
        console.error(`Document ${docId} does not match expected ${key}:`, docDetails[key]);
      }
    });
    if (docDetails.direction !== 'incoming') {
      console.error('Document is not incoming, skipping:', docId);
      return;
    }
    docDetails.platformId = `scrada_${res.headers.get('x-scrada-document-id')!}`;
    await storeDocumentInDb(docDetails);
    console.log('Incoming document:', docDetails.platformId);
  }));
  console.log('Incoming documents:', docIds);
}
