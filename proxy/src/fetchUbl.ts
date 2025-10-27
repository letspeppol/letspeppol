import { getPostgresClient } from './db.js';
import { Ion } from './ion.js';
import { Peppyrus } from './peppyrus.js';
import { Scrada } from './scrada.js';
import { Acube } from './acube.js';
import { parseDocument } from './parse.js';

export async function fetchUbl(): Promise<void> {
  const queryStr = 'select * from FrontDocs where amount is null limit 1';
  const client = await getPostgresClient();
  const result = await client.query(queryStr);
  if (result.rows.length === 0) {
    console.log('No documents without UBL found');
    return;
  }
  const row = result.rows[0];
  const parts = row.platformid.split(':');
  if (parts.length !== 2) {
    console.error('Invalid platformId format:', row.platformid);
    return;
  }
  console.log('Fetching UBL for document', parts);
  const backends = {
    peppyrus: new Peppyrus(),
    acube: new Acube(),
    scrada: new Scrada(),
    ion: new Ion(),
  };
  const backend = backends[parts[0] as keyof typeof backends];
  if (!backend) {
    console.error('Unknown platform:', parts[0]);
    return;
  }
  let peppolId;
  if (row.direction === 'incoming') {
    peppolId = row.receiverid;
  } else {
    peppolId = row.senderid;
  }
  try {
    const ubl = await backend.getDocumentXml({
      peppolId,
      type: row.doctype === 'invoice' ? 'invoices' : 'credit-notes',
      uuid: parts[1],
      direction: row.direction,
    });
    const data = parseDocument(ubl);
    if (!data) {
      console.error('Failed to parse UBL');
      return;
    }
    const updateQuery =
      'update FrontDocs set "docid" = $1, "sendername" = $2, "receivername" = $3, "amount" = $4 where platformid = $5';
    const updateParams = [
      data.docId,
      data.senderName,
      data.recipientName,
      data.amount,
      row.platformid,
    ];
    console.log(
      'Updating document with query:',
      updateQuery,
      'and params:',
      updateParams,
    );
    await client.query(updateQuery, updateParams);
    console.log('Updated document with UBL and metadata:', data);
  } catch (error) {
    console.error('Error fetching UBL:', error);
    const updateQuery =
      'update FrontDocs set "amount" = 0 where platformid = $1';
    const updateParams = [row.platformid];
    console.log(
      'Updating document with query:',
      updateQuery,
      'and params:',
      updateParams,
    );
    await client.query(updateQuery, updateParams);
  } finally {
    client.end();
  }
}

// ...
fetchUbl();
