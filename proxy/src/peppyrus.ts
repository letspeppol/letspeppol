import { INVOICES } from './constants.js';
import { Backend } from './Backend.js';
import { parseDocument } from './parse.js';

export class Peppyrus implements Backend {
  apiUrl = 'https://api.test.peppyrus.be/v1';
  async sendDocument(
    documentXml: string,
    sendingEntity: string,
  ): Promise<void> {
    const { sender, recipient } = parseDocument(documentXml);
    if (sender !== sendingEntity) {
      throw new Error(
        `Sender ${sender} does not match sending entity ${sendingEntity}`,
      );
    }
    const body = JSON.stringify({
      sender,
      recipient,
      processType: `${INVOICES.processScheme}::${INVOICES.process}`,
      documentType: `${INVOICES.documentTypeScheme}::${INVOICES.documentType}`,
      fileName: 'invoice.xml',
      fileContent: Buffer.from(documentXml).toString('base64'),
    });
    const response = await fetch(`${this.apiUrl}/message`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'X-Api-Key': process.env.PEPPYRUS_TOKEN_TEST!,
      },
      body,
    });
    console.log(response.status, await response.text());
    if (response.status !== 200 && response.status !== 202) {
      throw new Error(
        `Failed to send document, status code ${response.status}: ${await response.text()}`,
      );
    }
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
    void identifier;

    throw new Error('Method not implemented.');
  }
  async unreg(identifier: string): Promise<void> {
    void identifier;
    throw new Error('Method not implemented.');
  }
  async getDocumentXml({
    peppolId,
    type,
    uuid,
  }: {
    peppolId: string;
    type: string;
    uuid: string;
  }): Promise<string> {
    void peppolId;
    void type;
    const response = await fetch(`${this.apiUrl}/message/${uuid}`, {
      headers: {
        'X-Api-Key': process.env.PEPPYRUS_TOKEN_TEST!,
      },
    });
    if (response.status !== 200) {
      throw new Error(
        `Failed to retrieve document XML, status code ${response.status}: ${await response.text()}`,
      );
    }
    const { fileContent } = await response.json();
    return Buffer.from(fileContent, 'base64').toString('utf-8');
  }
}
