import {describe, expect, test} from 'vitest';
import moment from 'moment';
import {documentStatus} from '../../src/invoice/document-status';
import {DocumentDirection, type DocumentDto} from '../../src/services/app/invoice-service';

const now = moment('2026-10-29T12:00:00Z');

function doc(overrides: Partial<DocumentDto>): DocumentDto {
    return {direction: DocumentDirection.OUTGOING, ownerPeppolId: 'a', partnerPeppolId: 'b', ubl: '', ...overrides};
}

describe('documentStatus', () => {
    test('a draft is a draft whatever else is set', () => {
        expect(documentStatus(doc({draftedOn: '2026-10-01', paidOn: '2026-10-02'}), now).key).toBe('draft');
    });

    test('a delivery error wins over payment and due date', () => {
        expect(documentStatus(doc({processedOn: '2026-10-01', processedStatus: 'Receiver not found', dueDate: '2026-10-02'}), now))
            .toMatchObject({key: 'error', tone: 'error'});
    });

    test('a paid invoice says when it was sent', () => {
        expect(documentStatus(doc({processedOn: '2026-10-01', paidOn: '2026-10-10', dueDate: '2026-10-02'}), now))
            .toEqual({key: 'paid', tone: 'paid', detailKey: 'sent', detailDate: '2026-10-01'});
    });

    test('an unpaid invoice past its due date is overdue', () => {
        expect(documentStatus(doc({direction: DocumentDirection.INCOMING, processedOn: '2026-10-01', dueDate: '2026-10-20'}), now))
            .toEqual({key: 'overdue', tone: 'overdue', detailKey: 'received', detailDate: '2026-10-01'});
    });

    test('an outgoing invoice waiting to go out is scheduled or sending', () => {
        expect(documentStatus(doc({scheduledOn: '2026-10-30T08:00:00Z', dueDate: '2026-10-01'}), now))
            .toMatchObject({key: 'scheduled', detailKey: 'scheduled'});
        expect(documentStatus(doc({}), now).key).toBe('sending');
    });

    test('a delivered invoice that is not yet due is sent or received', () => {
        expect(documentStatus(doc({processedOn: '2026-10-25', dueDate: '2026-11-25'}), now))
            .toMatchObject({key: 'sent', tone: 'sent'});
        expect(documentStatus(doc({direction: DocumentDirection.INCOMING, processedOn: '2026-10-25', dueDate: '2026-11-25'}), now))
            .toMatchObject({key: 'received', tone: 'incoming'});
    });
});
