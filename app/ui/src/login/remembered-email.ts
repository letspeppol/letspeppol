const REMEMBERED_EMAIL_KEY = 'peppol.login.email.v1';
const LOGIN_HINT_KEY = 'peppol.login.hint.v1';

export function getRememberedEmail(): string | null {
    try {
        const email = localStorage.getItem(REMEMBERED_EMAIL_KEY)?.trim();
        return email || null;
    } catch {
        return null;
    }
}

export function updateRememberedEmail(email: string, remember: boolean): void {
    try {
        if (remember) {
            localStorage.setItem(REMEMBERED_EMAIL_KEY, email.trim());
        } else {
            localStorage.removeItem(REMEMBERED_EMAIL_KEY);
        }
    } catch {
        // Login must continue when storage is unavailable (for example in a locked-down browser).
    }
}

export function rememberLoginHint(email: string): void {
    try {
        sessionStorage.setItem(LOGIN_HINT_KEY, email);
    } catch {
        return;
    }
}

export function consumeLoginHint(): string | null {
    try {
        const email = sessionStorage.getItem(LOGIN_HINT_KEY);
        sessionStorage.removeItem(LOGIN_HINT_KEY);
        return email;
    } catch {
        return null;
    }
}
