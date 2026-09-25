import {afterEach, describe, expect, it} from 'vitest';
import {getRememberedEmail, updateRememberedEmail} from '../../src/login/remembered-email';

afterEach(() => {
    localStorage.clear();
});

describe('remembered login email', () => {
    it('restores a remembered email address', () => {
        updateRememberedEmail('  person@example.com  ', true);

        expect(getRememberedEmail()).toBe('person@example.com');
    });

    it('forgets the email address when remembering is disabled', () => {
        updateRememberedEmail('person@example.com', true);
        updateRememberedEmail('person@example.com', false);

        expect(getRememberedEmail()).toBeNull();
    });
});
