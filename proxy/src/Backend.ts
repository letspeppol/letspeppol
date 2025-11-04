export type ListEntityDocumentsParams = {
  peppolId: string;
  direction: 'incoming' | 'outgoing';
  type: 'invoices' | 'credit-notes';
  query: Record<string, string | string[] | undefined>;
  apiVersion?: 'v1';
  page: number;
  pageSize: number;
};

export type ListItemV1 = {
  uuid: string;
  type: 'Invoice' | 'CreditNote' | string;
  direction: 'incoming' | 'outgoing';
  format?: string;
  number?: string;
  senderId: string;
  senderName?: string;
  recipientId: string;
  recipientName?: string;
  requestSentAt?: string;
  responseSentAt?: string;
  success: boolean;
  errorCode: string | null;
};

export type ListItemV2 = {
  platformId: string;
  docType: 'invoice' | 'credit-note';
  direction: 'incoming' | 'outgoing';
  senderId: string;
  receiverId: string;
  createdAt: string;
};

export abstract class Backend {
  abstract reg(identifier: string, name: string): Promise<void>;
  abstract unreg(identifier: string): Promise<void>;
  abstract sendDocument(
    documentXml: string,
    sendingEntity: string,
  ): Promise<void>;
  abstract getDocumentXml(query: {
    peppolId: string;
    type: string;
    uuid: string;
    direction: string;
  }): Promise<string>;
}
