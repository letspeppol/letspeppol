import {I18nConfiguration} from '@aurelia/i18n';
import {IRouter, RouteNode} from '@aurelia/router';
import {createFixture} from '@aurelia/testing';
import {Registration} from 'aurelia';
import {afterEach, describe, expect, it, vi} from 'vitest';
import en from '../../src/app/locale/translation_en.json';
import {EmailConfirmation} from '../../src/registration/email-confirmation';
import {KYCApi} from '../../src/services/kyc/kyc-api';
import {Director, RegistrationService} from '../../src/services/kyc/registration-service';
import {PeppolDirService} from '../../src/services/peppol/peppol-dir-service';

async function mountConfirmation(bypassAvailable: boolean, directors: Director[] = []) {
    const registrationService = {
        verifyToken: vi.fn().mockResolvedValue({
            company: {identifier: '1029545627', peppolId: '0208:1029545627', name: 'Ordinary company', directors},
        }),
        supportsWebEidTestBypass: vi.fn().mockResolvedValue(bypassAvailable),
        verifyAccountWithoutWebEid: vi.fn().mockResolvedValue(undefined),
        getContractUrl: vi.fn().mockReturnValue('/kyc/api/identity/contract/0208%3A1029545627/7'),
        prepareSign: vi.fn(),
        finalizeSign: vi.fn(),
    };
    const fixture = createFixture(
        '<email-confirmation component.ref="confirmation"></email-confirmation>',
        class { confirmation: EmailConfirmation; },
        [EmailConfirmation,
            Registration.instance(IRouter, {} as IRouter),
            Registration.instance(KYCApi, {} as KYCApi),
            Registration.instance(RegistrationService, registrationService),
            Registration.instance(PeppolDirService, {findByParticipant: vi.fn().mockResolvedValue({matches: []})}),
            I18nConfiguration.customize(options => {
                options.initOptions = {lng: 'en', resources: {en: {translation: en}}};
            })],
    );
    await fixture.started;
    fixture.component.confirmation.loading({}, {queryParams: new URLSearchParams('token=test-email-link')} as RouteNode);
    await vi.waitFor(() => expect(fixture.getBy('form')).toBeTruthy());
    const button = (label: string) => fixture.getAllBy('button').find(el => el.textContent.trim() === label);
    return {fixture, registrationService, button};
}

describe('email confirmation Web eID test switch', () => {
    afterEach(() => vi.unstubAllGlobals());

    it('does not show the switch unless KYC allows it', async () => {
        const {fixture} = await mountConfirmation(false);
        expect(fixture.queryBy('[role="switch"]')).toBeNull();
    });

    it('does not show the switch in production even with a test-enabled server', async () => {
        vi.stubGlobal('__WEB_EID_TEST_BYPASS__', false);
        const {fixture, registrationService} = await mountConfirmation(true);
        expect(fixture.queryBy('[role="switch"]')).toBeNull();
        expect(registrationService.supportsWebEidTestBypass).not.toHaveBeenCalled();
    });

    it.each([7, null])('completes registration without signing (director %s)', async directorId => {
        const directors = directorId === null ? [] : [{id: directorId, name: 'Selected Director'}];
        const {fixture, registrationService, button} = await mountConfirmation(true, directors);
        await vi.waitFor(() => expect(fixture.queryBy('[role="switch"]')).toBeTruthy());
        const toggle = fixture.getBy<HTMLInputElement>('[role="switch"]');
        expect(toggle.checked).toBe(false);
        toggle.click();
        const download = vi.spyOn(fixture.component.confirmation, 'downloadFile').mockResolvedValue(undefined);
        const directorButton = en.confirmation[directorId === null ? 'test-continue' : 'director-confirm'];
        await vi.waitFor(() => expect(button(directorButton)).toBeDefined());
        button(directorButton).click();
        await vi.waitFor(() => expect(button(en.confirmation['test-complete'])?.disabled).toBe(true));
        if (directorId === null) {
            expect(fixture.queryBy('iframe')).toBeNull();
            expect(registrationService.getContractUrl).not.toHaveBeenCalled();
        } else {
            expect(registrationService.getContractUrl).toHaveBeenCalledWith('0208:1029545627', directorId);
            expect(fixture.getBy('iframe').getAttribute('src')).toContain('/kyc/api/identity/contract/');
            fixture.getBy<HTMLInputElement>('input[name="confirm"]').click();
        }

        fixture.type('input[name="password"]', 'Strong-local-pw1!');
        fixture.type('input[name="confirm-password"]', 'Strong-local-pw1!');
        const activate = button(en.confirmation['test-complete']);
        await vi.waitFor(() => expect(activate.disabled).toBe(false));
        activate.click();

        await vi.waitFor(() => fixture.assertTextContain('form', en.confirmation['success-title']));
        expect(registrationService.verifyAccountWithoutWebEid).toHaveBeenCalledWith({token: 'test-email-link', newPassword: 'Strong-local-pw1!', directorId});
        expect(download).not.toHaveBeenCalled();
        expect(registrationService.prepareSign).not.toHaveBeenCalled();
        expect(registrationService.finalizeSign).not.toHaveBeenCalled();
        expect(fixture.getBy('form').textContent).not.toContain(en.confirmation['success-contract']);
    });
});
