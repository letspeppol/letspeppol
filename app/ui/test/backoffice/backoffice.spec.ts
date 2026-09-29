import {describe, expect, it} from 'vitest';
import {decisionAlertKey} from '../../src/backoffice/backoffice';
import {RegistrationReviewDto} from '../../src/services/kyc/registration-review-service';

const review = {id: 1, reviewStatus: 'APPROVED', requestedType: 'ADMIN'} as RegistrationReviewDto;

describe('decisionAlertKey', () => {
    it('reports a rejection', () => {
        expect(decisionAlertKey({review: {...review, reviewStatus: 'REJECTED'}})).toBe('alert.backoffice.rejected');
    });

    it('reports an approval that activated the company on Peppol', () => {
        expect(decisionAlertKey({review, registration: {peppolActive: true}})).toBe('alert.backoffice.approved');
    });

    it('reports an approval without registration, such as an affiliate request', () => {
        expect(decisionAlertKey({review: {...review, requestedType: 'AFFILIATE'}})).toBe('alert.backoffice.approved');
    });

    it('warns when the Peppol activation failed after approval', () => {
        expect(decisionAlertKey({review, registration: {peppolActive: false, errorCode: 'proxy_failed'}})).toBe('alert.backoffice.approved-not-registered');
    });

    it('explains that Peppol was not activated when the requested role is unknown', () => {
        expect(decisionAlertKey({review: {...review, requestedType: null}})).toBe('alert.backoffice.approved-without-activation');
    });
});
