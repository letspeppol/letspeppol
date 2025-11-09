import {I18N} from "@aurelia/i18n";
import {resolve} from "@aurelia/kernel";

export class LanguageSelector {
    currentLocale: string = 'nl';

    constructor(private readonly i18n: I18N = resolve(I18N)) {
        this.initLocale();
    }

    initLocale() {
        this.currentLocale = localStorage.getItem("locale");
        if (!this.currentLocale) {
            this.currentLocale = this.i18n.getLocale();
        }
    }

    public async changeLocale(locale: string) {
        this.currentLocale = locale;
        await this.i18n.setLocale(locale);
        localStorage.setItem('locale', locale);
    }
}
