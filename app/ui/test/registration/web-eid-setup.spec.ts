import {createFixture} from '@aurelia/testing';
import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';
import * as webeid from '@web-eid/web-eid-library/web-eid';
import {extensionStores, getExtensionStore, getWebEidSetupIssue, WebEidSetup} from '../../src/components/eid/web-eid-setup';
import {Onboarding} from '../../src/registration/onboarding';
import {EmailConfirmation} from '../../src/registration/email-confirmation';

vi.mock('@web-eid/web-eid-library/web-eid', async importOriginal => ({
    ...await importOriginal<typeof webeid>(),
    getSigningCertificate: vi.fn(),
    sign: vi.fn(),
}));

beforeEach(() => vi.clearAllMocks());
afterEach(() => vi.restoreAllMocks());

describe('Web eID setup recovery', () => {
    it.each([
        ['Mozilla/5.0 Chrome/140.0 Safari/537.36 Edg/140.0', 0],
        ['Mozilla/5.0 Chrome/140.0 Safari/537.36', 1],
        ['Mozilla/5.0 Firefox/140.0', 2],
    ])('chooses the visiting browser store for %s', (agent, index) => {
        expect(getExtensionStore(agent)).toBe(extensionStores[index]);
    });

    it.each(['Safari/605.1.15', 'Android Chrome/140.0', 'iPhone CriOS/140.0', 'Chrome/140.0 OPR/120.0', ''])(
        'offers a browser choice rather than guessing for %s', agent => {
            expect(getExtensionStore(agent)).toBeUndefined();
        });

    it('distinguishes extension availability from native-app availability', () => {
        expect(getWebEidSetupIssue({code: webeid.ErrorCode.ERR_WEBEID_EXTENSION_UNAVAILABLE})).toBe('extension');
        expect(getWebEidSetupIssue({code: webeid.ErrorCode.ERR_WEBEID_NATIVE_UNAVAILABLE})).toBe('native');
    });

    it.each([null, new Error('card missing'), {code: webeid.ErrorCode.ERR_WEBEID_USER_CANCELLED},
        {code: webeid.ErrorCode.ERR_WEBEID_NATIVE_FATAL}, {code: webeid.ErrorCode.ERR_WEBEID_ACTION_TIMEOUT}])(
        'does not turn cancellation/card/other errors into installation requests: %s', error => {
            expect(getWebEidSetupIssue(error)).toBeNull();
        });

    it('clears stale onboarding success and shows setup recovery when the extension is unavailable', async () => {
        vi.mocked(webeid.getSigningCertificate).mockRejectedValue({code: webeid.ErrorCode.ERR_WEBEID_EXTENSION_UNAVAILABLE});
        const page = {certificate: 'previous', signatureAlgorithm: {}, webEidSetupIssue: null, ea: {publish: vi.fn()}};
        await Onboarding.prototype.checkWebEID.call(page);
        expect(page.webEidSetupIssue).toBe('extension');
        expect(page.certificate).toBeNull();
        expect(page.signatureAlgorithm).toBeNull();
        expect(page.ea.publish).not.toHaveBeenCalled();
    });

    it('keeps email confirmation in place and unlocks director retry', async () => {
        vi.mocked(webeid.getSigningCertificate).mockRejectedValue({code: webeid.ErrorCode.ERR_WEBEID_EXTENSION_UNAVAILABLE});
        const page = {confirmedDirector: undefined, emailToken: 'test-token', webEidSetupIssue: null, step: 1};
        await EmailConfirmation.prototype.confirmDirector.call(page, {id: 1, name: 'Test Director'});
        expect(page.webEidSetupIssue).toBe('extension');
        expect(page.confirmedDirector).toBeUndefined();
        expect(page.emailToken).toBe('test-token');
        expect(page.step).toBe(1);
    });

    it('unlocks signing retry for a disconnected native app without finalizing', async () => {
        vi.mocked(webeid.sign).mockRejectedValue({code: webeid.ErrorCode.ERR_WEBEID_NATIVE_UNAVAILABLE});
        const page = {prepareSigningResponse: {allowedToSign: true}, confirmedDirector: {},
            signatureAlgorithm: {hashFunction: 'SHA-256'}, confirmInProgress: false,
            webEidSetupIssue: null, finalizeSigning: vi.fn(), step: 2};
        await EmailConfirmation.prototype.confirmContract.call(page);
        expect(page.webEidSetupIssue).toBe('native');
        expect(page.confirmInProgress).toBe(false);
        expect(page.finalizeSigning).not.toHaveBeenCalled();
        expect(page.step).toBe(2);
    });
});

describe('Web eID setup messages', () => {
    it('renders the Edge store link in a new tab and preserves confirmation recovery instructions', async () => {
        vi.spyOn(navigator, 'userAgent', 'get').mockReturnValue('Chrome/140.0 Edg/140.0');
        const fixture = createFixture('<web-eid-setup issue.bind="issue" email-confirmation.bind="true"></web-eid-setup>',
            {issue: 'extension'}, [WebEidSetup]);
        await fixture.started;
        const link = fixture.appHost.querySelector('a');
        expect(link?.href).toBe(extensionStores[0].url);
        expect(link?.target).toBe('_blank');
        expect(link?.rel).toBe('noopener noreferrer');
        expect(fixture.appHost.textContent).toContain('reopen the confirmation link');
        expect(fixture.appHost.textContent).not.toContain('cannot reach the desktop');
    });

    it('renders desktop-application instructions instead of a plugin-install link for a native failure', async () => {
        const fixture = createFixture('<web-eid-setup issue.bind="issue"></web-eid-setup>',
            {issue: 'native'}, [WebEidSetup]);
        await fixture.started;
        const links = fixture.appHost.querySelectorAll('a');
        expect(links.length).toBe(1);
        expect(links[0].getAttribute('href')).toBe('/onboarding');
        expect(fixture.appHost.textContent).toContain('cannot reach the desktop');
    });
});
