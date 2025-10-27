import { INVOICES, CREDIT_NOTES, ID_SCHEME } from "./constants.js";
import { Backend } from "./Backend.js";
import { parseDocument } from "./parse.js";

function toScrada(docSpec: { documentTypeScheme: string; documentType: string; processScheme: string; process: string }): object {
  return {
    "scheme": docSpec.documentTypeScheme,
    "value": docSpec.documentType,
    "processIdentifier": {
      "scheme": docSpec.processScheme,
      "value": docSpec.process
    }
  };
}

export class Scrada implements Backend {
  apiUrl = 'https://apitest.scrada.be';
  async sendDocument(documentXml: string, sendingEntity: string): Promise<void> {
    const { sender, recipient } = parseDocument(documentXml);
    if (sender !== sendingEntity) {
      throw new Error(`Sender ${sender} does not match sending entity ${sendingEntity}`);
    }
    const body = Buffer.from(documentXml).toString('utf-8');
    //   sender,
    //   recipient,
    //   processType: `${INVOICES.processScheme}::${INVOICES.process}`,
    //   documentType: `${INVOICES.documentTypeScheme}::${INVOICES.documentType}`,
    //   fileName: 'invoice.xml',
    //   "fileContent": Buffer.from(documentXml).toString('base64'),
    // });
    const response = await fetch(`${this.apiUrl}/v1/company/${process.env.SCRADA_COMPANY_ID}/peppol/outbound/document`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/xml',
        'X-Api-Key': process.env.SCRADA_API_KEY!,
        'X-Password': process.env.SCRADA_API_PWD!,
        'X-Scrada-Peppol-Sender-Scheme': ID_SCHEME,
        'X-Scrada-Peppol-Sender-Id': sender!,
        'X-Scrada-Peppol-Receiver-Scheme': ID_SCHEME,
        'X-Scrada-Peppol-Receiver-Id': recipient!,
        'X-Scrada-Peppol-C1-Country-Code': 'BE',
        'X-Scrada-Peppol-Document-Type-Scheme': INVOICES.documentTypeScheme,
        'X-Scrada-Peppol-Document-Type-Value': INVOICES.documentType,
        'X-Scrada-Peppol-Process-Scheme': INVOICES.processScheme,
        'X-Scrada-Peppol-Process-Value': INVOICES.process,
      },
      body
    });
    if (response.status !== 200 && response.status !== 202) {
      throw new Error(`Failed to send document, status code ${response.status}: ${await response.text()}`);
    }
    const docUuid = await response.json();
    console.log('Sent document, got UUID', docUuid);
    // console.log();
    // console.log(`curl -H "Authorization: Bearer $TWO" $PROXY_HOST/v1/invoices/outgoing/${docUuid}`);
    // console.log();
    // await new Promise(resolve => setTimeout(resolve, 20000));
    const statusCheck = await fetch(`${this.apiUrl}/v1/company/${process.env.SCRADA_COMPANY_ID}/peppol/outbound/document/${docUuid}/info`, {
      headers: {
        'X-Api-Key': process.env.SCRADA_API_KEY!,
        'X-Password': process.env.SCRADA_API_PWD!,
      }
    });
    console.log('Status check', await statusCheck.text(), statusCheck.status);
    const readBack = await this.getDocumentXml({ peppolId: sender!, type: 'invoices', uuid: docUuid, direction: 'outgoing' });
    console.log('ReadBack Check', readBack);
  }
  async getUuid(identifier: string): Promise<string> {
    void identifier;
    throw new Error('Method not implemented.');
  }
  async createLegalEntity(identifier: string): Promise<void> {
    void identifier;
    throw new Error('Method not implemented.');
  }
  async reg(identifier: string): Promise<void> {
    const response = await fetch(`${this.apiUrl}/v1/company/${process.env.SCRADA_COMPANY_ID}/peppol/register`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'X-Api-Key': process.env.SCRADA_API_KEY!,
        'X-Password': process.env.SCRADA_API_PWD!,
      },
      body: JSON.stringify({
        participantIdentifier: {
          scheme: ID_SCHEME,
          id: identifier
        },
        migrationKey: null,
        businessEntity: {
          name: "Business Entity Name",
          languageCode: "NL",
          countryCode: "BE"
        },
        documentTypes: [ toScrada(INVOICES), toScrada(CREDIT_NOTES) ],
      }),
    });
    if (response.status !== 200 && response.status !== 202) {
      throw new Error(`Failed to register company, status code ${response.status}: ${await response.text()}`);
    }
  }
  async unreg(identifier: string): Promise<void> {
    const response = await fetch(`${this.apiUrl}/v1/company/${process.env.SCRADA_COMPANY_ID}/peppol/deregister/${ID_SCHEME}//${identifier}`, {
      method: 'DELETE',
      headers: {
        'X-Api-Key': process.env.SCRADA_API_KEY!,
        'X-Password': process.env.SCRADA_API_PWD!,
      },
    });
    if (response.status !== 200 && response.status !== 202) {
      throw new Error(`Failed to deregister company, status code ${response.status}: ${await response.text()}`);
    }
  }
  async getDocumentXml({ peppolId, type, uuid, direction }: { peppolId: string; type: string; uuid: string, direction: string }): Promise<string> {
    console.log('Getting document XML for', { peppolId, type, uuid, direction });
    void peppolId;
    void type;
    let url;
    if (direction === 'incoming') {
      url = `${this.apiUrl}/v1/company/${process.env.SCRADA_COMPANY_ID}/peppol/inbound/document/${uuid}`;
    } else {
      url = `${this.apiUrl}/v1/company/${process.env.SCRADA_COMPANY_ID}/peppol/outbound/document/${uuid}/ubl`;
    }
    console.log('Fetching', url);
    const response = await fetch(url, {
      headers: {
        'X-Api-Key': process.env.SCRADA_API_KEY!,
        'X-Password': process.env.SCRADA_API_PWD!,
      }
    });
    if (response.status !== 200) {
      throw new Error(`Failed to retrieve document XML, status code ${response.status}: ${await response.text()}`);
    }
    return await response.text();
  }
}
