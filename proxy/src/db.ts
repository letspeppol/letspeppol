/* eslint @typescript-eslint/no-explicit-any: 0 */
import { Client } from 'pg';
import {
  ListEntityDocumentsParams,
  ListItem,
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

export async function storeDocumentInDb(docDetails: components['schemas']['Document']): Promise<void> {
  const client = await getPostgresClient();
  const insertQuery = `
    INSERT INTO FrontDocs (userId, platformId, createdAt, docType, direction, counterPartyId, counterPartyName, docId, amount, dueDate, paymentTerms, paid, ubl)
    VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10, $11, $12, $13)
    ON CONFLICT (platformId) DO NOTHING;
  `;
  const values = [
    docDetails.userId, // 1
    docDetails.platformId, // 2
    docDetails.createdAt, // 3
    docDetails.docType, // 4
    docDetails.direction, // 5
    docDetails.counterPartyId, // 6
    docDetails.counterPartyName, // 7
    docDetails.docId, // 8
    docDetails.amount, // 9
    docDetails.dueDate || null, // 10
    docDetails.paymentTerms || null, // 11
    docDetails.paid || null, // 12
    docDetails.ubl // 13
  ];
  await client.query(insertQuery, values);
}

export async function listEntityDocuments(
  params: ListEntityDocumentsParams,
): Promise<ListItem[]> {
  const { userId, page, pageSize, counterPartyId, counterPartyNameLike, docType, direction, docId, sortBy } = params;
  const offset = (page - 1) * pageSize;
  const orders = {
    amountAsc: 'amount ASC',
    amountDesc: 'amount DESC',
    createdAtAsc: 'createdAt ASC',
    createdAtDesc: 'createdAt DESC',
  };
  const orderBy = orders[sortBy || 'createdAtAsc'] || 'createdAt ASC';
  const queryParams = [
    userId,
    pageSize,
    offset,
    orderBy
  ];
  const whereClauses: string[] = [
    `userId = $1`
  ];
  if (typeof counterPartyId === 'string') {
    queryParams.push(counterPartyId);
    whereClauses.push(`counterPartyId = $${queryParams.length}`);
  }
  if (typeof counterPartyNameLike === 'string') {
    queryParams.push(`%${counterPartyNameLike}%`);
    whereClauses.push(`counterPartyName ILIKE $${queryParams.length}`);
  }
  if (typeof docType === 'string') {
    queryParams.push(docType);
    whereClauses.push(`docType = $${queryParams.length}`);
  }
  if (typeof direction === 'string') {
    queryParams.push(direction);
    whereClauses.push(`direction = $${queryParams.length}`);
  }
  if (typeof docId === 'string') {
    queryParams.push(docId);
    whereClauses.push(`docId = $${queryParams.length}`);
  }
  const queryStr = `
    SELECT * FROM FrontDocs
    WHERE ${whereClauses.join(' AND ')}
    ORDER BY $4
    LIMIT $2 OFFSET $3
  `;

  console.log('Executing query:', queryStr, 'with params:', queryParams);
  const client = await getPostgresClient();
  const result = await client.query(queryStr, queryParams);

  // map to camelCase and ListItemV1
  return result.rows.map(
    (row) =>
      ({
        platformId: row.platformid,
        createdAt: row.createdat,
        docType: row.doctype,
        direction: row.direction,
        counterPartyId: row.counterpartyid,
        counterPartyName: row.counterpartyname,
        docId: row.docid,
        amount: row.amount,
        dueDate: row.duedate || undefined,
        paymentTerms: row.paymentterms || undefined,
        paid: row.paid || undefined,
      }) as ListItem,
  );
}

export async function getDocumentUbl(requestingEntity: string, platformId: string): Promise<string> {
  const client = await getPostgresClient();
  const queryStr = `
    SELECT ubl FROM FrontDocs
    WHERE userId = $1 AND platformId = $2
    LIMIT 1
  `;
  const result = await client.query(queryStr, [requestingEntity, platformId]);
  if (result.rows.length === 0) {
    throw new Error('Document not found or access denied');
  }
  return result.rows[0].ubl;
}

export async function markDocumentAsPaid(
  userId: string,
  platformId: string,
  paid: string,
): Promise<void> {
  const client = await getPostgresClient();
  const updateQuery = `
    UPDATE FrontDocs
    SET paid = $1
    WHERE userId = $2 AND platformId = $3
  `;
  const values = [paid, userId, platformId];
  await client.query(updateQuery, values);
}

export async function getTotalsForUser(userId: string): Promise<{
  totalPayable: number;
  totalReceivable: number;
}> {
  const client = await getPostgresClient();
  const queryStr = `
    SELECT
      SUM(CASE WHEN direction = 'incoming' THEN amount ELSE 0 END) AS totalPayable,
      SUM(CASE WHEN direction = 'outgoing' THEN amount ELSE 0 END) AS totalReceivable
    FROM FrontDocs
    WHERE userId = $1
  `;
  console.log('Executing totals query:', queryStr, 'with userId:', userId);
  const result = await client.query(queryStr, [userId]);
  if (result.rows.length === 0) {
    return { totalPayable: 0, totalReceivable: 0 };
  }
  return {
    totalPayable: result.rows[0].totalpayable || 0,
    totalReceivable: result.rows[0].totalreceivable || 0,
  };
}
