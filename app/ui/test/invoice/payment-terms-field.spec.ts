import { describe, expect, it } from 'vitest';
import { createFixture } from '@aurelia/testing';
import { I18nConfiguration } from '@aurelia/i18n';
import { I18N } from '@aurelia/i18n';
import { InvoiceComposer } from '../../src/invoice/invoice-composer';
import { PaymentTermsField } from '../../src/components/payment-terms/payment-terms-field';
import en from '../../src/app/locale/translation_en.json';

const i18n = I18nConfiguration.customize(options => {
    options.initOptions = {lng: 'en', resources: {en: {translation: en}}};
});

const tick = () => new Promise(resolve => setTimeout(resolve, 0));

async function mount(term: string | undefined) {
    const host = class { term = term; };
    const fixture = createFixture(
        '<payment-terms-field value.two-way="term"></payment-terms-field>',
        host,
        [PaymentTermsField, i18n],
    );
    await fixture.started;
    return {
        fixture,
        days: fixture.getBy('input') as HTMLInputElement,
        basis: fixture.getBy('select') as HTMLSelectElement,
    };
}

describe('payment-terms-field', () => {
    it('shows a stored term as a day count and a basis', async () => {
        const {days, basis} = await mount('EOM_30');

        expect(days.value).toBe('30');
        expect(basis.value).toBe('END_OF_MONTH');
    });

    it('shows a term stored under the old codes', async () => {
        const {days, basis} = await mount('END_OF_NEXT_MONTH');

        expect(days.value).toBe('0');
        expect(basis.value).toBe('END_OF_NEXT_MONTH');
    });

    it('starts empty when no term is set', async () => {
        const {days, basis} = await mount(undefined);

        expect(days.value).toBe('');
        expect(basis.value).toBe('INVOICE_DATE');
    });

    it('writes a typed day count back as a term code', async () => {
        const {fixture, days} = await mount('NET_30');

        days.value = '45';
        days.dispatchEvent(new Event('change'));

        expect(fixture.component.term).toBe('NET_45');
    });

    it('writes the chosen basis back as a term code', async () => {
        const {fixture, basis} = await mount('NET_30');

        basis.value = 'END_OF_MONTH';
        basis.dispatchEvent(new Event('change'));

        expect(fixture.component.term).toBe('EOM_30');
    });

    it('reads a cleared day count as no term at all', async () => {
        const {fixture, days} = await mount('NET_30');

        days.value = '';
        days.dispatchEvent(new Event('change'));

        expect(fixture.component.term).toBe('');
    });

    it('treats a basis picked with no day count as "on that date"', async () => {
        const {fixture, days, basis} = await mount(undefined);

        basis.value = 'END_OF_MONTH';
        basis.dispatchEvent(new Event('change'));
        await tick();

        expect(fixture.component.term).toBe('EOM_0');
        expect(days.value).toBe('0');
    });

    it('labels the bases in the active language', async () => {
        const {basis} = await mount('NET_30');

        expect([...basis.options].map(option => option.textContent)).toEqual([
            'after invoice date',
            'after end of month',
            'after end of next month',
        ]);
    });
});

describe('payment term notes through real i18next', () => {
    it('picks the plural form from the day count', async () => {
        const {fixture} = await mount('NET_30');
        const composer = Object.create(InvoiceComposer.prototype) as InvoiceComposer;
        (composer as unknown as {i18n: I18N}).i18n = fixture.container.get(I18N);

        expect(composer.translatePaymentTerm('NET_1')).toBe('1 day');
        expect(composer.translatePaymentTerm('NET_30')).toBe('30 days');
        expect(composer.translatePaymentTerm('EOM_1')).toBe('1 day end of month');
        expect(composer.translatePaymentTerm('EOM_30')).toBe('30 days end of month');
        expect(composer.translatePaymentTerm('EOM_0')).toBe('End of month');
    });

    it('reads its own notes back, singular and plural alike', async () => {
        const {fixture} = await mount('NET_30');
        const composer = Object.create(InvoiceComposer.prototype) as InvoiceComposer;
        (composer as unknown as {i18n: I18N}).i18n = fixture.container.get(I18N);

        for (const code of ['NET_1', 'NET_30', 'EOM_1', 'EOM_30', 'EOM_0', 'EONM_0']) {
            const note = composer.translatePaymentTerm(code);
            const dueDate = composer.getDueDate(code, '2026-07-05');
            expect(composer.derivePaymentTermCode('2026-07-05', dueDate, note), code).toBe(code);
        }
    });
});
