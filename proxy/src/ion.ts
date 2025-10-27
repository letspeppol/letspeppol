import { Backend } from './Backend.js';
import { parseDocument } from './parse.js';

const API_BASE = 'https://test.ion-ap.net/api';

export class Ion implements Backend {
  async reg(identifier: string): Promise<void> {
    const response1 = await fetch(`${API_BASE}/v2/organizations`, {
      method: 'POST',
      headers: {
        Authorization: `Token ${process.env.ION_API_KEY}`,
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({
        name: identifier,
        country: 'BE',
        publish_in_smp: true,
        reference: 'Batch 1',
      }),
    });
    if (!response1.ok) {
      throw new Error(`Error creating organization: ${response1.statusText}`);
    }
    const responseBody1 = await response1.json();
    console.log('Created organization', responseBody1);
    const response2 = await fetch(
      `${API_BASE}/v2/organizations/${responseBody1.id}/identifiers`,
      {
        method: 'POST',
        headers: {
          Authorization: `Token ${process.env.ION_API_KEY}`,
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({
          identifier,
          verified: true,
          publish_receive_peppolbis: true,
        }),
      },
    );
    if (!response2.ok) {
      throw new Error(`Error creating legal entity: ${response2.statusText}`);
    }
    const responseBody2 = await response2.json();
    console.log('Created Identifier', responseBody2);
    console.log('Created Legal Entity', {
      orgId: responseBody1.id,
      smpRecordId: responseBody2.id,
    });
  }
  async lookupIdentifier(
    identifier: string,
  ): Promise<{ orgId: number; smpRecordId: number }> {
    const response1 = await fetch(
      `${API_BASE}/v2/organizations?filter_name=${identifier}`,
      {
        headers: {
          Authorization: `Token ${process.env.ION_API_KEY}`,
        },
      },
    );
    if (!response1.ok) {
      throw new Error(
        `Error finding organization ${identifier}: ${response1.statusText}`,
      );
    }
    const responseBody1 = await response1.json();
    if (responseBody1.count !== 1) {
      throw new Error(
        `Error finding organization ${identifier}: found ${responseBody1.count} results`,
      );
    }
    console.log('Looked up organization', responseBody1.results[0]);
    const orgId = responseBody1.results[0].id;
    const response2 = await fetch(
      `${API_BASE}/v2/organizations/${orgId}/identifiers?filter_identifier=${identifier}`,
      {
        headers: {
          Authorization: `Token ${process.env.ION_API_KEY}`,
        },
      },
    );
    if (!response2.ok) {
      throw new Error(
        `Error finding organization ${identifier}: ${response2.statusText}`,
      );
    }
    const responseBody2 = await response2.json();
    if (responseBody2.count !== 1) {
      throw new Error(
        `Error finding organization ${identifier}: found ${responseBody2.count} results`,
      );
    }
    console.log('Looked up SMP record', responseBody2.results[0]);
    const smpRecordId = responseBody2.results[0].id;
    return { orgId, smpRecordId };
  }
  async unreg(identifier: string): Promise<void> {
    const { orgId, smpRecordId } = await this.lookupIdentifier(identifier);
    const response1 = await fetch(
      `${API_BASE}/v2/organizations/${orgId}/identifiers/${smpRecordId}`,
      {
        method: 'DELETE',
        headers: {
          Authorization: `Token ${process.env.ION_API_KEY}`,
        },
      },
    );
    if (!response1.ok) {
      console.error(`Error deleting SMP record: ${response1.statusText}`);
    }
    const response2 = await fetch(`${API_BASE}/v2/organizations/${orgId}`, {
      method: 'DELETE',
      headers: {
        Authorization: `Token ${process.env.ION_API_KEY}`,
      },
    });
    if (!response2.ok) {
      throw new Error(`Error deleting company: ${response2.statusText}`);
    }
  }
  async sendDocument(
    documentXml: string,
    sendingEntity: string,
  ): Promise<void> {
    const { sender } = await parseDocument(documentXml);
    if (sender !== sendingEntity) {
      throw new Error(
        `Sender in document (${sender}) does not match sending entity (${sendingEntity})`,
      );
    }
    const response = await fetch(`${API_BASE}/v2/send-document`, {
      method: 'POST',
      headers: {
        Authorization: `Token ${process.env.ION_API_KEY}`,
        'Content-Type': 'application/xml',
      },
      body: documentXml,
    });
    if (!response.ok) {
      throw new Error(`Error sending document: ${response.statusText}`);
    }
    const responseBody = await response.json();
    console.log('Sent document', responseBody);
  }
  async getDocumentXml(query: {
    peppolId: string;
    type: string;
    uuid: string;
    direction: string;
  }): Promise<string> {
    let url = `${API_BASE}/v2/receive-transactions/${query.uuid}/document`;
    if (query.direction === 'outgoing') {
      url = `${API_BASE}/v2/send-transactions/${query.uuid}/document`;
    }
    const response = await fetch(url, {
      headers: {
        Authorization: `Token ${process.env.ION_API_KEY}`,
      },
    });
    if (!response.ok) {
      throw new Error(`Error fetching document XML: ${response.statusText}`);
    }
    const responseBody = await response.text();
    return responseBody;
  }
}
