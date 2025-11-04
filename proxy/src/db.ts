/* eslint @typescript-eslint/no-explicit-any: 0 */
import { Client } from 'pg';
import {
  ListEntityDocumentsParams,
  ListItemV1,
  ListItemV2,
} from './Backend.js';
export { Client } from 'pg';
import { components } from './front.js';

let client: Client | null = null;

export async function getPostgresClient(): Promise<Client> {
  if (client) {
    return client;
  }
  if (!process.env.DATABASE_URL) {
    throw new Error('DATABASE_URL not set');
  }
  client = new Client({
    connectionString: process.env.DATABASE_URL,
    ssl: {
      rejectUnauthorized: process.env.NODE_ENV === 'production',
    },
  });
  await client.connect();
  return client;
}

export function getFields(
  openApiSpec: any,
  endPoint: string,
  rowsFrom: string | undefined,
): { [key: string]: { type: string } } | undefined {
  const successResponseProperties =
    openApiSpec.paths[endPoint]?.get?.responses?.['200']?.content;
  // console.log(openApiSpec.paths, endPoint);
  const schema =
    successResponseProperties?.['application/ld+json']?.schema ||
    successResponseProperties?.['application/json']?.schema;
  // console.log(`Schema for ${endPoint}:`, JSON.stringify(schema, null, 2));
  // const whatWeWant = schema?.properties?.[rowsFrom].items?.properties;
  const whatWeWant =
    typeof rowsFrom === 'string'
      ? schema?.properties?.[rowsFrom]?.items?.properties
      : schema?.items?.properties;
  // console.log(`What we want (getFields ${endPoint} ${rowsFrom}):`, JSON.stringify(whatWeWant, null, 2));
  return whatWeWant;
}
export async function createSqlTable(
  client: Client,
  tableName: string,
  whatWeWant: { [key: string]: { type: string } },
): Promise<void> {
  const rowSpecs: string[] = [];
  // console.log(`What we want (createSqlTable ${tableName}):`, JSON.stringify(whatWeWant, null, 2));
  Object.entries(whatWeWant).forEach(([key, value]) => {
    const type = (value as { type: string }).type;
    if (type === 'string') {
      rowSpecs.push(`"S${key}" TEXT`);
    } else if (type === 'integer') {
      rowSpecs.push(`"S${key}" INTEGER`);
      // } else if (type === 'boolean') {
      //   rowSpecs.push(`"S${key}" BOOLEAN`);
    }
  });
  const createTableQuery = `
CREATE TABLE IF NOT EXISTS ${tableName.replace('-', '_')} (
  ${rowSpecs.join(',\n  ')}\n
);
`;
  console.log(createTableQuery);
  await client.query(createTableQuery);
}
export async function insertData(
  client: Client,
  tableName: string,
  items: any[],
  fields: string[],
): Promise<void> {
  console.log(`Fetched data:`, items);
  await Promise.all(
    items.map((item: any) => {
      const insertQuery = `INSERT INTO ${tableName.replace('-', '_')} (${fields.map((x) => `"S${x}"`).join(', ')}) VALUES (${fields.map((field) => `'${item[field]}'`).join(', ')})`;
      // console.log(`Executing insert query: ${insertQuery}`);
      return client.query(insertQuery);
    }),
  );
}

export async function storeDocumentInDb(
  platformId: string,
  docDetails: components['schemas']['Document'],
  docType: string,
  direction: string,
  docId: string,
  amount: number,
  xmlContent: string,
): Promise<void> {
  const client = await getPostgresClient();
  const insertQuery = `
    INSERT INTO FrontDocs (senderId, senderName, receiverId, receiverName, docType, direction, docId, amount, platformId, createdAt, ubl)
    VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10, $11)
    ON CONFLICT (platformId) DO NOTHING;
  `;
  const values = [
    docDetails.senderId,
    docDetails.senderName,
    docDetails.receiverId,
    docDetails.receiverName,
    docType,
    direction,
    docId,
    amount,
    platformId,
    docDetails.createdAt,
    xmlContent,
  ];
  await client.query(insertQuery, values);
}

export async function listEntityDocuments(
  params: ListEntityDocumentsParams,
): Promise<ListItemV1[]> {
  const { peppolId, direction, type, page, pageSize } = params;
  const offset = (page - 1) * pageSize;
  let constrainedParty;
  if (direction === 'incoming') {
    constrainedParty = 'receiver';
  } else {
    constrainedParty = 'sender';
  }
  let singularType;
  if (type === 'invoices') {
    singularType = 'invoice';
  } else if (type === 'credit-notes') {
    singularType = 'credit-note';
  } else {
    throw new Error(`Unknown document type: ${type}`);
  }

  const queryStr = `
    SELECT * FROM FrontDocs
    WHERE ${constrainedParty}Id = $1 AND direction = $2 AND docType = $3
    ORDER BY createdAt DESC
    LIMIT $4 OFFSET $5
  `;
  const queryParams = [peppolId, direction, singularType, pageSize, offset];
  console.log('Executing query:', queryStr, 'with params:', queryParams);
  const client = await getPostgresClient();
  const result = await client.query(queryStr, queryParams);

  // map to camelCase and ListItemV1
  return result.rows.map(
    (row) =>
      ({
        platformId: row.platformid,
        docType: row.doctype,
        direction: row.direction,
        senderId: row.senderid,
        senderName: row.sendername,
        receiverId: row.receiverid,
        receiverName: row.receivername,
        createdAt: row.createdat,
        amount: row.amount,
        docId: row.docid,
      }) as ListItemV2,
  );
}
