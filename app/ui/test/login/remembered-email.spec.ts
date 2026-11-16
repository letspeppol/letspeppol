import {afterEach, describe, expect, it} from 'vitest';
import {consumeLoginHint, getRememberedEmail, rememberLoginHint, updateRememberedEmail} from '../../src/login/remembered-email';

afterEach(() => {
    localStorage.clear();
    sessionStorage.clear();
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

    it('hands a login hint to the next login page once', () => {
        rememberLoginHint('invitee@example.com');

        expect(consumeLoginHint()).toBe('invitee@example.com');
        expect(consumeLoginHint()).toBeNull();
    });
});
