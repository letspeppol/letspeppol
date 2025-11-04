export type ListEntityDocumentsParams = {
  userId: string;
  // paging:
  page: number;
  pageSize: number;
  // filters:
  counterPartyId?: string | undefined;
  counterPartyNameLike?: string | undefined;
  docType: 'invoices' | 'credit-notes' | undefined;
  direction: 'incoming' | 'outgoing' | undefined;
  docId?: string | undefined;
  // sorting:
  sortBy?: 'amountAsc' | 'amountDesc' | 'createdAtAsc' | 'createdAtDesc';
};

export type ListItem = {
  platformId: string;
  docType: 'invoice' | 'credit-note';
  direction: 'incoming' | 'outgoing';
  counterPartyId: string;
  counterPartyName: string;
  createdAt: string; // ISO 8601 Date string
  amount: number;
  docId: string;
};

export abstract class Backend {
  abstract reg(identifier: string, name: string): Promise<void>;
  abstract unreg(identifier: string): Promise<void>;
  abstract sendDocument(
    documentXml: string,
    sendingEntity: string,
  ): Promise<void>;
}
