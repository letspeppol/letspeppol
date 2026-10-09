import { describe, expect, it } from 'vitest';
import { InvoiceComposer } from '../../src/invoice/invoice-composer';
import en from '../../src/app/locale/translation_en.json';
import nl from '../../src/app/locale/translation_nl.json';
import fr from '../../src/app/locale/translation_fr.json';
import de from '../../src/app/locale/translation_de.json';

const resources: Record<string, unknown> = {en, nl, fr, de};

type ComposerInternals = { i18n: { tr(key: string, options?: Record<string, unknown>): string } };

function fakeI18n(locale: string) {
    return {
        tr(key: string, options: Record<string, unknown> = {}) {
            const lookup = String(options.lng ?? locale);
            const read = (path: string) => path.split('.')
                .reduce<unknown>((node, part) => (node as Record<string, unknown>)?.[part], resources[lookup]);
            const suffix = typeof options.count === 'number'
                ? `_${new Intl.PluralRules(lookup).select(options.count)}`
                : '';
            const value = read(key + suffix) ?? read(key);
            return String(value).replace(/{{(\w+)}}/g, (_, name) => String(options[name]));
        },
    };
}

function composerFor(locale = 'en'): InvoiceComposer {
    const composer = Object.create(InvoiceComposer.prototype) as InvoiceComposer;
    (composer as unknown as ComposerInternals).i18n = fakeI18n(locale);
    return composer;
}

describe('translatePaymentTerm', () => {
    it('renders each shape of term', () => {
        const composer = composerFor();
        expect(composer.translatePaymentTerm('NET_30')).toBe('30 days');
        expect(composer.translatePaymentTerm('NET_0')).toBe('Due on receipt');
        expect(composer.translatePaymentTerm('EOM_0')).toBe('End of month');
        expect(composer.translatePaymentTerm('EOM_30')).toBe('30 days end of month');
        expect(composer.translatePaymentTerm('EONM_0')).toBe('End of next month');
    });

    it('renders a single day in the singular', () => {
        const composer = composerFor();
        expect(composer.translatePaymentTerm('NET_1')).toBe('1 day');
        expect(composer.translatePaymentTerm('NET_2')).toBe('2 days');
        expect(composer.translatePaymentTerm('EOM_1')).toBe('1 day end of month');
        expect(composer.translatePaymentTerm('EONM_1')).toBe('1 day end of next month');
    });

    it('renders the term in the active language', () => {
        expect(composerFor('nl').translatePaymentTerm('EOM_30')).toBe('30 dagen einde maand');
        expect(composerFor('fr').translatePaymentTerm('EOM_30')).toBe('30 jours fin de mois');
        expect(composerFor('de').translatePaymentTerm('NET_45')).toBe('45 Tage');
    });

    it('renders a single day in the singular in every language', () => {
        expect(composerFor('nl').translatePaymentTerm('NET_1')).toBe('1 dag');
        expect(composerFor('fr').translatePaymentTerm('NET_1')).toBe('1 jour');
        expect(composerFor('de').translatePaymentTerm('NET_1')).toBe('1 Tag');
        expect(composerFor('nl').translatePaymentTerm('NET_2')).toBe('2 dagen');
        expect(composerFor('fr').translatePaymentTerm('NET_2')).toBe('2 jours');
        expect(composerFor('de').translatePaymentTerm('NET_2')).toBe('2 Tage');
    });

    it('leaves notes written by other systems alone', () => {
        expect(composerFor().translatePaymentTerm('Net within 30 days')).toBe('Net within 30 days');
    });
});

describe('derivePaymentTermCode', () => {
    it('recovers the term from a note written in another language', () => {
        const composer = composerFor('en');
        expect(composer.derivePaymentTermCode('2026-07-05', '2026-08-30', '30 dagen einde maand')).toBe('EOM_30');
        expect(composer.derivePaymentTermCode('2026-07-05', '2026-08-04', '30 jours')).toBe('NET_30');
        expect(composer.derivePaymentTermCode('2026-07-05', '2026-07-06', '1 Tag')).toBe('NET_1');
    });

    it('still reads a note whose day count was not pluralised', () => {
        expect(composerFor().derivePaymentTermCode('2026-07-05', '2026-07-06', '1 days')).toBe('NET_1');
    });

    it('recovers a term with no due date at all, as credit notes carry', () => {
        const composer = composerFor('en');
        expect(composer.derivePaymentTermCode('2026-07-05', undefined, '15 days')).toBe('NET_15');
        expect(composer.derivePaymentTermCode('2026-07-05', undefined, 'Einde maand')).toBe('EOM_0');
    });

    it('lets the due date win when the note disagrees with it', () => {
        expect(composerFor().derivePaymentTermCode('2026-07-05', '2026-07-31', '30 days')).toBe('EOM_0');
    });

    it('falls back to the dates when the note means nothing to us', () => {
        expect(composerFor().derivePaymentTermCode('2026-07-05', '2026-08-04', 'Payable per contract')).toBe('NET_30');
        expect(composerFor().derivePaymentTermCode('2026-07-05', '2026-08-04', undefined)).toBe('NET_30');
    });

    it('gives up when there is nothing to go on', () => {
        expect(composerFor().derivePaymentTermCode('2026-07-05', undefined, undefined)).toBeUndefined();
        expect(composerFor().derivePaymentTermCode('2026-07-05', '2026-07-04', 'unparseable')).toBeUndefined();
    });

    it('round-trips every term through its note in every language', () => {
        const codes = ['NET_0', 'NET_1', 'NET_15', 'NET_30', 'EOM_0', 'EOM_1', 'EOM_30', 'EONM_0', 'EONM_15'];
        for (const locale of ['en', 'nl', 'fr', 'de']) {
            const composer = composerFor(locale);
            for (const code of codes) {
                const note = composer.translatePaymentTerm(code);
                const dueDate = composer.getDueDate(code, '2026-07-05');
                expect(composer.derivePaymentTermCode('2026-07-05', dueDate, note), `${locale} ${code}`).toBe(code);
            }
        }
    });
});
