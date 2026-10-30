import moment from "moment";
import {DocumentDirection, DocumentDto} from "../services/app/invoice-service";

export type DocumentStatusKey = 'draft' | 'error' | 'paid' | 'scheduled' | 'sending' | 'overdue' | 'sent' | 'received';

export interface DocumentStatus {
    key: DocumentStatusKey;
    tone: string;
    detailKey?: 'sent' | 'received' | 'scheduled';
    detailDate?: string;
}

const TONES: Record<DocumentStatusKey, string> = {
    draft: '',
    error: 'error',
    paid: 'paid',
    scheduled: 'scheduled',
    sending: 'scheduled',
    overdue: 'overdue',
    sent: 'sent',
    received: 'incoming',
};

export function isDocumentOverdue(doc: Pick<DocumentDto, 'dueDate'>, now: moment.Moment = moment()): boolean {
    return !!doc.dueDate && now.isAfter(moment(doc.dueDate));
}

function status(key: DocumentStatusKey, detail: Partial<DocumentStatus> = {}): DocumentStatus {
    return {key, tone: TONES[key], ...detail};
}

export function documentStatus(doc: DocumentDto, now: moment.Moment = moment()): DocumentStatus {
    if (doc.draftedOn) {
        return status('draft');
    }
    if (doc.processedStatus) {
        return status('error');
    }
    const incoming = doc.direction === DocumentDirection.INCOMING;
    const delivery: Partial<DocumentStatus> = doc.processedOn
        ? {detailKey: incoming ? 'received' : 'sent', detailDate: doc.processedOn}
        : {};
    if (doc.paidOn) {
        return status('paid', delivery);
    }
    if (!incoming && !doc.processedOn) {
        return doc.scheduledOn
            ? status('scheduled', {detailKey: 'scheduled', detailDate: doc.scheduledOn})
            : status('sending');
    }
    if (isDocumentOverdue(doc, now)) {
        return status('overdue', delivery);
    }
    return status(incoming ? 'received' : 'sent');
}
