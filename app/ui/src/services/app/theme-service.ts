import {IContainer, singleton} from '@aurelia/kernel';
import {watch} from 'aurelia';

const STORAGE_KEY_THEME: string = 'peppol.theme.v1';

@singleton()
export class ThemeService {
    themeDark: boolean = false;

    constructor() {
        this.initTheme();
    }

    initTheme() {
        const savedTheme = localStorage.getItem(STORAGE_KEY_THEME);
        if (savedTheme === 'light' || savedTheme === 'dark') {
            this.applyTheme(savedTheme);
        } else {
            // const prefersDark = window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches; // TODO Dark theme needs restyling
            const prefersDark = false;
            this.applyTheme(prefersDark ? 'dark' : 'light');
        }
    }

    switchTheme() {
        const current = document.documentElement.getAttribute('data-theme') === 'dark' ? 'dark' : 'light';
        const next = current === 'dark' ? 'light' : 'dark';
        this.applyTheme(next);
        localStorage.setItem(STORAGE_KEY_THEME, next);
    }

    applyTheme(mode) {
        document.documentElement.setAttribute('data-theme', mode);
        this.themeDark = document.documentElement.getAttribute('data-theme') === 'dark';
    }
}
