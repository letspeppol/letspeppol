import {
  doSendDocument,
  doGetUuid,
  doCreateLegalEntity,
  doSetSmpRecord,
  doGetDocumentXml,
} from './acubeClient.js';
import { parseDocument } from './parse.js';
import { Backend } from './Backend.js';

export class Acube implements Backend {
  async sendDocument(
    documentXml: string,
    sendingEntity: string,
  ): Promise<void> {
    const { sender, docType } = parseDocument(documentXml);
    if (sender !== sendingEntity) {
      throw new Error(
        `Sender ${sender} does not match sending entity ${sendingEntity}`,
      );
    }
    const result = await doSendDocument(documentXml, docType!);
    if (result.status !== 200 && result.status !== 202) {
      throw new Error(
        `Failed to send document, status code ${result.status}: ${await result.text()}`,
      );
    }
  }

  async getUuid(identifier: string): Promise<string> {
    const identifierValue = identifier.split(':')[1];
    const response = await doGetUuid(identifierValue);
    if (response.status !== 200) {
      throw new Error(
        `Failed to get UUID, status code ${response.status}: ${await response.text()}`,
      );
    }
    const responseObj = await response.json();
    if (responseObj['hydra:member'].length === 0) {
      throw new Error(`Legal entity ${identifierValue} not found`);
    }
    if (responseObj['hydra:member'].length > 1) {
      throw new Error(
        `Multiple legal entities found for identifier ${identifierValue}`,
      );
    }
    if (typeof responseObj['hydra:member'][0].uuid !== 'string') {
      throw new Error(
        `Invalid UUID format for legal entity ${identifierValue}`,
      );
    }
    return responseObj['hydra:member'][0].uuid;
  }

  async createLegalEntity(identifier: string): Promise<void> {
    const response = await doCreateLegalEntity(identifier);
    if (
      response.status !== 201 &&
      response.status !== 202 &&
      response.status !== 500
    ) {
      throw new Error(
        `Failed to create legal entity, status code ${response.status}: ${await response.text()}`,
      );
    }
  }

  async setSmpRecord(identifier: string, enabled: boolean): Promise<void> {
    const uuid = await this.getUuid(identifier);
    const response = await doSetSmpRecord(uuid, enabled);
    if (response.status !== 200) {
      throw new Error(
        `Failed to ${enabled ? 'set' : 'remove'} SMP record, status code ${response.status}: ${await response.text()}`,
      );
    }
  }

  async reg(identifier: string): Promise<void> {
    await this.createLegalEntity(identifier);
    await this.setSmpRecord(identifier, true);
  }

  async unreg(identifier: string): Promise<void> {
    await this.setSmpRecord(identifier, false);
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
    // FIXME: check that the document with this uuid is actually associated with this peppolId
    const response = await doGetDocumentXml({ type, uuid });
    if (response.status !== 200) {
      throw new Error(
        `Failed to get document XML, status code ${response.status}: ${await response.text()}`,
      );
    }
    const responseBody = await response.text();
    console.log('Fetched document XML:', responseBody);
    const { sender, recipient } = parseDocument(
      `<?xml version="1.0" encoding="UTF-8"?>\n${responseBody}`,
    );
    if (sender !== peppolId && recipient !== peppolId) {
      throw new Error(
        `Document sender ${sender} and recipient ${recipient} do not match peppolId ${peppolId}`,
      );
    }
    return responseBody;
  }
}
