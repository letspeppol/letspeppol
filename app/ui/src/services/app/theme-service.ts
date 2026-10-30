import {singleton} from '@aurelia/kernel';

const STORAGE_KEY_THEME: string = 'peppol.theme.v1';

type Theme = 'light' | 'dark';

@singleton()
export class ThemeService {
    themeDark: boolean = false;

    private readonly systemDark: MediaQueryList | undefined = window.matchMedia?.('(prefers-color-scheme: dark)');

    constructor() {
        this.initTheme();
    }

    initTheme() {
        const savedTheme = localStorage.getItem(STORAGE_KEY_THEME);
        if (savedTheme === 'light' || savedTheme === 'dark') {
            this.applyTheme(savedTheme);
        } else {
            document.documentElement.removeAttribute('data-theme');
            this.themeDark = this.resolvedTheme() === 'dark';
        }
        this.systemDark?.addEventListener('change', () => {
            this.themeDark = this.resolvedTheme() === 'dark';
        });
    }

    switchTheme() {
        const next: Theme = this.resolvedTheme() === 'dark' ? 'light' : 'dark';
        this.applyTheme(next);
        localStorage.setItem(STORAGE_KEY_THEME, next);
    }

    applyTheme(mode: Theme) {
        document.documentElement.setAttribute('data-theme', mode);
        this.themeDark = mode === 'dark';
    }

    private resolvedTheme(): Theme {
        const explicit = document.documentElement.getAttribute('data-theme');
        if (explicit === 'light' || explicit === 'dark') {
            return explicit;
        }
        return this.systemDark?.matches ? 'dark' : 'light';
    }
}
